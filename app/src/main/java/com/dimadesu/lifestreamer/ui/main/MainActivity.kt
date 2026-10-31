/*
 * Copyright (C) 2021 Thibault B.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dimadesu.lifestreamer.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.dimadesu.lifestreamer.R
import com.dimadesu.lifestreamer.rtmp.audio.MediaProjectionService
import com.dimadesu.lifestreamer.services.CameraStreamerService

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Notification Quit starts a new activity when none is running.
        // Handle it here — onNewIntent is not called for a fresh launch —
        // and skip UI/service bind so we don't race teardown.
        if (intent?.action == CameraStreamerService.ACTION_EXIT_APP) {
            exitApp()
            return
        }

        setContentView(R.layout.main_activity)

        // Note: Permissions (Camera, Microphone, Notifications) are requested by PreviewFragment.
        // Bluetooth permissions are requested on-demand when user taps the BT mic toggle.

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, PreviewFragment())
                .commitNow()
        }

    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Handle notification tap action to avoid re-creating activity and
        // triggering unnecessary view detach/attach which can race with camera.
        val action = intent.action
        if (action == CameraStreamerService.ACTION_EXIT_APP) {
            exitApp()
        } else if (action == CameraStreamerService.ACTION_OPEN_FROM_NOTIFICATION) {
            // If the PreviewFragment is already present, do nothing. If not,
            // ensure it's added without recreating the fragment stack.
            val current = supportFragmentManager.findFragmentById(R.id.container)
            if (current == null) {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.container, PreviewFragment())
                    .commitNow()
            }
        }
    }

    fun exitApp() {
        // Unbind first: a started+bound service ignores stopService() until
        // every client unbinds. PreviewViewModel otherwise unbinds only in
        // onCleared(), which runs after the activity is already finishing.
        (supportFragmentManager.findFragmentById(R.id.container) as? PreviewFragment)
            ?.prepareForExit()

        try {
            stopService(Intent(this, CameraStreamerService::class.java))
        } catch (_: Exception) {
            // Service might not be running, that's okay
        }
        try {
            stopService(Intent(this, MediaProjectionService::class.java))
        } catch (_: Exception) {
            // Optional companion service; ignore if absent
        }

        finishAndRemoveTask()
    }
}
