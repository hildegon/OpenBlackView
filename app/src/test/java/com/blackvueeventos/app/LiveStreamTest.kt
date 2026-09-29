package com.blackvueeventos.app

import com.blackvueeventos.app.blackvue.JpegAssembler
import com.blackvueeventos.app.blackvue.liveStreamUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveStreamTest {
    @Test
    fun liveStreamUrl_usesFrontAndRearPaths() {
        assertEquals("http://10.99.77.1/blackvue_live.cgi", liveStreamUrl("10.99.77.1", rear = false))
        assertEquals(
            "http://10.99.77.1/blackvue_live.cgi?direction=R",
            liveStreamUrl("http://10.99.77.1/", rear = true),
        )
        assertEquals(
            "http://10.99.77.1:8080/blackvue_live.cgi",
            liveStreamUrl("10.99.77.1:8080", rear = false),
        )
    }

    @Test
    fun jpegAssembler_joinsAFrameSplitAcrossReads() {
        val assembler = JpegAssembler()
        assertEquals(0, assembler.push(byteArrayOf(0x00, 0xFF.toByte(), 0xD8.toByte(), 0x11)).size)
        val frames = assembler.push(byteArrayOf(0x22, 0xFF.toByte(), 0xD9.toByte(), 0xFF.toByte()))
        assertEquals(1, frames.size)
        assertEquals(
            listOf<Byte>(0xFF.toByte(), 0xD8.toByte(), 0x11, 0x22, 0xFF.toByte(), 0xD9.toByte()),
            frames[0].toList(),
        )
        val rest = assembler.push(byteArrayOf(0xD8.toByte(), 0x33, 0xFF.toByte(), 0xD9.toByte()))
        assertEquals(1, rest.size)
        assertEquals(
            listOf<Byte>(0xFF.toByte(), 0xD8.toByte(), 0x33, 0xFF.toByte(), 0xD9.toByte()),
            rest[0].toList(),
        )
    }
}