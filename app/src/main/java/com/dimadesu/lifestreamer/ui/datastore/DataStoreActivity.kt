package com.dimadesu.lifestreamer.ui.datastore

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dimadesu.lifestreamer.R
import com.dimadesu.lifestreamer.utils.dataStore

class DataStoreActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val preferences by dataStore.data.collectAsStateWithLifecycle(initialValue = null)

            DataStoreScreen(
                preferences = preferences,
                onCopy = { text -> copyToClipboard(text) },
                onShare = { text -> shareContent(text) },
                onBack = { finish() }
            )
        }
    }

    private fun copyToClipboard(text: String) {
        if (text.isEmpty()) {
            Toast.makeText(this, R.string.datastore_nothing_to_copy, Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("DataStore Content", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, R.string.datastore_copied, Toast.LENGTH_SHORT).show()
    }

    private fun shareContent(text: String) {
        if (text.isEmpty()) {
            Toast.makeText(this, R.string.datastore_nothing_to_share, Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.title_activity_datastore))
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.datastore_share_title)))
    }
}
