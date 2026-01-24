package com.example.mediaserver.ui.screens.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mediaserver.data.remote.FileSystemEntryDto
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import com.example.mediaserver.data.remote.MediaItemDto
import com.example.mediaserver.data.remote.SeriesDto
import com.example.mediaserver.data.repository.MediaRepository
import com.example.mediaserver.ui.components.AppHeader
import com.example.mediaserver.ui.components.ModernMediaCard
import com.example.mediaserver.ui.components.SectionHeader
import com.example.mediaserver.ui.theme.DeepBackground
import com.example.mediaserver.ui.theme.PrimaryBlue
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

data class LibraryUiState(
    val isLoading: Boolean = true,
    val mediaItems: List<MediaItemDto> = emptyList(),
    val seriesList: List<SeriesDto> = emptyList(),
    val fileSystemEntries: List<FileSystemEntryDto> = emptyList(),
    val currentPath: String = "",
    val error: String? = null
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: MediaRepository
) : ViewModel() {
    var uiState by mutableStateOf(LibraryUiState())
        private set

    fun loadLibraryContent(libId: Long, libraryType: String) {
        if (uiState.mediaItems.isNotEmpty() || uiState.seriesList.isNotEmpty() || uiState.fileSystemEntries.isNotEmpty()) return // Already loaded
        
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null)
            
            if (libraryType == "tv_shows") {
                repository.getSeries().onSuccess { allSeries ->
                    uiState = uiState.copy(isLoading = false, seriesList = allSeries)
                }.onFailure { error -> uiState = uiState.copy(isLoading = false, error = error.message) }
            } else if (libraryType == "other") {
                browse(libId, "")
            } else {
                repository.getLibraryMedia(libId).onSuccess { items ->
                    uiState = uiState.copy(isLoading = false, mediaItems = items)
                }.onFailure { error -> uiState = uiState.copy(isLoading = false, error = error.message) }
            }
        }
    }

    fun browse(libId: Long, path: String) {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null)
            repository.browseLibrary(libId, path).onSuccess { entries ->
                 uiState = uiState.copy(
                     isLoading = false, 
                     fileSystemEntries = entries,
                     currentPath = path
                 )
            }.onFailure { error ->
                 uiState = uiState.copy(isLoading = false, error = error.message)
            }
        }
    }

    fun goUp(libId: Long) {
        val current = uiState.currentPath
        if (current.isEmpty()) return
        
        // Remove last segment
        val parts = current.split("/").filter { it.isNotEmpty() }
        val newPath = if (parts.size <= 1) "" else parts.dropLast(1).joinToString("/")
        browse(libId, newPath)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    libraryId: Long,
    libraryName: String,
    libraryType: String,
    onPlayMedia: (Long) -> Unit,
    onOpenSeries: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState = viewModel.uiState
    
    LaunchedEffect(libraryId, libraryType) {
        viewModel.loadLibraryContent(libraryId, libraryType)
    }

    // Handle Back Press for browsing
    BackHandler(enabled = uiState.currentPath.isNotEmpty()) {
        viewModel.goUp(libraryId)
    }

    // Override generic back actions if searching
    val effectiveOnBack = {
        if (uiState.currentPath.isNotEmpty()) {
            viewModel.goUp(libraryId)
        } else {
            onBack()
        }
    }

    Scaffold(
        containerColor = DeepBackground,
        topBar = {
            AppHeader(onMenuClick = effectiveOnBack)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Library Name Header
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                SectionHeader(title = if (uiState.currentPath.isEmpty()) libraryName else "$libraryName / ...")
            }
            
            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = PrimaryBlue)
                    }
                }
                uiState.error != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = uiState.error ?: "Unknown error", color = Color.Red)
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 120.dp),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (libraryType == "tv_shows") {
                            items(uiState.seriesList) { series ->
                                ModernMediaCard(
                                    title = series.name,
                                    posterUrl = series.poster_url,
                                    year = null,
                                    onClick = { onOpenSeries(series.name) },
                                    modifier = Modifier.width(140.dp)
                                )
                            }
                        } else if (libraryType == "other") {
                            items(uiState.fileSystemEntries) { entry ->
                                // Custom Card for Files/Folders
                                Card(
                                    modifier = Modifier
                                        .width(140.dp)
                                        .aspectRatio(1f) // Square for folders
                                        .clickable { 
                                            if (entry.is_directory) {
                                                viewModel.browse(libraryId, entry.path)
                                            } else {
                                                entry.media_id?.let { onPlayMedia(it) }
                                            }
                                        },
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = com.example.mediaserver.ui.theme.SurfaceColor)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize().padding(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = if (entry.is_directory) androidx.compose.material.icons.Icons.Filled.Home else androidx.compose.material.icons.Icons.Filled.PlayArrow, // Use generic icons
                                            contentDescription = null,
                                            tint = if (entry.is_directory) Color(0xFFFFC107) else Color.White,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = entry.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White,
                                            maxLines = 2,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        } else {
                            items(uiState.mediaItems) { item ->
                                ModernMediaCard(
                                    title = item.title,
                                    posterUrl = item.poster_url,
                                    year = item.year,
                                    onClick = { onPlayMedia(item.id) },
                                    modifier = Modifier.width(140.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
