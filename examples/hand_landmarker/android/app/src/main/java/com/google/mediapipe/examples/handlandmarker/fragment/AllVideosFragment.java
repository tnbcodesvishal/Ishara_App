package com.google.mediapipe.examples.handlandmarker.fragment;


import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import androidx.recyclerview.widget.RecyclerView;


import com.google.mediapipe.examples.handlandmarker.R;
import com.google.mediapipe.examples.handlandmarker.VideoAdapter;
import com.google.mediapipe.examples.handlandmarker.VideoModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AllVideosFragment extends Fragment {

    private RecyclerView recyclerView;
    private EditText edtSearch;
    private Button btnRandom;
    private VideoAdapter adapter;
    private List<VideoModel> videoList;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_all_videos, container, false);

        recyclerView = view.findViewById(R.id.recyclerVideos);
        edtSearch = view.findViewById(R.id.edtSearchVideo);
        btnRandom = view.findViewById(R.id.btnRandomVideo);

        videoList = new ArrayList<>();

        // --- ADD YOUR VIDEOS HERE (make sure these raw resources exist) ---
//        videoList.add(new VideoModel("Hello", R.raw.hello));
//        videoList.add(new VideoModel("Thank You", R.raw.thankyou));
//        videoList.add(new VideoModel("Sorry", R.raw.sorry));
//        videoList.add(new VideoModel("Goodbye", R.raw.goodbye));
//        videoList.add(new VideoModel("Please", R.raw.please));
//        videoList.add(new VideoModel("Yes", R.raw.yes));
//
//        // Alphabet (sample)
//        videoList.add(new VideoModel("A", R.raw.a));
//        videoList.add(new VideoModel("B", R.raw.b));
//        videoList.add(new VideoModel("C", R.raw.c));
        // add the rest (d..z) and numbers as you have them
        // ------------------------------------------------------------------

        adapter = new VideoAdapter(requireContext(), videoList, model -> {
            VideoPopupFragment popup = VideoPopupFragment.newInstance(model.getVideoRes());
            popup.show(getParentFragmentManager(), "play_video");
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        btnRandom.setOnClickListener(v -> {
            if (videoList.isEmpty()) return;
            Random r = new Random();
            int idx = r.nextInt(videoList.size());
            VideoModel m = videoList.get(idx);
            VideoPopupFragment popup = VideoPopupFragment.newInstance(m.getVideoRes());
            popup.show(getParentFragmentManager(), "random_video");
        });

        return view;
    }
}
