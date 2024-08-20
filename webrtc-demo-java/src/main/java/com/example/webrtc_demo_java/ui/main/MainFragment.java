package com.example.webrtc_demo_java.ui.main;

import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.example.webrtc_demo_java.R;
import com.nabto.edge.client.Coap;
import com.nabto.edge.client.Connection;
import com.nabto.edge.client.ConnectionEventsCallback;
import com.nabto.edge.client.NabtoClient;
import com.nabto.edge.client.webrtc.EdgeAudioTrack;
import com.nabto.edge.client.webrtc.EdgeMediaTrackType;
import com.nabto.edge.client.webrtc.EdgeVideoTrack;
import com.nabto.edge.client.webrtc.EdgeVideoView;
import com.nabto.edge.client.webrtc.EdgeWebrtcConnection;
import com.nabto.edge.client.webrtc.EdgeWebrtcManager;

import org.json.JSONException;
import org.json.JSONObject;

public class MainFragment extends Fragment {
    private MainViewModel mViewModel;
    private EdgeVideoView mVideoView;
    private NabtoClient mClient;

    public static MainFragment newInstance() {
        return new MainFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mViewModel = new ViewModelProvider(this).get(MainViewModel.class);
        mClient = NabtoClient.create(requireContext());
        mViewModel.establishConnection(mClient);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mVideoView = view.findViewById(R.id.videoView);
        EdgeWebrtcManager.getInstance().initVideoView(mVideoView);

        mViewModel.getRemoteVideoTrack().observe(getViewLifecycleOwner(), new Observer<EdgeVideoTrack>() {
            @Override
            public void onChanged(EdgeVideoTrack edgeVideoTrack) {
                edgeVideoTrack.add(mVideoView);
            }
        });
    }
}