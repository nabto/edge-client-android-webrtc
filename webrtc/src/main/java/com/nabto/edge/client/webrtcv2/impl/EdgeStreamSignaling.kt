package com.nabto.edge.client.webrtcv2.impl

import android.util.Log
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.nabto.edge.client.webrtcv2.Candidate
import com.nabto.edge.client.webrtcv2.Description
import com.nabto.edge.client.webrtcv2.EdgeSignaling
import com.nabto.edge.client.webrtcv2.EdgeSignalingObserver
import com.nabto.edge.client.webrtcv2.IceServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.launch
import java.util.concurrent.CompletableFuture

enum class MessageTypes(val type: String) {
    SETUP_REQUEST("SETUP_REQUEST"),
    SETUP_RESPONSE("SETUP_RESPONSE"),
    SETUP_ERROR(type = "SETUP_ERROR"),
    DESCRIPTION(type = "DESCRIPTION"),
    CANDIDATE(type = "CANDIDATE"),
    METADATA(type = "METADATA")
}

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SignalingMessage(
    val type: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SignalingCandidate (

    override val candidate: String?,
    override val sdpMid: String?,

    // the org.webrtc library needs an sdpMLineIndex in its IceCandidate constructor, if the underlying object does not have an index the value is set to -1
    override val sdpMLineIndex: Int,
    override val usernameFragment: String?
) : Candidate {
    constructor(candidate: Candidate): this(candidate.candidate, candidate.sdpMid, candidate.sdpMLineIndex, candidate.usernameFragment) { }
}


@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SignalingCandidateMessage(
    val type: String = MessageTypes.CANDIDATE.type,
    val candidate : SignalingCandidate
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SignalingDescription(
    override val type : String,
    override val sdp : String) : Description
{
    constructor(description: Description): this(description.type, description.sdp) {}
}

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SignalingDescriptionMessage(
    val type: String = MessageTypes.DESCRIPTION.type,
    val description : SignalingDescription
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SignalingMetadataMessage(
    val type: String = MessageTypes.METADATA.type,
    val metadata: String,
)



@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SignalingSetupRequestMessage(
    val type: String = MessageTypes.SETUP_REQUEST.type,
    val polite: Boolean? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SignalingSetupResponseMessage(
    val type: String = MessageTypes.SETUP_RESPONSE.type,
    val id: String,
    val polite: Boolean,
    val iceServers: List<SignalingIceServer>?
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SignalingError(
    val errorCode: String,
    val errorDescription: String
)


@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SignalingSetupErrorMessage(
    val type: String = MessageTypes.SETUP_ERROR.type,
    val error: SignalingError,
)


@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SignalingIceServer(
    override val urls: List<String>,
    override val username: String?,
    override val credential: String?
) : IceServer

/**
 * After a stream has been made and the setup messages has been exhanged than create this class such that it is initialized in a ready state for future signaling messages.
 */
class EdgeStreamSignaling(private val stream: StringStream, val setupResponse: SignalingSetupResponseMessage ) : EdgeSignaling {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val mapper = jacksonObjectMapper()

    private val initialized = CompletableDeferred<Unit>()

    lateinit private var observer : EdgeSignalingObserver

    private val TAG = "EdgeStreamSignaling"

    fun startReceiveMessages() {
        scope.launch {
            val str = stream.revcMessage()
            val type = mapper.readValue(str, SignalingMessage::class.java)
            if (type.type == MessageTypes.DESCRIPTION.type) {
                val description = mapper.readValue(str, SignalingDescriptionMessage::class.java)
                // TODO handle exception
                observer.onSignalingDescription(description.description);
            } else if (type.type == MessageTypes.CANDIDATE.type) {
                val candidate = mapper.readValue(str, SignalingCandidateMessage::class.java)
                // TODO handle exception
                observer.onSignalingCandidate(candidate.candidate);
            } else if (type.type == MessageTypes.METADATA.type) {
                val metadata = mapper.readValue(str, SignalingMetadataMessage::class.java)
                // TODO handle exception
                observer.onSignalingMetadata(metadata.metadata);
            } else {
                Log.i(TAG, "received unknown message of type ${type.type}")
            }
        }
    }

    override fun getIceServers(): List<com.nabto.edge.client.webrtcv2.IceServer> {
        if (setupResponse.iceServers == null) {
            return listOf()
        }
        return setupResponse.iceServers;
    }

    override fun polite(): Boolean {
        return setupResponse.polite
    }

    override fun setSignalingObserver(observer: EdgeSignalingObserver) {
        this.observer = observer
        startReceiveMessages()
    }

    override fun sendDescription(
        description: Description
    ): CompletableFuture<Unit> {
        return scope.launch {
            val msg = SignalingDescriptionMessage(description = SignalingDescription(description))
            stream.sendMessage(mapper.writeValueAsString(msg))
        }.asCompletableFuture()
    }

    override fun sendCandidate(candidate: Candidate): CompletableFuture<Unit> {
        return scope.launch {
            val msg = SignalingCandidateMessage(candidate = SignalingCandidate(candidate))
            stream.sendMessage(mapper.writeValueAsString(msg))
        }.asCompletableFuture()
    }

    override fun sendMetadata(metadata: String): CompletableFuture<Unit> {
        return scope.launch {
            val msg = SignalingMetadataMessage(metadata = metadata)
            stream.sendMessage(mapper.writeValueAsString(msg))
        }.asCompletableFuture()
    }
}

