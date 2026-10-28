package com.dimadesu.lifestreamer.ui.main.video

import android.app.Application
import android.content.Context
import android.hardware.camera2.CameraManager
import android.media.MediaFormat
import android.util.Size
import com.dimadesu.lifestreamer.ApplicationConstants
import com.dimadesu.lifestreamer.R
import com.dimadesu.lifestreamer.data.storage.StoredVideoSettings
import com.dimadesu.lifestreamer.models.EndpointType
import com.dimadesu.lifestreamer.models.StreamOrientation
import com.dimadesu.lifestreamer.utils.ProfileLevelDisplay
import com.dimadesu.lifestreamer.utils.StreamerInfoFactory
import io.github.thibaultbee.streampack.core.elements.encoders.mediacodec.MediaCodecHelper
import io.github.thibaultbee.streampack.core.elements.sources.video.camera.extensions.defaultCameraId
import io.github.thibaultbee.streampack.core.elements.sources.video.camera.extensions.isFpsSupported
import io.github.thibaultbee.streampack.core.streamers.single.VideoConfig
import kotlin.math.abs

/**
 * Video settings currently selected plus the options the active encoder/endpoint/cameras support.
 */
data class VideoSettingsState(
    val orientation: StreamOrientation = StreamOrientation.AUTO,
    val encoder: String = ApplicationConstants.Video.defaultEncoder,
    val showHardwareResolutionsOnly: Boolean = true,
    val resolution: Size = ApplicationConstants.Video.defaultResolution,
    val cameraFps: Int = ApplicationConstants.Video.defaultFps,
    val matchFps: Boolean = true,
    val fps: Int = ApplicationConstants.Video.defaultFps,
    val bitrateKbps: Int = ApplicationConstants.Video.defaultBitrateInBps / 1000,
    val profile: Int = 0,
    val level: Int = 0,
    val availableOrientations: List<Pair<StreamOrientation, String>> = emptyList(),
    val availableEncoders: List<Pair<String, String>> = emptyList(),
    val availableResolutions: List<Pair<Size, String>> = emptyList(),
    val availableCameraFps: List<Pair<Int, String>> = emptyList(),
    val availableFps: List<Pair<Int, String>> = emptyList(),
    val availableBitrates: List<Pair<Int, String>> = emptyList(),
    val availableProfiles: List<Pair<Int, String>> = emptyList(),
    val availableLevels: List<Pair<Int, String>> = emptyList(),
    /** Set when some Android cameras cannot do [cameraFps]. */
    val cameraFpsWarning: String? = null
) {
    fun toStored() = StoredVideoSettings(
        orientation = orientation,
        encoder = encoder,
        showHardwareResolutionsOnly = showHardwareResolutionsOnly,
        resolution = resolution.toString(),
        cameraFps = cameraFps,
        matchFps = matchFps,
        fps = fps,
        bitrateKbps = bitrateKbps,
        profile = profile,
        level = level
    )
}

/**
 * Builds a [VideoSettingsState] from the stored settings, correcting values the encoder or
 * cameras do not support.
 */
class VideoSettingsStateBuilder(private val application: Application) {

    private val profileLevelDisplay by lazy { ProfileLevelDisplay(application) }

    /**
     * @property shouldPersist true when stored values were corrected and should be written back.
     */
    class Result(val state: VideoSettingsState, val shouldPersist: Boolean)

