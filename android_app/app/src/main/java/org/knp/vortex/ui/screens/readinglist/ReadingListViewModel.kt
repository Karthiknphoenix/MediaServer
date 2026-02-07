package org.knp.vortex.ui.screens.readinglist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.knp.vortex.data.remote.ReadingListDto
import org.knp.vortex.data.remote.ReadingListWithItemsDto
import org.knp.vortex.data.repository.MediaRepository
import org.knp.vortex.data.repository.SettingsRepository
import javax.inject.Inject

data class ReadingListUiState(
    val isLoading: Boolean = false,
    val lists: List<ReadingListDto> = emptyList(),
    val selectedList: ReadingListWithItemsDto? = null,
    val readingMode: String = "Horizontal",
    val error: String? = null
)

@HiltViewModel
class ReadingListViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReadingListUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadLists()
    }

    fun loadLists() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getReadingLists()
                .onSuccess { lists ->
                    _uiState.value = _uiState.value.copy(isLoading = false, lists = lists)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                }
        }
    }

    fun loadListDetails(listId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repository.getReadingListDetails(listId)
                .onSuccess { list ->
                    // Load saved reading mode for this list
                    val savedMode = settingsRepository.getReadingMode("list_$listId") ?: "Horizontal"
                    _uiState.value = _uiState.value.copy(
                        isLoading = false, 
                        selectedList = list,
                        readingMode = savedMode
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                }
        }
    }

    fun setReadingMode(mode: String) {
        _uiState.value = _uiState.value.copy(readingMode = mode)
        // Persist for this list
        val listId = _uiState.value.selectedList?.id
        if (listId != null) {
            settingsRepository.setReadingMode("list_$listId", mode)
        }
    }

    fun deleteList(listId: Long) {
        viewModelScope.launch {
            repository.deleteReadingList(listId)
                .onSuccess {
                    loadLists()
                    _uiState.value = _uiState.value.copy(selectedList = null)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(error = e.message)
                }
        }
    }

    fun clearSelectedList() {
        _uiState.value = _uiState.value.copy(selectedList = null)
    }
}
