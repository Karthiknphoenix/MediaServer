package com.example.mediaserver.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.mediaserver.data.repository.MediaRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.hilt.navigation.compose.hiltViewModel

// Note: In a real app, inject Repo via ViewModel. Using direct logic here for brevity if simple service.
// But better to use ViewModel. 

// Quick ViewModel for Player

import com.example.mediaserver.data.repository.SettingsRepository
import okhttp3.Call
import okhttp3.OkHttpClient

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val settingsRepository: SettingsRepository,
    private val okHttpClient: OkHttpClient
) : ViewModel() {
    
    val callFactory: Call.Factory get() = okHttpClient
    
    fun getServerUrl(): String = settingsRepository.getServerUrl()
    fun getProgress(id: Long, onResult: (Long) -> Unit) {
        viewModelScope.launch {
            repository.getProgress(id).onSuccess { 
                onResult(it.position) 
            }
        }
    }

    fun saveProgress(id: Long, position: Long, total: Long) {
        viewModelScope.launch {
             // Simple throttling could be added here
            repository.updateProgress(id, position, total)
        }
    }
}

// Need to pass VM via Hilt


@Composable
fun PlayerScreen(
    mediaId: Long,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var savedPosition by remember { mutableStateOf(0L) }
    var isReady by remember { mutableStateOf(false) }

    LaunchedEffect(mediaId) {
        viewModel.getProgress(mediaId) { pos ->
            savedPosition = pos
            isReady = true
        }
    }

    if (!isReady) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("Loading...", color = Color.White)
        }
        return
    }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    val exoPlayer = remember {
        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(
            context,
            androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(viewModel.callFactory)
        )
        
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)
            )
            .build()
            .apply {
                // Dynamic Media URL from Settings
                val baseUrl = viewModel.getServerUrl().trimEnd('/')
                val mediaUrl = "$baseUrl/api/v1/stream/$mediaId"
                setMediaItem(MediaItem.fromUri(mediaUrl))
                
                addListener(object : androidx.media3.common.Player.Listener {
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        errorMessage = "Error: ${error.message}\nCode: ${error.errorCodeName}"
                    }
                })

                prepare()
                if (savedPosition > 0) seekTo(savedPosition * 1000)
                playWhenReady = true
        }
    }
    
    // Auto-save Progress
    LaunchedEffect(exoPlayer) {
        while(true) {
            delay(5000)
            if (exoPlayer.isPlaying) {
                 // DB expects seconds usually, Exo uses ms
                viewModel.saveProgress(mediaId, exoPlayer.currentPosition / 1000, exoPlayer.duration / 1000)
            }
        }
    }

    // Lifecycle handling
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                exoPlayer.pause()
                viewModel.saveProgress(mediaId, exoPlayer.currentPosition / 1000, exoPlayer.duration / 1000)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = {
            PlayerView(context).apply {
                player = exoPlayer
                // Hide controller timeout to allow back button visibility if custom UI
            }
        },
        modifier = Modifier.fillMaxSize().background(Color.Black)
    )
    
    // Simple Back Button Overlay
    Box(Modifier.fillMaxSize()) {
        androidx.compose.material3.IconButton(
            onClick = { 
                exoPlayer.pause()
                onBack() 
            },
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
        ) {
             androidx.compose.material3.Icon(
                 imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                 contentDescription = "Back",
                 tint = Color.White
             )
        }

        if (errorMessage != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(16.dp)
            ) {
                Text(
                    text = errorMessage!!,
                    color = Color.Red,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}
