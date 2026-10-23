package com.dimadesu.lifestreamer.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.dimadesu.lifestreamer.ui.settings.compose.SettingsNavGraph
import com.dimadesu.lifestreamer.ui.theme.LifeStreamerTheme

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LifeStreamerTheme {
                SettingsNavGraph(onFinish = { finish() })
            }
        }
    }
}