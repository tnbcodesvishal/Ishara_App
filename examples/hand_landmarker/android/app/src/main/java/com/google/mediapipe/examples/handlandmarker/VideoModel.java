package com.google.mediapipe.examples.handlandmarker;



public class VideoModel {
    private String videoName;
    private int videoRes;

    public VideoModel(String videoName, int videoRes) {
        this.videoName = videoName;
        this.videoRes = videoRes;
    }

    public String getVideoName() {
        return videoName;
    }

    public int getVideoRes() {
        return videoRes;
    }
}
