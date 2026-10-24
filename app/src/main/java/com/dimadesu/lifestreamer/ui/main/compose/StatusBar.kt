package com.dimadesu.lifestreamer.ui.main.compose

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.dimadesu.lifestreamer.ui.components.AudioLevelMeterView
import com.dimadesu.lifestreamer.ui.main.state.AudioSourceState
import com.dimadesu.lifestreamer.ui.main.state.StreamStatusState

/**
 * Top-right overlay status bar displaying live stream metrics (FPS, uptime, bitrate, status)
 * and active audio input indicators with a VU meter.
 */
@Composable
fun StatusBar(
    streamStatusState: StreamStatusState,
    audioSourceState: AudioSourceState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0x99000000))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        // Stream metrics row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // FPS / Encoder stats
            streamStatusState.encoderStatsText?.let { fps ->
                Text(
                    text = fps,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )
            }

            // Uptime
            streamStatusState.uptimeText?.let { uptime ->
                Text(
                    text = uptime,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )
            }

            // Bitrate
            streamStatusState.bitrateText?.let { bitrate ->
                Text(
                    text = bitrate,
                    color = Color(0xFF64B5F6), // subtle cyan/blue for bitrate
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Status Badge
            StreamStatusBadge(
                statusText = streamStatusState.statusText,
                isStreaming = streamStatusState.isStreaming,
                isReconnecting = streamStatusState.isReconnecting
            )
        }

        // Audio indicator & level meter row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Audio source indicator text
            val audioText = when {
                audioSourceState.isMuted -> "MUTED"
                !audioSourceState.audioSourceIndicator.isNullOrBlank() -> audioSourceState.audioSourceIndicator
                else -> null
            }

            if (audioText != null) {
                Text(
                    text = audioText,
                    color = if (audioSourceState.isMuted) Color(0xFFFF5252) else Color(0xFFB0BEC5),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // VU Meter
            Box(
                modifier = Modifier
                    .width(72.dp)
                    .height(12.dp)
            ) {
                AndroidView(
                    factory = { ctx ->
                        AudioLevelMeterView(ctx)
                    },
                    update = { view ->
                        // View will be updated by audio monitor or level updates
                    },
                    modifier = Modifier.matchParentSize()
                )
            }
        }
    }
}

@Composable
private fun StreamStatusBadge(
    statusText: String,
    isStreaming: Boolean,
    isReconnecting: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val (dotColor, pulsing) = when {
        isStreaming -> Color(0xFFFF1744) to true // bright live red pulsing
        isReconnecting -> Color(0xFFFF9100) to true // amber pulsing
        statusText.equals("CONNECTING", ignoreCase = true) -> Color(0xFFFFC400) to true
        statusText.equals("STOPPING", ignoreCase = true) -> Color(0xFF9E9E9E) to false
        else -> Color(0xFF757575) to false
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor.copy(alpha = if (pulsing) alpha else 1f))
        )

        Text(
            text = statusText,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
