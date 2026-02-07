package org.knp.vortex.ui.screens.series

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import org.knp.vortex.ui.components.AppHeader
import org.knp.vortex.ui.theme.DeepBackground
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.PlayArrow
import org.knp.vortex.data.remote.ComicSeriesDetailDto
import org.knp.vortex.data.remote.ComicChapterDto

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ComicSeriesDetailScreen(
    seriesName: String,
    onBack: () -> Unit,
    onPlayChapter: (Long) -> Unit,
    onEditMetadata: (String) -> Unit,
    viewModel: ComicSeriesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var showReadingModeDialog by remember { mutableStateOf(false) }
    var newListName by remember { mutableStateOf("") }

    LaunchedEffect(seriesName) {
        viewModel.loadSeries(seriesName)
    }

    Scaffold(
        topBar = {
            if (uiState.isSelectionMode) {
                // Selection mode top bar
                TopAppBar(
                    title = { Text("${uiState.selectedChapterIds.size} selected", color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, "Clear selection", tint = Color.White)
                        }
                    },
                    actions = {
                        TextButton(onClick = { viewModel.selectAll() }) {
                            Text("Select All", color = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { viewModel.showAddToReadingListDialog() }) {
                            Icon(Icons.Default.Add, "Add to list", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepBackground)
                )
            } else {
                AppHeader(
                    title = uiState.series?.name ?: "Loading...",
                    onBack = onBack,
                    actions = {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier.background(DeepBackground)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Edit Metadata", color = Color.White) },
                                    onClick = {
                                        onEditMetadata(seriesName)
                                        showMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Refresh Metadata", color = Color.White) },
                                    onClick = { 
                                        viewModel.refreshMetadata()
                                        showMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Reading Style", color = Color.White) },
                                    onClick = { 
                                        showReadingModeDialog = true
                                        showMenu = false
                                    }
                                )
                            }
                        }
                    }
                )
            }
        },
        containerColor = DeepBackground
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.error != null) {
                Text(
                    text = uiState.error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                uiState.series?.let { series ->
                    ComicSeriesContent(
                        series = series,
                        serverUrl = uiState.serverUrl,
                        isSelectionMode = uiState.isSelectionMode,
                        selectedIds = uiState.selectedChapterIds,
                        onPlayChapter = { id ->
                            if (uiState.isSelectionMode) {
                                viewModel.toggleChapterSelection(id)
                            } else {
                                onPlayChapter(id)
                            }
                        },
                        onLongPressChapter = { id ->
                            viewModel.toggleChapterSelection(id)
                        }
                    )
                }
            }
        }
    }
        
    // Reading Mode Dialog
    if (showReadingModeDialog) {
        AlertDialog(
            onDismissRequest = { showReadingModeDialog = false },
            title = { Text("Select Reading Style", color = Color.White) },
            text = {
                Column(Modifier.selectableGroup()) {
                    val modes = listOf("Horizontal", "Vertical", "Webtoon")
                    modes.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .selectable(
                                    selected = (mode == uiState.readingMode),
                                    onClick = {
                                        viewModel.setReadingMode(mode)
                                        showReadingModeDialog = false
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (mode == uiState.readingMode),
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary, unselectedColor = Color.LightGray)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(text = mode, style = MaterialTheme.typography.bodyLarge, color = Color.White)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReadingModeDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.primary)
                }
            },
            containerColor = DeepBackground
        )
    }

    // Add to Reading List Dialog
    if (uiState.showReadingListDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideReadingListDialog() },
            title = { Text("Add to Reading List", color = Color.White) },
            text = {
                Column {
                    if (uiState.isLoadingLists) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    } else {
                        // Create new list input
                        OutlinedTextField(
                            value = newListName,
                            onValueChange = { newListName = it },
                            label = { Text("New List Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = MaterialTheme.colorScheme.primary
                            ),
                            trailingIcon = {
                                if (newListName.isNotBlank()) {
                                    IconButton(onClick = {
                                        viewModel.createListAndAddSelected(newListName)
                                        newListName = ""
                                    }) {
                                        Icon(Icons.Default.Add, "Create", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (uiState.readingLists.isEmpty()) {
                            Text("No existing lists", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Text("Or add to existing:", color = Color.Gray, style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            uiState.readingLists.forEach { list ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.addSelectedToReadingList(list.id) }
                                        .padding(vertical = 8.dp),
                                    color = Color.Transparent
                                ) {
                                    Text(list.name, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.hideReadingListDialog() }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.primary)
                }
            },
            containerColor = DeepBackground
        )
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ComicSeriesContent(
    series: ComicSeriesDetailDto,
    serverUrl: String,
    isSelectionMode: Boolean,
    selectedIds: Set<Long>,
    onPlayChapter: (Long) -> Unit,
    onLongPressChapter: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            // Hero Image / Backdrop
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                AsyncImage(
                    model = series.poster_url?.let { url ->
                        if (url.startsWith("/")) "${serverUrl.trimEnd('/')}$url" else url
                    },
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(20.dp),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, DeepBackground),
                                startY = 100f
                            )
                        )
                )
                
                // Poster and Info overlaid
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    AsyncImage(
                        model = series.poster_url?.let { url ->
                            if (url.startsWith("/")) "${serverUrl.trimEnd('/')}$url" else url
                        },
                        contentDescription = null,
                        modifier = Modifier
                            .width(100.dp)
                            .aspectRatio(2f/3f)
                            .padding(end = 16.dp),
                        contentScale = ContentScale.Crop
                    )
                    
                    Column {
                        Text(
                            text = series.name,
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White
                        )
                        if (series.year != null && series.year > 0) {
                            Text(
                                text = "${series.year}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray
                            )
                        }
                        if (!series.genres.isNullOrBlank()) {
                            Text(
                                text = series.genres,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
        
        if (!series.plot.isNullOrBlank()) {
            item {
                Text(
                    text = series.plot,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.LightGray,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        item {
            Text(
                text = "${series.chapter_count} Chapters",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
            )
        }

        items(series.chapters) { chapter ->
            val isSelected = selectedIds.contains(chapter.id)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onPlayChapter(chapter.id) },
                        onLongClick = { onLongPressChapter(chapter.id) }
                    )
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Checkbox if in selection mode
                    if (isSelectionMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onLongPressChapter(chapter.id) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary,
                                uncheckedColor = Color.Gray
                            ),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    
                    AsyncImage(
                        model = chapter.poster_url?.let { url ->
                            if (url.startsWith("/")) "${serverUrl.trimEnd('/')}$url" else url
                        } ?: "${serverUrl.trimEnd('/')}/api/v1/media/${chapter.id}/thumbnail",
                        contentDescription = null,
                        modifier = Modifier
                            .width(60.dp)
                            .aspectRatio(2f/3f)
                            .background(Color.DarkGray),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = chapter.title.takeIf { !it.isBlank() } ?: "Chapter ${chapter.chapter_number ?: "?"}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (chapter.chapter_number != null) {
                            Text(
                                text = "Chapter ${chapter.chapter_number}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.Gray
                            )
                        }
                        if (chapter.year != null && chapter.year > 0) {
                            Text(
                                text = "${chapter.year}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                        if (!chapter.plot.isNullOrBlank()) {
                            Text(
                                text = chapter.plot,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                color = Color.Gray
                            )
                        }
                    }
                    if (!isSelectionMode) {
                        IconButton(onClick = { onPlayChapter(chapter.id) }) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Read")
                        }
                    }
                }
            }
            HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}
