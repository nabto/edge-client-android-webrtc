package com.nabto.edge.client.webrtc

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonValue
import kotlinx.coroutines.Deferred

enum class SignalMessageType(@get:JsonValue val num: Int) {
    OFFER(0),
    ANSWER(1),
    ICE_CANDIDATE(2),
    TURN_REQUEST(3),
    TURN_RESPONSE(4)
}

data class SignalingIceCandidate(
    val sdpMid: String,
    val candidate: String
)

data class TurnServer(
    val hostname: String,
    val port: Int,
    val username: String,
    val password: String
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class IceServer(
    val urls: List<String>,
    val username: String? = null,
    val credential: String? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class MetadataTrack(
    val mid: String,
    val trackId: String,
    val error: String? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class SignalMessageMetadata(
    val tracks: List<MetadataTrack>? = null,
    val noTrickle: Boolean = false,
    val status: String? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class SignalMessage(
    val type: SignalMessageType,
    val data: String? = null,
    val servers: List<TurnServer>? = null,
    val iceServers: List<IceServer>? = null,
    val metadata: SignalMessageMetadata? = null
)

interface EdgeSignaling {
    suspend fun send(msg: SignalMessage): Deferred<Unit>
    suspend fun recv(): SignalMessage
}
