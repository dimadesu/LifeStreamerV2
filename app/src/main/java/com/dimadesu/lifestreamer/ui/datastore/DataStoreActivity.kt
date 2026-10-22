package com.dimadesu.lifestreamer.ui.datastore

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.lifecycleScope
import com.dimadesu.lifestreamer.R
import com.dimadesu.lifestreamer.databinding.ActivityDatastoreBinding
import com.dimadesu.lifestreamer.utils.dataStore
import com.google.gson.GsonBuilder
import kotlinx.coroutines.launch

class DataStoreActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDatastoreBinding
    private var currentPreferences: Preferences? = null
    private var currentQuery: String = ""
    private var isJsonMode: Boolean = false
    private var currentRenderedText: String = ""

    private val gson by lazy {
        GsonBuilder().setPrettyPrinting().create()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDatastoreBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = getString(R.string.title_activity_datastore)
        }

        setupControls()
        observeDataStore()
    }

    private fun setupControls() {
        binding.searchFilter.addTextChangedListener { text ->
            currentQuery = text?.toString().orEmpty()
            renderContent()
        }

        binding.btnFormatKeyValue.setOnClickListener {
            if (isJsonMode) {
                isJsonMode = false
                updateFormatButtonStates()
                renderContent()
            }
        }

        binding.btnFormatJson.setOnClickListener {
            if (!isJsonMode) {
                isJsonMode = true
                updateFormatButtonStates()
                renderContent()
            }
        }

        updateFormatButtonStates()
    }

    private fun updateFormatButtonStates() {
        if (isJsonMode) {
            binding.btnFormatJson.alpha = 1.0f
            binding.btnFormatKeyValue.alpha = 0.5f
        } else {
            binding.btnFormatKeyValue.alpha = 1.0f
            binding.btnFormatJson.alpha = 0.5f
        }
    }

    private fun observeDataStore() {
        lifecycleScope.launch {
            dataStore.data.collect { preferences ->
                currentPreferences = preferences
                renderContent()
            }
        }
    }

    private fun renderContent() {
        val prefs = currentPreferences
        if (prefs == null || prefs.asMap().isEmpty()) {
            binding.emptyStateText.visibility = View.VISIBLE
            binding.emptyStateText.text = getString(R.string.datastore_empty)
            binding.scrollView.visibility = View.GONE
            binding.summaryText.text = getString(R.string.datastore_summary_empty)
            currentRenderedText = ""
            return
        }

        val allEntries = prefs.asMap().entries.sortedBy { it.key.name }
        val query = currentQuery.trim().lowercase()

        val filteredEntries = if (query.isEmpty()) {
            allEntries
        } else {
            allEntries.filter { entry ->
                entry.key.name.lowercase().contains(query) ||
                    entry.value.toString().lowercase().contains(query)
            }
        }

        if (filteredEntries.isEmpty()) {
            binding.emptyStateText.visibility = View.VISIBLE
            binding.emptyStateText.text = getString(R.string.datastore_no_matching_keys, currentQuery)
            binding.scrollView.visibility = View.GONE
            binding.summaryText.text = getString(
                R.string.datastore_summary_filtered,
                0,
                allEntries.size
            )
            currentRenderedText = ""
            return
        }

        binding.emptyStateText.visibility = View.GONE
        binding.scrollView.visibility = View.VISIBLE

        if (query.isEmpty()) {
            binding.summaryText.text = getString(R.string.datastore_summary_keys, allEntries.size)
        } else {
            binding.summaryText.text = getString(
                R.string.datastore_summary_filtered,
                filteredEntries.size,
                allEntries.size
            )
        }

        currentRenderedText = if (isJsonMode) {
            buildJsonText(filteredEntries)
        } else {
            buildKeyValueText(filteredEntries, allEntries.size, query.isNotEmpty())
        }

        binding.dataStoreContentText.text = currentRenderedText
    }

    private fun buildKeyValueText(
        entries: List<Map.Entry<Preferences.Key<*>, Any>>,
        totalCount: Int,
        isFiltered: Boolean
    ): String {
        val header = if (isFiltered) {
            "// DataStore Entries: ${entries.size} (filtered from $totalCount)\n\n"
        } else {
            "// DataStore Entries: ${entries.size}\n\n"
        }

        val body = entries.joinToString("\n") { entry ->
            val keyName = entry.key.name
            val typeName = entry.value::class.java.simpleName
            val formattedValue = when (val value = entry.value) {
                is String -> "\"$value\""
                is Set<*> -> value.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
                else -> value.toString()
            }
            "$keyName = $formattedValue  ($typeName)"
        }

        return header + body
    }

    private fun buildJsonText(entries: List<Map.Entry<Preferences.Key<*>, Any>>): String {
        val map = linkedMapOf<String, Any>()
        for (entry in entries) {
            map[entry.key.name] = entry.value
        }
        return try {
            gson.toJson(map)
        } catch (e: Exception) {
            map.toString()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.datastore_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_copy -> {
                copyToClipboard()
                true
            }
            R.id.action_share -> {
                shareContent()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun copyToClipboard() {
        if (currentRenderedText.isEmpty()) {
            Toast.makeText(this, R.string.datastore_nothing_to_copy, Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("DataStore Content", currentRenderedText)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, R.string.datastore_copied, Toast.LENGTH_SHORT).show()
    }

    private fun shareContent() {
        if (currentRenderedText.isEmpty()) {
            Toast.makeText(this, R.string.datastore_nothing_to_share, Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.title_activity_datastore))
            putExtra(Intent.EXTRA_TEXT, currentRenderedText)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.datastore_share_title)))
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
