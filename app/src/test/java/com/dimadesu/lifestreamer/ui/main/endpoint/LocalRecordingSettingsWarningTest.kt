package com.dimadesu.lifestreamer.ui.main.endpoint

import android.media.MediaFormat
import com.dimadesu.lifestreamer.models.EndpointType
import com.dimadesu.lifestreamer.models.LocalRecordingFormat
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalRecordingSettingsWarningTest {

    @Test
    fun standaloneFileEndpoint_returnsNull() {
        val warning = computeLocalRecordingCompatibilityWarning(
            endpointType = EndpointType.MP4_FILE,
            recordToDiskEnabled = true,
            recordToDiskFormatPref = LocalRecordingFormat.MP4.prefValue,
            videoMimeType = MediaFormat.MIMETYPE_VIDEO_AVC,
            audioMimeType = MediaFormat.MIMETYPE_AUDIO_OPUS
        )
        assertNull(warning)
    }

    @Test
    fun streamingRecordingDisabled_returnsNull() {
        val warning = computeLocalRecordingCompatibilityWarning(
            endpointType = EndpointType.SRT,
            recordToDiskEnabled = false,
            recordToDiskFormatPref = LocalRecordingFormat.MP4.prefValue,
            videoMimeType = MediaFormat.MIMETYPE_VIDEO_AVC,
            audioMimeType = MediaFormat.MIMETYPE_AUDIO_OPUS
        )
        assertNull(warning)
    }

    @Test
    fun streamingCompatibleCodecs_returnsNull() {
        val warning = computeLocalRecordingCompatibilityWarning(
            endpointType = EndpointType.SRT,
            recordToDiskEnabled = true,
            recordToDiskFormatPref = LocalRecordingFormat.TS.prefValue,
            videoMimeType = MediaFormat.MIMETYPE_VIDEO_HEVC,
            audioMimeType = MediaFormat.MIMETYPE_AUDIO_OPUS
        )
        assertNull(warning)
    }

    @Test
    fun srtMp4WithOpus_warnsInEndpointAndAudioScopesOnly() {
        val allWarning = computeLocalRecordingCompatibilityWarning(
            endpointType = EndpointType.SRT,
            recordToDiskEnabled = true,
            recordToDiskFormatPref = LocalRecordingFormat.MP4.prefValue,
            videoMimeType = MediaFormat.MIMETYPE_VIDEO_AVC,
            audioMimeType = MediaFormat.MIMETYPE_AUDIO_OPUS,
            scope = LocalRecordingWarningScope.ALL
        )
        val audioWarning = computeLocalRecordingCompatibilityWarning(
            endpointType = EndpointType.SRT,
            recordToDiskEnabled = true,
            recordToDiskFormatPref = LocalRecordingFormat.MP4.prefValue,
            videoMimeType = MediaFormat.MIMETYPE_VIDEO_AVC,
            audioMimeType = MediaFormat.MIMETYPE_AUDIO_OPUS,
            scope = LocalRecordingWarningScope.AUDIO_ONLY
        )
        val videoWarning = computeLocalRecordingCompatibilityWarning(
            endpointType = EndpointType.SRT,
            recordToDiskEnabled = true,
            recordToDiskFormatPref = LocalRecordingFormat.MP4.prefValue,
            videoMimeType = MediaFormat.MIMETYPE_VIDEO_AVC,
            audioMimeType = MediaFormat.MIMETYPE_AUDIO_OPUS,
            scope = LocalRecordingWarningScope.VIDEO_ONLY
        )

        assertNotNull(allWarning)
        assertTrue(allWarning!!.contains("Opus"))
        assertTrue(allWarning.contains("TS"))
        assertNotNull(audioWarning)
        assertNull(videoWarning)
    }

    @Test
    fun rtmpMp4WithVp9_warnsInEndpointAndVideoScopesAndSuggestsFlv() {
        val videoWarning = computeLocalRecordingCompatibilityWarning(
            endpointType = EndpointType.RTMP,
            recordToDiskEnabled = true,
            recordToDiskFormatPref = LocalRecordingFormat.MP4.prefValue,
            videoMimeType = MediaFormat.MIMETYPE_VIDEO_VP9,
            audioMimeType = MediaFormat.MIMETYPE_AUDIO_AAC,
            scope = LocalRecordingWarningScope.VIDEO_ONLY
        )
        val audioWarning = computeLocalRecordingCompatibilityWarning(
            endpointType = EndpointType.RTMP,
            recordToDiskEnabled = true,
            recordToDiskFormatPref = LocalRecordingFormat.MP4.prefValue,
            videoMimeType = MediaFormat.MIMETYPE_VIDEO_VP9,
            audioMimeType = MediaFormat.MIMETYPE_AUDIO_AAC,
            scope = LocalRecordingWarningScope.AUDIO_ONLY
        )

        assertNotNull(videoWarning)
        assertTrue(videoWarning!!.contains("VP9"))
        assertTrue(videoWarning.contains("FLV"))
        assertNull(audioWarning)
    }

    @Test
    fun srtWithStoredFlvFormat_fallsBackToTs() {
        // SRT does not support FLV recording; effective format falls back to TS, which supports Opus
        val warning = computeLocalRecordingCompatibilityWarning(
            endpointType = EndpointType.SRT,
            recordToDiskEnabled = true,
            recordToDiskFormatPref = LocalRecordingFormat.FLV.prefValue,
            videoMimeType = MediaFormat.MIMETYPE_VIDEO_AVC,
            audioMimeType = MediaFormat.MIMETYPE_AUDIO_OPUS
        )
        assertNull(warning)
    }
}
