package org.knp.vortex.ui.screens.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.knp.vortex.data.remote.MediaItemDto
import org.knp.vortex.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import org.knp.vortex.data.remote.MediaApi

data class BookPage(
    val index: Int,
    val filename: String
)

data class ReaderUiState(
    val media: MediaItemDto? = null,
    val pages: List<BookPage> = emptyList(),
    val currentPageIndex: Int = 0,
    val isLoading: Boolean = true,
    val isMenuVisible: Boolean = false,
    val readingMode: ReadingMode = ReadingMode.Horizontal, // Horizontal, Vertical
    val error: String? = null,
    val serverUrl: String = ""
)

enum class ReadingMode {
    Horizontal,
    Vertical,
    Webtoon // Vertical continuous
}

@HiltViewModel
class BookReaderViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val settingsRepository: org.knp.vortex.data.repository.SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    init {
        _uiState.value = _uiState.value.copy(serverUrl = settingsRepository.getServerUrl())
    }

    fun loadBook(id: Long, initialMode: String = "Horizontal") {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            // 1. Get Media Details
            repository.getMediaDetails(id)
                .onSuccess { media ->
                    // Use initialMode if valid, otherwise fallback to saved series preference
                    val mode = when(initialMode) {
                        "Vertical" -> ReadingMode.Vertical
                        "Webtoon" -> ReadingMode.Webtoon
                        "Horizontal" -> ReadingMode.Horizontal
                        else -> {
                            // Fallback to series preference if "Default" or invalid
                            media.series_name?.let { series ->
                                val modeStr = settingsRepository.getReadingMode(series)
                                when(modeStr) {
                                    "Vertical" -> ReadingMode.Vertical
                                    "Webtoon" -> ReadingMode.Webtoon
                                    else -> ReadingMode.Horizontal
                                }
                            } ?: ReadingMode.Horizontal
                        }
                    }

                    _uiState.value = _uiState.value.copy(
                        media = media,
                        readingMode = mode
                    )
                    loadPages(id)
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
                }
        }
    }
    
    private fun loadPages(id: Long) {
        viewModelScope.launch {
             repository.getBookPages(id)
                .onSuccess { pages ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false, 
                        pages = pages.map { BookPage(it.index, it.filename) }
                    )
                }
                .onFailure {
                    // Start with empty pages (might be PDF)
                     _uiState.value = _uiState.value.copy(
                        isLoading = false, 
                        pages = emptyList(),
                         // Don't show error yet, PDF might not return pages
                    )
                }
        }
    }

    fun toggleMenu() {
        _uiState.value = _uiState.value.copy(isMenuVisible = !_uiState.value.isMenuVisible)
    }
    
    fun setPage(index: Int) {
        if (index in 0.._uiState.value.pages.size) { // Allow slight OOB control by UI
             val safeIndex = index.coerceIn(0, _uiState.value.pages.size - 1)
            _uiState.value = _uiState.value.copy(currentPageIndex = safeIndex)
            
            // Persist progress
            // We use page index as position and total pages as total duration
            viewModelScope.launch {
                 repository.updateProgress(
                     _uiState.value.media?.id ?: return@launch,
                     safeIndex.toLong(),
                     _uiState.value.pages.size.toLong()
                 )
            }
        }
    }
    
    fun nextPage() {
        val next = _uiState.value.currentPageIndex + 1
        if (next < _uiState.value.pages.size) {
            setPage(next)
        }
    }
    
    fun prevPage() {
        val prev = _uiState.value.currentPageIndex - 1
        if (prev >= 0) {
            setPage(prev)
        }
    }
    
    fun isOnLastPage(): Boolean {
        return _uiState.value.currentPageIndex >= _uiState.value.pages.size - 1
    }
    
    fun setReadingMode(mode: ReadingMode) {
        _uiState.value = _uiState.value.copy(readingMode = mode)
        
        // Persist if associated with a series
        val seriesName = _uiState.value.media?.series_name
        if (seriesName != null) {
            val modeStr = when(mode) {
                ReadingMode.Vertical -> "Vertical"
                ReadingMode.Webtoon -> "Webtoon"
                else -> "Horizontal"
            }
            settingsRepository.setReadingMode(seriesName, modeStr)
        }
    }
}
