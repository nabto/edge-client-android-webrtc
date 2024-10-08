package com.nabto.edge.client.webrtc

import android.content.Context
import android.util.AttributeSet
import com.nabto.edge.client.Connection
import com.nabto.edge.client.webrtc.impl.EdgeWebrtcManagerInternal
import io.getstream.webrtc.android.ui.VideoTextureViewRenderer
import org.webrtc.AudioSource
import org.webrtc.MediaConstraints
import org.webrtc.VideoSource
import java.util.concurrent.CompletableFuture

// @TODO: Make our own TextureViewRenderer implementation?
// @TODO: Make a Jetpack Composable View?
class EdgeVideoView(
    context: Context,
    attrs: AttributeSet? = null
) : VideoTextureViewRenderer(context, attrs) {
}

/**
 * Track types used to identify if a track is Video or Audio.
 */
enum class EdgeMediaTrackType {
    AUDIO,
    VIDEO
}

/**
 * Interface used to represent all Media Tracks.
 */
interface EdgeMediaTrack {
    val type: EdgeMediaTrackType
}

/**
 * Log levels to use in the underlying SDK.
 * By default the log level is set to WARNING.
 */
enum class EdgeWebrtcLogLevel {
    ERROR,
    WARNING,
    INFO,
    VERBOSE
}

/**
 * Error class for errors emitted by the onErrorCallback
 */
sealed class EdgeWebrtcError : Error() {
    /**
     * The signaling stream could not be established properly.
     */
    class SignalingFailedToInitialize() : EdgeWebrtcError()

    /**
     * Reading from the Signaling Stream failed
     */
    class SignalingFailedRecv() : EdgeWebrtcError()

    /**
     * Writing to the Signaling Stream failed
     */
    class SignalingFailedSend(): EdgeWebrtcError()

    /**
     * An invalid signaling message was received
     */
    class SignalingInvalidMessage() : EdgeWebrtcError()

    /**
     * The remote description received from the other peer was invalid
     */
    class SetRemoteDescriptionError() : EdgeWebrtcError()

    /**
     * Failed to send an Answer on the signaling stream
     */
    class SendAnswerError() : EdgeWebrtcError()

    /**
     * A invalid ICE candidate was received from the other peer
     */
    class ICECandidateError() : EdgeWebrtcError()

    /**
     * The RTC PeerConnection could not be created
     */
    class ConnectionInitError() : EdgeWebrtcError()
}

/**
 * Callback invoked when the object has been closed
 */
typealias OnClosedCallback = () -> Unit

/**
 * Callback invoked when the object (e.g. a data channel) has opened.
 */
typealias OnOpenedCallback = () -> Unit

/**
 * Callback invoked when the remote peer has added a Track to the WebRTC connection
 *
 * @param track [in] The newly added track.
 * @param trackId [in] The track ID reported by the device for this track.
 */
typealias OnTrackCallback = (track: EdgeMediaTrack, trackId: String?) -> Unit

/**
 * Callback invoked when an error occurs in the WebRTC connection
 *
 * @param error [in] The Error that occurred
 */
typealias OnErrorCallback = (error: EdgeWebrtcError) -> Unit

/**
 * Callback invoked when a data channel has received a message.
 */
typealias OnMessageCallback = (bytes: ByteArray) -> Unit

/**
 * Video Track representing a Media Track of type Video
 */
interface EdgeVideoTrack : EdgeMediaTrack {
    /**
     * Attach a Video View to the track. The EdgeVideoTrack object will push video frames to this view.
     *
     * @param view [in] The view to add
     * @throws IllegalArgumentException if [view] is null.
     */
    fun add(view: EdgeVideoView)

    /**
     * remove a Video View to the track.
     *
     * If the EdgeVideoView was not attached to this track, this function is a no-op.
     *
     * This function does not throw any exceptions.
     */
    fun remove(view: EdgeVideoView)
}

/**
 * Audio Track representing a Media Track of type Audio
 */
interface EdgeAudioTrack : EdgeMediaTrack {
    /**
     * Enable or disable the Audio track
     *
     * This function does not throw any exceptions.
     *
     * @param enabled [in] Boolean determining if the track is enabled
     */
    fun setEnabled(enabled: Boolean)

    /**
     * Set the volume of the Audio track.
     *
     * This function does not throw any exceptions.
     *
     * @param volume [in] The volume to set
     */
    fun setVolume(volume: Double)
}

/**
 * Data channel for sending and receiving bytes on a webrtc connection
 */
interface EdgeDataChannel {
    /**
     * Set callback to be invoked when the data channel receives a message.
     *
     * @param cb The callback to set
     */
    fun onMessage(cb: OnMessageCallback)

