package com.dimadesu.lifestreamer.ui.main.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dimadesu.lifestreamer.R

/**
 * Top-left controls: a Quit button and a Help dropdown with links to the help screens.
 * Styled like the buttons in the bottom-right controls.
 */
@Composable
fun TopLeftControlsBar(
    onQuit: () -> Unit,
    onRtmpHelp: () -> Unit,
    onSrtHelp: () -> Unit,
    onUvcHelp: () -> Unit,
    onKnownIssues: () -> Unit,
    onFaq: () -> Unit,
    modifier: Modifier = Modifier
) {
    var helpExpanded by rememberSaveable { mutableStateOf(false) }
    var showQuitConfirm by rememberSaveable { mutableStateOf(false) }

    // Compose has no app MaterialTheme, so match the XML AppTheme's colorPrimary used by other dialogs
    val dialogButtonColors = ButtonDefaults.textButtonColors(
        contentColor = colorResource(R.color.colorPrimary)
    )

    if (showQuitConfirm) {
        AlertDialog(
            onDismissRequest = { showQuitConfirm = false },
            title = { Text(stringResource(R.string.action_quit)) },
            text = { Text(stringResource(R.string.quit_confirm_message)) },
            confirmButton = {
                TextButton(
                    colors = dialogButtonColors,
                    onClick = {
                        showQuitConfirm = false
                        onQuit()
                    }
                ) {
                    Text(stringResource(R.string.action_quit))
                }
            },
            dismissButton = {
                TextButton(colors = dialogButtonColors, onClick = { showQuitConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CameraControlButton(
            iconRes = R.drawable.ic_baseline_close_24,
            contentDescription = stringResource(R.string.action_quit),
            onClick = { showQuitConfirm = true }
        )

        Box {
            CameraControlButton(
                iconRes = R.drawable.ic_baseline_help_outline_24,
                contentDescription = stringResource(R.string.action_help),
                onClick = { helpExpanded = true }
            )
            DropdownMenu(
                expanded = helpExpanded,
                onDismissRequest = { helpExpanded = false }
            ) {
                HelpMenuItem(R.string.action_rtmp_help) { helpExpanded = false; onRtmpHelp() }
                HelpMenuItem(R.string.action_srt_help) { helpExpanded = false; onSrtHelp() }
                HelpMenuItem(R.string.action_uvc_help) { helpExpanded = false; onUvcHelp() }
                HelpMenuItem(R.string.action_known_issues) { helpExpanded = false; onKnownIssues() }
                HelpMenuItem(R.string.action_faq) { helpExpanded = false; onFaq() }
            }
        }
    }
}

@Composable
private fun HelpMenuItem(textRes: Int, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(textRes)) },
        onClick = onClick
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF222222)
@Composable
private fun TopLeftControlsBarPreview() {
    TopLeftControlsBar(
        onQuit = {},
        onRtmpHelp = {},
        onSrtHelp = {},
        onUvcHelp = {},
        onKnownIssues = {},
        onFaq = {},
        modifier = Modifier.padding(10.dp)
    )
}
