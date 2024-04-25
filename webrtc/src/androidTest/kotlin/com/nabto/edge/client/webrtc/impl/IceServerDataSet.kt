package com.nabto.edge.client.webrtc.impl

object IceServerDataSet {
    fun Message(iceServer: String): String {
        return """
            {
                "type": 4,
                "iceServers": [
                    $iceServer
                ]
            }
        """.trimIndent()
    }

    val example1 = Message("""
        {"urls": ["foobar"]}
    """.trimIndent())

    val example2 = Message("""
        {
            "urls": ["a", "b"],
            "username": "foo",
            "credential": "bar"
        }
    """.trimIndent())

    val example3 = Message("""
        {
            "urls": ["a", "b"],
            "username": "foo",
            "credential": "bar"
        },
        {
            "urls": ["c", "d"],
            "username": "bob",
            "credential": "dog"
        }
    """.trimIndent())

    val invalidMissingUrls = Message("""
        {
            "username": "foo",
            "credential": "bar"
        }
    """.trimIndent())
}