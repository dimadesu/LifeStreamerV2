package com.dimadesu.lifestreamer.ui.main.controllers

import android.app.Application
import android.content.Intent
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import com.dimadesu.lifestreamer.rtmp.audio.MediaProjectionHelper

/**
 * Manages active MediaProjection instances and token recovery across screen capture,
 * RTMP streaming, and system audio recording.
 */
class MediaProjectionTokenStore(
    private val application: Application
) {
    companion object {
        private const val TAG = "MediaProjectionStore"
    }

    val helper: MediaProjectionHelper = MediaProjectionHelper(application)

    var streamingMediaProjection: MediaProjection? = null
    var startupMediaProjection: MediaProjection? = null

    val currentProjection: MediaProjection?
        get() = startupMediaProjection ?: streamingMediaProjection ?: helper.getMediaProjection()

    fun hasProjection(): Boolean =
        startupMediaProjection != null || streamingMediaProjection != null || helper.getMediaProjection() != null

    fun tryReconnect(onResult: (MediaProjection?) -> Unit = {}) {
        helper.tryReconnect { projection ->
            if (projection != null) {
                Log.i(TAG, "Recovered MediaProjection from surviving service")
                startupMediaProjection = projection
            }
            onResult(projection)
        }
    }

    fun requestProjection(
        launcher: ActivityResultLauncher<Intent>,
        onResult: (MediaProjection?) -> Unit
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            helper.requestProjection(launcher) { projection ->
                if (projection != null) {
                    startupMediaProjection = projection
                }
                onResult(projection)
            }
        } else {
            onResult(null)
        }
    }

    fun release() {
        try { streamingMediaProjection?.stop() } catch (_: Throwable) {}
        streamingMediaProjection = null
        try { startupMediaProjection?.stop() } catch (_: Throwable) {}
        startupMediaProjection = null
        helper.release()
    }

    fun unbind() {
        helper.unbind()
    }
}
