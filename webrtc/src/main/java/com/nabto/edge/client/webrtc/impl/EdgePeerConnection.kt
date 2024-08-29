package com.nabto.edge.client.webrtc.impl

import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.nabto.edge.client.Coap
import com.nabto.edge.client.NabtoRuntimeException
import com.nabto.edge.client.Stream
import com.nabto.edge.client.ktx.awaitExecute
import com.nabto.edge.client.ktx.awaitOpen
import com.nabto.edge.client.ktx.awaitStreamClose
import com.nabto.edge.client.webrtc.EdgeDataChannel
import com.nabto.edge.client.webrtc.EdgeMediaTrack
import com.nabto.edge.client.webrtc.EdgeMediaTrackType
import com.nabto.edge.client.webrtc.EdgeSignaling
import com.nabto.edge.client.webrtc.EdgeWebrtcConnection
import com.nabto.edge.client.webrtc.EdgeWebrtcError
import com.nabto.edge.client.webrtc.MetadataTrack
import com.nabto.edge.client.webrtc.OnClosedCallback
import com.nabto.edge.client.webrtc.OnErrorCallback
import com.nabto.edge.client.webrtc.OnTrackCallback
import com.nabto.edge.client.webrtc.SignalMessage
import com.nabto.edge.client.webrtc.SignalMessageType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpTransceiver
import org.webrtc.VideoTrack
import java.lang.IllegalStateException
import java.util.concurrent.CompletableFuture

