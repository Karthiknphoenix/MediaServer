package org.knp.vortex.ui.screens.readinglist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.knp.vortex.ui.components.AppHeader
import org.knp.vortex.ui.theme.DeepBackground

@Composable
fun ReadingListsScreen(
    listId: Long? = null,
    onBack: () -> Unit,
    onPlayMedia: (mediaId: Long, playlist: List<Long>, index: Int, readingMode: String) -> Unit,
    viewModel: ReadingListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(listId) {
        if (listId != null) {
            viewModel.loadListDetails(listId)
        } else {
            viewModel.loadLists()
        }
    }

    Scaffold(
        topBar = {
            var showMenu by remember { mutableStateOf(false) }
            
            AppHeader(
                title = uiState.selectedList?.name ?: "Reading Lists",
                onBack = {
                    if (uiState.selectedList != null) {
                        viewModel.clearSelectedList()
                    } else {
                        onBack()
                    }
                },
                actions = {
                    if (uiState.selectedList != null) {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More options",
                                    tint = Color.White
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                Text(
                                    "Reading Style",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                                DropdownMenuItem(
                                    text = { Text("Horizontal") },
                                    onClick = { 
                                        viewModel.setReadingMode("Horizontal")
                                        showMenu = false 
                                    },
                                    leadingIcon = {
                                        if (uiState.readingMode == "Horizontal") {
                                            Text("✓")
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Vertical") },
                                    onClick = { 
                                        viewModel.setReadingMode("Vertical")
                                        showMenu = false 
                                    },
                                    leadingIcon = {
                                        if (uiState.readingMode == "Vertical") {
                                            Text("✓")
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Webtoon") },
                                    onClick = { 
                                        viewModel.setReadingMode("Webtoon")
                                        showMenu = false 
                                    },
                                    leadingIcon = {
                                        if (uiState.readingMode == "Webtoon") {
                                            Text("✓")
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        containerColor = DeepBackground
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.error != null -> {
                    Text(
                        text = uiState.error!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.selectedList != null -> {
                    // Show list items
                    val list = uiState.selectedList!!
                    if (list.items.isEmpty()) {
                        Text(
                            text = "This list is empty",
                            color = Color.Gray,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp)
                        ) {
                            val playlist = list.items.map { it.media_id }
                            items(list.items.size) { index ->
                                val item = list.items[index]
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color.DarkGray.copy(alpha = 0.3f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable { onPlayMedia(item.media_id, playlist, index, uiState.readingMode) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.title ?: "Untitled",
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    // Show all lists
                    if (uiState.lists.isEmpty()) {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No reading lists yet",
                                color = Color.Gray,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Long-press chapters in a comic series to add them to a list",
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp)
                        ) {
                            items(uiState.lists) { list ->
                                var showDeleteDialog by remember { mutableStateOf(false) }
                                
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color.DarkGray.copy(alpha = 0.3f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable { viewModel.loadListDetails(list.id) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = list.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(onClick = { showDeleteDialog = true }) {
                                            Icon(Icons.Default.Delete, "Delete", tint = Color.Gray)
                                        }
                                    }
                                }
                                
                                if (showDeleteDialog) {
                                    AlertDialog(
                                        onDismissRequest = { showDeleteDialog = false },
                                        title = { Text("Delete List?", color = Color.White) },
                                        text = { Text("Are you sure you want to delete \"${list.name}\"?", color = Color.LightGray) },
                                        confirmButton = {
                                            TextButton(onClick = {
                                                viewModel.deleteList(list.id)
                                                showDeleteDialog = false
                                            }) {
                                                Text("Delete", color = MaterialTheme.colorScheme.error)
                                            }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { showDeleteDialog = false }) {
                                                Text("Cancel", color = MaterialTheme.colorScheme.primary)
                                            }
                                        },
                                        containerColor = DeepBackground
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
