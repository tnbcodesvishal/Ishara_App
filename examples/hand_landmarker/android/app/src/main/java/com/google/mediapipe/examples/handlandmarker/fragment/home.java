package com.google.mediapipe.examples.handlandmarker.fragment;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.mediapipe.examples.handlandmarker.R;


public class home extends Fragment {
    private ImageView profile;
    private LinearLayout camera;
    private LinearLayout learn;
    private LinearLayout speak;
    private LinearLayout signlearned;
    private LinearLayout minutespra;
    private LinearLayout progress;





    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_home, container, false);
profile = view.findViewById(R.id.profilereal);
camera=view.findViewById(R.id.camerapage);
learn=view.findViewById(R.id.learnislmo);
speak=view.findViewById(R.id.speakk);
signlearned=view.findViewById(R.id.signlearned);
minutespra=view.findViewById(R.id.minutespra);
progress=view.findViewById(R.id.progressss);


        profile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Fragment fragment = new profilefragamnet();

                requireActivity()
                        .getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.main, fragment)
                        .addToBackStack(null)
                        .commit();
            }
        });


camera.setOnClickListener(new View.OnClickListener() {
    @Override
    public void onClick(View v) {

        Fragment fragment = new CameraFragment();

        requireActivity()
                .getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.main, fragment)
                .addToBackStack(null)
                .commit();
    }
});


        learn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Fragment fragment = new learnisl();

                requireActivity()
                        .getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.main, fragment)
                        .addToBackStack(null)
                        .commit();
            }
        });

        speak.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Fragment fragment = new SignTranslateFragment();

                requireActivity()
                        .getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.main, fragment)
                        .addToBackStack(null)
                        .commit();
            }
        });

        signlearned.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getActivity(), "Signs Learned", Toast.LENGTH_SHORT).show();
            }
        });

        minutespra.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getActivity(), "Minutes Practiced", Toast.LENGTH_SHORT).show();
            }
        });

        progress.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getActivity(), "progress", Toast.LENGTH_SHORT).show();
            }
        });
    return view;
    }
}