package com.nabto.edge.client.webrtc.impl

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.nabto.edge.client.Connection
import com.nabto.edge.client.NabtoRuntimeException
import com.nabto.edge.client.Stream
import com.nabto.edge.client.ktx.awaitExecute
import com.nabto.edge.client.ktx.awaitOpen
import com.nabto.edge.client.ktx.awaitReadAll
import com.nabto.edge.client.ktx.awaitStreamClose
import com.nabto.edge.client.ktx.awaitWrite
import com.nabto.edge.client.webrtc.EdgeSignaling
import com.nabto.edge.client.webrtc.EdgeWebrtcError
import com.nabto.edge.client.webrtc.SignalMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

data class RTCInfo(
    @JsonProperty("SignalingStreamPort") val signalingStreamPort: Long
)

class EdgeStreamSignaling(private val stream: Stream) : EdgeSignaling {
    data class MessageBox(
        val message: SignalMessage,
        val completable: CompletableDeferred<Unit> = CompletableDeferred()
    )

    private val scope = CoroutineScope(Dispatchers.IO)
    private val messageFlow = MutableSharedFlow<MessageBox>(replay = 16)
    private val mapper = jacksonObjectMapper()

    private val initialized = CompletableDeferred<Unit>()

    override fun start() {
        initialized.complete(Unit)
        scope.launch {
            messageFlow.collect { box ->
                try {
                    sendMessage(box.message)
                    box.completable.complete(Unit)
                } catch (e: Exception) {
                    box.completable.completeExceptionally(e)
                }
            }
        }
    }

    private suspend fun sendMessage(msg: SignalMessage) {
        val json = mapper.writeValueAsString(msg)
        val lenBytes = byteArrayOf(
            (json.length shr 0).toByte(),
            (json.length shr 8).toByte(),
            (json.length shr 16).toByte(),
            (json.length shr 24).toByte()
        )
        val res = lenBytes + json.toByteArray(Charsets.UTF_8)

        stream.awaitWrite(res)
    }

    override suspend fun send(msg: SignalMessage): Deferred<Unit> {
        val box = MessageBox(msg)
        messageFlow.emit(box)
        return box.completable
    }

    override suspend fun recv(): SignalMessage {
        initialized.await()

        val lenData = stream.awaitReadAll(4)
        val len =
            ((lenData[0].toUInt() and 0xFFu)) or
                    ((lenData[1].toUInt() and 0xFFu) shl 8) or
                    ((lenData[2].toUInt() and 0xFFu) shl 16) or
                    ((lenData[3].toUInt() and 0xFFu) shl 24)

        val json = String(stream.awaitReadAll(len.toInt()), Charsets.UTF_8)
        return mapper.readValue(json, SignalMessage::class.java)
    }
}

