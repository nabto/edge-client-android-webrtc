package com.nabto.edge.client.webrtcv2

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.nabto.edge.client.webrtcv2.impl.EdgeStreamSignaling
import com.nabto.edge.client.webrtcv2.impl.SignalingSetupRequestMessage
import com.nabto.edge.client.webrtcv2.impl.SignalingSetupResponseMessage
import com.nabto.edge.client.webrtcv2.impl.StringStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Test

class TestStringStream : StringStream {
    val sendQueue = MutableSharedFlow<String>()
    val recvQueue = MutableSharedFlow<String>()
    override suspend fun sendMessage(message: String) {
        sendQueue.emit(message)
        TODO("Not yet implemented")
    }

    override suspend fun revcMessage(): String {
        val message = recvQueue.first();
        return message;
    }

}

class TestSignalingObserver : EdgeSignalingObserver {
    val candidates = MutableSharedFlow<Candidate>()
    val descriptions = MutableSharedFlow<Description>()
    val metadatas = MutableSharedFlow<String>()
    val scope = CoroutineScope(Dispatchers.IO)

    override fun onSignalingCandidate(candidate: Candidate) {
        scope.launch {
            candidates.emit(candidate);
        }
    }

    override fun onSignalingDescription(description: Description) {
        scope.launch {
            descriptions.emit(description)
        }
    }

    override fun onSignalingMetadata(metadata: String) {
        scope.launch {
            metadatas.emit(metadata)
        }
    }
}

class SignalingProtocolTest {
    @Test
    suspend fun receive_setup_response() {
        val testStringStream = TestStringStream()
        val signalingSetupResponseMessage = SignalingSetupResponseMessage(id = "id", polite = false, iceServers = null)
        val signaling = EdgeStreamSignaling(testStringStream, signalingSetupResponseMessage)
        val signalingOberser = TestSignalingObserver()
        signaling.setSignalingObserver(signalingOberser)
        val description = DescriptionImpl("offer", "...")
        signaling.sendDescription(description)
        val sent = testStringStream.sendQueue.first();

    }
}