package com.nabto.edge.client.webrtc.impl

import android.util.Log
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.nabto.edge.client.webrtc.EdgeSignaling
import com.nabto.edge.client.webrtc.MetadataTrack
import com.nabto.edge.client.webrtc.SignalMessage
import com.nabto.edge.client.webrtc.SignalMessageMetadata
import com.nabto.edge.client.webrtc.SignalMessageType
import com.nabto.edge.client.webrtc.SignalingIceCandidate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

data class SDP(
    val type: String,
    val sdp: String
)

private fun SdpToMessage(sdp: SessionDescription, jsonMapper: ObjectMapper, metadata: SignalMessageMetadata): SignalMessage {
    val type = when (sdp.type) {
        SessionDescription.Type.OFFER -> SignalMessageType.OFFER
        SessionDescription.Type.PRANSWER -> SignalMessageType.ANSWER
        SessionDescription.Type.ANSWER -> SignalMessageType.ANSWER
        SessionDescription.Type.ROLLBACK -> SignalMessageType.OFFER
        null -> throw IllegalArgumentException("StringTypeFromSdp called with null as argument")
    }

    val stringType = when (sdp.type) {
        SessionDescription.Type.OFFER -> "offer"
        SessionDescription.Type.PRANSWER -> "pranswer"
        SessionDescription.Type.ANSWER -> "answer"
        SessionDescription.Type.ROLLBACK -> "rollback"
        null -> throw IllegalArgumentException("StringTypeFromSdp called with null as argument")
    }

    val data = jsonMapper.writeValueAsString(SDP(stringType, sdp.description))
    // @TODO: Metadata
    return SignalMessage(type = type, data = data, metadata = metadata)
}

