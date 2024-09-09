package com.nabto.edge.client.webrtcv2.impl

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.nabto.edge.client.Connection
import com.nabto.edge.client.ktx.awaitExecute
import com.nabto.edge.client.ktx.awaitOpen
import com.nabto.edge.client.webrtc.EdgeWebrtcError
import com.nabto.edge.client.webrtc.SignalMessageType
import com.nabto.edge.client.webrtc.impl.RTCInfo
import com.nabto.edge.client.webrtcv2.EdgeSignaling
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import java.util.concurrent.CompletableFuture


data class RTCInfo(
    @JsonProperty("SignalingStreamPort") val signalingStreamPort: Long?,
    @JsonProperty("SignalingV2StreamPort") val signalingV2StreamPort: Long?
)

class NabtoSignalingFactoryImpl {
    companion object {
        suspend fun getStreamingPort(connection: Connection) : Long {
            val coap = connection.createCoap("GET", "/p2p/webrtc-info");
            coap.awaitExecute()
            if (coap.responseStatusCode != 205) {
                throw EdgeWebrtcError.SignalingFailedToInitialize()
            }
            val jsonCoapContentFormat = 50
            if (coap.responseContentFormat != jsonCoapContentFormat) {
                throw Exception("Invalid content format for coap response of /p2p/webrtc-info")
            }
            val rtcInfo = jacksonObjectMapper().readValue(coap.responsePayload, RTCInfo::class.java)

            // TODO handle missing V2StreamPort
            return rtcInfo.signalingV2StreamPort;
        }

        suspend fun createStringStream(connection: Connection, port : Long) : StringStream {
            val stream = connection.createStream();
            stream.awaitOpen(port.toInt())
            return EdgeStringStream(stream);
        }

        // Send a setup request and receive a setup response
        suspend fun setup(stringStream : StringStream) : SignalingSetupResponseMessage {
            val mapper = jacksonObjectMapper()

            val setupRequest = SignalingSetupRequestMessage()
            val str = mapper.writeValueAsString(setupRequest)

            stringStream.sendMessage(str)

            val recvStr = stringStream.revcMessage()
            val messageType = mapper.readValue(recvStr, SignalingMessage::class.java)
            if (messageType.type == MessageTypes.SETUP_ERROR.type) {
                val error = mapper.readValue(recvStr, SignalingSetupErrorMessage::class.java)
                throw Exception("Signaling setup failed code: ${error.error.errorCode}, description: ${error.error.errorDescription}")
            } else {
                return mapper.readValue(recvStr, SignalingSetupResponseMessage::class.java)
            }
        }
    
        suspend fun createEdgeSignalingImpl(connection: Connection) : EdgeSignaling {
            val port = getStreamingPort(connection)
            val stringStream = createStringStream(connection, port)
            val setupResponse = setup(stringStream);
            return EdgeStreamSignaling(stringStream, setupResponse)
        }

        fun createEdgeSignaling(connection : Connection) : CompletableFuture<EdgeSignaling> {
            val scope = CoroutineScope(Dispatchers.IO)
            return scope.future {
                createEdgeSignalingImpl(connection)
            }
            // TODO is the scope cancelled here
        }
    }
}