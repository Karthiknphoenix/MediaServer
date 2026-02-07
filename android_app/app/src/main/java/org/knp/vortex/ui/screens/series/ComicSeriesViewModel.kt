package org.knp.vortex.ui.screens.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.knp.vortex.data.remote.ComicSeriesDetailDto
import org.knp.vortex.data.remote.ReadingListDto
import org.knp.vortex.data.repository.MediaRepository
import org.knp.vortex.data.repository.SettingsRepository
import javax.inject.Inject

data class ComicSeriesUiState(
    val isLoading: Boolean = false,
    val series: ComicSeriesDetailDto? = null,
    val error: String? = null,
    val serverUrl: String = "",
    val readingMode: String = "Horizontal",
    // Multi-selection state
    val isSelectionMode: Boolean = false,
    val selectedChapterIds: Set<Long> = emptySet(),
    // Reading list state
    val showReadingListDialog: Boolean = false,
    val readingLists: List<ReadingListDto> = emptyList(),
    val isLoadingLists: Boolean = false
)

@HiltViewModel
class ComicSeriesViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ComicSeriesUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.serverUrl.collect { url ->
                _uiState.value = _uiState.value.copy(serverUrl = url)
            }
        }
    }

    fun loadSeries(name: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            repository.getComicSeriesDetail(name)
                .onSuccess { series ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        series = series,
                        readingMode = settingsRepository.getReadingMode(series.name)
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load comic series"
                    )
                }
        }
    }

    fun setReadingMode(mode: String) {
        val seriesName = _uiState.value.series?.name ?: return
        viewModelScope.launch {
            settingsRepository.setReadingMode(seriesName, mode)
            _uiState.value = _uiState.value.copy(readingMode = mode)
        }
    }

    fun refreshMetadata() {
        val seriesName = _uiState.value.series?.name ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repository.refreshSeriesMetadata(seriesName)
                .onSuccess {
                    loadSeries(seriesName) // Reload to get updates
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                }
        }
    }

    // Multi-selection functions
    fun toggleChapterSelection(chapterId: Long) {
        val current = _uiState.value.selectedChapterIds.toMutableSet()
        if (current.contains(chapterId)) {
            current.remove(chapterId)
        } else {
            current.add(chapterId)
        }
        _uiState.value = _uiState.value.copy(
            selectedChapterIds = current,
            isSelectionMode = current.isNotEmpty()
        )
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            selectedChapterIds = emptySet(),
            isSelectionMode = false
        )
    }

    fun selectAll() {
        val allIds = _uiState.value.series?.chapters?.map { it.id }?.toSet() ?: emptySet()
        _uiState.value = _uiState.value.copy(
            selectedChapterIds = allIds,
            isSelectionMode = allIds.isNotEmpty()
        )
    }

    // Reading List functions
    fun showAddToReadingListDialog() {
        _uiState.value = _uiState.value.copy(showReadingListDialog = true, isLoadingLists = true)
        viewModelScope.launch {
            repository.getReadingLists()
                .onSuccess { lists ->
                    _uiState.value = _uiState.value.copy(readingLists = lists, isLoadingLists = false)
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(readingLists = emptyList(), isLoadingLists = false)
                }
        }
    }

    fun hideReadingListDialog() {
        _uiState.value = _uiState.value.copy(showReadingListDialog = false)
    }

    fun addSelectedToReadingList(listId: Long) {
        val mediaIds = _uiState.value.selectedChapterIds.toList()
        if (mediaIds.isEmpty()) return
        
        viewModelScope.launch {
            repository.addItemsToReadingList(listId, mediaIds)
                .onSuccess {
                    clearSelection()
                    hideReadingListDialog()
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(error = e.message)
                }
        }
    }

    fun createListAndAddSelected(name: String) {
        val mediaIds = _uiState.value.selectedChapterIds.toList()
        if (mediaIds.isEmpty() || name.isBlank()) return
        
        viewModelScope.launch {
            repository.createReadingList(name)
                .onSuccess { newList ->
                    addSelectedToReadingList(newList.id)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(error = e.message)
                }
        }
    }
}

