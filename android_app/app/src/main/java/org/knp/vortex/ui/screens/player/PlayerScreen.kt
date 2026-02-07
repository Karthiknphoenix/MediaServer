package org.knp.vortex.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.OkHttpClient
import org.knp.vortex.data.repository.MediaRepository
import org.knp.vortex.data.repository.SettingsRepository
import org.knp.vortex.utils.findActivity

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
            }.onFailure {
                onResult(0L)
            }
        }
    }

    fun getSubtitles(id: Long, onResult: (List<org.knp.vortex.data.remote.SubtitleTrackDto>) -> Unit) {
        viewModelScope.launch {
            repository.getSubtitles(id).onSuccess { 
                onResult(it) 
            }
        }
    }

    fun saveProgress(id: Long, position: Long, total: Long) {
        viewModelScope.launch {
            repository.updateProgress(id, position, total)
        }
    }
}

@Composable
fun PlayerScreen(
    mediaId: Long,
    playlist: List<Long> = emptyList(),
    currentIndex: Int = 0,
    onBack: () -> Unit,
    onPlayNext: ((Long) -> Unit)? = null,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var savedPosition by remember { mutableStateOf(0L) }
    var isReady by remember { mutableStateOf(false) }
    var subtitles by remember { mutableStateOf<List<org.knp.vortex.data.remote.SubtitleTrackDto>>(emptyList()) }

    LaunchedEffect(mediaId) {
        viewModel.getSubtitles(mediaId) { subs ->
            subtitles = subs
        }
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

    // Gesture State
    var volumeLevel by remember { mutableStateOf(0f) }
    var brightnessLevel by remember { mutableStateOf(0.5f) }
    var showVolumeIndicator by remember { mutableStateOf(false) }
    var showBrightnessIndicator by remember { mutableStateOf(false) }

    // Helpers for Volume/Brightness
    val audioManager = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC) }
    
    LaunchedEffect(Unit) {
        val currentVol = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        volumeLevel = currentVol.toFloat() / maxVolume
        
        val window = context.findActivity()?.window
        brightnessLevel = window?.attributes?.screenBrightness?.takeIf { it >= 0 } ?: 0.5f
    }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    val exoPlayer = remember {
        val dataSourceFactory = DefaultDataSource.Factory(
            context,
            OkHttpDataSource.Factory(viewModel.callFactory)
        )
        
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(dataSourceFactory)
            )
            .build()
            .apply {
                val baseUrl = viewModel.getServerUrl().trimEnd('/')
                val mediaUrl = "$baseUrl/api/v1/stream/$mediaId"
                
                val mediaItemBuilder = MediaItem.Builder()
                    .setUri(mediaUrl)
                
                val subtitleConfigs = subtitles.map { sub ->
                    val mimeType = if (sub.url.endsWith(".vtt")) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP
                    val subUrl = "$baseUrl${sub.url}"
                    MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(subUrl))
                        .setMimeType(mimeType)
                        .setLanguage(sub.language)
                        .setLabel(sub.label)
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT) 
                        .build()
                }
                
                if (subtitleConfigs.isNotEmpty()) {
                    mediaItemBuilder.setSubtitleConfigurations(subtitleConfigs)
                }

                setMediaItem(mediaItemBuilder.build())
                
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        errorMessage = "Error: ${error.message}\nCode: ${error.errorCodeName}"
                    }
                    
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            // Auto-play next item in playlist
                            if (playlist.isNotEmpty() && currentIndex < playlist.size - 1) {
                                val nextIndex = currentIndex + 1
                                val nextMediaId = playlist[nextIndex]
                                onPlayNext?.invoke(nextMediaId)
                            }
                        }
                    }
                })

                prepare()
                if (savedPosition > 0) seekTo(savedPosition * 1000)
                playWhenReady = true
        }
    }
    
    LaunchedEffect(exoPlayer) {
        while(true) {
            delay(5000)
            if (exoPlayer.isPlaying) {
                viewModel.saveProgress(mediaId, exoPlayer.currentPosition / 1000, exoPlayer.duration / 1000)
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    var isFullscreen by rememberSaveable { mutableStateOf(false) }

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
    
    val activity = context.findActivity()
    val window = activity?.window

    DisposableEffect(isFullscreen) {
        if (activity != null && window != null) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            
            if (isFullscreen) {
                activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                WindowCompat.setDecorFitsSystemWindows(window, false)
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                WindowCompat.setDecorFitsSystemWindows(window, true)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }

        onDispose {
             if (activity != null && window != null) {
                 activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                 WindowCompat.setDecorFitsSystemWindows(window, true)
                 WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
             }
        }
    }

    AndroidView(
        factory = {
            PlayerView(context).apply {
                player = exoPlayer
                setShowSubtitleButton(true)
                setFullscreenButtonClickListener {
                    isFullscreen = !isFullscreen
                }
                controllerShowTimeoutMs = 3000
                keepScreenOn = true

                val gestureDetector = android.view.GestureDetector(context, object : android.view.GestureDetector.SimpleOnGestureListener() {
                    override fun onDown(e: android.view.MotionEvent): Boolean = true

                    override fun onSingleTapConfirmed(e: android.view.MotionEvent): Boolean {
                        if (isControllerFullyVisible) hideController() else showController()
                        return true
                    }

                    override fun onDoubleTap(e: android.view.MotionEvent): Boolean {
                        val width = width.toFloat()
                        val x = e.x
                        val currentPos = player?.currentPosition ?: 0L
                        val duration = player?.duration ?: 0L
                        
                        if (x > width / 2) {
                            val newPos = (currentPos + 10000).coerceAtMost(duration)
                            player?.seekTo(newPos)
                        } else {
                            val newPos = (currentPos - 10000).coerceAtLeast(0L)
                            player?.seekTo(newPos)
                        }
                        return true
                    }

                    override fun onScroll(
                        e1: android.view.MotionEvent?,
                        e2: android.view.MotionEvent,
                        distanceX: Float,
                        distanceY: Float
                    ): Boolean {
                        if (e1 == null) return false
                        val width = width.toFloat()
                        val height = height.toFloat()
                        val x = e1.x
                        
                        if (kotlin.math.abs(distanceY) > kotlin.math.abs(distanceX)) {
                            val delta = distanceY / height 
                            
                            if (x < width / 2) {
                                showBrightnessIndicator = true
                                showVolumeIndicator = false
                                val scrollWindow = context.findActivity()?.window
                                if (scrollWindow != null) {
                                    val lp = scrollWindow.attributes
                                    val newBrightness = (brightnessLevel + delta).coerceIn(0.01f, 1f)
                                    lp.screenBrightness = newBrightness
                                    scrollWindow.attributes = lp
                                    brightnessLevel = newBrightness
                                }
                            } else {
                                showVolumeIndicator = true
                                showBrightnessIndicator = false
                                val newVol = (volumeLevel + delta).coerceIn(0f, 1f)
                                volumeLevel = newVol
                                val volIndex = (newVol * maxVolume).toInt()
                                audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, volIndex, 0)
                            }
                            return true
                        }
                        return false
                    }
                })

                setOnTouchListener { _, event ->
                    gestureDetector.onTouchEvent(event)
                }
            }
        },
        modifier = Modifier.fillMaxSize().background(Color.Black)
    )
    
    Box(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = { 
                    exoPlayer.pause()
                    onBack() 
                }
            ) {
                 Icon(
                     imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                     contentDescription = "Back",
                     tint = Color.White
                 )
            }
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
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        
        if (showVolumeIndicator || showBrightnessIndicator) {
             LaunchedEffect(showVolumeIndicator, showBrightnessIndicator) {
                 delay(1500)
                 showVolumeIndicator = false
                 showBrightnessIndicator = false
             }
             
             Box(
                 modifier = Modifier.align(Alignment.Center)
                     .background(Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(16.dp))
                     .padding(24.dp)
             ) {
                 Column(
                     horizontalAlignment = Alignment.CenterHorizontally
                 ) {
                     Icon(
                         imageVector = if (showVolumeIndicator) Icons.AutoMirrored.Filled.VolumeUp else Icons.Filled.BrightnessMedium,
                         contentDescription = null,
                         tint = Color.White,
                         modifier = Modifier.size(48.dp)
                     )
                     Spacer(Modifier.height(16.dp))
                     LinearProgressIndicator(
                         progress = { if (showVolumeIndicator) volumeLevel else brightnessLevel },
                         modifier = Modifier.width(120.dp),
                         color = Color.White,
                         trackColor = Color.White.copy(alpha = 0.3f),
                     )
                 }
             }
        }
    }
}
