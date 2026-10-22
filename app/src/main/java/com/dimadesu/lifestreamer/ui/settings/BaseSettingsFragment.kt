package com.dimadesu.lifestreamer.ui.settings

import android.os.Bundle
import androidx.annotation.StringRes
import androidx.annotation.XmlRes
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.dimadesu.lifestreamer.R
import com.dimadesu.lifestreamer.data.storage.DataStoreRepository
import com.dimadesu.lifestreamer.data.storage.PreferencesDataStoreAdapter
import com.dimadesu.lifestreamer.models.EndpointType
import com.dimadesu.lifestreamer.utils.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

abstract class BaseSettingsFragment : PreferenceFragmentCompat() {

    @get:XmlRes
    protected abstract val preferencesRes: Int

    @get:StringRes
    abstract val titleRes: Int

    protected val storageRepository by lazy {
        DataStoreRepository(requireContext(), requireContext().dataStore)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore =
            PreferencesDataStoreAdapter(requireContext().dataStore, writeScope)
        setPreferencesFromResource(preferencesRes, rootKey)
        onPreferencesInflated()
    }

    protected open fun onPreferencesInflated() {}

    override fun onResume() {
        super.onResume()
        (activity as? AppCompatActivity)?.supportActionBar?.title = getString(titleRes)
    }

    protected fun <T : Preference> findPreferenceSafe(keyResId: Int): T? {
        return try {
            this.findPreference(getString(keyResId))
        } catch (e: Exception) {
            android.util.Log.e("BaseSettingsFragment", "Preference not found for key: ${getString(keyResId)}", e)
            null
        }
    }

    protected fun roundBitrate(value: Int, step: Int = 500): Int {
        return (value / step) * step
    }

    protected fun storedEndpointType(): EndpointType {
        val raw = preferenceManager.preferenceDataStore
            ?.getString(getString(R.string.endpoint_type_key), null)
            ?: "${EndpointType.SRT.id}"
        return EndpointType.fromId(raw.toIntOrNull() ?: EndpointType.SRT.id)
    }

    protected fun ListPreference.refreshStaleSettingUi() {
        summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
    }

    companion object {
        /**
         * Writes outlive the section: DataStore runs a write asynchronously, so using a supervisor
         * scope prevents lost writes when navigating back immediately after modifying a setting.
         */
        val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
}
