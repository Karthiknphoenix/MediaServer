package com.example.mediaserver.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mediaserver.data.remote.LibraryDto
import com.example.mediaserver.data.repository.MediaRepository
import com.example.mediaserver.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val serverUrl: String = "",
    val isSaved: Boolean = false,
    val tmdbApiKey: String = "",
    val error: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val mediaRepository: MediaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState(
        serverUrl = settingsRepository.getServerUrl()
    ))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadRemoteSettings()
    }

    fun loadRemoteSettings() {
        viewModelScope.launch {
            mediaRepository.getSettings().onSuccess { settingsList ->
                val key = settingsList.find { it.key == "tmdb_api_key" }?.value ?: ""
                _uiState.value = _uiState.value.copy(tmdbApiKey = key)
            }
        }
    }

    fun updateServerUrl(url: String) {
        _uiState.value = _uiState.value.copy(serverUrl = url, isSaved = false)
    }

    fun updateTmdbApiKey(key: String) {
        _uiState.value = _uiState.value.copy(tmdbApiKey = key, isSaved = false)
    }

    fun saveSettings() {
        settingsRepository.setServerUrl(_uiState.value.serverUrl)
        
        viewModelScope.launch {
            mediaRepository.updateRemoteSetting("tmdb_api_key", _uiState.value.tmdbApiKey)
            _uiState.value = _uiState.value.copy(isSaved = true)
        }
    }

    fun resetDatabase() {
        viewModelScope.launch {
            mediaRepository.resetDatabase().onSuccess {
                // Success
            }
        }
    }

    fun resetToDefault() {
        val defaultUrl = settingsRepository.getDefaultUrl()
        _uiState.value = _uiState.value.copy(serverUrl = defaultUrl, isSaved = false)
    }
}

