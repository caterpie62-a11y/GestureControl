package com.gesturecontrol.app.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesManager(private val context: Context) {

    companion object {
        val BUTTON_X = floatPreferencesKey("button_x")
        val BUTTON_Y = floatPreferencesKey("button_y")
        val LAST_APP = stringPreferencesKey("last_app")
    }

    val buttonX: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[BUTTON_X] ?: -1f
    }

    val buttonY: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[BUTTON_Y] ?: -1f
    }

    val lastApp: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[LAST_APP] ?: ""
    }

    suspend fun saveButtonPosition(x: Float, y: Float) {
        context.dataStore.edit { preferences ->
            preferences[BUTTON_X] = x
            preferences[BUTTON_Y] = y
        }
    }

    suspend fun saveLastApp(packageName: String) {
        context.dataStore.edit { preferences ->
            preferences[LAST_APP] = packageName
        }
    }
}
