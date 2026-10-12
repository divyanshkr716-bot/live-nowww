package com.example.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.PreparationPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_settings")

data class UserSettings(
    val batterySaverEnabled: Boolean = false,
    val thermalMonitoringEnabled: Boolean = true,
    val maxActiveStreams: Int = 2,
    val defaultPreset: PreparationPreset = PreparationPreset.PASSTHROUGH_REMUX,
    val retryMaxAttempts: Int = 5,
    val retryBackoffSec: Int = 3,
    val backgroundKeepScreenAwake: Boolean = false
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val BATTERY_SAVER = booleanPreferencesKey("battery_saver")
        val THERMAL_MONITORING = booleanPreferencesKey("thermal_monitoring")
        val MAX_STREAMS = intPreferencesKey("max_active_streams")
        val DEFAULT_PRESET = stringPreferencesKey("default_preset")
        val RETRY_MAX = intPreferencesKey("retry_max_attempts")
        val RETRY_BACKOFF = intPreferencesKey("retry_backoff_sec")
        val SCREEN_AWAKE = booleanPreferencesKey("background_keep_screen_awake")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        val presetName = prefs[Keys.DEFAULT_PRESET] ?: PreparationPreset.PASSTHROUGH_REMUX.name
        val preset = try {
            PreparationPreset.valueOf(presetName)
        } catch (e: Exception) {
            PreparationPreset.PASSTHROUGH_REMUX
        }

        UserSettings(
            batterySaverEnabled = prefs[Keys.BATTERY_SAVER] ?: false,
            thermalMonitoringEnabled = prefs[Keys.THERMAL_MONITORING] ?: true,
            maxActiveStreams = prefs[Keys.MAX_STREAMS] ?: 2,
            defaultPreset = preset,
            retryMaxAttempts = prefs[Keys.RETRY_MAX] ?: 5,
            retryBackoffSec = prefs[Keys.RETRY_BACKOFF] ?: 3,
            backgroundKeepScreenAwake = prefs[Keys.SCREEN_AWAKE] ?: false
        )
    }

    suspend fun setBatterySaver(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BATTERY_SAVER] = enabled }
    }

    suspend fun setThermalMonitoring(enabled: Boolean) {
        context.dataStore.edit { it[Keys.THERMAL_MONITORING] = enabled }
    }

    suspend fun setMaxActiveStreams(max: Int) {
        context.dataStore.edit { it[Keys.MAX_STREAMS] = max }
    }

    suspend fun setDefaultPreset(preset: PreparationPreset) {
        context.dataStore.edit { it[Keys.DEFAULT_PRESET] = preset.name }
    }

    suspend fun setRetrySettings(maxAttempts: Int, backoffSec: Int) {
        context.dataStore.edit {
            it[Keys.RETRY_MAX] = maxAttempts
            it[Keys.RETRY_BACKOFF] = backoffSec
        }
    }
}
