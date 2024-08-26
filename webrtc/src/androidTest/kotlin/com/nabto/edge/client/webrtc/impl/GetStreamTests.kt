package com.nabto.edge.client.webrtc.impl

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Test
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


class SignalingMessage {

    val description: SessionDescription?
    val candidate : IceCandidate?
    constructor(description: SessionDescription) {
        this.description = description;
        this.candidate = null;
    }
    constructor(candidate: IceCandidate) {
        this.description = null;
        this.candidate = candidate;
    }
}

interface SignalingConnection {
    suspend fun sendMessage(message: SignalingMessage);
    suspend fun receiveMessage() : SignalingMessage;
}

/**
 * Pauseable Signaling connection, the connection starts as paused, which makes it possible to control when to forward signaling messages such that perfect negotiation scenarios can be tested.
 *
 * sendMessages is put into the send queue where they are stored until they are forwarded to the outgoing channel
 */
class PausedSignalingConnection(val outgoing: Channel<SignalingMessage>, val incoming : Channel<SignalingMessage>) : SignalingConnection {
    val sendQueue : Channel<SignalingMessage> = Channel<SignalingMessage>()
    private val scope = CoroutineScope(Dispatchers.IO)

    private val hasReceivedDescription : MutableStateFlow<Boolean> = MutableStateFlow<Boolean>(false);

    override suspend fun sendMessage(message: SignalingMessage) {
        if (message.description != null) {
            hasReceivedDescription.emit(true);
        }
        sendQueue.send(message);
    }
    override suspend fun receiveMessage() : SignalingMessage {
        return incoming.receive();
    }

    suspend fun resume() {
        scope.launch {
            while (true) {
                val message = sendQueue.receive();
                outgoing.send(message);
            }
        }
    }

    suspend fun waitForDescription() {
        hasReceivedDescription.takeWhile { value -> value == false }
    }
}

class PerfectNegotiator {
    var makingOffer : Boolean = false;
    var ignoreOffer = false;
    val polite : Boolean
    val peerConnection : PeerConnection
    val signalingConnection: SignalingConnection
    private val scope = CoroutineScope(Dispatchers.IO)
    private val TAG = "webrtc-perfect-negotiator"

    constructor(peerConnection: PeerConnection, signalingConnection: SignalingConnection, polite: Boolean) {
        this.peerConnection = peerConnection
        this.polite = polite;
        this.signalingConnection = signalingConnection
        scope.launch {
            while (true) {
                val signalingMessage = signalingConnection.receiveMessage()
                handleSignalingMessage(signalingMessage);
            }
        }
    }

    private suspend fun setLocalDescriptionWrapper() =
        suspendCoroutine { cont ->
            peerConnection.setLocalDescription(object : SdpObserver {
                override fun onCreateSuccess(p0: SessionDescription?) {
                }

                override fun onSetSuccess() {
                    cont.resume(Unit);
                }

                override fun onCreateFailure(p0: String?) {
                    cont.resumeWithException(Exception(p0))
                }

                override fun onSetFailure(p0: String?) {
                    cont.resumeWithException(Exception(p0));
                }
            });
        }

    private suspend fun setRemoteDescriptionWrapper(description: SessionDescription) =
        suspendCoroutine { cont ->
            peerConnection.setRemoteDescription(object : SdpObserver {
                override fun onCreateSuccess(p0: SessionDescription?) {
                }

                override fun onSetSuccess() {
                    cont.resume(Unit);
                }

                override fun onCreateFailure(p0: String?) {
                    cont.resumeWithException(Exception(p0))
                }

                override fun onSetFailure(p0: String?) {
                    cont.resumeWithException(Exception(p0));
                }
            }, description);
        }

    private suspend fun handleSignalingMessage(message: SignalingMessage) {
        try {
            if (message.description != null) {
                val description : SessionDescription = message.description;
                val offerCollision = description.type == SessionDescription.Type.OFFER && (makingOffer || peerConnection.signalingState() != PeerConnection.SignalingState.STABLE);

                ignoreOffer = !polite && offerCollision;
                if (ignoreOffer) {
                    return;
                }
                setRemoteDescriptionWrapper(description)
                if (description.type == SessionDescription.Type.OFFER) {
                    setLocalDescriptionWrapper()
                    signalingConnection.sendMessage(SignalingMessage(peerConnection.localDescription))
                }

            } else if (message.candidate != null) {
                val candidate = message.candidate;
                try {
                    peerConnection.addIceCandidate(candidate)
                } catch (err : Throwable) {
                    if (!ignoreOffer) {
                        throw err;
                    }
                }
            }
        } catch (err: Throwable) {
            Log.e(TAG, err.message.toString())
        }
    }

