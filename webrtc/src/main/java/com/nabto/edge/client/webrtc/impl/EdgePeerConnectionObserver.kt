package com.nabto.edge.client.webrtc.impl

import android.util.Log
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.PeerConnection

internal open class EdgePeerConnectionObserver(val peerName: String) : PeerConnection.Observer {
    override fun onSignalingChange(p0: PeerConnection.SignalingState?) {
        EdgeLogger.info("$peerName signaling state changed t o ${p0.toString()}")
    }

    override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {
        EdgeLogger.info("$peerName ice connection state changed to ${p0.toString()}")
    }

    override fun onIceConnectionReceivingChange(p0: Boolean) {
        EdgeLogger.info("$peerName onIceConnectionReceivingChange $p0")
    }

    override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {
        EdgeLogger.info("$peerName ice gathering state changed to ${p0.toString()}")
    }

    override fun onIceCandidate(p0: IceCandidate?) {
        EdgeLogger.info("$peerName added ice candidate ${p0.toString()}")
    }

    override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {
        EdgeLogger.info("$peerName removed ice candidates ${p0.toString()}")
    }

    override fun onAddStream(p0: MediaStream?) {
        EdgeLogger.info("$peerName added MediaStream ${p0.toString()}")
    }

    override fun onRemoveStream(p0: MediaStream?) {
        EdgeLogger.info("$peerName removed MediaStream ${p0.toString()}")
    }

    override fun onDataChannel(p0: DataChannel?) {
        EdgeLogger.info("$peerName received data channel ${p0.toString()}")
    }

    override fun onRenegotiationNeeded() {
        EdgeLogger.info("$peerName renegotiation needed!")
    }
}
