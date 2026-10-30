package com.dimadesu.lifestreamer.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.github.thibaultbee.streampack.core.elements.sources.video.camera.CameraSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Controller managing camera video noise reduction:
 * - Detects per-camera capabilities (Off, Fast, High Quality, Minimal, Zero Shutter Lag)
 * - Resets to the best default mode on camera change, like other camera controls
 * - Exposes reactive state for UI controls
 */
class CameraNoiseReductionController {

    private val _isAvailable = MutableLiveData(false)
    val isAvailable: LiveData<Boolean> = _isAvailable

    private val _availableModes = MutableLiveData<List<Int>>(emptyList())
    val availableModes: LiveData<List<Int>> = _availableModes

    private val _currentMode = MutableLiveData(NOISE_REDUCTION_MODE_OFF)
    val currentMode: LiveData<Int> = _currentMode

    var userPreferredMode: Int? = null
        private set

    /**
     * Called when the active camera source changes.
     * Resets the user preference, computes available modes, picks the best default mode,
     * and applies it when active.
     */
    fun onCameraChanged(settings: CameraSettings, scope: CoroutineScope) {
        userPreferredMode = null
        val availModes = getAvailableModes(settings.characteristics)
        _isAvailable.postValue(availModes.size > 1)
        _availableModes.postValue(availModes)

        val targetMode = when {
            userPreferredMode != null && availModes.contains(userPreferredMode) -> {
                userPreferredMode!!
            }
            availModes.contains(NOISE_REDUCTION_MODE_FAST) -> {
                NOISE_REDUCTION_MODE_FAST
            }
            availModes.contains(NOISE_REDUCTION_MODE_HIGH_QUALITY) -> {
                NOISE_REDUCTION_MODE_HIGH_QUALITY
            }
            availModes.isNotEmpty() -> {
                availModes.first()
            }
            else -> {
                NOISE_REDUCTION_MODE_OFF
            }
        }
        _currentMode.postValue(targetMode)

        scope.launch {
            try {
                if (!settings.isActiveFlow.value) {
                    withTimeoutOrNull(2000) {
                        settings.isActiveFlow.first { it }
                    }
                }
                if (settings.isActiveFlow.value) {
                    applyModeToCamera(settings, targetMode)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to apply initial noise reduction mode: ${t.message}")
            }
        }
    }

    /**
     * Resets noise reduction state when switching to a non-camera source.
     */
    fun onSourceReset() {
        _isAvailable.postValue(false)
        _availableModes.postValue(emptyList())
    }

    /**
     * Sets a noise reduction mode on the active camera session.
     */
    fun setMode(
        settings: CameraSettings?,
        mode: Int,
        isUserExplicit: Boolean = true,
        scope: CoroutineScope,
        onFeedback: ((String) -> Unit)? = null
    ) {
        if (isUserExplicit) {
            userPreferredMode = mode
        }
        if (settings == null) {
            Log.e(TAG, "Camera settings is not accessible")
            return
        }
        scope.launch {
            try {
                if (settings.isActiveFlow.value) {
                    applyModeToCamera(settings, mode)
                    _currentMode.postValue(mode)
                    if (isUserExplicit) {
                        val modeName = getNoiseReductionModeName(mode)
                        onFeedback?.invoke("Noise Reduction: $modeName")
                    }
                } else {
                    Log.w(TAG, "Cannot set noise reduction: camera session is not active")
                }
            } catch (t: Throwable) {
                Log.w(TAG, "setNoiseReductionMode failed (camera session may be closed): ${t.message}")
            }
        }
    }

    /**
     * Toggles through available noise reduction modes sequentially.
     */
    fun toggleMode(
        settings: CameraSettings?,
        scope: CoroutineScope,
        onFeedback: ((String) -> Unit)? = null
    ) {
        val modes = _availableModes.value ?: return
        if (modes.isEmpty()) return
        val current = _currentMode.value ?: NOISE_REDUCTION_MODE_OFF
        val currentIndex = modes.indexOf(current)
        val nextMode = modes[(currentIndex + 1) % modes.size]
        setMode(settings, nextMode, isUserExplicit = true, scope = scope, onFeedback = onFeedback)
    }

    /**
     * Discovers supported noise reduction modes from [CameraCharacteristics].
     */
    fun getAvailableModes(characteristics: CameraCharacteristics): List<Int> {
        val modesArray = characteristics[CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES]
        val supported = modesArray?.toList() ?: return emptyList()

        val orderedCandidates = listOf(
            CaptureRequest.NOISE_REDUCTION_MODE_OFF,
            CaptureRequest.NOISE_REDUCTION_MODE_FAST,
            CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY,
            CaptureRequest.NOISE_REDUCTION_MODE_MINIMAL,
            CaptureRequest.NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG
        )
        val modes = orderedCandidates.filter { supported.contains(it) }.toMutableList()
        for (m in supported) {
            if (!modes.contains(m)) {
                modes.add(m)
            }
        }
        return modes
    }

    /**
     * Applies the noise reduction mode to [CameraSettings] and updates the repeating capture session.
     */
    suspend fun applyModeToCamera(settings: CameraSettings, mode: Int) {
        settings.set(CaptureRequest.NOISE_REDUCTION_MODE, mode)
        settings.applyRepeatingSession()
    }

    companion object {
        private const val TAG = "CameraNoiseReduction"

        const val NOISE_REDUCTION_MODE_OFF = CaptureRequest.NOISE_REDUCTION_MODE_OFF
        const val NOISE_REDUCTION_MODE_FAST = CaptureRequest.NOISE_REDUCTION_MODE_FAST
        const val NOISE_REDUCTION_MODE_HIGH_QUALITY = CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY
        const val NOISE_REDUCTION_MODE_MINIMAL = CaptureRequest.NOISE_REDUCTION_MODE_MINIMAL
        const val NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG = CaptureRequest.NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG

        fun getNoiseReductionModeName(mode: Int): String {
            return when (mode) {
                NOISE_REDUCTION_MODE_OFF -> "Off"
                NOISE_REDUCTION_MODE_FAST -> "Fast"
                NOISE_REDUCTION_MODE_HIGH_QUALITY -> "High Quality"
                NOISE_REDUCTION_MODE_MINIMAL -> "Minimal"
                NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG -> "Zero Shutter Lag"
                else -> "Unknown"
            }
        }
    }
}
