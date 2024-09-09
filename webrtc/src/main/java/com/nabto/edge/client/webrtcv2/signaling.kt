package com.nabto.edge.client.webrtcv2

import com.fasterxml.jackson.annotation.JsonInclude
import com.nabto.edge.client.Connection
import com.nabto.edge.client.webrtcv2.impl.NabtoSignalingFactoryImpl
import org.webrtc.PeerConnection
import java.util.concurrent.CompletableFuture

interface Description {
    val type: String
    val sdp: String
}

data class DescriptionImpl(
    override val type: String,
    override val sdp: String,
) : Description

interface Candidate {
    val candidate: String?
    val sdpMid: String?

    // the org.webrtc library needs an sdpMLineIndex in its IceCandidate constructor, if the underlying object does not have an index the value is set to -1
    val sdpMLineIndex: Int
    val usernameFragment: String?
}

data class CandidateImpl(
    override val candidate: String?,
    override val sdpMid: String?,
    override val sdpMLineIndex: Int,
    override val usernameFragment: String?
) : Candidate

interface IceServer {
    val urls: List<String>
    val username: String?
    val credential: String?
}

/**
 * Observer interface for signaling messages for webrtc connections
 */
interface EdgeSignalingObserver {
    fun onSignalingCandidate(candidate : Candidate)
    fun onSignalingDescription(description : Description)
    fun onSignalingMetadata(metadata : String)
}

/**
 * Singaling interface to use with PeerConnections.
 */
interface EdgeSignaling {
    fun getIceServers(): List<IceServer>

    /**
     * Return true if this end should act as polite in perfect negotiation.
     */
    fun polite() : Boolean

    /**
     * Set an observer for descriptions and candidates coming from the other peer.
     */
    fun setSignalingObserver(observer : EdgeSignalingObserver)

    /**
     * Exceptions: if the description cannot be sent or another error occurs the method throws an exception
     */
    fun sendDescription(description: Description) : CompletableFuture<Unit>
    /**
     * Exceptions: if the description cannot be sent or another error occurs the method throws an exception
     */
    fun sendCandidate(candidate: Candidate) : CompletableFuture<Unit>

    fun sendMetadata(metadata: String) : CompletableFuture<Unit>
}

/**
 * Given a nabto connection create a signaling stream based on a nabto stream.
 */
interface NabtoStreamSignalingFactory {
    companion object {
        /**
         * Create edge
         */
        fun createEdgeSignaling(connection : Connection) : CompletableFuture<EdgeSignaling> {
            return NabtoSignalingFactoryImpl.createEdgeSignaling(connection);
        }
    }
}