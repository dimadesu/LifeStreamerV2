package com.dimadesu.lifestreamer.ui.main.video

import com.dimadesu.lifestreamer.data.storage.StoredVideoSettings
import com.dimadesu.lifestreamer.models.StreamOrientation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoredVideoSettingsTest {

    @Test
    fun `forceEncoderFps defaults to false`() {
        val defaults = StoredVideoSettings()
        assertFalse(defaults.forceEncoderFps)
        assertTrue(defaults.matchFps)
    }

    @Test
    fun `toggling forceEncoderFps preserves cameraFps, matchFps, and encoder fps`() {
        val initial = StoredVideoSettings(
            orientation = StreamOrientation.LANDSCAPE,
            encoder = "video/avc",
            resolution = "1920x1080",
            cameraFps = 30,
            matchFps = false,
            fps = 60
        )

        val enabled = initial.copy(forceEncoderFps = true)
        assertTrue(enabled.forceEncoderFps)
        assertEquals(30, enabled.cameraFps)
        assertFalse(enabled.matchFps)
        assertEquals(60, enabled.fps)

        // Toggling matchFps keeps forceEncoderFps intact since the switch is always available
        val matched = enabled.copy(matchFps = true)
        assertTrue(matched.forceEncoderFps)
        assertTrue(matched.matchFps)
    }
}
