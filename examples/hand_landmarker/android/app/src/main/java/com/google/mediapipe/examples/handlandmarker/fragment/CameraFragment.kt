package com.google.mediapipe.examples.handlandmarker.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.mediapipe.examples.handlandmarker.HandLandmarkerHelper
import com.google.mediapipe.examples.handlandmarker.SignClassifier
import com.google.mediapipe.examples.handlandmarker.databinding.FragmentCameraBinding
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.collections.ArrayDeque
import kotlin.collections.HashMap

class CameraFragment : Fragment(), HandLandmarkerHelper.LandmarkerListener {

    companion object {
        private const val TAG = "CameraFragment"

        // Tuning params (feel free to tweak)
        private const val NO_HAND_FRAMES_THRESHOLD = 20               // ~2s (depends on analyzer FPS)
        private const val STABILITY_WINDOW = 12                      // keep last N predictions
        private const val STABILITY_THRESHOLD = 8                    // need label occurrence >= this to accept
        private const val SAME_SIGN_COOLDOWN_MS = 5000L              // cooldown for same sign (ms)
        private const val MIN_TIME_BETWEEN_DIFFERENT_SIGNS_MS = 2000L // min time between different signs (ms)
        private const val LETTER_COOLDOWN_FRAMES = 6                 // short frame cooldown after accept

        // dictionary used for segmentation (uppercase expected)
        private val DICTIONARY = setOf(
            "HELLO"
        )
    }

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    private lateinit var executor: ExecutorService
    private var handHelper: HandLandmarkerHelper? = null
    private var classifier: SignClassifier? = null
    private var cameraProvider: ProcessCameraProvider? = null

    @Volatile private var isActiveFragment = false
    private var isFrontCamera = true

    // TTS
    private var tts: TextToSpeech? = null

    // Buffers & state
    private val predictionBuffer: ArrayDeque<String> = ArrayDeque()
    private var lastAddedSign: String = ""
    private var lastSignTimeMs: Long = 0
    private var letterCooldownFrames: Int = 0

    // separation logic so duplicates require a separation or no-hand gap
    private var hadDifferentSinceLastAccept = true
    private var noHandSinceLastAccept = false

    // text building
    private val letterStream = StringBuilder()   // uppercase letters accepted so far (stream)
    private val currentWord = StringBuilder()    // building word (uppercase)
    private val sentence = StringBuilder()       // confirmed words (uppercase)

    private var noHandFrames = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        isActiveFragment = true
        executor = Executors.newSingleThreadExecutor()

