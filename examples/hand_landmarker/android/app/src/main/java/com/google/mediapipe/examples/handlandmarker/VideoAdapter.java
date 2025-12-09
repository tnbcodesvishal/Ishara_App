package com.google.mediapipe.examples.handlandmarker;



import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;


import java.util.ArrayList;
import java.util.List;

public class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VideoViewHolder> {

    public interface OnVideoClickListener {
        void onVideoClick(VideoModel model);
    }

    private Context context;
    private List<VideoModel> videoList;
    private List<VideoModel> fullList;
    private OnVideoClickListener listener;

    public VideoAdapter(Context context, List<VideoModel> videoList, OnVideoClickListener listener) {
        this.context = context;
        this.videoList = videoList;
        this.listener = listener;
        this.fullList = new ArrayList<>(videoList);
    }

    @NonNull
    @Override
    public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_video, parent, false);
        return new VideoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
        VideoModel model = videoList.get(position);
        holder.txtName.setText(model.getVideoName());
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onVideoClick(model);
        });
    }

    @Override
    public int getItemCount() {
        return videoList.size();
    }

    public void filter(String text) {
        videoList.clear();
        if (text == null || text.trim().isEmpty()) {
            videoList.addAll(fullList);
        } else {
            String lower = text.toLowerCase();
            for (VideoModel m : fullList) {
                if (m.getVideoName().toLowerCase().contains(lower)) {
                    videoList.add(m);
                }
            }
        }
        notifyDataSetChanged();
    }

    static class VideoViewHolder extends RecyclerView.ViewHolder {
        TextView txtName;
        VideoViewHolder(@NonNull View itemView) {
            super(itemView);
            txtName = itemView.findViewById(R.id.txtVideoName);
        }
    }
}
