package org.knp.vortex.ui.screens.book

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import org.knp.vortex.ui.components.GlassyTopBar
import org.knp.vortex.ui.theme.DeepBackground
import org.knp.vortex.ui.theme.PrimaryBlue
import org.knp.vortex.ui.theme.SurfaceColor
import org.knp.vortex.ui.theme.GrayText

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    mediaId: Long,
    onRead: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: BookDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(mediaId) {
        viewModel.loadMedia(mediaId)
    }

    org.knp.vortex.ui.components.GlassyBackground {
        Scaffold(containerColor = Color.Transparent) { _ ->
            Box(modifier = Modifier.fillMaxSize()) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = PrimaryBlue
                    )
                } else if (uiState.media != null) {
                    val media = uiState.media!!
                    
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                    ) {
                        // Header Section
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(400.dp)
                        ) {
                            // Blurred Backdrop
                            AsyncImage(
                                model = media.poster_url?.let { url ->
                                    if (url.startsWith("/")) "${uiState.serverUrl.trimEnd('/')}$url" else url
                                },
                                contentDescription = "Background",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(SurfaceColor),
                                contentScale = ContentScale.Crop,
                                alpha = 0.3f
                            )
                            
                            // Specific gradient for books
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(DeepBackground.copy(alpha=0.5f), DeepBackground),
                                            startY = 0f, 
                                            endY = 1200f
                                        )
                                    )
                            )
                            
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                // Poster Card
                                Card(
                                    shape = RoundedCornerShape(8.dp),
                                    elevation = CardDefaults.cardElevation(12.dp),
                                    modifier = Modifier.width(160.dp).aspectRatio(0.67f)
                                ) {
                                    AsyncImage(
                                        model = media.poster_url?.let { url ->
                                            if (url.startsWith("/")) "${uiState.serverUrl.trimEnd('/')}$url" else url
                                        },
                                        contentDescription = media.title,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(24.dp))
                                
                                Text(
                                    text = media.title ?: "Unknown Title",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 24.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }

                        // Content (Button + Info)
                        Column(
                             modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .offset(y = (-20).dp), // Overlap
                             horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Read Button
                            Button(
                                onClick = { onRead(mediaId) },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(56.dp)
                            ) {
                                // Icon(androidx.compose.material.icons.filled.Book, contentDescription = null) // Book icon might not exist in default set
                                Icon(androidx.compose.material.icons.Icons.Filled.Book, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Read Now", fontSize = 18.sp)
                            }
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            // Info
                            if (!media.plot.isNullOrEmpty()) {
                                Text(
                                    text = "Description",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.align(Alignment.Start)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = media.plot,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = GrayText,
                                    lineHeight = 24.sp,
                                    modifier = Modifier.align(Alignment.Start)
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                            
                             // File Info
                            org.knp.vortex.ui.components.GlassyCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "File Information",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = media.file_path.substringAfterLast("/").substringAfterLast("\\"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = GrayText,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            // Reading Lists Section
                            Text(
                                text = "Reading Lists",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.align(Alignment.Start)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            if (uiState.readingLists.isEmpty()) {
                                org.knp.vortex.ui.components.GlassyCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "No reading lists yet",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.Gray
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        TextButton(onClick = { viewModel.showAddToReadingListDialog() }) {
                                            Text("Create your first list", color = PrimaryBlue)
                                        }
                                    }
                                }
                            } else {
                                uiState.readingLists.forEach { list ->
                                    org.knp.vortex.ui.components.GlassyCard(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { viewModel.addToReadingList(list.id) }
                                                .padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = list.name,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = Color.White,
                                                modifier = Modifier.weight(1f)
                                            )
                                            TextButton(onClick = { viewModel.addToReadingList(list.id) }) {
                                                Text("+ Add", color = PrimaryBlue)
                                            }
                                        }
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                    
                    // Top Bar
                    org.knp.vortex.ui.components.GlassyTopBar(
                        title = "",
                        onBack = onBack,
                        containerColor = Color.Transparent,
                        actions = {
                            Box {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false },
                                    modifier = Modifier.background(SurfaceColor)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Add to Reading List", color = Color.White) }, 
                                        onClick = {
                                            viewModel.showAddToReadingListDialog()
                                            showMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    )
                } else if (uiState.error != null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "Error: ${uiState.error}", color = Color.Red)
                    }
                }
            }
        }
    }
    
    // Add to Reading List Dialog
    var newListName by remember { mutableStateOf("") }
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
                                focusedBorderColor = PrimaryBlue
                            ),
                            trailingIcon = {
                                if (newListName.isNotBlank()) {
                                    IconButton(onClick = {
                                        viewModel.createListAndAdd(newListName)
                                        newListName = ""
                                    }) {
                                        Icon(Icons.Default.Add, "Create", tint = PrimaryBlue)
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
                                        .clickable { viewModel.addToReadingList(list.id) }
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
                    Text("Cancel", color = PrimaryBlue)
                }
            },
            containerColor = DeepBackground
        )
    }
}

