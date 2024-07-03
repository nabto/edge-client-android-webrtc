package com.nabto.edge.client.webrtc.impl

import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.nabto.edge.client.ErrorCodes
import com.nabto.edge.client.NabtoCallback
import com.nabto.edge.client.Stream
import com.nabto.edge.client.ktx.awaitReadAll
import com.nabto.edge.client.webrtc.SignalMessage
import com.nabto.edge.client.webrtc.SignalMessageType
import com.nabto.edge.client.webrtc.SignalingIceCandidate
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import java.nio.ByteBuffer
import java.util.Optional

inline fun <reified T: Throwable> assertThrow(code: () -> Unit) {
    var hasThrown = false
    try {
        code()
    } catch (e: Throwable) {
        hasThrown = true
        if (!T::class.java.isAssignableFrom(e.javaClass)) {
            fail("assertThrow failed: expected a subtype of ${T::class.java.simpleName} but ${e.javaClass.simpleName} was thrown instead.")
        }
    }
    if (!hasThrown) {
        fail("assertThrow failed: expected a subtype of ${T::class.java.simpleName} but nothing was thrown.")
    }
}

class EdgeStreamSignalingTest {
    private val jsonMapper = jacksonObjectMapper()
    lateinit var signaling: EdgeStreamSignaling
    val mockStream = mockk<Stream>()
    val bytes = mutableListOf<Byte>()
    val writtenBytes = mutableListOf<Byte>()

    @Before
    fun beforeEach() {
        signaling = EdgeStreamSignaling(mockStream)
        signaling.start()
        bytes.clear()
        writtenBytes.clear()

        val bytesSlot = slot<ByteArray?>()
        val callbackSlot = slot<NabtoCallback<Any>?>()
        every { mockStream.writeCallback(captureNullable(bytesSlot), captureNullable(callbackSlot)) } answers {
            bytesSlot.captured?.let { writtenBytes.addAll(it.toList()) }
            callbackSlot.captured?.run(ErrorCodes.OK, Optional.empty())
        }
    }

    fun prepareString(str: String) {
        val len = str.length
        val lenBytes = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(len).array()
        lenBytes.reverse()
        val strBytes = str.toByteArray()
        bytes.addAll((lenBytes + strBytes).toList())

        val lengthSlot = slot<Int>()
        val callbackSlot = slot<NabtoCallback<ByteArray>?>()
        every { mockStream.readAllCallback(capture(lengthSlot), captureNullable(callbackSlot)) } answers {
            val result = bytes.take(lengthSlot.captured)
            bytes.subList(0, lengthSlot.captured).clear()
            result.toByteArray()
            callbackSlot.captured?.run(ErrorCodes.OK, Optional.of(result.toByteArray()))
        }
    }

    @Test
    fun offerShouldSucceed() = runTest {
        prepareString("""
            {
                "type": 0,
                "data": "{\"type\": \"offer\", \"sdp\": \"v=0...\"}"
            }
        """.trimIndent())

        val msg = signaling.recv()
        assertEquals(msg.type, SignalMessageType.OFFER)
        val offer = jsonMapper.readValue(msg.data!!, SDP::class.java)
        assertEquals(offer.type, "offer")
        assertEquals(offer.sdp, "v=0...")
    }

    @Test
    fun answerShouldSucceed() = runTest {
        prepareString("""
            {
                "type": 1,
                "data": "{\"type\": \"answer\", \"sdp\": \"v=0...\"}"
            }
        """.trimIndent())

        val msg = signaling.recv()
        assertEquals(msg.type, SignalMessageType.ANSWER)
        val answer = jsonMapper.readValue(msg.data!!, SDP::class.java)
        assertEquals(answer.type, "answer")
        assertEquals(answer.sdp, "v=0...")
    }

