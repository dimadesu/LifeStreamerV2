package com.dimadesu.lifestreamer.ui.main.compose

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dimadesu.lifestreamer.R
import com.dimadesu.lifestreamer.ui.main.PreviewViewModel

/**
 * Stateful Composable for the Mute/Unmute microphone toggle button,
 * binding directly to [PreviewViewModel.isMutedLiveData].
 */
@Composable
fun MuteButton(
    viewModel: PreviewViewModel,
    modifier: Modifier = Modifier
) {
    val isMuted by viewModel.isMutedLiveData.observeAsState(initial = false)

    MuteButton(
        isMuted = isMuted,
        onToggleMute = { viewModel.setMute(!isMuted) },
        modifier = modifier
    )
}

/**
 * Stateless Composable for the Mute/Unmute microphone toggle button.
 */
@Composable
fun MuteButton(
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onToggleMute,
        modifier = modifier.size(48.dp)
    ) {
        Icon(
            painter = painterResource(
                id = if (isMuted) R.drawable.ic_baseline_mic_off_24 else R.drawable.ic_baseline_mic_24
            ),
            contentDescription = stringResource(R.string.mute_unmute_microphone),
            tint = Color.White
        )
    }
}

@Preview(name = "Unmuted (Mic On)")
@Composable
private fun PreviewMuteButtonUnmuted() {
    MuteButton(isMuted = false, onToggleMute = {})
}

@Preview(name = "Muted (Mic Off)")
@Composable
private fun PreviewMuteButtonMuted() {
    MuteButton(isMuted = true, onToggleMute = {})
}
