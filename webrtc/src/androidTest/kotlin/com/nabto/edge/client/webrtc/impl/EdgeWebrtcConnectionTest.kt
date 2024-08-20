package com.nabto.edge.client.webrtc.impl

import com.nabto.edge.client.Coap
import com.nabto.edge.client.Connection
import com.nabto.edge.client.ErrorCodes
import com.nabto.edge.client.NabtoCallback
import com.nabto.edge.client.webrtc.EdgeSignaling
import com.nabto.edge.client.webrtc.EdgeWebrtcConnection
import com.nabto.edge.client.webrtc.EdgeWebrtcLogLevel
import com.nabto.edge.client.webrtc.EdgeWebrtcManager
import com.nabto.edge.client.webrtc.SignalMessage
import com.nabto.edge.client.webrtc.SignalMessageType
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import java.util.Optional

class EdgeWebrtcConnectionTest {
    val mockCoap = mockk<Coap>()
    val mockSignaling = mockk<EdgeSignaling>()
    val mockFactory = mockk<PeerConnectionFactory>()
    val mockPc = mockk<PeerConnection>()
    val mockDeferred = CompletableDeferred(Unit)
    lateinit var mockStream: MockStream
    lateinit var peerConnection: EdgeWebrtcConnection
    val msgChannel = Channel<SignalMessage>(Channel.Factory.BUFFERED, BufferOverflow.SUSPEND)

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun beforeEach() {
        EdgeWebrtcManager.getInstance().setLogLevel(EdgeWebrtcLogLevel.INFO)
        mockStream = MockStream()

        val callbackSlot = slot<NabtoCallback<Any>?>()
        every { mockCoap.executeCallback(captureNullable(callbackSlot)) } answers {
            callbackSlot.captured?.run(ErrorCodes.OK, Optional.empty())
        }

        every { mockCoap.responseStatusCode } returns(205)
        every { mockCoap.responseContentFormat } returns(50)
        every { mockCoap.responsePayload } answers {
            """
                {"SignalingStreamPort": 0}
            """.trimIndent().toByteArray()
        }

        every { mockSignaling.start() } just Runs

        coEvery { mockSignaling.recv() } coAnswers {
            msgChannel.receive()
        }

        coEvery { mockSignaling.send(any()) } returns(mockDeferred)

        every { mockFactory.createPeerConnection(any<PeerConnection.RTCConfiguration>(), any<PeerConnection.Observer>()) } returns(mockPc)

        peerConnection = EdgeWebrtcConnectionImpl(mockFactory, mockCoap, mockStream.getStream(), mockSignaling)
    }

    @After
    fun afterEach() {
    }

    @Test
    fun connectShouldSucceed() = runTest {
        msgChannel.send(SignalMessage(type = SignalMessageType.TURN_RESPONSE, iceServers = listOf()))
        peerConnection.connect().await()
        coVerify { mockSignaling.send(SignalMessage(type = SignalMessageType.TURN_REQUEST)) }
    }
}