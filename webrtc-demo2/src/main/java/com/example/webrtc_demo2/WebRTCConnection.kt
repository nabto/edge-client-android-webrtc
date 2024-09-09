package com.example.webrtc_demo2

import com.nabto.edge.client.Connection
import com.nabto.edge.client.webrtcv2.Candidate
import com.nabto.edge.client.webrtcv2.Description
import com.nabto.edge.client.webrtcv2.EdgeSignaling
import com.nabto.edge.client.webrtcv2.EdgeSignalingObserver
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.RtpReceiver

/**
 * When media is avialable the VideoConnection creator is notified.
 */
interface MediaHandler {
    fun onAddStream(p0: MediaStream?)
}

class WebRTCConnection(val signaling : EdgeSignaling, val peerConnectionManager: PeerConnectionManager, val mediaHandler: MediaHandler) : PeerConnection.Observer, EdgeSignalingObserver {
    private lateinit var conn: Connection
    private var peerConnection: PeerConnection?
    private var perfectNegotiator: PerfectNegotiator
    private val TAG = "WebRTC"

    init {
        val signalingIceServers = signaling.getIceServers()
        val iceServers = signalingIceServers.map { it ->
            PeerConnection.IceServer.builder(it.urls).run {
                it.username?.let { setUsername(it) }
                it.credential?.let { setPassword(it) }
                createIceServer()
            }
        }
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        rtcConfig.enableImplicitRollback = true
        peerConnection = peerConnectionManager.peerConnectionFactory.createPeerConnection(rtcConfig, this)
        perfectNegotiator = PerfectNegotiator(this?.peerConnection!!, signaling, signaling.polite())
    }

    fun createDataChannel(label : String) : DataChannel? {
        return peerConnection?.createDataChannel(label, DataChannel.Init());
    }

    override fun onSignalingChange(p0: PeerConnection.SignalingState?) {
    }

    override fun onIceConnectionReceivingChange(p0: Boolean) {
    }

    override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {
    }

    override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {
    }

    override fun onAddStream(p0: MediaStream?) {
        mediaHandler.onAddStream(p0);
    }

    override fun onRemoveStream(p0: MediaStream?) {
    }

    override fun onDataChannel(p0: DataChannel?) {
    }

    override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
        super.onAddTrack(receiver, mediaStreams)
    }

    // Handle signaling messages.

    // Handle the three events coming from the WebRTC PeerConnection
    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
        perfectNegotiator.onIceConnectionChange(state);
    }

    override fun onRenegotiationNeeded() {
        perfectNegotiator.onRenegotiationNeeded()
    }

    override fun onIceCandidate(candidate: IceCandidate?) {
        perfectNegotiator.onIceCandidate(candidate)
    }

    // Handle candidates and descriptions coming from the remote peer via signaling
    override fun onSignalingCandidate(candidate: Candidate) {
        perfectNegotiator.onCandidateFromSignaling(candidate);
    }

    override fun onSignalingDescription(description: Description) {
        perfectNegotiator.onDescriptionFromSignaling(description);
    }

    override fun onSignalingMetadata(metadata: String) {
        // Handle metadata here if neccessary
    }
}