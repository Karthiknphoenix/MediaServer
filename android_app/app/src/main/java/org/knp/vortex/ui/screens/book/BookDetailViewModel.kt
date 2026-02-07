package org.knp.vortex.ui.screens.book

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.knp.vortex.data.remote.MediaItemDto
import org.knp.vortex.data.remote.ReadingListDto
import org.knp.vortex.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BookDetailUiState(
    val media: MediaItemDto? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val serverUrl: String = "",
    // Reading list state
    val showReadingListDialog: Boolean = false,
    val readingLists: List<ReadingListDto> = emptyList(),
    val isLoadingLists: Boolean = false,
    val addedToList: Boolean = false
)

@HiltViewModel
class BookDetailViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val settingsRepository: org.knp.vortex.data.repository.SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookDetailUiState())
    val uiState: StateFlow<BookDetailUiState> = _uiState.asStateFlow()

    init {
        _uiState.value = _uiState.value.copy(serverUrl = settingsRepository.getServerUrl())
    }

    fun loadMedia(id: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repository.getMediaDetails(id)
                .onSuccess { media ->
                    _uiState.value = BookDetailUiState(media = media, isLoading = false)
                    // Also load reading lists
                    loadReadingLists()
                }
                .onFailure {
                    _uiState.value = BookDetailUiState(isLoading = false, error = it.message)
                }
        }
    }
    
    private fun loadReadingLists() {
        viewModelScope.launch {
            repository.getReadingLists()
                .onSuccess { lists ->
                    _uiState.value = _uiState.value.copy(readingLists = lists)
                }
        }
    }

    fun refreshMetadata(id: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repository.refreshMetadata(id)
                .onSuccess { media ->
                     _uiState.value = BookDetailUiState(media = media, isLoading = false)
                }
                .onFailure {
                    _uiState.value = BookDetailUiState(isLoading = false, error = it.message)
                }
        }
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
        _uiState.value = _uiState.value.copy(showReadingListDialog = false, addedToList = false)
    }

    fun addToReadingList(listId: Long) {
        val mediaId = _uiState.value.media?.id ?: return
        viewModelScope.launch {
            repository.addItemsToReadingList(listId, listOf(mediaId))
                .onSuccess {
                    _uiState.value = _uiState.value.copy(addedToList = true)
                    hideReadingListDialog()
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(error = e.message)
                }
        }
    }

    fun createListAndAdd(name: String) {
        val mediaId = _uiState.value.media?.id ?: return
        if (name.isBlank()) return
        
        viewModelScope.launch {
            repository.createReadingList(name)
                .onSuccess { newList ->
                    repository.addItemsToReadingList(newList.id, listOf(mediaId))
                        .onSuccess {
                            _uiState.value = _uiState.value.copy(addedToList = true)
                            hideReadingListDialog()
                        }
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(error = e.message)
                }
        }
    }
}

