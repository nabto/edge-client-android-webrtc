package com.example.webrtc_demo_java.ui.main;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

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

public class MainViewModel extends ViewModel {
    // @TODO: Things like connections and track objects should not live in a viewmodel
    //        but rather in ConnectionManager or somesuch class that handles open device connections.
    private Connection conn;
    private EdgeWebrtcConnection peerConn;
    private EdgeAudioTrack remoteAudioTrack;

    private MutableLiveData<EdgeVideoTrack> remoteVideoTrack;

    private void onConnected() {
        peerConn = EdgeWebrtcManager.getInstance().createRTCConnection(conn);
        peerConn.onTrack((track, trackId) -> {
            if (track.getType() == EdgeMediaTrackType.VIDEO) {
                remoteVideoTrack.postValue((EdgeVideoTrack)track);
            }

            if (track.getType() == EdgeMediaTrackType.AUDIO) {
                remoteAudioTrack = (EdgeAudioTrack)track;
                remoteAudioTrack.setEnabled(true);
            }
            return null;
        });

        peerConn.connect().whenComplete((result, exc) -> {
            Coap coap = conn.createCoap("GET", "/webrtc/get");
            coap.executeCallback((ec, res) -> {
                Log.i("TestApp", "Coap response: " + coap.getResponseStatusCode());
                if (coap.getResponseStatusCode() != 201) {
                    Log.i("TestApp", "Failed to get video feed with status " + coap.getResponseStatusCode());
                }
            });
        });
    }

    private void onClosed() {
        // Do something if the connection closes.
    }

    void establishConnection(NabtoClient client) {
        conn = client.createConnection();

        JSONObject opts = new JSONObject();
        try {
            opts.put("ProductId", "pr-3cbjt7cj");
            opts.put("DeviceId", "de-dmexphxx");
            opts.put("PrivateKey", client.createPrivateKey());
            opts.put("ServerConnectToken", "demosct");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        conn.updateOptions(opts.toString());
        conn.addConnectionEventsListener(new ConnectionEventsCallback() {
            @Override
            public void onEvent(int event) {
                if (event == CONNECTED) {
                    onConnected();
                }

                if (event == CLOSED) {
                    onClosed();
                }
            }
        });

        conn.connectCallback((ec, res) -> {});
    }

    public LiveData<EdgeVideoTrack> getRemoteVideoTrack() {
        if (remoteVideoTrack == null) {
            remoteVideoTrack = new MutableLiveData<>();
        }
        return remoteVideoTrack;
    }
}