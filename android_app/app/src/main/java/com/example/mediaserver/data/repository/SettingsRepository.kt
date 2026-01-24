package com.example.mediaserver.data.repository

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "media_server_settings"
        private const val KEY_SERVER_URL = "server_url"
        private const val DEFAULT_URL = "http://10.0.2.2:3000"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val _serverUrl = MutableStateFlow(getServerUrl())
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    fun getServerUrl(): String {
        return prefs.getString(KEY_SERVER_URL, DEFAULT_URL) ?: DEFAULT_URL
    }

    fun setServerUrl(url: String) {
        val normalizedUrl = url.trim().removeSuffix("/")
        prefs.edit().putString(KEY_SERVER_URL, normalizedUrl).apply()
        _serverUrl.value = normalizedUrl
    }

    fun getDefaultUrl(): String = DEFAULT_URL
}