    /**
     * Set callback to be invoked when the data channel is open and ready to send/receive messages.
     *
     * @param cb The callback to set
     */
    fun onOpen(cb: OnOpenedCallback)

    /**
     * Set callback to be invoked when the data channel is closed.
     *
     * @param cb The callback to set
     */
    fun onClose(cb: OnClosedCallback)

    /**
     * Send a byte array over the data channel.
     *
     * @param data The binary data to be sent.
     */
    fun send(data: ByteArray)

    /**
     * Closes the data channel.
     */
    fun dataChannelClose()
}

/**
 * Main Connection interface used to connect to a device and interact with it.
 */
interface EdgeWebrtcConnection {
    /**
     * Set callback to be invoked when the WebRTC connection is closed
     *
     * @param cb The callback to set
     */
    fun onClosed(cb: OnClosedCallback)

    /**
     * Set callback to be invoked when a new track is available on the WebRTC connection
     *
     * @param cb The callback to set
     */
    fun onTrack(cb: OnTrackCallback)

    /**
     * Set callback to be invoked when an error occurs on the WebRTC connection.
     *
     * @param cb The callback to set
     */
    fun onError(cb: OnErrorCallback)


    /**
     * Establish a WebRTC connection to the other peer
     *
     * @throws EdgeWebrtcError.SignalingFailedToInitialize if the signaling stream could not be set up for some reason.
     * @throws EdgeWebrtcError.SignalingFailedRecv if the signaling stream failed to receive messages necessary to setting up the connection.
     * @throws EdgeWebrtcError.SignalingFailedSend if the signaling stream failed to send messages necessary to setting up the connection.
     * @return A CompletableFuture
     */
    fun connect(): CompletableFuture<Unit>

    /**
     * Create a new data channel
     * WARNING: Data channels are experimental and may not work as expected.
     *
     * @param label A string that describes the data channel.
     */
    fun createDataChannel(label: String): EdgeDataChannel

    /**
     * Add a track to this connection.
     *
     * @param track The track to be added.
     * @param streamIds List of stream ids that this track will be added to.
     */
    fun addTrack(track: EdgeMediaTrack, streamIds: List<String>)

    /**
     * Close a connected WebRTC connection.
     *
     * This function does not throw any exceptions.
     */
    fun connectionClose(): CompletableFuture<Unit>
}

/**
 * Manager interface to keep track of global WebRTC state
 */
interface EdgeWebrtcManager {

    /**
     * Set the log level to use by the underlying SDK
     *
     * @param logLevel [in] The log level to set
     */
    fun setLogLevel(logLevel: EdgeWebrtcLogLevel)

    /**
     * Initialize a video view to use for video tracks.
     *
     * This should be called from main thread, e.g. in the onViewCreated of a Fragment or such.
     *
     * This function does not throw any exceptions.
     *
     * @param view [in] The view to initialize
     */
    fun initVideoView(view: EdgeVideoView)

    /**
     * Create a new WebRTC connection instance using a preexisting Nabto Edge Connection for signaling.
     *
     * Only one WebRTC connection can exist on a Nabto Edge Connection at a time.
     *
     * This function does not throw any exceptions.
     *
     * @param conn [in] The Nabto Edge Connection to use for signaling
     * @return The created EdgeWebrtcConnection object
     */
    fun createRTCConnection(conn: Connection): EdgeWebrtcConnection

    /**
     * Create an AudioSource object that can be used to create a EdgeAudioTrack with createAudioTrack
     *
     * @param mediaConstraints a MediaConstraints object. Refer to https://developer.mozilla.org/en-US/docs/Web/API/MediaTrackConstraints#instance_properties_of_audio_tracks
     */
    fun createAudioSource(mediaConstraints: MediaConstraints): AudioSource

    /**
     * Create a VideoSource object that can be used to create a EdgeVideoTrack with createVideoTrack
     *
     * @param isScreenCast Sets whether the video a screencast or not
     */
    fun createVideoSource(isScreenCast: Boolean): VideoSource

    /**
     * Create a EdgeAudioTrack that can be added to a peer connection
     *
     * @param trackId The id of the track
     * @param source An AudioSource object created with createAudioSource
     */
    fun createAudioTrack(trackId: String, source: AudioSource): EdgeAudioTrack

    /**
     * Create a EdgeVideoTrack that can be added to a peer connection.
     *
     * @param trackId The id of the track.
     * @param source A VideoSource object created with createvideoSource
     */
    fun createVideoTrack(trackId: String, source: VideoSource): EdgeVideoTrack

    companion object {
        @JvmStatic
        fun getInstance(): EdgeWebrtcManager = EdgeWebrtcManagerInternal.instance
    }
}