internal class PerfectNegotiator(
    val peerConnection: PeerConnection,
    val polite: Boolean,
    val signaling: EdgeSignaling
) {
    var makingOffer : Boolean = false;
    var ignoreOffer = false;

    private val _receivedMetadata = mutableMapOf<String, MetadataTrack>()
    val receivedMetadata: Map<String, MetadataTrack> = _receivedMetadata

    private val scope = CoroutineScope(Dispatchers.IO)
    private val jsonMapper = jacksonObjectMapper()
    private val TAG = "EdgeNegotiator"

    init {
        scope.launch {
            while (true) {
                val signalingMessage = try {
                    signaling.recv()
                } catch (e: Exception) {
                    EdgeLogger.error("Failed to receive signaling message: $e")
                    null
                }

                if (signalingMessage != null) {
                    handleSignalingMessage(signalingMessage)
                }
            }
        }
    }

    fun dispose() {
        scope.cancel()
    }

    fun onRenegotiationNeeded() {
        // potentially a racecondition if this is called concurrently
        scope.launch {
            try {
                makingOffer = true
                setLocalDescriptionWrapper()
                signaling.send(SdpToMessage(peerConnection.localDescription, jsonMapper, createMetadata())).await()
            } catch (err: Throwable) {
                Log.e(TAG, err.message.toString())
            } finally {
                makingOffer = false;
            }
        }
    }

    fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
        if (state == PeerConnection.IceConnectionState.FAILED) {
            peerConnection.restartIce();
        }
    }

    fun onIceCandidate(candidate : IceCandidate?) {
        if (candidate == null) {
            return;
        }
        scope.launch {
            val data = jsonMapper.writeValueAsString(
                SignalingIceCandidate(
                    sdpMid = candidate.sdpMid,
                    candidate = candidate.sdp
                )
            )

            val msg = SignalMessage(type = SignalMessageType.ICE_CANDIDATE, data = data)
            signaling.send(msg).await()
        }
    }

    private suspend fun setLocalDescriptionWrapper() =
        suspendCoroutine { cont ->
            peerConnection.setLocalDescription(object : SdpObserver {
                override fun onCreateSuccess(p0: SessionDescription?) {
                }

                override fun onSetSuccess() {
                    cont.resume(Unit);
                }

                override fun onCreateFailure(p0: String?) {
                    cont.resumeWithException(Exception(p0))
                }

                override fun onSetFailure(p0: String?) {
                    cont.resumeWithException(Exception(p0));
                }
            });
        }

    private suspend fun setRemoteDescriptionWrapper(description: SessionDescription) =
        suspendCoroutine { cont ->
            peerConnection.setRemoteDescription(object : SdpObserver {
                override fun onCreateSuccess(p0: SessionDescription?) {
                }

                override fun onSetSuccess() {
                    cont.resume(Unit);
                }

                override fun onCreateFailure(p0: String?) {
                    cont.resumeWithException(Exception(p0))
                }

                override fun onSetFailure(p0: String?) {
                    cont.resumeWithException(Exception(p0));
                }
            }, description);
        }

    private fun handleMetadata(data: SignalMessageMetadata) {
        if (data.status != null && data.status == "FAILED") {
            if (data.tracks != null) {
                for (track in data.tracks) {
                    if (track.error != null) {
                        EdgeLogger.error("Device reported track ${track.mid}:${track.trackId} failed with error: ${track.error}")
                    }
                }
            }
        }

        data.tracks?.forEach { track ->
            _receivedMetadata[track.mid] = track
        }
    }

    private suspend fun handleDescription(description: SessionDescription, metadata: SignalMessageMetadata?) {
        val offerCollision = description.type == SessionDescription.Type.OFFER && (makingOffer || peerConnection.signalingState() != PeerConnection.SignalingState.STABLE)

        ignoreOffer = !polite && offerCollision;
        if (ignoreOffer) {
            return;
        }

        if (metadata != null) {
            handleMetadata(metadata)
        }

        setRemoteDescriptionWrapper(description)
        if (description.type == SessionDescription.Type.OFFER) {
            setLocalDescriptionWrapper()
            signaling.send(SdpToMessage(peerConnection.localDescription, jsonMapper, createMetadata())).await()
        }
    }

    private suspend fun handleSignalingMessage(message: SignalMessage) {
        EdgeLogger.info("Negotiator received message: ${message.type}")

        try {
            when (message.type) {
                SignalMessageType.OFFER,
                SignalMessageType.ANSWER -> {
                    val description = try {
                        val type = if (message.type == SignalMessageType.OFFER) {
                            SessionDescription.Type.OFFER
                        } else {
                            SessionDescription.Type.ANSWER
                        }
                        val data = jsonMapper.readValue(message.data!!, SDP::class.java)
                        SessionDescription(type, data.sdp)
                    } catch (e: Exception) {
                        null
                    }
                    if (description != null) {
                        handleDescription(description, message.metadata)
                    }
                }

                SignalMessageType.ICE_CANDIDATE -> {
                    val candidate = try {
                        val json = jsonMapper.readValue(message.data!!, SignalingIceCandidate::class.java)
                        IceCandidate(json.sdpMid, 0, json.candidate)
                    } catch (e: Exception) {
                        null
                    }

                    if (candidate != null) {
                        try {
                            peerConnection.addIceCandidate(candidate)
                        } catch (err : Throwable) {
                            if (!ignoreOffer) {
                                throw err;
                            }
                        }
                    }
                }

                else -> {
                    EdgeLogger.error("PerfectNegotiator received message of unexpected type ${message.type}.")
                }
            }
        } catch (err: Throwable) {
            Log.e(TAG, err.message.toString())
        }
    }

    private fun createMetadata(): SignalMessageMetadata {
        val tracks = mutableListOf<MetadataTrack>()

        peerConnection.transceivers.forEach { transceiver ->
            val mid = transceiver.mid
            val metaTrack = receivedMetadata[mid]
            if (metaTrack != null) {
                tracks.add(metaTrack)
            }
        }

        var status = "OK"
        tracks.forEach { track ->
            if (track.error != null && track.error != "OK") {
                status = "FAILED"
            }
        }

        return SignalMessageMetadata(
            tracks = tracks,
            status =  status
        )
    }
}