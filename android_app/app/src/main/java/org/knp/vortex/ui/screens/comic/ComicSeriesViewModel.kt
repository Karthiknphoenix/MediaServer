package org.knp.vortex.ui.screens.comic

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.knp.vortex.data.remote.ComicChapterDto
import org.knp.vortex.data.remote.ComicSeriesDetailDto
import org.knp.vortex.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class ComicSeriesUiState(
    val seriesDetail: ComicSeriesDetailDto? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val serverUrl: String = ""
)

@HiltViewModel
class ComicSeriesViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val settingsRepository: org.knp.vortex.data.repository.SettingsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(ComicSeriesUiState(serverUrl = settingsRepository.getServerUrl()))
    val uiState: StateFlow<ComicSeriesUiState> = _uiState.asStateFlow()

    private val rawSeriesName: String = savedStateHandle.get<String>("seriesName") ?: ""
    val seriesName: String = try {
        URLDecoder.decode(rawSeriesName, StandardCharsets.UTF_8.toString())
    } catch (e: Exception) {
        rawSeriesName
    }

    init {
        loadSeriesDetail()
    }

    fun loadSeriesDetail() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repository.getComicSeriesDetail(seriesName)
                .onSuccess { detail ->
                    _uiState.value = _uiState.value.copy(
                        seriesDetail = detail,
                        isLoading = false
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
                }
        }
    }
}
