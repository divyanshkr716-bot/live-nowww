package com.example

import com.example.streaming.rtmp.Amf0
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class Amf0CodecTest {

    @Test
    fun `test string round trip serialization`() {
        val out = ByteArrayOutputStream()
        Amf0.writeString(out, "NetStream.Publish.Start")

        val list = Amf0.readAll(out.toByteArray())
        assertEquals(1, list.size)
        assertEquals("NetStream.Publish.Start", list[0])
    }

    @Test
    fun `test number round trip serialization`() {
        val out = ByteArrayOutputStream()
        Amf0.writeNumber(out, 4.0)

        val list = Amf0.readAll(out.toByteArray())
        assertEquals(1, list.size)
        assertEquals(4.0, (list[0] as Number).toDouble(), 0.001)
    }

    @Test
    fun `test object round trip serialization`() {
        val out = ByteArrayOutputStream()
        val original = mapOf(
            "code" to "NetStream.Publish.Start",
            "level" to "status",
            "description" to "Publishing test stream"
        )
        Amf0.writeObject(out, original)

        val list = Amf0.readAll(out.toByteArray())
        assertEquals(1, list.size)
        val decoded = list[0] as Map<*, *>
        assertEquals("NetStream.Publish.Start", decoded["code"])
        assertEquals("status", decoded["level"])
        assertEquals("Publishing test stream", decoded["description"])
    }

    @Test
    fun `test createStream result parsing`() {
        // Simulates server response to createStream:
        // [0] = "_result", [1] = 4.0 (transactionId), [2] = null, [3] = 1.0 (streamId)
        val out = ByteArrayOutputStream()
        Amf0.writeString(out, "_result")
        Amf0.writeNumber(out, 4.0)
        Amf0.writeNull(out)
        Amf0.writeNumber(out, 1.0)

        val response = Amf0.readAll(out.toByteArray())
        assertEquals(4, response.size)
        assertEquals("_result", response[0])
        assertEquals(4.0, (response[1] as Number).toDouble(), 0.001)
        assertEquals(null, response[2])
        assertEquals(1, (response[3] as Number).toInt())
    }

    @Test
    fun `test onStatus publish rejection parsing`() {
        // Simulates server response when stream key is invalid:
        // [0] = "onStatus", [1] = 0.0, [2] = null, [3] = Object { code: "NetStream.Publish.BadName", level: "error" }
        val out = ByteArrayOutputStream()
        Amf0.writeString(out, "onStatus")
        Amf0.writeNumber(out, 0.0)
        Amf0.writeNull(out)
        Amf0.writeObject(
            out,
            mapOf(
                "code" to "NetStream.Publish.BadName",
                "level" to "error",
                "description" to "Invalid stream name or unauthorized"
            )
        )

        val response = Amf0.readAll(out.toByteArray())
        assertEquals(4, response.size)
        assertEquals("onStatus", response[0])
        val info = response[3] as Map<*, *>
        assertEquals("NetStream.Publish.BadName", info["code"])
        assertEquals("error", info["level"])
    }
}
