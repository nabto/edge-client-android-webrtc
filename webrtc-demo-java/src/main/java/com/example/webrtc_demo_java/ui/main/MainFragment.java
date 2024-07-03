package com.example.webrtc_demo_java.ui.main;

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
    private Connection mConn;
    private EdgeWebrtcConnection mPeerConn;
    private EdgeVideoTrack mRemoteVideoTrack;
    private EdgeAudioTrack mRemoteAudioTrack;

    public static MainFragment newInstance() {
        return new MainFragment();
    }

    private void onConnected() {
        mPeerConn = EdgeWebrtcManager.getInstance().createRTCConnection(mConn);

        mPeerConn.onTrack((track, trackId) -> {
            if (track.getType() == EdgeMediaTrackType.VIDEO) {
                mRemoteVideoTrack = (EdgeVideoTrack)track;
                mRemoteVideoTrack.add(mVideoView);
            }

            if (track.getType() == EdgeMediaTrackType.AUDIO) {
                mRemoteAudioTrack = (EdgeAudioTrack)track;
                mRemoteAudioTrack.setEnabled(true);
            }
            return null;
        });

        mPeerConn.connect().whenComplete((result, exc) -> {
            Coap coap = mConn.createCoap("GET", "/webrtc/get");
            coap.execute();
            Log.i("TestApp", "Coap response: " + coap.getResponseStatusCode());
            if (coap.getResponseStatusCode() != 201) {
                Log.i("TestApp", "Failed to get video feed with status " + coap.getResponseStatusCode());
            }
        });
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mViewModel = new ViewModelProvider(this).get(MainViewModel.class);
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
        EdgeWebrtcManager.getInstance().initVideoView(mVideoView); // @TODO
        NabtoClient client = NabtoClient.create(requireActivity());
        mConn = client.createConnection();

        JSONObject opts = new JSONObject();
        try {
            opts.put("ProductId", "pr-3cbjt7cj");
            opts.put("DeviceId", "de-dmexphxx");
            opts.put("PrivateKey", client.createPrivateKey());
            opts.put("ServerConnectToken", "demosct");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        mConn.updateOptions(opts.toString());
        mConn.addConnectionEventsListener(new ConnectionEventsCallback() {
            @Override
            public void onEvent(int event) {
                if (event == CONNECTED) {
                    onConnected();
                }
            }
        });

        mConn.connect();
    }
}