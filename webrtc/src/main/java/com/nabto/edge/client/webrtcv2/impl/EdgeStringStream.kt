package com.nabto.edge.client.webrtcv2.impl

import com.nabto.edge.client.Stream
import com.nabto.edge.client.ktx.awaitReadAll
import com.nabto.edge.client.ktx.awaitWrite
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * Take a stream, split the incoming messages according to the length prefix, and write messages with a length prefix.
 */
class EdgeStringStream(val stream : Stream) : StringStream {
    data class MessageBox(
        val message: String,
        val completable: CompletableDeferred<Unit> = CompletableDeferred()
    )
    private val scope = CoroutineScope(Dispatchers.IO)
    private val messageFlow = MutableSharedFlow<MessageBox>(replay = 16)

    init {
        scope.launch {
            messageFlow.collect { box ->
                try {
                    sendMessageOnStream(box.message)
                    box.completable.complete(Unit)
                } catch (e: Exception) {
                    box.completable.completeExceptionally(e)
                }
            }
        }
    }

    suspend fun sendMessageOnStream(message: String) {
        val bytes = message.toByteArray(Charsets.UTF_8);
        val length = bytes.size
        val lenBytes = byteArrayOf(
            (length shr 0).toByte(),
            (length shr 8).toByte(),
            (length shr 16).toByte(),
            (length shr 24).toByte()
        )
        val res = lenBytes + bytes
        stream.awaitWrite(res)
    }

    override suspend fun sendMessage(message: String) {
        val box = MessageBox(message)
        messageFlow.emit(box)
        return box.completable.await()
    }

    override suspend fun revcMessage(): String {
        val lenData = stream.awaitReadAll(4)
        val len =
            ((lenData[0].toUInt() and 0xFFu)) or
                    ((lenData[1].toUInt() and 0xFFu) shl 8) or
                    ((lenData[2].toUInt() and 0xFFu) shl 16) or
                    ((lenData[3].toUInt() and 0xFFu) shl 24)

        val json = String(stream.awaitReadAll(len.toInt()), Charsets.UTF_8)
        return json;
    }
}