    fun build(stored: StoredVideoSettings, endpointType: EndpointType): Result {
        val video = StreamerInfoFactory(application, endpointType).build().video
        val cameraId = application.defaultCameraId

        val supportedEncoders = video.supportedEncoders
        val encoderNames = mapOf(
            MediaFormat.MIMETYPE_VIDEO_AVC to application.getString(R.string.video_encoder_h264),
            MediaFormat.MIMETYPE_VIDEO_HEVC to application.getString(R.string.video_encoder_h265),
            MediaFormat.MIMETYPE_VIDEO_H263 to application.getString(R.string.video_encoder_h263),
            MediaFormat.MIMETYPE_VIDEO_VP9 to application.getString(R.string.video_encoder_vp9),
            MediaFormat.MIMETYPE_VIDEO_VP8 to application.getString(R.string.video_encoder_vp8),
            MediaFormat.MIMETYPE_VIDEO_AV1 to application.getString(R.string.video_encoder_av1)
        )
        val storedEncoder = stored.encoder ?: ApplicationConstants.Video.defaultEncoder
        val encoder = when {
            supportedEncoders.isEmpty() || supportedEncoders.contains(storedEncoder) -> storedEncoder
            supportedEncoders.contains(MediaFormat.MIMETYPE_VIDEO_AVC) -> MediaFormat.MIMETYPE_VIDEO_AVC
            else -> supportedEncoders.first()
        }

        val hardwareResolutions = video.getSupportedResolutions(application, encoder)
        val (codecWidths, codecHeights) = video.getSupportedResolutions(encoder)
        val resolutions = if (stored.showHardwareResolutionsOnly) {
            hardwareResolutions
        } else {
            ApplicationConstants.Video.standardResolutions.filter {
                codecWidths.contains(it.width) && codecHeights.contains(it.height)
            }
        }.distinct().sortedWith(compareByDescending<Size> { it.height }.thenByDescending { it.width })
        val storedResolution = parseResolution(stored.resolution) ?: ApplicationConstants.Video.defaultResolution
        val resolution = when {
            resolutions.isEmpty() || resolutions.contains(storedResolution) -> storedResolution
            else -> {
                val preferred = parseResolution(application.getString(R.string.default_video_resolution))
                    ?: storedResolution
                resolutions.firstOrNull { it == preferred }
                    ?: resolutions.minBy { abs(it.width * it.height - preferred.width * preferred.height) }
            }
        }

        val allFps = application.resources.getStringArray(R.array.FpsEntries).mapNotNull { it.toIntOrNull() }
        val encoderFpsRange = video.getSupportedFramerate(encoder)
        val fpsOptions = allFps.filter { encoderFpsRange.contains(it) }
        val fps = pick(stored.fps ?: ApplicationConstants.Video.defaultFps, fpsOptions)

        val cameraFramerates = video.getSupportedFramerates(application, encoder, cameraId)
        val cameraFpsOptions = allFps.filter { fpsValue -> cameraFramerates.any { it.contains(fpsValue) } }
        val cameraFps = pick(stored.cameraFps ?: ApplicationConstants.Video.defaultFps, cameraFpsOptions)

        val bitrateRange = video.getSupportedBitrates(encoder)
        val bitrateOptions = ApplicationConstants.Video.bitrateOptionsInKbps
            .filter { bitrateRange.contains(it * 1000) }
        val storedBitrate = stored.bitrateKbps ?: (ApplicationConstants.Video.defaultBitrateInBps / 1000)
        val bitrate = if (bitrateOptions.isEmpty() || bitrateOptions.contains(storedBitrate)) {
            storedBitrate
        } else {
            bitrateOptions.minBy { abs(it - storedBitrate) }
        }

        val profiles = video.getSupportedAllProfiles(application, encoder, cameraId)
        val bestProfile = VideoConfig.getBestProfile(encoder)
        val storedProfile = stored.profile ?: bestProfile
        val profile = when {
            profiles.isEmpty() || profiles.contains(storedProfile) -> storedProfile
            profiles.contains(bestProfile) -> bestProfile
            else -> profiles.first()
        }

        val maxLevel = MediaCodecHelper.getMaxLevel(encoder, profile)
        val levels = profileLevelDisplay.getAllLevelSet(encoder).filter { it <= maxLevel }
        val bestLevel = VideoConfig.getBestLevel(encoder, profile)
        // A level chosen for a different profile is not meaningful, so start over from the best one
        val storedLevel = stored.level.takeIf { profile == storedProfile } ?: bestLevel
        val level = if (levels.isEmpty() || levels.contains(storedLevel)) storedLevel else bestLevel

        val resources = application.resources
        val state = VideoSettingsState(
            orientation = stored.orientation,
            encoder = encoder,
            showHardwareResolutionsOnly = stored.showHardwareResolutionsOnly,
            resolution = resolution,
            cameraFps = cameraFps,
            matchFps = stored.matchFps,
            fps = fps,
            bitrateKbps = bitrate,
            profile = profile,
            level = level,
            availableOrientations = resources.getStringArray(R.array.stream_orientation_values)
                .zip(resources.getStringArray(R.array.stream_orientation_entries))
                .map { (id, name) -> StreamOrientation.fromId(id) to name },
            availableEncoders = supportedEncoders.map { it to (encoderNames[it] ?: it) },
            availableResolutions = resolutions.map { it to it.toString() },
            availableCameraFps = cameraFpsOptions.map { it to "$it" },
            availableFps = fpsOptions.map { it to "$it" },
            availableBitrates = bitrateOptions.map { it to "$it Kbps" },
            availableProfiles = profiles.map { it to profileLevelDisplay.getProfileName(encoder, it) },
            availableLevels = levels.map { it to profileLevelDisplay.getLevelName(encoder, it) },
            cameraFpsWarning = cameraFpsWarning(cameraFps)
        )

        val shouldPersist = (stored.encoder != null && stored.encoder != encoder) ||
            (stored.resolution != null && stored.resolution != resolution.toString()) ||
            (stored.cameraFps != null && stored.cameraFps != cameraFps) ||
            (stored.fps != null && stored.fps != fps) ||
            (stored.bitrateKbps != null && stored.bitrateKbps != bitrate) ||
            (stored.profile != null && stored.profile != profile) ||
            (stored.level != null && stored.level != level)

        return Result(state, shouldPersist)
    }

    private fun pick(stored: Int, options: List<Int>): Int = when {
        options.isEmpty() || options.contains(stored) -> stored
        options.contains(ApplicationConstants.Video.defaultFps) -> ApplicationConstants.Video.defaultFps
        else -> options.first()
    }

    private fun parseResolution(value: String?): Size? {
        val parts = value?.split("x") ?: return null
        if (parts.size != 2) return null
        val width = parts[0].toIntOrNull() ?: return null
        val height = parts[1].toIntOrNull() ?: return null
        return Size(width, height)
    }

    private fun cameraFpsWarning(fps: Int): String? {
        val cameraManager = application.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val unsupportedCameras = try {
            cameraManager.cameraIdList.filter {
                !cameraManager.getCameraCharacteristics(it).isFpsSupported(fps)
            }
        } catch (_: Exception) {
            emptyList()
        }
        if (unsupportedCameras.isEmpty()) return null
        return application.resources.getQuantityString(
            R.plurals.camera_frame_rate_not_supported,
            unsupportedCameras.size,
            unsupportedCameras.joinToString(", "),
            fps
        )
    }
}
