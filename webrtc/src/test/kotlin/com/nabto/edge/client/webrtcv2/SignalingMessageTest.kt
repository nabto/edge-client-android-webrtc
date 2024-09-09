package com.nabto.edge.client.webrtcv2

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.readValues
import com.nabto.edge.client.webrtcv2.impl.SignalingDescriptionMessage
import com.nabto.edge.client.webrtcv2.impl.SignalingSetupRequestMessage
import com.nabto.edge.client.webrtcv2.impl.SignalingSetupResponseMessage
import io.mockk.InternalPlatformDsl.toArray
import org.junit.Test

import org.junit.Assert.*


class SignalingMessageTest {
    @Test
    fun parse_setup_response() {
        val str1 = """{"type": "setupResponse", "polite": false, "iceServers": null, "id": "string"}"""
        val str2 = """{"type": "setupResponse", "polite": false, "id": "string"}"""
        val str3 = """{"type": "setupResponse", "polite": false, "id": "string", "extradata": "foo"}"""
        val strings : List<String> = listOf(str1, str2, str3)
        strings.forEach {
            val mapper = jacksonObjectMapper()
            val setupResponse = mapper.readValue(it, SignalingSetupResponseMessage::class.java)
            assertEquals(setupResponse.type, "setupResponse")
            assertEquals(setupResponse.polite, false)
            assertEquals(setupResponse.iceServers, null)
            assertEquals(setupResponse.id, "string")
        }
    }

    @Test
    fun parse_setup_response_ice_server() {
        val str1 = """{"type": "setupResponse", "polite": false, "iceServers": [{"urls": ["url1","url2"], "username": "foo", "credential": "quux", "extradata": "extra"}], "id": "string"}""".trimMargin()
        val mapper = jacksonObjectMapper()
        val setupResponse = mapper.readValue(str1, SignalingSetupResponseMessage::class.java)
        assertEquals(setupResponse.iceServers?.size, 1)
        val iceServer = setupResponse.iceServers?.get(0);
        assertEquals(iceServer?.urls, listOf("url1", "url2"))
        assertEquals(iceServer?.username, "foo")
        assertEquals(iceServer?.credential, "quux")
    }

    @Test
    fun parse_setup_response_ice_servers() {
        val str1 = """{"type": "setupResponse", "polite": false, "iceServers": [{"urls": ["url1","url2"]}, {"urls": ["url3"], "username": "foo"}, {"urls": ["url4"], "credential": "bar"}, {"urls": ["url5"], "username": "baz", "credential": "quux"}], "id": "string"}""".trimMargin()
        val mapper = jacksonObjectMapper()
        val setupResponse = mapper.readValue(str1, SignalingSetupResponseMessage::class.java)
        assertEquals(setupResponse.iceServers?.size, 4)
    }

    @Test
    fun parse_description_message() {
        val str1 = """{"type": "description", "description": { "type": "offer", "sdp": "sdpstuff"}}"""
        val str2 = """{"type": "description", "description": { "type": "offer", "sdp": "sdpstuff"}, "metadata": null}"""
        val str3 = """{"type": "description", "description": { "type": "offer", "sdp": "sdpstuff"}, "metadata": "meta"}"""
        val str4 = """{"type": "description", "description": { "type": "offer", "sdp": "sdpstuff", "extradata": "extra"}, "extradata": "extra"}"""
        val mapper = jacksonObjectMapper()
        listOf(str1, str2, str3, str4).forEach {
            val mapper = jacksonObjectMapper()
            val description = mapper.readValue(it, SignalingDescriptionMessage::class.java)
            assertEquals(description.type, "description")
            assertEquals(description.description.type, "offer")
            assertEquals(description.description.sdp, "sdpstuff")
        }
    }

    @Test
    fun write_setup_request() {
        val mapper = jacksonObjectMapper()
        val request = SignalingSetupRequestMessage();
        val str = mapper.writeValueAsString(request)
        assertEquals(str, """{"type": "SETUP_REQUEST"}""")
    }

    @Test
    fun write_setup_request_wish_politeness() {
        val mapper = jacksonObjectMapper()
        val request = SignalingSetupRequestMessage(polite = true);
        val str = mapper.writeValueAsString(request)
        assertEquals(str, """{"type":"SETUP_REQUEST","polite":true}""")
    }

    @Test
    fun parse_setup_request_null_politeness() {
        val mapper = jacksonObjectMapper()
        val obj = mapper.readValue("""{"type":"SETUP_REQUEST","polite":null}""", SignalingSetupRequestMessage::class.java)
        assertEquals(obj.polite, null)
        assertEquals(obj.type, "SETUP_REQUEST")
    }
    @Test
    fun parse_setup_request_no_politeness() {
        val mapper = jacksonObjectMapper()
        val obj = mapper.readValue("""{"type":"SETUP_REQUEST"}""", SignalingSetupRequestMessage::class.java)
        assertEquals(obj.polite, null)
    }
    @Test
    fun parse_setup_request_no_politeness2() {
        val mapper = jacksonObjectMapper()
        val obj = mapper.readValue("""{"type":"SETUP_REQUEST"}""", SignalingSetupRequestMessage::class.java)
        assertEquals(obj.polite, null)
    }
    @Test
    fun parse_setup_request_no_type() {
        val mapper = jacksonObjectMapper()
        val obj = mapper.readValue("""{}""", SignalingSetupRequestMessage::class.java)
        assertEquals(obj.polite, null)
    }
}

