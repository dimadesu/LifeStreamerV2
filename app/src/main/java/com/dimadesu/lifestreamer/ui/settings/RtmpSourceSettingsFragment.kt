package com.dimadesu.lifestreamer.ui.settings

import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.SeekBarPreference
import com.dimadesu.lifestreamer.R
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class RtmpSourceSettingsFragment : BaseSettingsFragment() {
    override val preferencesRes = R.xml.settings_rtmp_source
    override val titleRes = R.string.rtmp_source_category_title

    private val rtmpSourceBufferForPlaybackMsPreference: SeekBarPreference by lazy {
        findPreferenceSafe<SeekBarPreference>(R.string.rtmp_source_buffer_for_playback_ms_key)
            ?: error("rtmp_source_buffer_for_playback_ms_key not found")
    }

    override fun onPreferencesInflated() {
        loadRtmpSourceSettings()
    }

    private fun loadRtmpSourceSettings() {
        rtmpSourceBufferForPlaybackMsPreference.setOnPreferenceChangeListener { _, newValue ->
            val rounded = roundBitrate(newValue as Int, step = 500)
            if (rounded != newValue) {
                rtmpSourceBufferForPlaybackMsPreference.value = rounded
                false
            } else {
                true
            }
        }

        // Dynamic RTMP source URLs
        val rtmpCategory = findPreferenceSafe<PreferenceCategory>(R.string.rtmp_source_key)
        if (rtmpCategory != null) {
            val count = preferenceManager.preferenceDataStore?.getInt(
                getString(R.string.rtmp_source_count_key), 1
            ) ?: 1
            rebuildRtmpSourceUrlPreferences(rtmpCategory, count)
        }
    }

    private fun rebuildRtmpSourceUrlPreferences(category: PreferenceCategory, count: Int) {
        val toRemove = mutableListOf<Preference>()
        for (i in 0 until category.preferenceCount) {
            val pref = category.getPreference(i)
            val order = pref.order
            if ((order in 12..899) || order == 800 || order == 801) {
                toRemove.add(pref)
            }
        }
        toRemove.forEach { category.removePreference(it) }

        for (i in 2..count) {
            val actualKey = storageRepository.rtmpSourceUrlKey(i)
            val urlPref = EditTextPreference(requireContext()).apply {
                key = actualKey
                title = getString(R.string.rtmp_source_url_title_numbered, i)
                order = 10 + i
                summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
            }
            category.addPreference(urlPref)
            if (urlPref.text.isNullOrEmpty()) {
                urlPref.text = storageRepository.defaultRtmpSourceUrl(i)
            }
        }

        val addPref = Preference(requireContext()).apply {
            key = "rtmp_source_add_btn"
            title = getString(R.string.rtmp_source_add_title)
            order = 800
            setOnPreferenceClickListener {
                lifecycleScope.launch {
                    val currentCount = storageRepository.rtmpSourceCountFlow.first()
                    val newCount = currentCount + 1
                    storageRepository.setRtmpSourceCount(newCount)
                    rebuildRtmpSourceUrlPreferences(category, newCount)
                }
                true
            }
        }
        category.addPreference(addPref)

        if (count > 1) {
            val removePref = Preference(requireContext()).apply {
                key = "rtmp_source_remove_btn"
                title = getString(R.string.rtmp_source_remove_title)
                order = 801
                setOnPreferenceClickListener {
                    lifecycleScope.launch {
                        val currentCount = storageRepository.rtmpSourceCountFlow.first()
                        if (currentCount > 1) {
                            storageRepository.removeRtmpSourceUrl(currentCount)
                            storageRepository.setRtmpSourceCount(currentCount - 1)
                            rebuildRtmpSourceUrlPreferences(category, currentCount - 1)
                        }
                    }
                    true
                }
            }
            category.addPreference(removePref)
        }
    }
}
