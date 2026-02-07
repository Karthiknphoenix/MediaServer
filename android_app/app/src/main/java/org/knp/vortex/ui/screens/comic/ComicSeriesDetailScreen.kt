package org.knp.vortex.ui.screens.comic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import org.knp.vortex.data.remote.ComicChapterDto
import org.knp.vortex.ui.theme.*

@Composable
fun ComicSeriesDetailScreen(
    onBack: () -> Unit,
    onReadChapter: (Long) -> Unit,
    viewModel: ComicSeriesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    org.knp.vortex.ui.components.GlassyBackground {
        Scaffold(containerColor = Color.Transparent) { _ ->
            Box(modifier = Modifier.fillMaxSize()) {
                if (uiState.isLoading && uiState.seriesDetail == null) {
                    CircularProgressIndicator(
                        color = PrimaryBlue,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else if (uiState.seriesDetail != null) {
                    val detail = uiState.seriesDetail!!
                    
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        item {
                            // Header with poster
                            Box(modifier = Modifier.fillMaxWidth().height(350.dp)) {
                                // Background
                                AsyncImage(
                                    model = detail.poster_url?.let { 
                                        "${uiState.serverUrl.trimEnd('/')}/api/v1/media/${detail.chapters.firstOrNull()?.id}/thumbnail" 
                                    },
                                    contentDescription = "Background",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(SurfaceColor),
                                    contentScale = ContentScale.Crop,
                                    alpha = 0.3f
                                )
                                
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(Color.Transparent, DeepBackground),
                                                startY = 0f, 
                                                endY = 1000f
                                            )
                                        )
                                )

                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(horizontal = 24.dp, vertical = 24.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        // Cover
                                        Card(
                                            shape = RoundedCornerShape(12.dp),
                                            elevation = CardDefaults.cardElevation(12.dp),
                                            modifier = Modifier.width(120.dp).aspectRatio(0.67f)
                                        ) {
                                            val firstChapter = detail.chapters.firstOrNull()
                                            AsyncImage(
                                                model = if (firstChapter != null) 
                                                    "${uiState.serverUrl.trimEnd('/')}/api/v1/media/${firstChapter.id}/thumbnail"
                                                else null,
                                                contentDescription = detail.name,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                        
                                        Spacer(modifier = Modifier.width(16.dp))
                                        
                                        Column {
                                            Text(
                                                text = detail.name,
                                                style = MaterialTheme.typography.headlineMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "${detail.chapter_count} Chapters",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = GrayText
                                            )
                                            
                                            Spacer(modifier = Modifier.height(16.dp))
                                            
                                            // Continue Reading button
                                            Button(
                                                onClick = { 
                                                    detail.chapters.firstOrNull()?.let { 
                                                        onReadChapter(it.id) 
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Icon(Icons.Filled.Book, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Start Reading")
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Chapters",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        items(detail.chapters) { chapter ->
                            ChapterItem(chapter, uiState.serverUrl, onClick = { onReadChapter(chapter.id) })
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                } else if (uiState.error != null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "Error: ${uiState.error}", color = Color.Red)
                    }
                }
                
                // Top Bar
                org.knp.vortex.ui.components.GlassyTopBar(
                    title = "",
                    onBack = onBack,
                    containerColor = Color.Transparent
                )
            }
        }
    }
}

@Composable
fun ChapterItem(chapter: ComicChapterDto, serverUrl: String, onClick: () -> Unit) {
    org.knp.vortex.ui.components.GlassyCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceColor)
            ) {
                AsyncImage(
                    model = "${serverUrl.trimEnd('/')}/api/v1/media/${chapter.id}/thumbnail",
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (chapter.chapter_number != null) 
                        "Chapter ${chapter.chapter_number}" 
                    else 
                        chapter.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (chapter.chapter_number != null) {
                    Text(
                        text = chapter.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = GrayText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            Icon(
                imageVector = Icons.Filled.Book,
                contentDescription = "Read",
                tint = PrimaryBlue,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
