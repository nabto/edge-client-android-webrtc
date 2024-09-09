package com.example.webrtc_demo2

import android.util.Log
import com.nabto.edge.client.webrtcv2.Candidate
import com.nabto.edge.client.webrtcv2.CandidateImpl
import com.nabto.edge.client.webrtcv2.Description
import com.nabto.edge.client.webrtcv2.DescriptionImpl
import com.nabto.edge.client.webrtcv2.EdgeSignaling
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

internal class PerfectNegotiator(
    val peerConnection: PeerConnection,
    val signaling: EdgeSignaling,
    val polite: Boolean,
) {
    var makingOffer: Boolean = false;
    var ignoreOffer = false;

    private val scope = CoroutineScope(Dispatchers.IO)
    private val TAG = "EdgeNegotiator"

    fun dispose() {
        scope.cancel()
    }

    fun onRenegotiationNeeded() {
        // potentially a racecondition if this is called concurrently
        scope.launch {
            try {
                makingOffer = true
                setLocalDescriptionWrapper()
                val description = peerConnection.localDescription;
                signaling.sendDescription(DescriptionImpl(description.type.canonicalForm(), description.description))
            } catch (err: Throwable) {
                Log.e(TAG, err.message.toString())
            } finally {
                makingOffer = false;
            }
        }
    }

    fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
        if (state == PeerConnection.IceConnectionState.FAILED) {
            peerConnection.restartIce();
        }
    }

    fun onIceCandidate(candidate: IceCandidate?) {
        if (candidate == null) {
            return;
        }
        scope.launch {
            signaling.sendCandidate(
                CandidateImpl(
                    candidate.sdp,
                    candidate.sdpMid,
                    candidate.sdpMLineIndex,
                    null
                )
            )
        }
    }

    fun onDescriptionFromSignaling(signalingDescription: Description) {

            val description = SessionDescription(
                SessionDescription.Type.fromCanonicalForm(signalingDescription.type),
                signalingDescription.sdp
            )
            val offerCollision =
                description.type == SessionDescription.Type.OFFER && (makingOffer || peerConnection.signalingState() != PeerConnection.SignalingState.STABLE)

            ignoreOffer = !polite && offerCollision;
            if (ignoreOffer) {
                return;
            }
        scope.launch {
            setRemoteDescriptionWrapper(description)
            if (description.type == SessionDescription.Type.OFFER) {
                setLocalDescriptionWrapper()
                signaling.sendDescription(
                    DescriptionImpl(
                        description.type.canonicalForm(),
                        description.description
                    ))
            }
        }
    }

    fun onCandidateFromSignaling(candidate: Candidate) {
        scope.launch {
            val iceCandidate =
                IceCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.candidate);
            try {
                peerConnection.addIceCandidate(iceCandidate)
            } catch (err: Throwable) {
                if (!ignoreOffer) {
                    throw err;
                }
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

}