        // init TTS
        tts = TextToSpeech(requireContext()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
            }
        }

        // set up camera once view ready
        binding.viewFinder.post { setUpCamera() }

        // double-tap to switch camera
        val gestureDetector = GestureDetector(requireContext(),
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    switchCamera()
                    return true
                }
            }
        )
        binding.viewFinder.setOnTouchListener { _, ev ->
            gestureDetector.onTouchEvent(ev)
            true
        }

        // reset & speak
        binding.btnReset.setOnClickListener { resetAll() }
        binding.btnSpeak.setOnClickListener {
            val final = buildFinalSentence()
            if (final.isNotBlank()) {
                // speak full sentence (words)
                speakText(final, flush = true)
            } else {
                Toast.makeText(requireContext(), "Nothing to speak", Toast.LENGTH_SHORT).show()
            }
        }

        // init mediapipe & classifier in background
        executor.execute {
            try {
                handHelper = HandLandmarkerHelper(
                    context = requireContext(),
                    runningMode = RunningMode.LIVE_STREAM,
                    maxNumHands = 2,
                    handLandmarkerHelperListener = this
                )
                Log.i(TAG, "HandLandmarkerHelper initialized")
            } catch (e: Exception) {
                Log.e(TAG, "HandLandmarker init failed: ${e.message}")
            }
        }

        executor.execute {
            try {
                classifier = SignClassifier(requireContext())
                Log.i(TAG, "SignClassifier loaded")
            } catch (e: Exception) {
                Log.e(TAG, "Classifier load failed: ${e.message}")
                classifier = null
            }
        }
    }

    private fun switchCamera() {
        isFrontCamera = !isFrontCamera
        resetAll()
        Toast.makeText(requireContext(), if (isFrontCamera) "Front Camera" else "Back Camera", Toast.LENGTH_SHORT).show()
        bindCameraUseCases()
    }

    private fun resetAll() {
        synchronized(predictionBuffer) { predictionBuffer.clear() }
        letterStream.clear()
        currentWord.clear()
        sentence.clear()
        lastAddedSign = ""
        lastSignTimeMs = 0
        letterCooldownFrames = 0
        noHandFrames = 0
        hadDifferentSinceLastAccept = true
        noHandSinceLastAccept = false

        activity?.runOnUiThread {
            binding.predictedWord.text = "Predicted Sign: -"
            binding.currentWord.text = "Word: "
            binding.sentenceText.text = "Final Translation: "
            binding.bottomSheetLayout.inferenceTimeVal.text = ""
        }
    }

    private fun speakText(text: String, flush: Boolean = false) {
        // flush=true -> speak full sentence immediately (clear queue)
        if (flush) tts?.stop()
        tts?.speak(text, if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "TTS_${System.currentTimeMillis()}")
    }

    private fun setUpCamera() {
        val future = ProcessCameraProvider.getInstance(requireContext())
        future.addListener({
            cameraProvider = future.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return

        val preview = Preview.Builder()
            .setTargetRotation(binding.viewFinder.display.rotation)
            .build()
        preview.setSurfaceProvider(binding.viewFinder.surfaceProvider)

        val analyzer = ImageAnalysis.Builder()
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        analyzer.setAnalyzer(executor) { image ->
            if (!isActiveFragment) { image.close(); return@setAnalyzer }
            handHelper?.detectLiveStream(image, isFrontCamera)
        }

        val selector = if (isFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

        provider.unbindAll()
        provider.bindToLifecycle(this, selector, preview, analyzer)
    }

    // segmentation helper: break letter stream into longest-known prefix of dictionary words
    private fun segmentSentence(input: String): Pair<String, String> {
        val str = input.uppercase(Locale.US)
        val n = str.length
        if (n == 0) return "" to ""

        val dp = IntArray(n + 1) { -1 }
        dp[0] = 0

        for (i in 1..n) {
            for (j in i - 1 downTo 0) {
                if (dp[j] != -1) {
                    val word = str.substring(j, i)
                    if (DICTIONARY.contains(word)) {
                        dp[i] = j
                        break
                    }
                }
            }
        }

        var longest = 0
        for (i in 1..n) if (dp[i] != -1) longest = i
        if (longest == 0) return "" to str.lowercase(Locale.US)

        val words = mutableListOf<String>()
        var cur = longest
        while (cur > 0) {
            val prev = dp[cur]
            words.add(str.substring(prev, cur))
            cur = prev
        }
        words.reverse()
        val sentenceStr = words.joinToString(" ").lowercase(Locale.US)
        val remainder = str.substring(longest).lowercase(Locale.US)
        return sentenceStr to remainder
    }

    private fun buildFinalSentence(): String {
        val confirmed = sentence.toString().trim().lowercase(Locale.US)
        val (seg, rem) = segmentSentence(letterStream.toString())
        return listOf(confirmed, seg, rem).filter { it.isNotBlank() }.joinToString(" ").trim()
    }

    // ----------------------- main onResults -----------------------
    override fun onResults(resultBundle: HandLandmarkerHelper.ResultBundle) {
        if (!isActiveFragment) return

        executor.execute {
            val mpResult = resultBundle.results.firstOrNull() ?: return@execute

            val raw = mpResult.landmarks()
            val landmarkLists = ArrayList<List<NormalizedLandmark>>()
            try {
                @Suppress("UNCHECKED_CAST")
                landmarkLists.addAll(raw as Collection<out List<NormalizedLandmark>>)
            } catch (e: Exception) {
                Log.e(TAG, "Landmark cast error: ${e.message}")
                return@execute
            }

            // NO HAND -> count frames and finish current word if threshold reached
            if (landmarkLists.isEmpty() || classifier == null) {
                noHandFrames++
                if (noHandFrames > 0) noHandSinceLastAccept = true

                if (noHandFrames >= NO_HAND_FRAMES_THRESHOLD && currentWord.isNotEmpty()) {
                    // finalize current word
                    val w = currentWord.toString()
                    sentence.append("$w ")
                    currentWord.clear()

                    // keep letterStream in sync (remove prefix if matches)
                    val ls = letterStream.toString()
                    if (ls.startsWith(w)) {
                        letterStream.delete(0, w.length)
                    }

                    activity?.runOnUiThread {
                        binding.currentWord.text = "Word: "
                        binding.sentenceText.text = "Sentence: ${sentence.toString().trim()}"
                    }

                    // speak full sentence (confirmed + segmentation) — flush queue to avoid letter parroting
                    val finalPreview = buildFinalSentence()
                    if (finalPreview.isNotBlank()) speakText(finalPreview, flush = true)

                    // after finishing word, require re-separation for duplicates
                    hadDifferentSinceLastAccept = false
                    noHandSinceLastAccept = false
                }
                return@execute
            } else {
                noHandFrames = 0
            }

            // prepare hands for classifier
            val hands = listOf(
                landmarkLists[0],
                if (landmarkLists.size > 1) landmarkLists[1] else emptyList()
            )

            val cls = classifier ?: return@execute
            val (labelRaw, score) = try {
                cls.predictFromLandmarks(hands)
            } catch (e: Exception) {
                Log.e(TAG, "predict error: ${e.message}")
                return@execute
            }

            val label = labelRaw.trim().uppercase(Locale.US)

            // low-confidence filter
            if (score < 0.70f) {
                activity?.runOnUiThread {
                    binding.bottomSheetLayout.inferenceTimeVal.text = "${resultBundle.inferenceTime} ms | - (low)"
                }
                // still add to buffer to maintain history
                synchronized(predictionBuffer) {
                    if (predictionBuffer.size >= STABILITY_WINDOW) predictionBuffer.removeFirst()
                    predictionBuffer.addLast(label)
                }
                return@execute
            }

            // update predicted UI
            activity?.runOnUiThread {
                binding.predictedWord.text = "Predicted: $label"
            }

            // maintain stability buffer
            synchronized(predictionBuffer) {
                if (predictionBuffer.size >= STABILITY_WINDOW) predictionBuffer.removeFirst()
                predictionBuffer.addLast(label)
            }

            // compute stable label (appears >= STABILITY_THRESHOLD in buffer)
            val stableLabel = synchronized(predictionBuffer) {
                val map = HashMap<String, Int>()
                for (p in predictionBuffer) map[p] = (map[p] ?: 0) + 1
                map.entries.find { it.value >= STABILITY_THRESHOLD }?.key
            }

            // frame cooldown
            if (letterCooldownFrames > 0) letterCooldownFrames--

            // if a different stable label appears mark separation allowed
            if (stableLabel != null && stableLabel != lastAddedSign) hadDifferentSinceLastAccept = true

            if (stableLabel != null) {
                val now = System.currentTimeMillis()
                val allowByTime: Boolean = if (stableLabel == lastAddedSign) {
                    // same sign: allow only if separation/no-hand/cooldown elapsed
                    hadDifferentSinceLastAccept || noHandSinceLastAccept || (now - lastSignTimeMs) >= SAME_SIGN_COOLDOWN_MS
                } else {
                    // different sign: require a small gap
                    (now - lastSignTimeMs) >= MIN_TIME_BETWEEN_DIFFERENT_SIGNS_MS
                }

                if (!allowByTime) {
                    // blocked by timing/separation rules
                    return@execute
                }

                // Accept single-letter signs if cooldown frames ok
                if (stableLabel.length == 1 && letterCooldownFrames == 0) {
                    val upper = stableLabel.uppercase(Locale.US)
                    val lastCharSame = letterStream.isNotEmpty() && letterStream.last().toString() == upper
                    if (lastCharSame && !(hadDifferentSinceLastAccept || noHandSinceLastAccept)) {
                        // duplicate without separation -> skip
                    } else {
                        // ACCEPT letter
                        letterStream.append(upper)
                        currentWord.append(upper)
                        lastAddedSign = stableLabel
                        lastSignTimeMs = now
                        letterCooldownFrames = LETTER_COOLDOWN_FRAMES

                        // reset separation flags because we accepted
                        hadDifferentSinceLastAccept = false
                        noHandSinceLastAccept = false

                        activity?.runOnUiThread {
                            binding.currentWord.text = "Word: $currentWord"
                        }

                        // update segmentation preview
                        val (segmented, remainder) = segmentSentence(letterStream.toString())
                        activity?.runOnUiThread {
                            binding.sentenceText.text = "Segmented: $segmented | Remain: $remainder"
                        }

                        // Speak the accepted letter (QUEUE_ADD so letters are spoken in order)
                        // To make letters pronounce separately, pass with a space
                        speakText(upper.lowercase(Locale.US), flush = false)
                    }
                } else if (stableLabel.length > 1) {
                    // classifier returned a whole word; accept as confirmed word
                    val wordUpper = stableLabel.uppercase(Locale.US)
                    val now2 = System.currentTimeMillis()
                    if (!(letterStream.isNotEmpty() && letterStream.endsWith(wordUpper))) {
                        sentence.append("$wordUpper ")
                        lastAddedSign = stableLabel
                        lastSignTimeMs = now2
                        letterStream.clear()
                        currentWord.clear()
                        hadDifferentSinceLastAccept = false
                        noHandSinceLastAccept = false

                        activity?.runOnUiThread {
                            binding.currentWord.text = "Word: "
                            binding.sentenceText.text = "Sentence: ${sentence.toString().trim()}"
                        }

                        // speak the full word (lowercase natural)
                        speakText(wordUpper.lowercase(Locale.US), flush = false)
                    }
                }

                // clear buffer after accepting so it doesn't immediately re-trigger
                synchronized(predictionBuffer) { predictionBuffer.clear() }
            }

            // update inference time label
            activity?.runOnUiThread {
                binding.bottomSheetLayout.inferenceTimeVal.text = "${resultBundle.inferenceTime} ms | $labelRaw (${String.format("%.2f", score)})"
            }
        }
    }

    override fun onPause() {
        super.onPause()
        isActiveFragment = false
        cameraProvider?.unbindAll()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isActiveFragment = false
        executor.shutdownNow()
        handHelper?.handLandmarker?.close()
        classifier?.close()
        tts?.shutdown()
        handHelper = null
        classifier = null
        cameraProvider = null
        _binding = null
    }

    override fun onError(error: String, errorCode: Int) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), "Error: $error", Toast.LENGTH_SHORT).show()
        }
    }
}


