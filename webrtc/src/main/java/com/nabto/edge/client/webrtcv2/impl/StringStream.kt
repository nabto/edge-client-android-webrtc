package com.nabto.edge.client.webrtcv2.impl


/**
 * Interface for a stream of length value encoded signaling messages,
 */
interface StringStream {
    /**
     * Send message can be called from multiple threads and the order of the sends will be preserved.
     * This is relevant if a candidate is sent at the same time a description is being sent.
     */
    suspend fun sendMessage(message : String)
    suspend fun revcMessage() : String
}