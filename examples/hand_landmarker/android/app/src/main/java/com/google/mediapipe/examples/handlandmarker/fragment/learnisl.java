package com.google.mediapipe.examples.handlandmarker.fragment;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.google.mediapipe.examples.handlandmarker.R;


public class learnisl extends Fragment {

    private LinearLayout btnHello;
    private LinearLayout btnThankYou;

    private LinearLayout pleasee;

    private LinearLayout like;
    private LinearLayout trust;

    private LinearLayout goodmoring;
    private LinearLayout slow;
    private LinearLayout team;
    private LinearLayout want;
    private LinearLayout water;




    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_learnisl, container, false);

        btnHello = view.findViewById(R.id.hello);
        btnThankYou = view.findViewById(R.id.thankyou);
        pleasee = view.findViewById(R.id.please);

        like = view.findViewById(R.id.Like);
        trust = view.findViewById(R.id.trust);
        goodmoring = view.findViewById(R.id.goodmorning);

        slow = view.findViewById(R.id.slow);
        team = view.findViewById(R.id.team);
        want = view.findViewById(R.id.want);

        water = view.findViewById(R.id.water);





        slow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showVideo(R.raw.slow);
            }
        });

        team.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showVideo(R.raw.team);
            }
        });

        want.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showVideo(R.raw.want);
            }
        });

        water.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showVideo(R.raw.water);
            }
        });

        btnHello.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showVideo(R.raw.hello);
            }
        });

        btnThankYou.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showVideo(R.raw.thankyou);
            }
        });

        pleasee.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showVideo(R.raw.please);
            }
        });


        like.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showVideo(R.raw.like);
            }
        });

        trust.setOnClickListener(new View.OnClickListener() {
            @Override

            public void onClick(View v) {
                showVideo(R.raw.trust);
            }
        });

        goodmoring.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showVideo(R.raw.goodmorning);
            }
        });

        return view;
    }

    private void showVideo(int videoRes) {
        VideoPopupFragment popup = VideoPopupFragment.newInstance(videoRes);
        popup.show(getParentFragmentManager(), "video_popup");
    }
}