    public fun onIceCandidate(candidate : IceCandidate?) {
        if (candidate == null) {
            return;
        }
        scope.launch {
            signalingConnection.sendMessage(SignalingMessage(candidate));
        }
    }

    public fun onRenegotiationNeeded() {
        // potentially a racecondition if this is called concurrently
        scope.launch {
            try {
                makingOffer = true
                setLocalDescriptionWrapper();
                signalingConnection.sendMessage(SignalingMessage(peerConnection!!.localDescription))
            } catch (err: Throwable) {
                Log.e(TAG, err.message.toString())
            } finally {
                makingOffer = false;
            }
        }
    }

    public fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
        if (state == PeerConnection.IceConnectionState.FAILED) {
            peerConnection?.restartIce();
        }
    }

}

open class PeerConnectionLogObserver : PeerConnection.Observer {
    private val TAG = "peer-connection-log-observer"
    var peerName = ""
    override fun onSignalingChange(p0: PeerConnection.SignalingState?) {
        Log.i(TAG, "${peerName} onSignalingChange ${p0.toString()}")
    }

    override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {
        Log.i(TAG, "${peerName} onIceConnectionChange ${p0.toString()}")
    }

    override fun onIceConnectionReceivingChange(p0: Boolean) {
        Log.i(TAG, "${peerName} onIceConnectionReceivingChange ${p0.toString()}")
    }

    override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {
        Log.i(TAG, "${peerName} onIceGatheringChange ${p0.toString()}")
    }

    override fun onIceCandidate(p0: IceCandidate?) {
        Log.i(TAG, "${peerName} onIceCandidate ${p0.toString()}")
    }

    override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {
        Log.i(TAG, "${peerName} onIceCandidatesRemoved ${p0.toString()}")
    }

    override fun onAddStream(p0: MediaStream?) {
        Log.i(TAG, "${peerName} onAddStream ${p0.toString()}")
    }

    override fun onRemoveStream(p0: MediaStream?) {
        Log.i(TAG, "${peerName} onRemoveStream ${p0.toString()}")
    }

    override fun onDataChannel(p0: DataChannel?) {
        Log.i(TAG, "${peerName} onDataChannel ${p0.toString()}")
    }

    override fun onRenegotiationNeeded() {
        Log.i(TAG, "${peerName} onRenegotiationNeeded")
    }
}

class StringDataChannel {
    private val incoming : Channel<String> = Channel<String>()
    private val outgoing : Channel<String> = Channel<String>()
    private val scope = CoroutineScope(Dispatchers.IO)
    constructor(dataChannel: DataChannel) {
        dataChannel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(p0: Long) {

            }

            override fun onStateChange() {
                if (dataChannel.state() == DataChannel.State.OPEN) {
                    scope.launch {
                        while(true) {
                            val message = outgoing.receive();
                            val byteArray = message.toByteArray();
                            dataChannel.send(DataChannel.Buffer(ByteBuffer.wrap(byteArray), false) )
                        }
                    }
                }
            }

            override fun onMessage(buffer: DataChannel.Buffer?) {
                if (buffer == null) {
                    return;
                }
                if (buffer.binary) {
                    throw Exception("unsupported format");
                }
                val byteBuffer = buffer.data
                if (byteBuffer == null) {
                    return;
                }
                val str = StandardCharsets.UTF_8.decode(byteBuffer).toString();
                scope.launch {
                    incoming.send(str);
                }
            }

        })
    }

    suspend fun send(message : String ) {
        outgoing.send(message);
    }
    suspend fun receive() : String {
        return incoming.receive()
    }
}

class TestPeerConnection : PeerConnectionLogObserver
{
    val peerConnection : PeerConnection?
    val perfectNegotiator : PerfectNegotiator?
    private val scope = CoroutineScope(Dispatchers.IO)
    private val TAG = "webrtc";

    val tracks : Channel<RtpTransceiver> = Channel<RtpTransceiver>()

