package com.nabto.edge.client.webrtc.impl

import com.nabto.edge.client.ErrorCodes
import com.nabto.edge.client.NabtoCallback
import com.nabto.edge.client.Stream
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import java.nio.ByteBuffer
import java.util.Optional

class MockStream {
    private val mock = mockk<Stream>()
    private val bytesSlot = slot<ByteArray?>()
    private val callbackSlot = slot<NabtoCallback<Any>?>()

    val bytes = mutableListOf<Byte>()
    val writtenBytes = mutableListOf<Byte>()

    init {
        every { mock.writeCallback(captureNullable(bytesSlot), captureNullable(callbackSlot)) } answers {
            bytesSlot.captured?.let { writtenBytes.addAll(it.toList()) }
            callbackSlot.captured?.run(ErrorCodes.OK, Optional.empty())
        }

        val openCallbackSlot = slot<NabtoCallback<Any>?>()
        every { mock.openCallback(any(), captureNullable(openCallbackSlot)) } answers {
            openCallbackSlot.captured?.run(ErrorCodes.OK, Optional.empty())
        }

        val closeCallbackSlot = slot<NabtoCallback<Any>?>()
        every { mock.streamCloseCallback(captureNullable(closeCallbackSlot)) } answers {
            closeCallbackSlot.captured?.run(ErrorCodes.OK, Optional.empty())
        }
    }

    fun getStream(): Stream {
        return mock
    }

    fun prepareString(str: String) {
        val len = str.length
        val lenBytes = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(len).array()
        lenBytes.reverse()
        val strBytes = str.toByteArray()
        bytes.addAll((lenBytes + strBytes).toList())

        val lengthSlot = slot<Int>()
        val callbackSlot = slot<NabtoCallback<ByteArray>?>()
        every { mock.readAllCallback(capture(lengthSlot), captureNullable(callbackSlot)) } answers {
            val result = bytes.take(lengthSlot.captured)
            bytes.subList(0, lengthSlot.captured).clear()
            result.toByteArray()
            callbackSlot.captured?.run(ErrorCodes.OK, Optional.of(result.toByteArray()))
        }
    }
}