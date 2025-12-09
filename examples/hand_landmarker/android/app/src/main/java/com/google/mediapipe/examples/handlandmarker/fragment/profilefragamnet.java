package com.google.mediapipe.examples.handlandmarker.fragment;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.google.mediapipe.examples.handlandmarker.R;

public class profilefragamnet extends Fragment {
private LinkageError btnHello;
private LinearLayout btnThankYou;



    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_profilefragamnet, container, false);






        return view;
    }

    private void showVideo(int videoRes) {
        VideoPopupFragment popup = VideoPopupFragment.newInstance(videoRes);
        popup.show(getParentFragmentManager(), "video_popup");
    }
}