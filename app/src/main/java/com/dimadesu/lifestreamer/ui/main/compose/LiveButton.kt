package com.dimadesu.lifestreamer.ui.main.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dimadesu.lifestreamer.R
import com.dimadesu.lifestreamer.models.StreamStatus
import com.dimadesu.lifestreamer.ui.main.PreviewViewModel

/**
 * Stateful floating Live/Stop stream toggle button.
 */
@Composable
fun LiveButton(
    viewModel: PreviewViewModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val status by viewModel.streamStatus.collectAsState(initial = StreamStatus.NOT_STREAMING)
    val isReconnecting by viewModel.isReconnectingLiveData.observeAsState(initial = false)

    LiveButton(
        status = status,
        isReconnecting = isReconnecting,
        onClick = onClick,
        modifier = modifier
    )
}

/**
 * Stateless floating Live/Stop stream button with dynamic visual feedback.
 */
@Composable
fun LiveButton(
    status: StreamStatus,
    isReconnecting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isConnecting = status == StreamStatus.STARTING || status == StreamStatus.CONNECTING || (isReconnecting && status != StreamStatus.STREAMING)
    val isLive = status == StreamStatus.STREAMING

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val buttonColor by animateColorAsState(
        targetValue = when {
            isLive -> Color(0xFFD32F2F)      // Red when live (clicking will STOP)
            isConnecting -> Color(0xFFE65100) // Deep Orange when connecting/reconnecting
            else -> Color(0xFF2E7D32)         // Green when idle (clicking will START)
        },
        label = "buttonColor"
    )

    val borderColor = when {
        isLive -> Color(0xFFFF5252)
        isConnecting -> Color(0xFFFFB74D)
        else -> Color(0xFF81C784)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(buttonColor)
            .border(width = 1.5.dp, color = borderColor, shape = RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isConnecting) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp)
                )
            } else if (isLive) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = pulseAlpha))
                )
            }

            val label = when {
                isLive -> stringResource(R.string.stop)
                isConnecting -> if (isReconnecting) "RECONNECTING..." else "CONNECTING..."
                else -> stringResource(R.string.live)
            }

            Text(
                text = label,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Preview(name = "Idle (Live)")
@Composable
private fun PreviewLiveButtonIdle() {
    LiveButton(status = StreamStatus.NOT_STREAMING, isReconnecting = false, onClick = {})
}

@Preview(name = "Connecting")
@Composable
private fun PreviewLiveButtonConnecting() {
    LiveButton(status = StreamStatus.CONNECTING, isReconnecting = false, onClick = {})
}

@Preview(name = "Reconnecting")
@Composable
private fun PreviewLiveButtonReconnecting() {
    LiveButton(status = StreamStatus.NOT_STREAMING, isReconnecting = true, onClick = {})
}

@Preview(name = "Streaming (Stop)")
@Composable
private fun PreviewLiveButtonStreaming() {
    LiveButton(status = StreamStatus.STREAMING, isReconnecting = false, onClick = {})
}