internal class EdgePeerConnection(
    private val peerConnectionFactory: PeerConnectionFactory,
    private val webrtcInfoCoap: Coap,
    private val signalingStream: Stream,
    private val signaling: EdgeSignaling = EdgeStreamSignaling(signalingStream)
) : EdgeWebrtcConnection, EdgePeerConnectionObserver("client")
{
    // Peer connection and negotiator
    private var peerConnection: PeerConnection? = null
    private var perfectNegotiator: PerfectNegotiator? = null

    // Callbacks
    private var onClosedCallback: OnClosedCallback? = null
    private var onTrackCallback: OnTrackCallback? = null
    private var onErrorCallback: OnErrorCallback? = null

    // Misc
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onClosed(cb: OnClosedCallback) {
        onClosedCallback = cb
    }

    override fun onTrack(cb: OnTrackCallback) {
        onTrackCallback = cb
    }

    override fun onError(cb: OnErrorCallback) {
        onErrorCallback = cb
    }

    override fun connect(): CompletableFuture<Unit> {
        val connectPromise = CompletableDeferred<Unit>()
        scope.launch {
            awaitTurnResponse(connectPromise)
        }

        return scope.future {
            try {
                connectSignalingStream(signalingStream)
                signaling.start()
            } catch (error: EdgeWebrtcError.SignalingFailedToInitialize) {
                EdgeLogger.error("Failed to initialize signaling service.")
                throw error
            }

            val turnRequest = SignalMessage(type = SignalMessageType.TURN_REQUEST)
            signaling.send(turnRequest).await()
            connectPromise.await()
        }
    }

    override fun createDataChannel(label: String): EdgeDataChannel {
        val init = DataChannel.Init()
        val dc = peerConnection?.createDataChannel(label, init)
        if (dc == null) {
            throw IllegalStateException("createDataChannel was called before the connection was established.")
        } else {
            return EdgeDataChannelImpl(dc)
        }
    }

    override fun addTrack(track: EdgeMediaTrack, streamIds: List<String>) {
        val rtcTrack = when (track.type) {
            EdgeMediaTrackType.AUDIO -> (track as EdgeAudioTrackImpl).track
            EdgeMediaTrackType.VIDEO -> (track as EdgeVideoTrackImpl).track
        }
        peerConnection?.addTrack(rtcTrack, streamIds)
    }

    override fun connectionClose(): CompletableFuture<Unit> {
        return scope.future {
            perfectNegotiator?.dispose()
            peerConnection?.dispose()

            try {
                signalingStream.awaitStreamClose()
            } catch (exception: NabtoRuntimeException) {
                EdgeLogger.warning("Attempted to close signaling service but received error $exception")
            }
        }
    }

    // Forward to PerfectNegotiator
    override fun onRenegotiationNeeded() {
        super.onRenegotiationNeeded()
        perfectNegotiator?.onRenegotiationNeeded()
    }

    // Forward to PerfectNegotiator
    override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {
        super.onIceConnectionChange(p0)
        perfectNegotiator?.onIceConnectionChange(p0)
    }

    // Forward to PerfectNegotiator
    override fun onIceCandidate(p0: IceCandidate?) {
        super.onIceCandidate(p0)
        perfectNegotiator?.onIceCandidate(p0)
    }

    override fun onTrack(transceiver: RtpTransceiver?) {
        super.onTrack(transceiver)
        val track = transceiver?.receiver?.track()
        track?.let { t ->
            val metadata = perfectNegotiator?.receivedMetadata?.get(transceiver.mid)

            if (t.kind() == "video") {
                val videoTrack = t as VideoTrack
                onTrackCallback?.invoke(EdgeVideoTrackImpl(videoTrack), metadata?.trackId)
            }

            if (t.kind() == "audio") {
                val audioTrack = t as AudioTrack
                onTrackCallback?.invoke(EdgeAudioTrackImpl(audioTrack), metadata?.trackId)
            }
        }
    }

    private suspend fun awaitTurnResponse(connectPromise: CompletableDeferred<Unit>) {
        val msg = try {
            signaling.recv()
        } catch (e: Exception) {
            EdgeLogger.error("Failed to receive turn response message: $e")
            connectPromise.completeExceptionally(EdgeWebrtcError.SignalingFailedRecv())
            onErrorCallback?.invoke(EdgeWebrtcError.SignalingFailedRecv())
            return
        }

        EdgeLogger.info("awaitTurnResponse received message of type ${msg.type}")
        if (msg.type == SignalMessageType.TURN_RESPONSE) {
            val iceServers = mutableListOf<PeerConnection.IceServer>()
            if (msg.iceServers != null) {
                for (server in msg.iceServers) {
                    iceServers.add(
                        PeerConnection.IceServer.builder(server.urls).run {
                            server.username?.let { setUsername(it) }
                            server.credential?.let { setPassword(it) }
                            createIceServer()
                        }
                    )
                }
            }

            if (msg.servers != null) {
                for (server in msg.servers) {
                    iceServers.add(
                        PeerConnection.IceServer.builder(server.hostname).run {
                            setUsername(server.username)
                            setPassword(server.password)
                            createIceServer()
                        }
                    )
                }
            }

            if (iceServers.isEmpty()) {
                EdgeLogger.error("Turn response message does not include any ice server information!")
            }

            setupPeerConnection(iceServers)
            connectPromise.complete(Unit)
        } else {
            EdgeLogger.error("Expected message of type TURN_RESPONSE for setting up connection but received ${msg.type}")
        }
    }

    private fun setupPeerConnection(iceServers: List<PeerConnection.IceServer>) {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        rtcConfig.enableImplicitRollback = true
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, this)
        if (peerConnection != null) {
            perfectNegotiator = PerfectNegotiator(peerConnection!!, true, signaling)
        }
    }

    private suspend fun connectSignalingStream(stream: Stream) {
        webrtcInfoCoap.awaitExecute()

        if (webrtcInfoCoap.responseStatusCode != 205) {
            EdgeLogger.error("Unexpected /p2p/webrtc-info return code ${webrtcInfoCoap.responseStatusCode}")
            throw EdgeWebrtcError.SignalingFailedToInitialize()
        }

        val rtcInfo = if (webrtcInfoCoap.responseContentFormat == 60) {
            val cborMapper = CBORMapper().registerKotlinModule()
            cborMapper.readValue(webrtcInfoCoap.responsePayload, RTCInfo::class.java)
        } else {
            jacksonObjectMapper().readValue(webrtcInfoCoap.responsePayload, RTCInfo::class.java)
        }

        try {
            stream.awaitOpen(rtcInfo.signalingStreamPort.toInt())
        } catch (e: NabtoRuntimeException) {
            EdgeLogger.error("Failed to open Nabto stream for signaling: $e")
            throw EdgeWebrtcError.SignalingFailedToInitialize()
        }
    }
}
