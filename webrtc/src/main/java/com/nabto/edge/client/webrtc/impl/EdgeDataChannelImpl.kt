package com.nabto.edge.client.webrtc.impl

import com.nabto.edge.client.webrtc.EdgeDataChannel
import com.nabto.edge.client.webrtc.OnClosedCallback
import com.nabto.edge.client.webrtc.OnMessageCallback
import com.nabto.edge.client.webrtc.OnOpenedCallback
import org.webrtc.DataChannel
import java.nio.ByteBuffer

class EdgeDataChannelImpl(private val dataChannel: DataChannel) : EdgeDataChannel {
    var onMessageCallback: OnMessageCallback? = null
    var onOpenedCallback: OnOpenedCallback? = null
    var onClosedCallback: OnClosedCallback? = null

    val observer = object : DataChannel.Observer {
        override fun onBufferedAmountChange(p0: Long) {}

        override fun onStateChange() {
            // @TODO: Should we export all states or just close/open?
            val state = dataChannel.state()
            when (state) {
                DataChannel.State.CLOSED -> onClosedCallback?.invoke()
                DataChannel.State.CLOSING -> {}
                DataChannel.State.OPEN -> onOpenedCallback?.invoke()
                DataChannel.State.CONNECTING -> {}
                null -> {}
            }
        }

        override fun onMessage(maybeBuffer: DataChannel.Buffer?) {
            onMessageCallback?.let { cb ->
                maybeBuffer?.let { buffer ->
                    val data = buffer.data
                    val bytes = ByteArray(data.remaining())
                    data.get(bytes)
                    cb(bytes)
                }
            }
        }

    }

    init {
        dataChannel.registerObserver(observer)
    }

    override fun onMessage(cb: OnMessageCallback) {
        onMessageCallback = cb
    }

    override fun onOpen(cb: OnOpenedCallback) {
        onOpenedCallback = cb
    }

    override fun onClose(cb: OnClosedCallback) {
        onClosedCallback = cb
    }

    override fun send(data: ByteArray) {
        val buffer = ByteBuffer.wrap(data)
        dataChannel.send(DataChannel.Buffer(buffer, true))
    }

    override fun dataChannelClose() {
        dataChannel.close()
    }
}