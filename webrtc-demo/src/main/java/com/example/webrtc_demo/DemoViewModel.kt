package com.example.webrtc_demo

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nabto.edge.client.Connection
import com.nabto.edge.client.ConnectionEventsCallback
import com.nabto.edge.client.NabtoClient
import com.nabto.edge.client.ktx.awaitConnect
import com.nabto.edge.client.ktx.awaitExecute
import com.nabto.edge.client.webrtc.EdgeAudioTrack
import com.nabto.edge.client.webrtc.EdgeMediaTrackType
import com.nabto.edge.client.webrtc.EdgeVideoTrack
import com.nabto.edge.client.webrtc.EdgeWebrtcConnection
import com.nabto.edge.client.webrtc.EdgeWebrtcLogLevel
import com.nabto.edge.client.webrtc.EdgeWebrtcManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "WebRTCDemo"

class DemoViewModel(application: Application) : AndroidViewModel(application) {
    private val _statusLog = MutableStateFlow(listOf<String>())
    val statusLog: StateFlow<List<String>> = _statusLog

    private val _videoTrack = MutableStateFlow<EdgeVideoTrack?>(null)
    val videoTrack: StateFlow<EdgeVideoTrack?> = _videoTrack

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private var client: NabtoClient? = null
    private var conn: Connection? = null
    private var pc: EdgeWebrtcConnection? = null
    private var audioTrack: EdgeAudioTrack? = null
    private var currentParams: Triple<String, String, String>? = null

    private fun status(msg: String) {
        Log.i(TAG, msg)
        _statusLog.value = _statusLog.value + "${timeFormat.format(Date())} $msg"
    }

    /**
     * Connect to the device. If we are already connected (or connecting) with the same
     * parameters this is a no-op, so the video screen can call it every time it is shown.
     */
    fun connect(productId: String, deviceId: String, sct: String) {
        val params = Triple(productId, deviceId, sct)
        if (params == currentParams) {
            return
        }
        if (currentParams != null) {
            status("Settings changed, reconnecting")
        }
        currentParams = params
        closeCurrent()

        viewModelScope.launch {
            runCatching { doConnect(productId, deviceId, sct) }
                .onFailure { status("Unexpected failure: $it") }
        }
    }

    private suspend fun doConnect(productId: String, deviceId: String, sct: String) {
        EdgeWebrtcManager.getInstance().setLogLevel(EdgeWebrtcLogLevel.VERBOSE)

        val client = client ?: NabtoClient.create(getApplication()).also { client = it }
        val conn = client.createConnection().also { conn = it }

        val opts = JSONObject()
        opts.put("ProductId", productId)
        opts.put("DeviceId", deviceId)
        opts.put("PrivateKey", client.createPrivateKey())
        opts.put("ServerConnectToken", sct)
        conn.updateOptions(opts.toString())

        conn.addConnectionEventsListener(object : ConnectionEventsCallback() {
            override fun onEvent(event: Int) {
                val name = when (event) {
                    CONNECTED -> "CONNECTED"
                    CLOSED -> "CLOSED"
                    CHANNEL_CHANGED -> "CHANNEL_CHANGED"
                    WAITING_FOR_ATTACH -> "WAITING_FOR_ATTACH"
                    else -> "unknown ($event)"
                }
                status("Connection event: $name")
            }
        })

        status("Connecting to $productId/$deviceId (SCT \"$sct\")")
        try {
            conn.awaitConnect()
        } catch (e: Exception) {
            status("Nabto connect FAILED: $e")
            status("Check that the device is online and that product id, device id and SCT are correct (Settings menu)")
            return
        }
        status("Nabto connection established, device fingerprint: ${runCatching { conn.deviceFingerprint }.getOrDefault("?")}")

        status("Creating WebRTC connection")
        val pc = EdgeWebrtcManager.getInstance().createRTCConnection(conn).also { pc = it }

        pc.onError { error ->
            status("WebRTC error: ${error::class.simpleName ?: error}")
        }
        pc.onClosed {
            status("WebRTC connection closed")
        }
        pc.onTrack { track, _ ->
            status("Received ${track.type} track")
            when (track.type) {
                EdgeMediaTrackType.VIDEO -> _videoTrack.value = track as EdgeVideoTrack
                EdgeMediaTrackType.AUDIO -> {
                    audioTrack = (track as EdgeAudioTrack).apply { setEnabled(true) }
                    status("Audio track enabled")
                }
            }
        }

        status("Starting WebRTC signaling")
        try {
            pc.connect().await()
        } catch (e: Exception) {
            status("WebRTC signaling FAILED: $e")
            return
        }
        status("WebRTC signaling connected")

        val dc = pc.createDataChannel("test")
        dc.onOpen { status("DataChannel opened") }
        dc.onClose { status("DataChannel closed") }
        dc.onMessage { msg -> status("DataChannel message: ${msg.toString(Charsets.UTF_8)}") }

        // Ask the device to add its video track to this connection (simple-webrtc
        // protocol: a bare POST, no payload, no track selection). This must happen
        // after WebRTC signaling is established: the device attaches the track to
        // this connection's peer connection and returns 500 if there is none yet.
        try {
            status("Requesting video track: CoAP POST /webrtc/tracks")
            val request = conn.createCoap("POST", "/webrtc/tracks")
            request.awaitExecute()
            when (request.responseStatusCode) {
                201 -> status("Video track requested (CoAP 201), waiting for renegotiation and media")
                500 -> status("Track request FAILED (500): device could not attach the track to this connection")
                else -> status("Track request FAILED: CoAP status ${request.responseStatusCode}")
            }
        } catch (e: Exception) {
            status("CoAP request FAILED: $e")
        }
    }

    private fun closeCurrent() {
        _videoTrack.value = null
        audioTrack = null
        pc?.let { old -> runCatching { old.connectionClose() } }
        pc = null
        conn?.let { old -> runCatching { old.close() } }
        conn = null
    }

    override fun onCleared() {
        super.onCleared()
        closeCurrent()
    }
}
