package com.nabto.edge.client.webrtc.impl

import com.nabto.edge.client.ErrorCodes
import com.nabto.edge.client.NabtoCallback
import com.nabto.edge.client.Stream
import java.nio.ByteBuffer
import java.util.Optional

class MockStream: Stream {
    val bytes = mutableListOf<Byte>()
    val writtenBytes = mutableListOf<Byte>()

    fun prepareString(str: String) {
        val len = str.length
        val lenBytes = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(len).array()
        lenBytes.reverse()
        val strBytes = str.toByteArray()
        val msg = (lenBytes + strBytes).toList()
        bytes.addAll(msg)
    }

    override fun close() {
        TODO("Not yet implemented")
    }

    override fun open(streamPort: Int) {
        TODO("Not yet implemented")
    }

    override fun openCallback(streamPort: Int, callback: NabtoCallback<*>?) {
        TODO("Not yet implemented")
    }

    override fun readSome(): ByteArray {
        TODO("Not yet implemented")
    }

    override fun readSomeCallback(callback: NabtoCallback<ByteArray>?) {
        TODO("Not yet implemented")
    }

    override fun readAll(length: Int): ByteArray {
        TODO("Not yet implemented")
    }

    override fun readAllCallback(length: Int, callback: NabtoCallback<ByteArray>?) {
        if (length in 1..bytes.size) {
            val result = bytes.take(length)
            bytes.subList(0, length).clear()
            callback?.run(ErrorCodes.OK, Optional.of(result.toByteArray()))
        } else {
            throw IllegalStateException("MockStream tried to call readAll without enough data.")
        }
    }

    override fun write(bytes: ByteArray?) {
        TODO("Not yet implemented")
    }

    override fun writeCallback(bytes: ByteArray?, callback: NabtoCallback<Any>?) {
        bytes?.let { writtenBytes.addAll(it.toList()) }
        callback?.run(ErrorCodes.OK, Optional.empty())
    }

    override fun streamClose() {
        TODO("Not yet implemented")
    }

    override fun streamCloseCallback(callback: NabtoCallback<Any>?) {
        TODO("Not yet implemented")
    }

    @Deprecated("Deprecated in Java")
    override fun abort() {
        TODO("Not yet implemented")
    }
}