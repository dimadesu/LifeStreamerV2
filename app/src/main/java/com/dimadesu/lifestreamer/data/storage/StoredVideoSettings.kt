package com.dimadesu.lifestreamer.data.storage

import com.dimadesu.lifestreamer.models.StreamOrientation

/**
 * Video settings exactly as stored. A null value means the user has never chosen one.
 *
 * @property resolution stored as "WIDTHxHEIGHT"
 * @property bitrateKbps video bitrate in kbps
 */
data class StoredVideoSettings(
    val orientation: StreamOrientation = StreamOrientation.AUTO,
    val encoder: String? = null,
    val showHardwareResolutionsOnly: Boolean = true,
    val resolution: String? = null,
    val cameraFps: Int? = null,
    val matchFps: Boolean = true,
    val fps: Int? = null,
    val bitrateKbps: Int? = null,
    val profile: Int? = null,
    val level: Int? = null
)