//
//package com.google.mediapipe.examples.handlandmarker.fragment
//
//import android.annotation.SuppressLint
//import android.os.Bundle
//import android.speech.tts.TextToSpeech
//import android.util.Log
//import android.view.GestureDetector
//import android.view.LayoutInflater
//import android.view.MotionEvent
//import android.view.View
//import android.view.ViewGroup
//import android.widget.Toast
//import androidx.camera.core.*
//import androidx.camera.lifecycle.ProcessCameraProvider
//import androidx.core.content.ContextCompat
//import androidx.fragment.app.Fragment
//import com.google.mediapipe.examples.handlandmarker.HandLandmarkerHelper
//import com.google.mediapipe.examples.handlandmarker.SignClassifier
//import com.google.mediapipe.examples.handlandmarker.databinding.FragmentCameraBinding
//import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
//import com.google.mediapipe.tasks.vision.core.RunningMode
//import java.util.*
//import java.util.concurrent.ExecutorService
//import java.util.concurrent.Executors
//
//class CameraFragment : Fragment(), HandLandmarkerHelper.LandmarkerListener {
//
//    companion object {
//        private const val TAG = "CameraFragment"
//
//        // Stability logic
//        private const val NO_HAND_FRAMES_THRESHOLD = 20
//        private const val STABILITY_WINDOW = 12
//        private const val STABILITY_THRESHOLD = 8
//        private const val SAME_SIGN_COOLDOWN_MS = 5000L
//        private const val MIN_TIME_BETWEEN_SIGNS_MS = 2000L
//        private const val LETTER_COOLDOWN_FRAMES = 6
//
//        // Noise filters
//        private const val MIN_CONFIDENCE = 0.85f
//        private const val MIN_LANDMARKS = 8
//
//        private val DICTIONARY = setOf("HELLO", "GOOD", "MORNING")
//    }
//
//    private var _binding: FragmentCameraBinding? = null
//    private val binding get() = _binding!!
//
//    private lateinit var executor: ExecutorService
//    private var handHelper: HandLandmarkerHelper? = null
//    private var classifier: SignClassifier? = null
//    private var cameraProvider: ProcessCameraProvider? = null
//
//    private var isActiveFragment = false
//    private var isFrontCamera = true
//
//    private var tts: TextToSpeech? = null
//
//    // Buffers / state
//    private val predictionBuffer = ArrayDeque<String>()
//    private var lastAddedSign = ""
//    private var lastSignTime = 0L
//    private var letterCooldown = 0
//    private var hadDifferentSinceLast = true
//    private var noHandSinceLast = false
//    private var noHandFrames = 0
//
//    // Text building
//    private val letterStream = StringBuilder()
//    private val currentWord = StringBuilder()
//    private val sentence = StringBuilder()
//
//    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
//        _binding = FragmentCameraBinding.inflate(inflater, container, false)
//        return binding.root
//    }
//
//    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//        super.onViewCreated(view, savedInstanceState)
//
//        isActiveFragment = true
//        executor = Executors.newSingleThreadExecutor()
//
//        tts = TextToSpeech(requireContext()) {
//            if (it == TextToSpeech.SUCCESS) tts?.language = Locale.US
//        }
//
//        binding.viewFinder.post { setUpCamera() }
//
//        // Double tap → switch camera
//        val gd = GestureDetector(requireContext(),
//            object : GestureDetector.SimpleOnGestureListener() {
//                override fun onDoubleTap(e: MotionEvent): Boolean {
//                    switchCamera()
//                    return true
//                }
//            })
//
//        binding.viewFinder.setOnTouchListener { _, ev ->
//            gd.onTouchEvent(ev)
//            true
//        }
//
//        binding.btnReset.setOnClickListener { resetAll() }
//
//        binding.btnSpeak.setOnClickListener {
//            val final = buildFinalSentence()
//            if (final.isNotBlank()) speakText(final, flush = true)
//        }
//
//        executor.execute {
//            try {
//                handHelper = HandLandmarkerHelper(
//                    context = requireContext(),
//                    runningMode = RunningMode.LIVE_STREAM,
//                    maxNumHands = 2,
//                    handLandmarkerHelperListener = this
//                )
//            } catch (_: Exception) {}
//        }
//
//        executor.execute {
//            try { classifier = SignClassifier(requireContext()) }
//            catch (_: Exception) {}
//        }
//    }
//
//    private fun switchCamera() {
//        isFrontCamera = !isFrontCamera
//        resetAll()
//        activity?.runOnUiThread {
//            Toast.makeText(requireContext(), if (isFrontCamera) "Front Camera" else "Back Camera", Toast.LENGTH_SHORT).show()
//        }
//        bindCameraUseCases()
//    }
//
//    private fun resetAll() {
//        predictionBuffer.clear()
//        letterStream.clear()
//        currentWord.clear()
//        sentence.clear()
//
//        lastAddedSign = ""
//        lastSignTime = 0L
//        letterCooldown = 0
//        noHandFrames = 0
//        hadDifferentSinceLast = true
//        noHandSinceLast = false
//
//        activity?.runOnUiThread {
//            binding.predictedWord.text = "Predicted: -"
//            binding.currentWord.text = "Word:"
//            binding.sentenceText.text = "Sentence:"
//        }
//    }
//
//    private fun speakText(text: String, flush: Boolean = false) {
//        if (flush) {
//            tts?.stop()
//            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "FINAL_${System.currentTimeMillis()}")
//        } else {
//            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "SEQ_${System.currentTimeMillis()}")
//        }
//    }
//
//    private fun setUpCamera() {
//        val providerFuture = ProcessCameraProvider.getInstance(requireContext())
//        providerFuture.addListener({
//            cameraProvider = providerFuture.get()
//            bindCameraUseCases()
//        }, ContextCompat.getMainExecutor(requireContext()))
//    }
//
//    @SuppressLint("UnsafeOptInUsageError")
//    private fun bindCameraUseCases() {
//        val provider = cameraProvider ?: return
//
//        val preview = Preview.Builder()
//            .build()
//            .apply { setSurfaceProvider(binding.viewFinder.surfaceProvider) }
//
//        val analysis = ImageAnalysis.Builder()
//            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
//            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
//            .build()
//
//        analysis.setAnalyzer(executor) { img ->
//            if (!isActiveFragment) { img.close(); return@setAnalyzer }
//            handHelper?.detectLiveStream(img, isFrontCamera)
//        }
//
//        provider.unbindAll()
//        provider.bindToLifecycle(
//            this,
//            if (isFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA,
//            preview,
//            analysis
//        )
//    }
//
//    // ---------------- segmentation ----------------
//    private fun segmentSentence(input: String): Pair<String, String> {
//        val s = input.uppercase(Locale.US)
//        if (s.isEmpty()) return "" to ""
//
//        val n = s.length
//        val dp = IntArray(n + 1) { -1 }
//        dp[0] = 0
//
//        for (i in 1..n) {
//            for (j in i - 1 downTo 0) {
//                if (dp[j] != -1) {
//                    val word = s.substring(j, i)
//                    if (DICTIONARY.contains(word)) {
//                        dp[i] = j
//                        break
//                    }
//                }
//            }
//        }
//
//        var end = 0
//        for (i in 1..n) if (dp[i] != -1) end = i
//        if (end == 0) return "" to s.lowercase(Locale.US)
//
//        val list = mutableListOf<String>()
//        var cur = end
//
//        while (cur > 0) {
//            val prev = dp[cur]
//            list.add(s.substring(prev, cur))
//            cur = prev
//        }
//
//        list.reverse()
//        return list.joinToString(" ").lowercase() to s.substring(end).lowercase()
//    }
//
//    private fun buildFinalSentence(): String {
//        val confirmed = sentence.toString().trim().lowercase()
//        val (seg, rem) = segmentSentence(letterStream.toString())
//        return listOf(confirmed, seg, rem).filter { it.isNotBlank() }.joinToString(" ").trim()
//    }
//
//    // --------------- NO HAND HANDLING ---------------------
//    private fun handleNoHand() {
//        noHandFrames++
//        noHandSinceLast = true
//
//        if (noHandFrames >= NO_HAND_FRAMES_THRESHOLD && currentWord.isNotEmpty()) {
//            val w = currentWord.toString()
//            sentence.append("$w ")
//
//            currentWord.clear()
//            if (letterStream.startsWith(w)) letterStream.delete(0, w.length)
//
//            val final = buildFinalSentence()
//            if (final.isNotBlank()) speakText(final, flush = true)
//
//            activity?.runOnUiThread {
//                binding.currentWord.text = "Word:"
//                binding.sentenceText.text = "Sentence: ${sentence.toString().trim()}"
//            }
//
//            hadDifferentSinceLast = false
//            noHandSinceLast = false
//        }
//    }
//
//    // ---------------- MAIN CALLBACK -----------------------
//    override fun onResults(resultBundle: HandLandmarkerHelper.ResultBundle) {
//        if (!isActiveFragment) return
//
//        executor.execute {
//
//            val mpResult = resultBundle.results.firstOrNull() ?: return@execute
//            val raw = mpResult.landmarks()
//
//            val landmarks = try { raw as List<List<NormalizedLandmark>> }
//            catch (_: Exception) { return@execute }
//
//            if (landmarks.isEmpty() || landmarks[0].size < MIN_LANDMARKS) {
//                handleNoHand()
//                return@execute
//            }
//
//            noHandFrames = 0
//
//            val hands = listOf(
//                landmarks[0],
//                if (landmarks.size > 1) landmarks[1] else emptyList()
//            )
//
//            val cls = classifier ?: return@execute
//            val (labelRaw, score) = try { cls.predictFromLandmarks(hands) }
//            catch (_: Exception) { return@execute }
//
//            if (score < MIN_CONFIDENCE) {
//                activity?.runOnUiThread {
//                    binding.predictedWord.text = "Predicted: -"
//                }
//                return@execute
//            }
//
//            val label = labelRaw.trim().uppercase(Locale.US)
//
//            activity?.runOnUiThread {
//                binding.predictedWord.text = "Predicted: $label"
//            }
//
//            synchronized(predictionBuffer) {
//                if (predictionBuffer.size >= STABILITY_WINDOW) predictionBuffer.removeFirst()
//                predictionBuffer.add(label)
//            }
//
//            val stable = synchronized(predictionBuffer) {
//                val freq = HashMap<String, Int>()
//                for (x in predictionBuffer) freq[x] = (freq[x] ?: 0) + 1
//                freq.entries.find { it.value >= STABILITY_THRESHOLD }?.key
//            }
//
//            if (letterCooldown > 0) letterCooldown--
//
//            if (stable != null && stable != lastAddedSign)
//                hadDifferentSinceLast = true
//
//            if (stable != null) {
//
//                val now = System.currentTimeMillis()
//                val allow = if (stable == lastAddedSign) {
//                    hadDifferentSinceLast || noHandSinceLast ||
//                            now - lastSignTime >= SAME_SIGN_COOLDOWN_MS
//                } else now - lastSignTime >= MIN_TIME_BETWEEN_SIGNS_MS
//
//                if (!allow) return@execute
//
//                // Accept single letter
//                if (stable.length == 1 && letterCooldown == 0) {
//
//                    val upper = stable.uppercase()
//
//                    val duplicate = letterStream.isNotEmpty() && letterStream.last().toString() == upper
//                    if (!(duplicate && !hadDifferentSinceLast && !noHandSinceLast)) {
//
//                        letterStream.append(upper)
//                        currentWord.append(upper)
//
//                        lastAddedSign = stable
//                        lastSignTime = now
//                        letterCooldown = LETTER_COOLDOWN_FRAMES
//
//                        hadDifferentSinceLast = false
//                        noHandSinceLast = false
//
//                        activity?.runOnUiThread {
//                            binding.currentWord.text = "Word: $currentWord"
//                        }
//
//                        val (seg, rem) = segmentSentence(letterStream.toString())
//                        activity?.runOnUiThread {
//                            binding.sentenceText.text = "Segmented: $seg | Remain: $rem"
//                        }
//
//                        speakText(upper.lowercase(), flush = false)
//                    }
//                }
//
//                predictionBuffer.clear()
//            }
//
//            // inference display
//            activity?.runOnUiThread {
//                binding.bottomSheetLayout.inferenceTimeVal.text =
//                    "${resultBundle.inferenceTime} ms | $labelRaw (${String.format("%.2f", score)})"
//            }
//        }
//    }
//
//    override fun onPause() {
//        super.onPause()
//        isActiveFragment = false
//        cameraProvider?.unbindAll()
//    }
//
//    override fun onDestroyView() {
//        super.onDestroyView()
//        isActiveFragment = false
//        executor.shutdownNow()
//        handHelper?.handLandmarker?.close()
//        classifier?.close()
//        tts?.shutdown()
//        _binding = null
//    }
//
//    override fun onError(error: String, errorCode: Int) {
//        activity?.runOnUiThread {
//            Toast.makeText(requireContext(), "Error: $error", Toast.LENGTH_SHORT).show()
//        }
//    }
//}
