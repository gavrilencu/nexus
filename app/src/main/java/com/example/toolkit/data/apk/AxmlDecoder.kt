package com.example.toolkit.data.apk

/**
 * Minimal decoder for Android's binary XML (AXML) format — the encoding used
 * for `AndroidManifest.xml` and compiled `res/**/*.xml` inside an APK. Turns
 * the compiled resource chunks back into readable, indented XML text so the
 * inspector can display the manifest and let the user browse layout/xml files.
 *
 * Best-effort and fully defensive: any malformed input yields `null` and the
 * caller falls back to a hex/raw preview.
 */
object AxmlDecoder {

    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

    fun isAxml(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0].toInt() == 0x03 && bytes[1].toInt() == 0x00 &&
            bytes[2].toInt() == 0x08 && bytes[3].toInt() == 0x00

    fun decode(bytes: ByteArray): String? = runCatching { decodeInternal(bytes) }.getOrNull()

    private fun decodeInternal(bytes: ByteArray): String {
        val r = Reader(bytes)
        r.u16() // type (0x0003)
        r.u16() // header size
        r.u32() // file size

        // --- string pool ---
        val poolStart = r.pos
        r.u16() // 0x0001
        r.u16() // header size
        val poolSize = r.u32()
        val stringCount = r.u32()
        r.u32() // style count
        val flags = r.u32()
        val stringsStart = r.u32()
        r.u32() // styles start
        val isUtf8 = (flags and 0x100) != 0L
        val offsets = IntArray(stringCount.toInt()) { r.u32().toInt() }
        val stringDataBase = poolStart + stringsStart.toInt()
        val strings = Array(stringCount.toInt()) { i ->
            readPoolString(bytes, stringDataBase + offsets[i], isUtf8)
        }
        // jump past the whole string-pool chunk
        r.pos = poolStart + poolSize.toInt()

        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        val nsPrefix = HashMap<String, String>()
        var indent = 0

        fun name(ref: Long): String {
            val i = ref.toInt()
            return if (i in strings.indices) strings[i] else ""
        }

        while (r.pos + 8 <= bytes.size) {
            val chunkStart = r.pos
            val type = r.u16()
            r.u16() // header size
            val size = r.u32().toInt()
            when (type) {
                0x0100 -> { // START_NAMESPACE
                    r.u32(); r.u32()
                    val prefix = name(r.u32().toLong())
                    val uri = name(r.u32().toLong())
                    if (uri.isNotEmpty()) nsPrefix[uri] = prefix
                }
                0x0101 -> { r.pos = chunkStart + size } // END_NAMESPACE
                0x0102 -> { // START_ELEMENT
                    r.u32() // line
                    r.u32() // comment
                    r.u32() // ns
                    val elName = name(r.u32().toLong())
                    r.u16() // attribute start
                    r.u16() // attribute size
                    val attrCount = r.u16()
                    r.u16(); r.u16(); r.u16() // id/class/style index
                    val pad = "  ".repeat(indent)
                    sb.append(pad).append('<').append(elName)
                    for (a in 0 until attrCount) {
                        val ns = r.u32()
                        val an = name(r.u32().toLong())
                        val rawRef = r.u32()
                        r.u16() // size
                        r.u8()  // res0
                        val dataType = r.u8()
                        val data = r.u32()
                        val prefix = if (ns != 0xFFFFFFFFL) {
                            val uri = name(ns.toLong())
                            (nsPrefix[uri] ?: if (uri == ANDROID_NS) "android" else "")
                        } else ""
                        val attrName = if (prefix.isNotEmpty()) "$prefix:$an" else an
                        val value = attrValue(rawRef, dataType, data, strings)
                        sb.append(' ').append(attrName).append("=\"").append(value.escapeXml()).append('"')
                    }
                    sb.append(">\n")
                    indent++
                }
                0x0103 -> { // END_ELEMENT
                    r.u32() // line
                    r.u32() // comment
                    r.u32() // ns
                    val elName = name(r.u32().toLong())
                    indent = (indent - 1).coerceAtLeast(0)
                    sb.append("  ".repeat(indent)).append("</").append(elName).append(">\n")
                }
                0x0104 -> { r.pos = chunkStart + size } // CDATA
                else -> {
                    if (size <= 0) return sb.toString()
                    r.pos = chunkStart + size
                }
            }
            if (size > 0) r.pos = chunkStart + size
        }
        return sb.toString()
    }

    private fun attrValue(rawRef: Long, dataType: Int, data: Long, strings: Array<String>): String {
        if (rawRef != 0xFFFFFFFFL) {
            val i = rawRef.toInt()
            if (i in strings.indices) return strings[i]
        }
        return when (dataType) {
            0x03 -> { val i = data.toInt(); if (i in strings.indices) strings[i] else "" }
            0x10 -> data.toInt().toString()
            0x11 -> "0x%x".format(data)
            0x12 -> if (data != 0L) "true" else "false"
            0x01 -> "@0x%08x".format(data)
            0x02 -> "?0x%08x".format(data)
            0x04 -> Float.fromBits(data.toInt()).toString()
            else -> data.toString()
        }
    }

    private fun readPoolString(bytes: ByteArray, off: Int, utf8: Boolean): String = runCatching {
        var p = off
        if (utf8) {
            // char count (skip), then byte count, then UTF-8 bytes
            var n = bytes[p].toInt() and 0xFF; p++
            if (n and 0x80 != 0) { n = ((n and 0x7F) shl 8) or (bytes[p].toInt() and 0xFF); p++ }
            var bl = bytes[p].toInt() and 0xFF; p++
            if (bl and 0x80 != 0) { bl = ((bl and 0x7F) shl 8) or (bytes[p].toInt() and 0xFF); p++ }
            String(bytes, p, bl, Charsets.UTF_8)
        } else {
            var n = (bytes[p].toInt() and 0xFF) or ((bytes[p + 1].toInt() and 0xFF) shl 8); p += 2
            if (n and 0x8000 != 0) {
                n = ((n and 0x7FFF) shl 16) or ((bytes[p].toInt() and 0xFF) or ((bytes[p + 1].toInt() and 0xFF) shl 8))
                p += 2
            }
            String(bytes, p, n * 2, Charsets.UTF_16LE)
        }
    }.getOrDefault("")

    private fun String.escapeXml(): String =
        replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /** Little-endian cursor over the AXML byte array. */
    private class Reader(val b: ByteArray) {
        var pos = 0
        fun u8(): Int = (b[pos++].toInt() and 0xFF)
        fun u16(): Int { val v = (b[pos].toInt() and 0xFF) or ((b[pos + 1].toInt() and 0xFF) shl 8); pos += 2; return v }
        fun u32(): Long {
            val v = (b[pos].toLong() and 0xFF) or ((b[pos + 1].toLong() and 0xFF) shl 8) or
                ((b[pos + 2].toLong() and 0xFF) shl 16) or ((b[pos + 3].toLong() and 0xFF) shl 24)
            pos += 4; return v
        }
    }
}
