package com.example.mediaserver.ui.screens.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mediaserver.data.remote.MediaItemDto
import com.example.mediaserver.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MovieDetailUiState(
    val media: MediaItemDto? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    private val repository: MediaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MovieDetailUiState())
    val uiState: StateFlow<MovieDetailUiState> = _uiState.asStateFlow()

    fun loadMedia(id: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repository.getMediaDetails(id)
                .onSuccess { media ->
                    _uiState.value = MovieDetailUiState(media = media, isLoading = false)
                }
                .onFailure {
                    _uiState.value = MovieDetailUiState(isLoading = false, error = it.message)
                }
        }
    }

    fun refreshMetadata(id: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repository.refreshMetadata(id)
                .onSuccess { media ->
                     _uiState.value = MovieDetailUiState(media = media, isLoading = false)
                }
                .onFailure {
                    _uiState.value = MovieDetailUiState(isLoading = false, error = it.message)
                }
        }
    }
}
