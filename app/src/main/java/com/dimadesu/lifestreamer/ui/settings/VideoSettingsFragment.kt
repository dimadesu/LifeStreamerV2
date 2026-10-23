package com.dimadesu.lifestreamer.ui.settings

import android.content.Context
import android.media.MediaFormat
import androidx.preference.ListPreference
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreference
import com.dimadesu.lifestreamer.R
import com.dimadesu.lifestreamer.utils.DialogUtils
import com.dimadesu.lifestreamer.utils.ProfileLevelDisplay
import com.dimadesu.lifestreamer.utils.StreamerInfoFactory
import io.github.thibaultbee.streampack.core.elements.encoders.mediacodec.MediaCodecHelper
import io.github.thibaultbee.streampack.core.elements.sources.video.camera.extensions.defaultCameraId
import io.github.thibaultbee.streampack.core.elements.sources.video.camera.extensions.isFpsSupported
import io.github.thibaultbee.streampack.core.streamers.infos.CameraStreamerConfigurationInfo
import io.github.thibaultbee.streampack.core.streamers.single.VideoConfig

class VideoSettingsFragment : BaseSettingsFragment() {
    override val preferencesRes = R.xml.settings_video
    override val titleRes = R.string.video

    private lateinit var streamerInfo: CameraStreamerConfigurationInfo
    private val profileLevelDisplay by lazy { ProfileLevelDisplay(requireContext()) }

    private val videoEncoderListPreference: ListPreference by lazy {
        findPreferenceSafe<ListPreference>(R.string.video_encoder_key) ?: error("video_encoder_key not found")
    }

    private val videoResolutionListPreference: ListPreference by lazy {
        findPreferenceSafe<ListPreference>(R.string.video_resolution_key) ?: error("video_resolution_key not found")
    }

    private val cameraFpsListPreference: ListPreference by lazy {
        findPreferenceSafe<ListPreference>(R.string.camera_fps_key) ?: error("camera_fps_key not found")
    }

    private val videoResolutionHardwareFilterPreference: SwitchPreference by lazy {
        findPreferenceSafe<SwitchPreference>(R.string.video_resolution_hardware_filter_key) ?: error("video_resolution_hardware_filter_key not found")
    }

    private val videoFpsListPreference: ListPreference by lazy {
        findPreferenceSafe<ListPreference>(R.string.video_fps_key) ?: error("video_fps_key not found")
    }

    private val videoBitrateSeekBar: SeekBarPreference by lazy {
        findPreferenceSafe<SeekBarPreference>(R.string.video_bitrate_key) ?: error("video_bitrate_key not found")
    }

    private val videoProfileListPreference: ListPreference by lazy {
        findPreferenceSafe<ListPreference>(R.string.video_profile_key) ?: error("video_profile_key not found")
    }

    private val videoLevelListPreference: ListPreference by lazy {
        findPreferenceSafe<ListPreference>(R.string.video_level_key) ?: error("video_level_key not found")
    }

    override fun onPreferencesInflated() {
        streamerInfo = StreamerInfoFactory(requireContext(), storedEndpointType()).build()
        loadVideoSettings()
    }

