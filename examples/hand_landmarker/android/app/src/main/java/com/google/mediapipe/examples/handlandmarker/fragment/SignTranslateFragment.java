package com.google.mediapipe.examples.handlandmarker.fragment;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.airbnb.lottie.LottieAnimationView;
import com.bumptech.glide.Glide;
import com.google.mediapipe.examples.handlandmarker.R;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SignTranslateFragment extends Fragment {

    private static final int REQ_RECORD_AUDIO = 101;
    private static final long IMAGE_DURATION = 800L;

    boolean isActive = false;
    boolean viewDestroyed = false;

    ImageView imgSign;
    PlayerView playerView;
    LottieAnimationView lottieVoice;
    TextView txtStatus;

    SpeechRecognizer speechRecognizer;
    Intent speechIntent;

    ExoPlayer player;

    Deque<String> mediaQueue = new ArrayDeque<>();

    Map<String, Integer> localSigns = new HashMap<>();

    public SignTranslateFragment() {}

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        isActive = true;
        viewDestroyed = false;

        View view = inflater.inflate(R.layout.fragment_sign_translate, container, false);

        imgSign = view.findViewById(R.id.imgSign);
        playerView = view.findViewById(R.id.playerView);
        lottieVoice = view.findViewById(R.id.voiceAnim);
        txtStatus = view.findViewById(R.id.txtStatus);

        setupLocalSigns();
        setupPlayer();
        setupSpeechRecognition();
        requestMicPermission();

        return view;
    }

    private void setupLocalSigns() {
        // Add ALL your local video mappings here
        localSigns.put("hello", R.raw.hello);
        localSigns.put("i", R.raw.i);
        localSigns.put("like", R.raw.like);
        localSigns.put("want", R.raw.want);
        localSigns.put("team", R.raw.team);
        localSigns.put("thankyou", R.raw.thankyou);
        localSigns.put("goodmorning", R.raw.goodmorning);
        localSigns.put("water", R.raw.water);
        localSigns.put("please", R.raw.please);
        localSigns.put("slow", R.raw.slow);
        localSigns.put("trust", R.raw.trust);
        localSigns.put("eat", R.raw.eat);

        // Add more signs as needed
    }

    private void setupPlayer() {
        player = new ExoPlayer.Builder(requireContext()).build();
        playerView.setPlayer(player);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_ENDED && isActive) {
                    playNextItem();
                }
            }
        });
    }

    private void requestMicPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQ_RECORD_AUDIO);
        } else {
            startVoiceAnimation();
            startSpeech();
        }
    }

    private void setupSpeechRecognition() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext());

        speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {}
            @Override public void onBeginningOfSpeech() { txtStatus.setText("Listening..."); }
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() { txtStatus.setText("Processing..."); }

            @Override
            public void onError(int error) { txtStatus.setText("Error"); stopVoiceAnimation(); }

            @Override
            public void onResults(Bundle results) {
                stopVoiceAnimation();

                ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (list != null && !list.isEmpty()) {
                    String spoken = list.get(0);
                    txtStatus.setText(spoken);
                    processSentence(spoken);
                }
            }

            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });
    }

    private void startSpeech() {
        speechRecognizer.startListening(speechIntent);
    }

    private void startVoiceAnimation() {
        lottieVoice.setVisibility(View.VISIBLE);
        lottieVoice.setAnimation(R.raw.voice_anim);
        lottieVoice.playAnimation();
    }

    private void stopVoiceAnimation() {
        lottieVoice.cancelAnimation();
        lottieVoice.setVisibility(View.GONE);
    }

    // 🔥 MAIN LOGIC — NO FIREBASE, LOCAL ONLY
    private void processSentence(String sentence) {
        String clean = sentence.toLowerCase().replaceAll("[^a-z\\s]", " ").trim();
        String[] parts = clean.split("\\s+");

        List<String> words = new ArrayList<>();
        for (String w : parts) if (!w.isEmpty()) words.add(w);

        mediaQueue.clear();

        for (String w : words) {

            if (localSigns.containsKey(w)) {
                int resId = localSigns.get(w);
                mediaQueue.add("VID|android.resource://" + requireContext().getPackageName() + "/" + resId);
            } else {
                // Fallback to alphabet images
                for (char c : w.toCharArray()) {
                    if (Character.isLetter(c)) {
                        mediaQueue.add("IMG|file:///android_asset/signs/" + Character.toUpperCase(c) + ".jpg");
                    }
                }
            }
        }

        txtStatus.setText("Playing...");
        playNextItem();
    }

    private void playNextItem() {
        if (mediaQueue.isEmpty()) {
            txtStatus.setText("Done");
            return;
        }

        String item = mediaQueue.poll();

        if (item.startsWith("VID|")) {
            playVideo(item.substring(4));
        } else {
            playImage(item.substring(4));
        }
    }

    private void playImage(String url) {
        imgSign.setVisibility(View.VISIBLE);
        playerView.setVisibility(View.GONE);

        Glide.with(requireContext()).load(url).into(imgSign);

        imgSign.postDelayed(() -> {
            if (isActive) playNextItem();
        }, IMAGE_DURATION);
    }

    private void playVideo(String url) {
        imgSign.setVisibility(View.GONE);
        playerView.setVisibility(View.VISIBLE);

        player.setMediaItem(MediaItem.fromUri(Uri.parse(url)));
        player.prepare();
        player.play();
    }

    @Override
    public void onPause() {
        super.onPause();
        isActive = false;
        if (player != null) player.pause();
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
            speechRecognizer.cancel();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        isActive = false;
        viewDestroyed = true;

        if (player != null) {
            player.release();
            player = null;
        }

        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }

        mediaQueue.clear();
    }
}