    @Test
    fun iceCandidateShouldSucceed() = runTest {
        prepareString("""
            {
                "type": 2,
                "data": "{\"sdpMid\": \"foo\", \"candidate\": \"bar\"}"
            }
        """.trimIndent())

        val msg = signaling.recv()
        assertEquals(msg.type, SignalMessageType.ICE_CANDIDATE)
        val candidate = jsonMapper.readValue(msg.data!!, SignalingIceCandidate::class.java)
        assertEquals("foo", candidate.sdpMid)
        assertEquals("bar", candidate.candidate)
    }

    @Test
    fun iceCandidateMissingSdpShouldFail() = runTest {
        prepareString("""
            {
                "type": 2,
                "data": "{\"sdpMid\": \"bobdog\"}"
            }
        """.trimIndent())

        val msg = signaling.recv()
        assertEquals(msg.type, SignalMessageType.ICE_CANDIDATE)

        assertThrow<MismatchedInputException> {
            jsonMapper.readValue(msg.data!!, SignalingIceCandidate::class.java)
        }
    }

    @Test
    fun testIceServerExample1() = runTest {
        prepareString(IceServerDataSet.example1)

        val msg = signaling.recv()
        assertEquals(msg.type, SignalMessageType.TURN_RESPONSE)
        assertEquals("foobar", msg.iceServers?.get(0)?.urls?.get(0))
    }

    @Test
    fun testIceServerExample2() = runTest {
        prepareString(IceServerDataSet.example2)

        val msg = signaling.recv()
        assertEquals(msg.type, SignalMessageType.TURN_RESPONSE)
        assertEquals("a", msg.iceServers?.get(0)?.urls?.get(0))
        assertEquals("b", msg.iceServers?.get(0)?.urls?.get(1))
        assertEquals("foo", msg.iceServers?.get(0)?.username)
        assertEquals("bar", msg.iceServers?.get(0)?.credential)
    }

    @Test
    fun testIceServerExample3() = runTest {
        prepareString(IceServerDataSet.example3)

        val msg = signaling.recv()
        assertEquals(msg.type, SignalMessageType.TURN_RESPONSE)
        assertEquals("a", msg.iceServers?.get(0)?.urls?.get(0))
        assertEquals("b", msg.iceServers?.get(0)?.urls?.get(1))
        assertEquals("foo", msg.iceServers?.get(0)?.username)
        assertEquals("bar", msg.iceServers?.get(0)?.credential)

        assertEquals("c", msg.iceServers?.get(1)?.urls?.get(0))
        assertEquals("d", msg.iceServers?.get(1)?.urls?.get(1))
        assertEquals("bob", msg.iceServers?.get(1)?.username)
        assertEquals("dog", msg.iceServers?.get(1)?.credential)
    }

    @Test
    fun testIceServerMissingUrls() = runTest {
        prepareString(IceServerDataSet.invalidMissingUrls)

        assertThrow<MismatchedInputException> {
            signaling.recv()
        }
    }

    @Test
    fun sendMessageShouldSucceed() = runTest {
        val msg = SignalMessage(type = SignalMessageType.TURN_REQUEST)
        signaling.send(msg).await()

        val lenBytes = writtenBytes.subList(0, 4)
        val len = ByteBuffer.wrap(lenBytes.toByteArray().reversedArray()).getInt()

        val strBytes = writtenBytes.subList(4, writtenBytes.size)
        val str = String(strBytes.toByteArray(), Charsets.UTF_8)
        val writtenMsg = jsonMapper.readValue(str, SignalMessage::class.java)

        assertEquals(str.length, len)
        assertEquals(writtenMsg, msg)
    }

    @Test
    fun nullFieldsShouldntBeSent() = runTest {
        val msg = SignalMessage(type = SignalMessageType.TURN_REQUEST)
        signaling.send(msg).await()

        val strBytes = writtenBytes.subList(4, writtenBytes.size)
        val str = String(strBytes.toByteArray(), Charsets.UTF_8)
        // expect no other fields like "data", "iceSerers" etc.
        assertEquals(str, "{\"type\":3}")
    }
}