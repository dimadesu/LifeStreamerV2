package com.dimadesu.lifestreamer.ui.main.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dimadesu.lifestreamer.R
import com.dimadesu.lifestreamer.ui.main.PreviewViewModel

/**
 * Stateful vertical action bar hosting microphone mute toggle on the right edge of the screen.
 * Camera action controls have moved to [CameraControlsBottomDrawer].
 */
@Composable
fun CameraControlsBar(
    viewModel: PreviewViewModel,
    modifier: Modifier = Modifier
) {
    val isMuted by viewModel.isMutedLiveData.observeAsState(initial = false)

    CameraControlsBar(
        isMuted = isMuted,
        onToggleMute = { viewModel.setMute(!isMuted) },
        modifier = modifier
    )
}

/**
 * Stateless vertical action bar for previewability and testing.
 */
@Composable
fun CameraControlsBar(
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxHeight(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .wrapContentHeight()
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom)
        ) {
            CameraControlButton(
                iconRes = if (isMuted) R.drawable.ic_baseline_mic_off_24 else R.drawable.ic_baseline_mic_24,
                contentDescription = stringResource(R.string.mute_unmute_microphone),
                onClick = onToggleMute
            )
        }
    }
}

@Composable
fun CameraControlButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    activeContainerColor: Color = Color(0xFF4CAF50)
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(4.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isActive) activeContainerColor else Color(0xFF616161),
            contentColor = Color.White
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
        modifier = modifier.size(44.dp)
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            tint = Color.White
        )
    }
}

@Preview(name = "Unmuted", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewCameraControlsBarUnmuted() {
    CameraControlsBar(
        isMuted = false,
        onToggleMute = {}
    )
}

@Preview(name = "Muted", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewCameraControlsBarMuted() {
    CameraControlsBar(
        isMuted = true,
        onToggleMute = {}
    )
}
