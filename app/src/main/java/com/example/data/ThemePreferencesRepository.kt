package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.pairlySettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "pairly_settings"
)

class ThemePreferencesRepository(private val context: Context) {
    private val appContext = context.applicationContext
    private val themeKey = stringPreferencesKey("theme_mode")

    val themeMode: Flow<ThemeMode> = appContext.pairlySettingsDataStore.data.map { prefs ->
        ThemeMode.fromName(prefs[themeKey])
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        appContext.pairlySettingsDataStore.edit { prefs ->
            prefs[themeKey] = mode.name
        }
    }
}
