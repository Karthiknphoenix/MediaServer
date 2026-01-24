package com.example.mediaserver.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mediaserver.data.remote.LibraryDto
import com.example.mediaserver.data.remote.MediaItemDto
import com.example.mediaserver.data.remote.SeriesDto
import com.example.mediaserver.ui.components.ModernMediaCard
import com.example.mediaserver.ui.components.SectionHeader
import com.example.mediaserver.ui.theme.*
import kotlinx.coroutines.launch
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.example.mediaserver.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onPlayMedia: (Long) -> Unit,
    onOpenSeries: (String) -> Unit,
    onOpenLibrary: (Long, String, String) -> Unit,  // id, name, type
    onOpenSettings: () -> Unit,
    onManageLibraries: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val pullToRefreshState = rememberPullToRefreshState()
    
    // PIN Dialog state
    var showPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }
    var isSettingPin by remember { mutableStateOf(false) }
    var confirmPin by remember { mutableStateOf("") }
    
    // PIN Dialog
    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { 
                showPinDialog = false
                pinInput = ""
                confirmPin = ""
                pinError = false
                isSettingPin = false
            },
            containerColor = SurfaceColor,
            title = { 
                Text(
                    if (uiState.isUnlocked) "Lock Content" 
                    else if (!uiState.isPinSet) (if (isSettingPin) "Confirm PIN" else "Set PIN")
                    else "Enter PIN",
                    color = Color.White
                ) 
            },
            text = {
                Column {
                    if (uiState.isUnlocked) {
                        Text("Lock hidden content?", color = GrayText)
                    } else {
                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = { 
                                pinInput = it
                                pinError = false
                            },
                            label = { Text(if (!uiState.isPinSet && isSettingPin) "Confirm PIN" else "PIN") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            isError = pinError,
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = PrimaryBlue,
                                unfocusedBorderColor = GrayText,
                                cursorColor = PrimaryBlue,
                                focusedLabelColor = PrimaryBlue,
                                unfocusedLabelColor = GrayText,
                                errorBorderColor = Color.Red
                            )
                        )
                        if (pinError) {
                            Text(
                                if (!uiState.isPinSet) "PINs don't match" else "Incorrect PIN",
                                color = Color.Red,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (uiState.isUnlocked) {
                        viewModel.lock()
                        showPinDialog = false
                    } else if (!uiState.isPinSet) {
                        if (!isSettingPin) {
                            confirmPin = pinInput
                            pinInput = ""
                            isSettingPin = true
                        } else {
                            if (pinInput == confirmPin) {
                                viewModel.setPin(pinInput)
                                viewModel.verifyAndUnlock(pinInput)
                                showPinDialog = false
                                pinInput = ""
                                confirmPin = ""
                                isSettingPin = false
                            } else {
                                pinError = true
                            }
                        }
                    } else {
                        if (viewModel.verifyAndUnlock(pinInput)) {
                            showPinDialog = false
                            pinInput = ""
                        } else {
                            pinError = true
                        }
                    }
                }) {
                    Text(
                        if (uiState.isUnlocked) "Lock" 
                        else if (!uiState.isPinSet && !isSettingPin) "Next"
                        else "Unlock",
                        color = PrimaryBlue
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showPinDialog = false
                    pinInput = ""
                    confirmPin = ""
                    pinError = false
                    isSettingPin = false
                }) {
                    Text("Cancel", color = GrayText)
                }
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = DeepBackground,
                drawerShape = RoundedCornerShape(0.dp), // Full height vertical edge
                modifier = Modifier.width(300.dp)
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .combinedClickable(
                            onClick = { },
                            onLongClick = { showPinDialog = true }
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_vortex_logo),
                        contentDescription = "Logo",
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "VORTEX", 
                        color = PrimaryBlue, 
                        fontWeight = FontWeight.Bold, 
                        style = MaterialTheme.typography.headlineSmall
                    )
                    if (uiState.isUnlocked) {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = "Unlocked",
                            tint = PrimaryBlue,
                            modifier = Modifier.padding(start = 8.dp).size(20.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    "LIBRARIES",
                    style = MaterialTheme.typography.labelMedium,
                    color = GrayText,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                
                uiState.visibleLibraries.forEach { lib ->
                    NavigationDrawerItem(
                        label = { Text(lib.name) },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            onOpenLibrary(lib.id, lib.name, lib.library_type)
                        },
                        icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedContainerColor = Color.Transparent,
                            unselectedTextColor = Color.White,
                            unselectedIconColor = PrimaryBlue
                        ),
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))
                
                Divider(modifier = Modifier.padding(horizontal = 24.dp), color = SurfaceColor)
                
                NavigationDrawerItem(
                    label = { Text("Manage Libraries") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onManageLibraries()
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        unselectedTextColor = Color.White,
                        unselectedIconColor = Color.Gray
                    ),
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    label = { Text("Settings") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onOpenSettings()
                    },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        unselectedTextColor = Color.White,
                        unselectedIconColor = Color.Gray
                    ),
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    ) {
        Scaffold(
            containerColor = DeepBackground,
            topBar = {
                com.example.mediaserver.ui.components.AppHeader(
                    onMenuClick = { scope.launch { drawerState.open() } }
                )
            }
        ) { padding ->
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.loadData(true) },
                modifier = Modifier.fillMaxSize().padding(padding),
                state = pullToRefreshState
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        color = PrimaryBlue,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else if (uiState.error != null) {
                    Text(
                        text = "Error: ${uiState.error}\nIs the backend running?",
                        color = ErrorRed,
                        modifier = Modifier.align(Alignment.Center)
                    ) 
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // Featured Carousel
                        item {
                            val featuredItems = (uiState.allSeries.take(5) + uiState.recentlyAdded.take(3)).shuffled().take(5)
                            if (featuredItems.isNotEmpty()) {
                                FeaturedCarousel(
                                    items = featuredItems,
                                    onItemClick = { item ->
                                        when (item) {
                                            is SeriesDto -> onOpenSeries(item.name)
                                            is MediaItemDto -> {
                                                if (item.media_type == "series") {
                                                    onOpenSeries(item.series_name ?: item.title ?: "")
                                                } else {
                                                    onPlayMedia(item.id)
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }

                        // Continue Watching
                        if (uiState.continueWatching.isNotEmpty()) {
                            item {
                                SectionHeader("Continue Watching")
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 24.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(uiState.continueWatching) { item ->
                                        ModernMediaCard(
                                            title = item.title,
                                            posterUrl = item.poster_url,
                                            year = item.year,
                                            onClick = {
                                                if (item.media_type == "series") {
                                                    onOpenSeries(item.series_name ?: item.title ?: "")
                                                } else {
                                                    onPlayMedia(item.id)
                                                }
                                            },
                                            modifier = Modifier.width(160.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Dynamic Library Rows (each library gets its own row)
                        uiState.visibleLibraries.forEach { library ->
                            if (library.library_type == "tv_shows") {
                                // TV Shows library - use SeriesDto content
                                val seriesContent = uiState.tvShowLibraryContent[library.id] ?: emptyList()
                                if (seriesContent.isNotEmpty()) {
                                    item {
                                        SectionHeader(library.name)
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 24.dp),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            items(seriesContent) { series ->
                                                ModernMediaCard(
                                                    title = series.name,
                                                    posterUrl = series.poster_url,
                                                    onClick = { onOpenSeries(series.name) },
                                                    modifier = Modifier.width(140.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Movies and other libraries - use MediaItemDto content
                                val content = uiState.libraryContent[library.id] ?: emptyList()
                                if (content.isNotEmpty()) {
                                    item {
                                        SectionHeader(library.name)
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 24.dp),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            items(content) { item ->
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
                          // Recently Added
                        item {
                            SectionHeader("Recently Added")
                            if (uiState.recentlyAdded.isNotEmpty()) {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 24.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(uiState.recentlyAdded) { item ->
                                        ModernMediaCard(
                                            title = item.title,
                                            posterUrl = item.poster_url,
                                            year = item.year,
                                            onClick = {
                                                if (item.media_type == "series") {
                                                    onOpenSeries(item.series_name ?: item.title ?: "")
                                                } else {
                                                    onPlayMedia(item.id)
                                                }
                                            },
                                            modifier = Modifier.width(140.dp)
                                        )
                                    }
                                }
                            } else {
                                 Text(
                                    "No recent media found.",
                                    color = GrayText,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FeaturedCarousel(
    items: List<Any>,
    onItemClick: (Any) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { items.size })
    
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentPadding = PaddingValues(horizontal = 24.dp),
            pageSpacing = 16.dp
        ) { page ->
            val item = items[page]
            val (title, imageUrl) = when (item) {
                is SeriesDto -> item.name to item.poster_url
                is MediaItemDto -> (item.title ?: "Unknown") to item.poster_url
                else -> "Unknown" to null
            }
            
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onItemClick(item) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceColor)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    // Gradient overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, DeepBackground.copy(alpha = 0.9f)),
                                    startY = 100f
                                )
                            )
                    )
                    
                    // Title
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    )
                }
            }
        }
        
        // Page indicators
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(items.size) { index ->
                val color = if (pagerState.currentPage == index) PrimaryBlue else GrayText
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(color)
                )
            }
        }
    }
}