    private fun loadVideoSettings() {
        // Inflates video encoders
        val supportedVideoEncoderName = mapOf(
            MediaFormat.MIMETYPE_VIDEO_AVC to getString(R.string.video_encoder_h264),
            MediaFormat.MIMETYPE_VIDEO_HEVC to getString(R.string.video_encoder_h265),
            MediaFormat.MIMETYPE_VIDEO_H263 to getString(R.string.video_encoder_h263),
            MediaFormat.MIMETYPE_VIDEO_VP9 to getString(R.string.video_encoder_vp9),
            MediaFormat.MIMETYPE_VIDEO_VP8 to getString(R.string.video_encoder_vp8),
            MediaFormat.MIMETYPE_VIDEO_AV1 to getString(R.string.video_encoder_av1)
        )

        val supportedVideoEncoder = streamerInfo.video.supportedEncoders
        val defaultVideoEncoder = when {
            supportedVideoEncoder.isEmpty() -> null
            supportedVideoEncoder.contains(MediaFormat.MIMETYPE_VIDEO_AVC) -> {
                MediaFormat.MIMETYPE_VIDEO_AVC
            }

            else -> supportedVideoEncoder.first()
        }

        videoEncoderListPreference.entryValues = supportedVideoEncoder.toTypedArray()
        videoEncoderListPreference.entries =
            supportedVideoEncoder.map { supportedVideoEncoderName[it] }.toTypedArray()
        if (videoEncoderListPreference.entry == null) {
            videoEncoderListPreference.value = defaultVideoEncoder
        }
        videoEncoderListPreference.setOnPreferenceChangeListener { _, newValue ->
            loadVideoSettings(newValue as String, resetToDefaults = true)
            true
        }

        videoResolutionHardwareFilterPreference.setOnPreferenceChangeListener { _, newValue ->
            videoEncoderListPreference.value?.let { loadVideoSettings(it, showHardwareOnly = newValue as Boolean) }
            true
        }

        videoEncoderListPreference.value?.let { loadVideoSettings(it) }
    }

