package com.example.telemetryapp

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {
    private object Keys {
        val strengthThreshold = intPreferencesKey("strength_threshold")
        val alertsEnabled = booleanPreferencesKey("alerts_enabled")
        val plotSignal = stringPreferencesKey("plot_signal")
        val demoMode = booleanPreferencesKey("demo_mode")
        val lastDevice = stringPreferencesKey("last_device")
    }

    val settingsFlow: Flow<SettingsState> = context.dataStore.data.map { prefs ->
        SettingsState(
            strengthThreshold = prefs[Keys.strengthThreshold] ?: 70,
            alertsEnabled = prefs[Keys.alertsEnabled] ?: true,
            plotSignal = PlotSignal.valueOf(prefs[Keys.plotSignal] ?: PlotSignal.Raw.name),
            demoMode = prefs[Keys.demoMode] ?: false,
            lastDeviceAddress = prefs[Keys.lastDevice]
        )
    }

    suspend fun updateThreshold(value: Int) {
        context.dataStore.edit { it[Keys.strengthThreshold] = value }
    }

    suspend fun updateAlertsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.alertsEnabled] = enabled }
    }

    suspend fun updatePlotSignal(signal: PlotSignal) {
        context.dataStore.edit { it[Keys.plotSignal] = signal.name }
    }

    suspend fun updateDemoMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.demoMode] = enabled }
    }

    suspend fun updateLastDevice(address: String?) {
        context.dataStore.edit { prefs: Preferences ->
            if (address == null) {
                prefs.remove(Keys.lastDevice)
            } else {
                prefs[Keys.lastDevice] = address
            }
        }
    }
}
