package com.dimadesu.lifestreamer.camera

import android.Manifest
import android.hardware.camera2.CameraManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import io.github.thibaultbee.streampack.core.elements.processing.video.CompositionMode
import io.github.thibaultbee.streampack.core.elements.sources.video.camera.ConcurrentDualCameraSourceFactory
import io.github.thibaultbee.streampack.core.elements.sources.video.camera.extensions.isBackCamera
import io.github.thibaultbee.streampack.core.elements.sources.video.camera.extensions.isFrontCamera
import io.github.thibaultbee.streampack.core.interfaces.IWithVideoSource

@RequiresApi(Build.VERSION_CODES.R)
private fun CameraManager.concurrentIds() = concurrentCameraIds

/**
 * Gets a list of compatible (back, front) camera ID pairs that support concurrent camera
 * streaming (API 30+).
 */
val CameraManager.concurrentCameraPairs: List<Pair<String, String>>
    get() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return emptyList()
        }
        val concurrentSets = try {
            concurrentIds()
        } catch (t: Throwable) {
            emptySet()
        }
        val validPairs = mutableListOf<Pair<String, String>>()
        for (set in concurrentSets) {
            val backId = set.firstOrNull { isBackCamera(it) }
            val frontId = set.firstOrNull { isFrontCamera(it) }
            if (backId != null && frontId != null) {
                validPairs.add(Pair(backId, frontId))
            }
        }
        return validPairs
    }

/**
 * Checks if the given primary and secondary cameras can be streamed concurrently (API 30+).
 */
fun CameraManager.isConcurrentStreamingSupported(primaryId: String, secondaryId: String): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        return false
    }
    return try {
        concurrentIds().any { set -> set.contains(primaryId) && set.contains(secondaryId) }
    } catch (t: Throwable) {
        false
    }
}

/**
 * Sets concurrent dual camera streaming (primary + secondary camera with the given layout).
 *
 * @param primaryCameraId the primary camera id (e.g. rear)
 * @param secondaryCameraId the secondary camera id (e.g. front)
 * @param compositionMode the composition layout mode (defaults to PiP top-right)
 */
@RequiresPermission(Manifest.permission.CAMERA)
suspend fun IWithVideoSource.setConcurrentDualCamera(
    primaryCameraId: String,
    secondaryCameraId: String,
    compositionMode: CompositionMode = CompositionMode.PIP_TOP_RIGHT
) {
    videoInput.setSource(
        ConcurrentDualCameraSourceFactory(primaryCameraId, secondaryCameraId, compositionMode)
    )
}