    private fun loadVideoSettings(
        encoder: String,
        resetToDefaults: Boolean = false,
        showHardwareOnly: Boolean = videoResolutionHardwareFilterPreference.isChecked
    ) {
        // Inflates video resolutions
        val hardwareResolutions = streamerInfo.video.getSupportedResolutions(
            requireContext(), encoder
        )
        val codecSupport = streamerInfo.video.getSupportedResolutions(encoder)
        val codecSupportedWidths = codecSupport.first
        val codecSupportedHeights = codecSupport.second

        val standardResolutions = listOf(
            android.util.Size(4032, 3024),
            android.util.Size(3840, 2160),
            android.util.Size(2560, 1440),
            android.util.Size(1920, 1440),
            android.util.Size(1920, 1080),
            android.util.Size(1664, 936),
            android.util.Size(1280, 720),
            android.util.Size(1024, 768),
            android.util.Size(960, 540),
            android.util.Size(854, 480),
            android.util.Size(640, 360),
            android.util.Size(426, 240)
        ).filter { codecSupportedWidths.contains(it.width) && codecSupportedHeights.contains(it.height) }

        val finalResolutions = if (showHardwareOnly) {
            hardwareResolutions
        } else {
            standardResolutions
        }

        finalResolutions
            .sortedWith(compareByDescending<android.util.Size> { it.height }.thenByDescending { it.width })
            .map { it.toString() }.toTypedArray().run {
                videoResolutionListPreference.entries = this
                videoResolutionListPreference.entryValues = this
            }
        val savedVideoResolution = videoResolutionListPreference.value
        videoResolutionListPreference.value =
            if (videoResolutionListPreference.findIndexOfValue(savedVideoResolution) >= 0) savedVideoResolution
            else getString(R.string.default_video_resolution)
        videoResolutionListPreference.refreshStaleSettingUi()

        // Inflates video fps based on encoder capabilities
        val encoderFpsRange = streamerInfo.video.getSupportedFramerate(encoder)
        videoFpsListPreference.entryValues.filter { fps ->
            encoderFpsRange.contains(fps.toString().toIntOrNull() ?: 0)
        }.toTypedArray().run {
            videoFpsListPreference.entries = this
            videoFpsListPreference.entryValues = this
        }
        val savedVideoFps = videoFpsListPreference.value
        videoFpsListPreference.value =
            if (videoFpsListPreference.findIndexOfValue(savedVideoFps) >= 0) savedVideoFps
            else getString(R.string.default_fps)
        videoFpsListPreference.refreshStaleSettingUi()

        // Inflates camera fps based on hardware capabilities
        val supportedFramerates = streamerInfo.video.getSupportedFramerates(
            requireContext(), encoder, requireContext().defaultCameraId
        )
        cameraFpsListPreference.entryValues.filter { fps ->
            supportedFramerates.any { it.contains(fps.toString().toIntOrNull() ?: 0) }
        }.toTypedArray().run {
            cameraFpsListPreference.entries = this
            cameraFpsListPreference.entryValues = this
        }
        val savedCameraFps = cameraFpsListPreference.value
        cameraFpsListPreference.value =
            if (cameraFpsListPreference.findIndexOfValue(savedCameraFps) >= 0) savedCameraFps
            else getString(R.string.default_fps)
        cameraFpsListPreference.refreshStaleSettingUi()
        cameraFpsListPreference.setOnPreferenceChangeListener { _, newValue ->
            val fps = (newValue as? String)?.toIntOrNull() ?: return@setOnPreferenceChangeListener true
            val cameraManager = requireContext().getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
            val unsupportedCameras = cameraManager.cameraIdList.filter {
                !cameraManager.getCameraCharacteristics(it).isFpsSupported(fps)
            }
            if (unsupportedCameras.isNotEmpty()) {
                DialogUtils.showAlertDialog(
                    requireContext(), getString(R.string.warning), resources.getQuantityString(
                        R.plurals.camera_frame_rate_not_supported,
                        unsupportedCameras.size,
                        unsupportedCameras.joinToString(", "),
                        fps
                    )
                )
            }
            true
        }

        // Inflates video bitrate
        streamerInfo.video.getSupportedBitrates(encoder).run {
            videoBitrateSeekBar.min = maxOf(videoBitrateSeekBar.min, lower / 1000) // to kb/s
            videoBitrateSeekBar.max = minOf(videoBitrateSeekBar.max, upper / 1000) // to kb/s
        }

        videoBitrateSeekBar.setOnPreferenceChangeListener { _, newValue ->
            val rounded = roundBitrate(newValue as Int)
            if (rounded != newValue) {
                videoBitrateSeekBar.value = rounded
                false
            } else {
                true
            }
        }

        // Inflates profile
        val profiles = streamerInfo.video.getSupportedAllProfiles(
            requireContext(), encoder, requireContext().defaultCameraId
        )

        val profilesName = profiles.map {
            profileLevelDisplay.getProfileName(
                encoder, it
            )
        }.toTypedArray()

        videoProfileListPreference.entries = profilesName
        videoProfileListPreference.entryValues = profiles.map { it.toString() }.toTypedArray()
        val savedProfile = videoProfileListPreference.value
        videoProfileListPreference.value =
            if (!resetToDefaults && videoProfileListPreference.findIndexOfValue(savedProfile) >= 0) savedProfile
            else VideoConfig.getBestProfile(encoder).toString()
        videoProfileListPreference.refreshStaleSettingUi()
        videoProfileListPreference.setOnPreferenceChangeListener { _, newValue ->
            loadVideoLevel(encoder, (newValue as String).toInt())
            true
        }

        videoProfileListPreference.value?.toIntOrNull()?.let { loadVideoLevel(encoder, it, resetToDefaults) }
    }

    private fun loadVideoLevel(encoder: String, profile: Int, resetToDefaults: Boolean = false) {
        // Inflates level
        val levels = profileLevelDisplay.getAllLevelSet(encoder)
            .filter { it <= MediaCodecHelper.getMaxLevel(encoder, profile) }
        val levelsName = levels.map {
            profileLevelDisplay.getLevelName(
                encoder, it
            )
        }.toTypedArray()

        videoLevelListPreference.entries = levelsName
        videoLevelListPreference.entryValues = levels.map { it.toString() }.toTypedArray()
        val savedLevel = videoLevelListPreference.value
        videoLevelListPreference.value =
            if (!resetToDefaults && videoLevelListPreference.findIndexOfValue(savedLevel) >= 0) savedLevel
            else VideoConfig.getBestLevel(encoder, profile).toString()
        videoLevelListPreference.refreshStaleSettingUi()
    }
}
