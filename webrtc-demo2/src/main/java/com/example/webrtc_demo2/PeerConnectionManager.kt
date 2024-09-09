package com.example.webrtc_demo2

import android.content.Context
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory

/**
 * The manager is responsible for creating the egl context and a PeerConnectionFactory which is appropriate for the application
 */
class PeerConnectionManager(val context : Context) {
    val eglBase: EglBase = EglBase.create()
    lateinit var peerConnectionFactory : PeerConnectionFactory
    init {
        val initOpts = PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions()
        PeerConnectionFactory.initialize(initOpts)
        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
        peerConnectionFactory = PeerConnectionFactory.builder().apply {
            setVideoEncoderFactory(encoderFactory)
            setVideoDecoderFactory(decoderFactory)
        }.createPeerConnectionFactory()
    }
}