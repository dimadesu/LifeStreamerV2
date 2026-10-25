package com.dimadesu.lifestreamer.ui.main.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.dimadesu.lifestreamer.audio.AudioLevel
import com.dimadesu.lifestreamer.ui.components.AudioLevelMeterView
import com.dimadesu.lifestreamer.ui.main.PreviewViewModel

/**
 * Stateful top-right controls bar displaying stream statistics (FPS, uptime, bitrate, status),
 * current audio source indicator, and VU meter.
 */
@Composable
fun TopRightControlsBar(
    viewModel: PreviewViewModel,
    modifier: Modifier = Modifier
) {
    val encoderStats by viewModel.encoderStatsLiveData.observeAsState()
    val uptime by viewModel.uptimeLiveData.observeAsState()
    val bitrate by viewModel.bitrateLiveData.observeAsState()
    val streamStatusText by viewModel.streamStatusTextLiveData.observeAsState(initial = "")
    val audioSourceIndicator by viewModel.audioSourceIndicatorLiveData.observeAsState()
    val audioLevel by viewModel.audioLevelFlow.collectAsState()

    TopRightControlsBar(
        encoderStats = encoderStats,
        uptime = uptime,
        bitrate = bitrate,
        streamStatusText = streamStatusText,
        audioSourceIndicator = audioSourceIndicator,
        audioLevel = audioLevel,
        modifier = modifier
    )
}

/**
 * Stateless top-right controls bar displaying stream statistics and audio indicators.
 */
@Composable
fun TopRightControlsBar(
    encoderStats: String?,
    uptime: String?,
    bitrate: String?,
    streamStatusText: String,
    audioSourceIndicator: String?,
    audioLevel: AudioLevel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End
    ) {
        // Stream stats row (FPS, Uptime, Bitrate, Stream Status)
        Row(
            modifier = Modifier
                .background(Color(0x80000000))
                .padding(start = 5.dp, end = 5.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!encoderStats.isNullOrEmpty()) {
                Text(
                    text = encoderStats,
                    color = Color.White,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(end = 16.dp)
                )
            }
            if (!uptime.isNullOrEmpty()) {
                Text(
                    text = uptime,
                    color = Color.White,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(end = 16.dp)
                )
            }
            if (!bitrate.isNullOrEmpty()) {
                Text(
                    text = bitrate,
                    color = Color.White,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(end = 16.dp)
                )
            }
            Text(
                text = streamStatusText,
                color = Color.White,
                fontSize = 16.sp
            )
        }

        // Audio source indicator and VU meter
        if (!audioSourceIndicator.isNullOrEmpty()) {
            Column(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .defaultMinSize(minWidth = 60.dp)
                    .background(Color(0x80000000))
                    .padding(start = 5.dp, end = 5.dp, bottom = 3.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = audioSourceIndicator,
                    color = Color.White,
                    fontSize = 16.sp,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (LocalInspectionMode.current) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .background(Color.DarkGray)
                    )
                } else {
                    AndroidView(
                        factory = { ctx -> AudioLevelMeterView(ctx) },
                        update = { meter ->
                            if (audioLevel == AudioLevel.SILENT) {
                                meter.reset()
                            } else {
                                meter.setAudioLevel(audioLevel)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF222222)
@Composable
private fun TopRightControlsBarIdlePreview() {
    TopRightControlsBar(
        encoderStats = null,
        uptime = null,
        bitrate = null,
        streamStatusText = "Not streaming",
        audioSourceIndicator = "Microphone",
        audioLevel = AudioLevel.SILENT
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF222222)
@Composable
private fun TopRightControlsBarStreamingPreview() {
    TopRightControlsBar(
        encoderStats = "30.0 fps",
        uptime = "00:04:12",
        bitrate = "4500 kbps",
        streamStatusText = "Streaming",
        audioSourceIndicator = "Media Projection Audio",
        audioLevel = AudioLevel(rms = 0.5f, peak = 0.7f)
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF222222)
@Composable
private fun TopRightControlsBarBluetoothPreview() {
    TopRightControlsBar(
        encoderStats = "30.0 fps",
        uptime = "00:04:12",
        bitrate = "4500 kbps",
        streamStatusText = "Streaming",
        audioSourceIndicator = "Bluetooth",
        audioLevel = AudioLevel(rms = 0.5f, peak = 0.7f)
    )
}
