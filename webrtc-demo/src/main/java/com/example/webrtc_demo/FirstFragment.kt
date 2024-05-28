package com.example.webrtc_demo

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.example.webrtc_demo.databinding.FragmentFirstBinding
import com.nabto.edge.client.Connection
import com.nabto.edge.client.ConnectionEventsCallback
import com.nabto.edge.client.NabtoClient
import com.nabto.edge.client.NabtoNoChannelsException
import com.nabto.edge.client.webrtc.*
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {
    private var _binding: FragmentFirstBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private lateinit var conn: Connection
    private lateinit var pc: EdgeWebrtcConnection
    private lateinit var remoteTrack: EdgeVideoTrack
    private lateinit var remoteAudioTrack: EdgeAudioTrack

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root

    }

    suspend fun onConnected() {
        Log.i("TestApp", "Logged in")
        pc = EdgeWebrtcManager.getInstance().createRTCConnection(conn)

        pc.onTrack { track, _ ->
            Log.i("TestApp", "Track of type ${track.type}")
            if (track.type == EdgeMediaTrackType.VIDEO) {
                remoteTrack = track as EdgeVideoTrack
                remoteTrack.add(binding.videoView)
            }

            if (track.type == EdgeMediaTrackType.AUDIO) {
                remoteAudioTrack = track as EdgeAudioTrack
                remoteAudioTrack.setEnabled(true)
            }
        }

        pc.connect()
        val trackInfo = """
            {"tracks": ["frontdoor-video", "frontdoor-audio"]}
        """.trimIndent()
        val coap = conn.createCoap("POST", "/webrtc/tracks")
        coap.setRequestPayload(50, trackInfo.toByteArray())
        coap.execute()
        Log.i("TestApp", "Coap response: ${coap.responseStatusCode}")
        if (coap.responseStatusCode != 201) {
            Log.i("TestApp", "Failed to get video feed with status ${coap.responseStatusCode}")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        EdgeWebrtcManager.getInstance().initVideoView(binding.videoView)
        val client = NabtoClient.create(requireActivity())
        conn = client.createConnection()

        val opts = JSONObject()
        opts.put("ProductId", "pr-t3vyxzj3")
        opts.put("DeviceId", "de-evhnqyix")
        opts.put("PrivateKey", client.createPrivateKey())
        opts.put("ServerConnectToken", "ivt9EQMffMQl")

        conn.updateOptions(opts.toString())
        conn.addConnectionEventsListener(object : ConnectionEventsCallback() {
            override fun onEvent(event: Int) {
                if (event == CONNECTED) {
                    conn.passwordAuthenticate("admin", "FEVNHyYvxgWF")
                    lifecycleScope.launch {
                        onConnected()
                    }
                }
            }

        })

        try {
            conn.connect()
        } catch (e: NabtoNoChannelsException) {
            Log.e("WebRTCDemo", "${e.localChannelErrorCode.name} :: ${e.remoteChannelErrorCode.name}")
            throw e
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}