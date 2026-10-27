package com.dimadesu.lifestreamer.camera

import android.hardware.camera2.CaptureRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CameraNoiseReductionControllerTest {

    @Test
    fun testModeConstantsMatchCamera2CaptureRequest() {
        assertEquals(CaptureRequest.NOISE_REDUCTION_MODE_OFF, CameraNoiseReductionController.NOISE_REDUCTION_MODE_OFF)
        assertEquals(CaptureRequest.NOISE_REDUCTION_MODE_FAST, CameraNoiseReductionController.NOISE_REDUCTION_MODE_FAST)
        assertEquals(CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY, CameraNoiseReductionController.NOISE_REDUCTION_MODE_HIGH_QUALITY)
        assertEquals(CaptureRequest.NOISE_REDUCTION_MODE_MINIMAL, CameraNoiseReductionController.NOISE_REDUCTION_MODE_MINIMAL)
        assertEquals(CaptureRequest.NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG, CameraNoiseReductionController.NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG)
    }

    @Test
    fun testGetNoiseReductionModeName() {
        assertEquals("Off", CameraNoiseReductionController.getNoiseReductionModeName(CameraNoiseReductionController.NOISE_REDUCTION_MODE_OFF))
        assertEquals("Fast", CameraNoiseReductionController.getNoiseReductionModeName(CameraNoiseReductionController.NOISE_REDUCTION_MODE_FAST))
        assertEquals("High Quality", CameraNoiseReductionController.getNoiseReductionModeName(CameraNoiseReductionController.NOISE_REDUCTION_MODE_HIGH_QUALITY))
        assertEquals("Minimal", CameraNoiseReductionController.getNoiseReductionModeName(CameraNoiseReductionController.NOISE_REDUCTION_MODE_MINIMAL))
        assertEquals("Zero Shutter Lag", CameraNoiseReductionController.getNoiseReductionModeName(CameraNoiseReductionController.NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG))
        assertEquals("Unknown", CameraNoiseReductionController.getNoiseReductionModeName(999))
    }

    @Test
    fun testInitialState() {
        val controller = CameraNoiseReductionController()
        assertNull(controller.userPreferredMode)
    }
}
