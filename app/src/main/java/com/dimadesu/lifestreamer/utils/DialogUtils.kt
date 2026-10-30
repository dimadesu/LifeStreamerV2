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
package com.dimadesu.lifestreamer.utils

import android.content.Context
import android.content.DialogInterface
import android.view.ContextThemeWrapper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.dimadesu.lifestreamer.R

object DialogUtils {
    /**
     * Creates a Material 3 dialog builder. The app theme is AppCompat-based, so the context is
     * wrapped with the Material 3 [R.style.AppTheme] to get Material 3 dialog styling.
     */
    @JvmStatic
    fun materialDialogBuilder(context: Context): MaterialAlertDialogBuilder =
        MaterialAlertDialogBuilder(ContextThemeWrapper(context, R.style.AppTheme))

    fun showAlertDialog(context: Context, title: String, message: String = "") {
        materialDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { dialogInterface: DialogInterface, _: Int -> dialogInterface.dismiss() }
            .show()
    }

    fun showPermissionAlertDialog(context: Context, afterPositiveButton: () -> Unit = {}) {
        materialDialogBuilder(context)
            .setTitle(R.string.permission)
            .setMessage(R.string.permission_not_granted)
            .setPositiveButton(android.R.string.ok) { dialogInterface: DialogInterface, _: Int ->
                dialogInterface.dismiss()
                afterPositiveButton()
            }
            .show()
    }
}