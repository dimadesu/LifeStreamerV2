package com.dimadesu.lifestreamer.rtmp

import com.dimadesu.lifestreamer.data.storage.DataStoreRepository
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.roundToInt

class RtmpSourceSettingsTest {

    @Test
    fun testRtmpSourceUrlEntryProperties() {
        val entry1 = DataStoreRepository.RtmpSourceUrlEntry(1, "rtmp://localhost:1935/publish/live")
        val entry2 = DataStoreRepository.RtmpSourceUrlEntry(2, "rtmp://localhost:1935/publish/live2")

        assertEquals(1, entry1.index)
        assertEquals("rtmp://localhost:1935/publish/live", entry1.url)

        assertEquals(2, entry2.index)
        assertEquals("rtmp://localhost:1935/publish/live2", entry2.url)
    }

    @Test
    fun testBufferMsRoundingAndCoercion() {
        fun roundBuffer(bufferMs: Int): Int {
            return ((bufferMs / 500) * 500).coerceIn(500, 6000)
        }

        assertEquals(500, roundBuffer(200))
        assertEquals(500, roundBuffer(500))
        assertEquals(2500, roundBuffer(2500))
        assertEquals(2500, roundBuffer(2700))
        assertEquals(3000, roundBuffer(3200))
        assertEquals(6000, roundBuffer(6000))
        assertEquals(6000, roundBuffer(7500))
    }

    @Test
    fun testBufferMsSliderConversion() {
        fun sliderValueToBuffer(floatVal: Float): Int {
            return ((floatVal / 500f).roundToInt() * 500).coerceIn(500, 6000)
        }

        assertEquals(500, sliderValueToBuffer(500f))
        assertEquals(500, sliderValueToBuffer(600f))
        assertEquals(1000, sliderValueToBuffer(800f))
        assertEquals(2500, sliderValueToBuffer(2500f))
        assertEquals(6000, sliderValueToBuffer(5950f))
        assertEquals(6000, sliderValueToBuffer(6000f))
    }
}
