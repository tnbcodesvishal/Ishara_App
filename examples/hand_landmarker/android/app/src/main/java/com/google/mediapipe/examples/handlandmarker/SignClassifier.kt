package com.google.mediapipe.examples.handlandmarker

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import org.json.JSONArray
import org.tensorflow.lite.Interpreter
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.sqrt

/**
 * SignClassifier loads a TFLite model from assets and predicts labels for two-hand landmarks.
 * Expects model input shape: [1, 42, 2, 1] (42 points: 21 per hand, x/y)
 *
 * The normalization here mirrors your Python training preprocessing:
 * - flatten the two hands (hand1 then hand2) into 42 (x,y) pairs
 * - subtract base point (first landmark's x,y)
 * - divide by max Euclidean distance
 */
class SignClassifier(
    private val context: Context,
    private val modelPath: String = "linux_fixed.tflite",
    private val labelsPath: String = "classes.json"
) {

    companion object {
        private const val TAG = "SignClassifier"
        private const val NUM_LANDMARKS_PER_HAND = 21
        private const val NUM_HANDS = 2
        private const val TOTAL_POINTS = NUM_LANDMARKS_PER_HAND * NUM_HANDS // 42
    }

    private var tflite: Interpreter? = null
    private var labels: List<String> = emptyList()

    init {
        try {
            tflite = Interpreter(loadModelFile(context, modelPath))
            labels = loadLabels(labelsPath)
            Log.i(TAG, "TFLite model loaded. #labels=${labels.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load TFLite model or labels: ${e.message}", e)
            throw e
        }
    }

    fun close() {
        try {
            tflite?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing tflite: ${e.message}", e)
        } finally {
            tflite = null
        }
    }

    private fun loadModelFile(ctx: Context, modelPath: String): MappedByteBuffer {
        val afd = ctx.assets.openFd(modelPath)
        val inputStream = FileInputStream(afd.fileDescriptor)
        val fc = inputStream.channel
        return fc.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
    }

    private fun loadLabels(path: String): List<String> {
        val json = context.assets.open(path).bufferedReader().use(BufferedReader::readText)
        val arr = JSONArray(json)
        val out = mutableListOf<String>()
        for (i in 0 until arr.length()) out.add(arr.getString(i))
        return out
    }

    // Normalize landmarks like Python preprocessing
    private fun normalize(hands: List<List<NormalizedLandmark>>): FloatArray {
        // create flat array of size 42*2 = 84
        val pts = FloatArray(TOTAL_POINTS * 2) { 0f }

        // Fill with 0 by default; if a hand is missing its list may be empty
        var idx = 0
        for (handIndex in 0 until NUM_HANDS) {
            val hand = if (handIndex < hands.size) hands[handIndex] else emptyList()
            if (hand.isNotEmpty()) {
                for (j in 0 until minOf(hand.size, NUM_LANDMARKS_PER_HAND)) {
                    val lm = hand[j]
                    pts[idx++] = lm.x()
                    pts[idx++] = lm.y()
                }
                // if fewer than 21 landmarks (shouldn't happen), fill remaining with zeros
                val remain = NUM_LANDMARKS_PER_HAND - minOf(hand.size, NUM_LANDMARKS_PER_HAND)
                for (r in 0 until remain) {
                    pts[idx++] = 0f
                    pts[idx++] = 0f
                }
            } else {
                // missing hand -> fill 21*(x,y) zeros
                for (r in 0 until NUM_LANDMARKS_PER_HAND) {
                    pts[idx++] = 0f
                    pts[idx++] = 0f
                }
            }
        }

        // Base point -> first landmark (hand1 landmark 0)
        val baseX = pts[0]
        val baseY = pts[1]

        // Center points
        val centered = FloatArray(pts.size)
        for (i in pts.indices step 2) {
            centered[i] = pts[i] - baseX
            centered[i + 1] = pts[i + 1] - baseY
        }

        // find max Euclidean distance
        var maxD = 0f
        for (i in centered.indices step 2) {
            val dx = centered[i]
            val dy = centered[i + 1]
            val d = sqrt(dx * dx + dy * dy)
            if (d > maxD) maxD = d
        }
        if (maxD == 0f) maxD = 1f

        for (i in centered.indices) centered[i] = centered[i] / maxD

        return centered
    }

    /**
     * Accepts `hands` as List<List<NormalizedLandmark>> (0..2 lists). Pads missing hand with zeros.
     * Returns Pair(label, score).
     */
    fun predictFromLandmarks(hands: List<List<NormalizedLandmark>>): Pair<String, Float> {
        val normalized = normalize(hands) // FloatArray(84)

        // Build input array [1, 42, 2, 1]
        val input = Array(1) {
            Array(TOTAL_POINTS) { i ->
                Array(2) { j ->
                    floatArrayOf(normalized[i * 2 + j])
                }
            }
        }

        val output = Array(1) { FloatArray(labels.size) }

        val interpreter = tflite ?: throw IllegalStateException("Interpreter is null")
        interpreter.run(input, output)

        val probs = output[0]
        var bestIdx = 0
        var bestScore = probs[0]
        for (i in 1 until probs.size) {
            if (probs[i] > bestScore) {
                bestScore = probs[i]
                bestIdx = i
            }
        }

        val label = labels.getOrNull(bestIdx) ?: bestIdx.toString()
        return Pair(label, bestScore)
    }
}
