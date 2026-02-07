package org.knp.vortex.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.knp.vortex.data.remote.LibraryDto
import org.knp.vortex.data.remote.MediaItemDto
import org.knp.vortex.data.remote.SeriesDto
import org.knp.vortex.data.repository.HiddenContentRepository
import org.knp.vortex.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val continueWatching: List<MediaItemDto> = emptyList(),
    val recentlyAdded: List<MediaItemDto> = emptyList(),
    val libraries: List<LibraryDto> = emptyList(),
    val visibleLibraries: List<LibraryDto> = emptyList(), // Filtered libraries for display
    val libraryContent: Map<Long, List<MediaItemDto>> = emptyMap(),
    val tvShowLibraryContent: Map<Long, List<SeriesDto>> = emptyMap(),
    val comicSeriesLibraryContent: Map<Long, List<org.knp.vortex.data.remote.ComicSeriesDto>> = emptyMap(),
    val allSeries: List<SeriesDto> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val isUnlocked: Boolean = false,
    val isPinSet: Boolean = false,
    val serverUrl: String = ""
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val hiddenContentRepository: HiddenContentRepository,
    private val settingsRepository: org.knp.vortex.data.repository.SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    
    val uiState: StateFlow<HomeUiState> = combine(
        _uiState,
        hiddenContentRepository.isUnlocked
    ) { state, isUnlocked ->
        val visibleLibraries = if (isUnlocked) {
            state.libraries
        } else {
            state.libraries.filter { it.library_type != "other" }
        }
        state.copy(
            visibleLibraries = visibleLibraries,
            isUnlocked = isUnlocked,
            isPinSet = hiddenContentRepository.isPinSet(),
            serverUrl = settingsRepository.getServerUrl()
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        HomeUiState()
    )

    init {
        loadData(false)
    }

    fun loadData(isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isRefresh) {
                _uiState.value = _uiState.value.copy(isRefreshing = true)
            } else {
                _uiState.value = _uiState.value.copy(isLoading = true)
            }
            
            // Execute all initial API calls in PARALLEL using async
            val recentDeferred = async { repository.getRecentlyAdded() }
            val librariesDeferred = async { repository.getLibraries() }
            val continueDeferred = async { repository.getContinueWatching() }
            val seriesDeferred = async { repository.getSeries() }
            val comicSeriesDeferred = async { repository.getComicSeries() }
            
            // Await all results concurrently
            val recentResult = recentDeferred.await()
            val librariesResult = librariesDeferred.await()
            val continueResult = continueDeferred.await()
            val seriesResult = seriesDeferred.await()
            val comicSeriesResult = comicSeriesDeferred.await()
            
            val libraries = librariesResult.getOrDefault(emptyList())
            val allSeries = seriesResult.getOrDefault(emptyList())
            val allComicSeries = comicSeriesResult.getOrDefault(emptyList())
            
            // Fetch content for each library in PARALLEL
            val libraryContent = mutableMapOf<Long, List<MediaItemDto>>()
            val tvShowLibraryContent = mutableMapOf<Long, List<SeriesDto>>()
            val comicSeriesLibraryContent = mutableMapOf<Long, List<org.knp.vortex.data.remote.ComicSeriesDto>>()
            
            // Identify which libraries need media fetching and create parallel jobs
            val libraryMediaJobs = libraries
                .filter { lib -> 
                    lib.library_type != "tv_shows" && 
                    !(lib.library_type == "books" && allComicSeries.isNotEmpty())
                }
                .map { lib ->
                    async {
                        lib.id to repository.getLibraryMedia(lib.id)
                    }
                }
            
            // Await all library media fetches in parallel
            libraryMediaJobs.forEach { job ->
                val (libId, result) = job.await()
                result.onSuccess { media ->
                    libraryContent[libId] = media.take(10)
                }
            }
            
            // Handle TV shows and books (non-network operations)
            libraries.forEach { lib ->
                when (lib.library_type) {
                    "tv_shows" -> {
                        tvShowLibraryContent[lib.id] = allSeries.take(10)
                    }
                    "books" -> {
                        if (allComicSeries.isNotEmpty()) {
                            comicSeriesLibraryContent[lib.id] = allComicSeries.take(10)
                        }
                        // If no comic series, libraryContent was already fetched in parallel above
                    }
                }
            }

            _uiState.value = _uiState.value.copy(
                recentlyAdded = recentResult.getOrDefault(emptyList()),
                libraries = libraries,
                libraryContent = libraryContent,
                tvShowLibraryContent = tvShowLibraryContent,
                comicSeriesLibraryContent = comicSeriesLibraryContent,
                continueWatching = continueResult.getOrDefault(emptyList()),
                allSeries = allSeries,
                isLoading = false,
                isRefreshing = false,
                error = if (recentResult.isFailure && !isRefresh) "Failed to connect to server" else null
            )
        }
    }

    fun isPinSet(): Boolean = hiddenContentRepository.isPinSet()

    fun setPin(pin: String) {
        hiddenContentRepository.setPin(pin)
    }

    fun verifyAndUnlock(pin: String): Boolean {
        return if (hiddenContentRepository.verifyPin(pin)) {
            hiddenContentRepository.unlock()
            true
        } else {
            false
        }
    }

    fun lock() {
        hiddenContentRepository.lock()
    }
}