    constructor(peerConnectionFactory: PeerConnectionFactory, signalingConnection : SignalingConnection, polite : Boolean) {
        val iceServers : List<PeerConnection.IceServer> = listOf()
        val rtcConfiguration = PeerConnection.RTCConfiguration(iceServers)
        rtcConfiguration.enableImplicitRollback = true;
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfiguration, this);
        if (peerConnection != null) {
            perfectNegotiator = PerfectNegotiator(peerConnection, signalingConnection, polite)
        } else {
            perfectNegotiator = null;
        }
        peerName = "client";
    }

    // forward to composite perfect negotiator
    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
        super.onIceConnectionChange(state);
        perfectNegotiator?.onIceConnectionChange(state);
    }

    override fun onRenegotiationNeeded() {
        super.onRenegotiationNeeded();
        perfectNegotiator?.onRenegotiationNeeded();
    }

    override fun onIceCandidate(p0: IceCandidate?) {
        super.onIceCandidate(p0);
        perfectNegotiator?.onIceCandidate(p0);
    }

    // Test code

    suspend fun createDataChannel(label : String) : DataChannel {
        val init = DataChannel.Init()
        val dataChannel = peerConnection!!.createDataChannel(label, init);
        return dataChannel;
    }

    override fun onTrack(transceiver: RtpTransceiver?) {
        super.onTrack(transceiver)
        scope.launch {
            if (transceiver != null) {
                tracks.send(transceiver)
            }
        };
    }

    // echo data on the datachannel
    override fun onDataChannel(dataChannel: DataChannel?) {
        super.onDataChannel(dataChannel)
        if (dataChannel != null) {
            dataChannel.registerObserver(object : DataChannel.Observer {
                override fun onBufferedAmountChange(p0: Long) {
                }

                override fun onStateChange() {
                }

                override fun onMessage(message: DataChannel.Buffer?) {
                    dataChannel.send(message);
                }
            })
        }
    }

    fun addTransceiver(track : MediaStreamTrack?) : RtpTransceiver? {
        return peerConnection?.addTransceiver(track);
    }

}

class GetStreamTests {
    @Ignore
    @Test
    fun perfectNegotiationTest() = runTest {
        val channel1 : Channel<SignalingMessage> = Channel<SignalingMessage>()
        val channel2 : Channel<SignalingMessage> = Channel<SignalingMessage>()
        val peer1Signaling : PausedSignalingConnection = PausedSignalingConnection(channel1, channel2)
        val peer2Signaling : PausedSignalingConnection = PausedSignalingConnection(channel2, channel1)
        val eglBase: EglBase = EglBase.create()
        val encoderFactory = DefaultVideoEncoderFactory(eglBase.getEglBaseContext(), true, true);
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.getEglBaseContext());

        val peerConnectionFactory = PeerConnectionFactory.builder().setVideoDecoderFactory(decoderFactory).setVideoEncoderFactory(encoderFactory).createPeerConnectionFactory()

        val videoSource = peerConnectionFactory.createVideoSource(false);
        val id = "video-1"
        val videoTrack = peerConnectionFactory.createVideoTrack(id, videoSource);

        val videoSource2 = peerConnectionFactory.createVideoSource(false);
        val id2 = "video-2"
        val videoTrack2 = peerConnectionFactory.createVideoTrack(id2, videoSource2);

        // create a client and a server initially the signaling connection is paused
        val server = TestPeerConnection(peerConnectionFactory, peer1Signaling, false);
        val client = TestPeerConnection(peerConnectionFactory, peer2Signaling, true);

        // add the data channel and an audio track, the signaling is paused so each side will create an offer.
        val dataChannel = client.createDataChannel("echo");
        //val clientAddedTransceiver = client.addTransceiver(videoTrack2)
        val serverAddedTransceiver = server.addTransceiver(videoTrack)

        // wait for each end to have created an offer
        peer1Signaling.waitForDescription()
        peer2Signaling.waitForDescription()

        // now both peers has made their offers ready and are standing in the state have-local-offer, one of them needs to do a rollback.
        peer2Signaling.resume()
        peer1Signaling.resume()


        val stringDataChannel = StringDataChannel(dataChannel);
        val message = "message";
        stringDataChannel.send(message);
        val response = stringDataChannel.receive();
        assertEquals(message, response);

        val track = client.tracks.receive();
        assertEquals(serverAddedTransceiver?.mid, track.mid);

        //val track2 = server.tracks.receive()
        //assertEquals(clientAddedTransceiver?.mid, track2.mid);
    }
}