package org.knp.vortex.ui.screens.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import org.knp.vortex.ui.theme.BlackBackground
import org.knp.vortex.ui.theme.SurfaceColor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

@Composable
fun BookReaderScreen(
    mediaId: Long,
    playlist: List<Long> = emptyList(),
    currentIndex: Int = 0,
    initialReadingMode: String = "Horizontal",
    onBack: () -> Unit,
    onNext: ((Long) -> Unit)? = null,
    viewModel: BookReaderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(mediaId, initialReadingMode) {
        viewModel.loadBook(mediaId, initialReadingMode)
    }

    // Hide system bars (immersive) - Implementation depends on activity, skipping for now as pure compose

    BackHandler {
        onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BlackBackground)
            .pointerInput(Unit) {
                val currentSize = size
                detectTapGestures(
                    onTap = { offset ->
                        val width = currentSize.width
                        val height = currentSize.height
                        
                        // Center tap toggles menu
                        if (offset.x > width * 0.3f && offset.x < width * 0.7f && offset.y > height * 0.3f && offset.y < height * 0.7f) {
                            viewModel.toggleMenu()
                        } else {
                            // Left/Right tap for navigation
                            if (offset.x < width * 0.3f) {
                                viewModel.prevPage()
                            } else if (offset.x > width * 0.7f) {
                                // Check if on last page and there's a next chapter
                                if (viewModel.isOnLastPage() && playlist.isNotEmpty() && currentIndex < playlist.size - 1) {
                                    val nextMediaId = playlist[currentIndex + 1]
                                    onNext?.invoke(nextMediaId)
                                } else {
                                    viewModel.nextPage()
                                }
                            }
                        }
                    }
                )
            }
    ) {
        if (uiState.isLoading) {
             CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
        } else if (uiState.pages.isNotEmpty()) {
            // Key to force recreation when mode changes between pager modes
            // Key to force recreation when mode changes between pager modes
            key(uiState.readingMode) {
                val hasNextChapter = playlist.isNotEmpty() && currentIndex < playlist.size - 1
                
                if (uiState.readingMode == ReadingMode.Horizontal || uiState.readingMode == ReadingMode.Vertical) {
                    val pagerPageCount = uiState.pages.size + (if (hasNextChapter) 1 else 0)
                    val pagerState = rememberPagerState(pageCount = { pagerPageCount })
                    
                    // Sync Logic for pager modes
                    LaunchedEffect(pagerState, viewModel) {
                        snapshotFlow { pagerState.currentPage }.collect { page ->
                            if (page < uiState.pages.size) {
                                viewModel.setPage(page)
                            }
                        }
                    }
                    
                    LaunchedEffect(uiState.currentPageIndex) {
                         if (pagerState.currentPage != uiState.currentPageIndex && uiState.currentPageIndex < uiState.pages.size) {
                             pagerState.scrollToPage(uiState.currentPageIndex)
                         }
                    }
                    
                    if (uiState.readingMode == ReadingMode.Horizontal) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            if (page < uiState.pages.size) {
                                val pageData = uiState.pages[page]
                                ReaderPage(
                                    serverUrl = uiState.serverUrl,
                                    mediaId = mediaId,
                                    pageIndex = pageData.index
                                )
                            } else {
                                // Next Chapter page - user must tap to proceed
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable {
                                            val nextMediaId = playlist[currentIndex + 1]
                                            onNext?.invoke(nextMediaId)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Tap for Next Chapter", color = Color.White, style = MaterialTheme.typography.titleLarge)
                                }
                            }
                        }
                    } else {
                        // Vertical mode
                        VerticalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            if (page < uiState.pages.size) {
                                val pageData = uiState.pages[page]
                                ReaderPage(
                                    serverUrl = uiState.serverUrl,
                                    mediaId = mediaId,
                                    pageIndex = pageData.index
                                )
                            } else {
                                // Next Chapter page - user must tap to proceed
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable {
                                            val nextMediaId = playlist[currentIndex + 1]
                                            onNext?.invoke(nextMediaId)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Tap for Next Chapter", color = Color.White, style = MaterialTheme.typography.titleLarge)
                                }
                            }
                        }
                    }
                } else {
                    // Webtoon mode - Continuous Vertical Scroll
                    val listState = rememberLazyListState()
                    
                    // Sync scroll position with page number
                    LaunchedEffect(listState) {
                        snapshotFlow { listState.firstVisibleItemIndex }.collect { index ->
                            if (index < uiState.pages.size) {
                                viewModel.setPage(index)
                            }
                        }
                    }

                    // Sync ViewModel page change to scroll position (initial load or slider)
                    LaunchedEffect(uiState.currentPageIndex) {
                        if (listState.firstVisibleItemIndex != uiState.currentPageIndex) {
                             // Only scroll if difference is significant to avoid jitter during scroll
                             if (Math.abs(listState.firstVisibleItemIndex - uiState.currentPageIndex) > 1) {
                                 // Check bounds
                                 if (uiState.currentPageIndex < uiState.pages.size) {
                                     listState.scrollToItem(uiState.currentPageIndex)
                                 }
                             }
                        }
                    }
                    
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.pages) { pageData ->
                            ReaderPage(
                                serverUrl = uiState.serverUrl,
                                mediaId = mediaId,
                                pageIndex = pageData.index,
                                fitScreen = false // Webtoons are usually long strips or fit width
                            )
                        }
                        
                        // Bottom item: Next Chapter button or spacer
                        item {
                            if (hasNextChapter) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp)
                                        .clickable {
                                            val nextMediaId = playlist[currentIndex + 1]
                                            onNext?.invoke(nextMediaId)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Tap for Next Chapter", color = Color.White, style = MaterialTheme.typography.titleMedium)
                                }
                            } else {
                                Spacer(modifier = Modifier.height(100.dp))
                            }
                        }
                    }
                }
            }
        } else {
             // Fallback for PDF (Placeholder)
             Box(modifier = Modifier.align(Alignment.Center)) {
                 Text("Format not supported for in-app reading yet or empty.", color = Color.White)
             }
        }
        
        // Overlays
        if (uiState.isMenuVisible) {
            // Top Bar
            Surface(
                color = SurfaceColor.copy(alpha = 0.9f),
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
            ) {
                Row(
                   modifier = Modifier.padding(16.dp),
                   verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Text(
                        text = uiState.media?.title ?: "Reading",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    IconButton(
                        onClick = { 
                            // Cycle reading modes for demo
                            val newMode = when(uiState.readingMode) {
                                ReadingMode.Horizontal -> ReadingMode.Vertical
                                ReadingMode.Vertical -> ReadingMode.Webtoon
                                ReadingMode.Webtoon -> ReadingMode.Horizontal
                            }
                            viewModel.setReadingMode(newMode)
                        }
                    ) {
                        // Icon based on mode
                        Text(
                            text = when(uiState.readingMode) {
                                ReadingMode.Horizontal -> "H"
                                ReadingMode.Vertical -> "V"
                                ReadingMode.Webtoon -> "W"
                            },
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }
            
            // Bottom Bar (Progress)
             Surface(
                color = SurfaceColor.copy(alpha = 0.8f),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = "${uiState.currentPageIndex + 1} / ${uiState.pages.size}",
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    if (uiState.pages.isNotEmpty()) {
                        Slider(
                             value = uiState.currentPageIndex.toFloat(),
                             onValueChange = { viewModel.setPage(it.toInt()) },
                             valueRange = 0f..(uiState.pages.size - 1).toFloat(),
                             colors = SliderDefaults.colors(
                                 thumbColor = Color.White,
                                 activeTrackColor = Color.White,
                                 inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                             ),
                             modifier = Modifier.height(24.dp)
                        )
                    }
                    
                    // Next Chapter button when on last page
                    if (viewModel.isOnLastPage() && playlist.isNotEmpty() && currentIndex < playlist.size - 1) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val nextMediaId = playlist[currentIndex + 1]
                                onNext?.invoke(nextMediaId)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.2f)
                            )
                        ) {
                            Text("Next Chapter →", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReaderPage(
    serverUrl: String,
    mediaId: Long,
    pageIndex: Int,
    fitScreen: Boolean = true
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val imageUrl = remember(serverUrl, mediaId, pageIndex) {
         "${serverUrl.trimEnd('/')}/api/v1/media/$mediaId/page/$pageIndex"
    }

    if (fitScreen) {
        // Pager modes (Horizontal/Vertical) — fill the screen, single page at a time
        val model = ImageRequest.Builder(context)
            .data(imageUrl)
            .crossfade(true)
            .build()

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = model,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    } else {
        // Webtoon mode — continuous vertical scroll in LazyColumn

        val model = ImageRequest.Builder(context)
            .data(imageUrl)
            .crossfade(true)
            .size(coil.size.Size.ORIGINAL)
            .allowHardware(false)
            .build()

        SubcomposeAsyncImage(
            model = model,
            contentDescription = null,
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.FillWidth,
            loading = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            },
            error = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Failed to load page ${pageIndex + 1}", color = Color.White.copy(alpha = 0.5f))
                }
            }
        )
    }
}
