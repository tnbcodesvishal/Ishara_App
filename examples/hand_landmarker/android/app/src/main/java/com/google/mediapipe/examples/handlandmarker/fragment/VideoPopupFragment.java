package com.google.mediapipe.examples.handlandmarker.fragment;



import android.app.Dialog;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.mediapipe.examples.handlandmarker.R;

public class VideoPopupFragment extends DialogFragment {

    private static final String ARG_VIDEO = "video_res";

    public static VideoPopupFragment newInstance(int videoRes) {
        VideoPopupFragment fragment = new VideoPopupFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_VIDEO, videoRes);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_video_popup, container, false);

        VideoView videoView = view.findViewById(R.id.popupVideoView);
        Button btnClose = view.findViewById(R.id.btnClose);

        int videoRes = getArguments().getInt(ARG_VIDEO);

        String path = "android.resource://" + getContext().getPackageName() + "/" + videoRes;
        Uri uri = Uri.parse(path);

        videoView.setVideoURI(uri);
        videoView.start();

        btnClose.setOnClickListener(v -> dismiss());

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();

        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
    }
}
