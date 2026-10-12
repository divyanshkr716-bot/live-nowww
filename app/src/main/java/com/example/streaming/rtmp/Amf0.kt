package com.example.streaming.rtmp

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

object Amf0 {
    const val TYPE_NUMBER: Byte = 0x00
    const val TYPE_BOOLEAN: Byte = 0x01
    const val TYPE_STRING: Byte = 0x02
    const val TYPE_OBJECT: Byte = 0x03
    const val TYPE_NULL: Byte = 0x05
    const val TYPE_UNDEFINED: Byte = 0x06
    const val TYPE_ECMA_ARRAY: Byte = 0x08
    const val TYPE_OBJECT_END: Byte = 0x09
    const val TYPE_STRICT_ARRAY: Byte = 0x0A
    const val TYPE_LONG_STRING: Byte = 0x0C

    fun writeString(out: OutputStream, value: String) {
        out.write(TYPE_STRING.toInt())
        val bytes = value.toByteArray(Charsets.UTF_8)
        out.write((bytes.size shr 8) and 0xFF)
        out.write(bytes.size and 0xFF)
        out.write(bytes)
    }

    fun writeNumber(out: OutputStream, value: Double) {
        out.write(TYPE_NUMBER.toInt())
        val dos = DataOutputStream(out)
        dos.writeDouble(value)
    }

    fun writeBoolean(out: OutputStream, value: Boolean) {
        out.write(TYPE_BOOLEAN.toInt())
        out.write(if (value) 1 else 0)
    }

    fun writeNull(out: OutputStream) {
        out.write(TYPE_NULL.toInt())
    }

    fun writeObject(out: OutputStream, properties: Map<String, Any?>) {
        out.write(TYPE_OBJECT.toInt())
        for ((key, value) in properties) {
            writePropertyKey(out, key)
            writeValue(out, value)
        }
        writePropertyKey(out, "")
        out.write(TYPE_OBJECT_END.toInt())
    }

    fun writeEcmaArray(out: OutputStream, properties: Map<String, Any?>) {
        out.write(TYPE_ECMA_ARRAY.toInt())
        val count = properties.size
        out.write((count shr 24) and 0xFF)
        out.write((count shr 16) and 0xFF)
        out.write((count shr 8) and 0xFF)
        out.write(count and 0xFF)

        for ((key, value) in properties) {
            writePropertyKey(out, key)
            writeValue(out, value)
        }
        writePropertyKey(out, "")
        out.write(TYPE_OBJECT_END.toInt())
    }

    private fun writePropertyKey(out: OutputStream, key: String) {
        val bytes = key.toByteArray(Charsets.UTF_8)
        out.write((bytes.size shr 8) and 0xFF)
        out.write(bytes.size and 0xFF)
        out.write(bytes)
    }

    private fun writeValue(out: OutputStream, value: Any?) {
        when (value) {
            null -> writeNull(out)
            is String -> writeString(out, value)
            is Number -> writeNumber(out, value.toDouble())
            is Boolean -> writeBoolean(out, value)
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                writeObject(out, value as Map<String, Any?>)
            }
            else -> writeString(out, value.toString())
        }
    }

    /**
     * Reads all AMF0 values from the given byte array.
     */
    fun readAll(payload: ByteArray): List<Any?> {
        val results = mutableListOf<Any?>()
        val stream = ByteArrayInputStream(payload)
        while (stream.available() > 0) {
            val item = readValue(stream)
            results.add(item)
        }
        return results
    }

    fun readValue(input: InputStream): Any? {
        val type = input.read()
        if (type == -1) throw EOFException("Unexpected EOF reading AMF0 type")
        return when (type.toByte()) {
            TYPE_NUMBER -> DataInputStream(input).readDouble()
            TYPE_BOOLEAN -> input.read() != 0
            TYPE_STRING -> readString(input)
            TYPE_OBJECT -> readObject(input)
            TYPE_NULL, TYPE_UNDEFINED -> null
            TYPE_ECMA_ARRAY -> {
                DataInputStream(input).readInt() // Skip count
                readObject(input)
            }
            TYPE_STRICT_ARRAY -> {
                val count = DataInputStream(input).readInt()
                val list = mutableListOf<Any?>()
                for (i in 0 until count) {
                    list.add(readValue(input))
                }
                list
            }
            TYPE_LONG_STRING -> readLongString(input)
            else -> null
        }
    }

    private fun readString(input: InputStream): String {
        val dis = DataInputStream(input)
        val length = dis.readUnsignedShort()
        val bytes = ByteArray(length)
        dis.readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun readLongString(input: InputStream): String {
        val dis = DataInputStream(input)
        val length = dis.readInt()
        val bytes = ByteArray(length)
        dis.readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun readObject(input: InputStream): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        val dis = DataInputStream(input)
        while (true) {
            val keyLength = dis.readUnsignedShort()
            if (keyLength == 0) {
                // Check if next byte is object end
                val endMarker = input.read()
                if (endMarker == TYPE_OBJECT_END.toInt()) {
                    break
                }
            }
            val keyBytes = ByteArray(keyLength)
            dis.readFully(keyBytes)
            val key = String(keyBytes, Charsets.UTF_8)
            val value = readValue(input)
            map[key] = value
        }
        return map
    }
}
