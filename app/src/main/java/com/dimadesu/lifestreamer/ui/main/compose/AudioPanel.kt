package com.dimadesu.lifestreamer.ui.main.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dimadesu.lifestreamer.models.AudioDebugInfo
import com.dimadesu.lifestreamer.ui.main.state.AudioSourceState

/**
 * Audio routing options (BT mic, System Audio, Audio Monitoring)
 * and detailed audio debug statistics overlay.
 */
@Composable
fun AudioPanel(
    audioSourceState: AudioSourceState,
    isCameraSource: Boolean,
    onToggleBluetooth: () -> Unit,
    onToggleSystemAudio: () -> Unit,
    onToggleMonitorAudio: () -> Unit,
    onToggleAudioDebug: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Audio routing action chips
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Monitor audio toggle
            SourceChip(
                text = "MONITOR",
                isActive = audioSourceState.isMonitorAudioOn,
                onClick = onToggleMonitorAudio
            )

            // BT mic toggle
            if (audioSourceState.showBluetoothToggle) {
                SourceChip(
                    text = "BT MIC",
                    isActive = audioSourceState.useBluetoothMic,
                    onClick = onToggleBluetooth
                )
            }

            // System audio toggle (camera source only)
            if (isCameraSource) {
                SourceChip(
                    text = "SYS AUDIO",
                    isActive = audioSourceState.useSystemAudioForCamera,
                    onClick = onToggleSystemAudio
                )
            }
        }

        // Audio Debug Card (Overlay when enabled)
        AnimatedVisibility(
            visible = audioSourceState.audioDebugOverlayVisible && audioSourceState.audioDebugInfo != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            audioSourceState.audioDebugInfo?.let { debugInfo ->
                AudioDebugCard(
                    debugInfo = debugInfo,
                    onDismiss = onToggleAudioDebug
                )
            }
        }
    }
}

@Composable
fun AudioDebugCard(
    debugInfo: AudioDebugInfo,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xE61E1E1E))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp))
            .clickable(onClick = onDismiss)
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "AUDIO DIAGNOSTICS",
                color = Color(0xFF64B5F6),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            DebugTextRow("Input", debugInfo.audioSource)
            DebugTextRow("System Source", debugInfo.getActualSystemSourceDisplay())
            DebugTextRow("Effects", debugInfo.getAudioEffectsString())
            DebugTextRow("Sample Rate", debugInfo.getSampleRateKHz())
            DebugTextRow("Format", debugInfo.getBitFormatString())
            DebugTextRow("Channels", debugInfo.getChannelConfigString())
        }
    }
}

@Composable
private fun DebugTextRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label:",
            color = Color(0xFFB0BEC5),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        )
    }
}
