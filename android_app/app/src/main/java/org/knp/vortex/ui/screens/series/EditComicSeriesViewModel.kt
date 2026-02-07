package org.knp.vortex.ui.screens.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.knp.vortex.data.remote.UpdateSeriesMetadataRequest
import org.knp.vortex.data.repository.MediaRepository
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import javax.inject.Inject

data class EditSeriesUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
    
    // Form fields
    val name: String = "",
    val posterUrl: String = "",
    val backdropUrl: String = "",
    val plot: String = "",
    val year: String = "",
    val genres: String = ""
)

@HiltViewModel
class EditComicSeriesViewModel @Inject constructor(
    private val repository: MediaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditSeriesUiState())
    val uiState = _uiState.asStateFlow()

    fun loadSeries(name: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, name = name)
            
            repository.getComicSeriesDetail(name)
                .onSuccess { series ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        posterUrl = series.poster_url ?: "",
                        backdropUrl = series.backdrop_url ?: "",
                        plot = series.plot ?: "",
                        year = series.year?.toString() ?: "",
                        genres = series.genres ?: ""
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                }
        }
    }

    fun updateField(
        posterUrl: String? = null,
        plot: String? = null,
        year: String? = null,
        genres: String? = null
    ) {
        _uiState.value = _uiState.value.copy(
            posterUrl = posterUrl ?: _uiState.value.posterUrl,
            plot = plot ?: _uiState.value.plot,
            year = year ?: _uiState.value.year,
            genres = genres ?: _uiState.value.genres
        )
    }

    fun saveChanges(context: android.content.Context) {
        if (_uiState.value.isSaving) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            
            try {
                // Prepare RequestBody parts
                val plotPart = _uiState.value.plot.takeIf { it.isNotBlank() }
                    ?.let { okhttp3.RequestBody.create("text/plain".toMediaTypeOrNull(), it) }
                val yearPart = _uiState.value.year.takeIf { it.isNotBlank() }
                    ?.let { okhttp3.RequestBody.create("text/plain".toMediaTypeOrNull(), it) }
                val genresPart = _uiState.value.genres.takeIf { it.isNotBlank() }
                    ?.let { okhttp3.RequestBody.create("text/plain".toMediaTypeOrNull(), it) }
                
                var posterPart: okhttp3.MultipartBody.Part? = null
                val posterUrl = _uiState.value.posterUrl
                
                if (posterUrl.isNotBlank()) {
                    if (posterUrl.startsWith("content://")) {
                        // It's a URI, resolve it
                        val uri = android.net.Uri.parse(posterUrl)
                        val inputStream = context.contentResolver.openInputStream(uri)
                        val bytes = inputStream?.readBytes()
                        inputStream?.close()
                        
                        if (bytes != null) {
                            val requestFile = okhttp3.RequestBody.create("image/*".toMediaTypeOrNull(), bytes)
                            posterPart = okhttp3.MultipartBody.Part.createFormData("poster", "poster.jpg", requestFile)
                        }
                    } else {
                        // It's a text URL (or existing value), pass as text part if API supported it?
                        // My API handles "poster" (file) or "poster_url" (text).
                        // If I didn't change the poster, I shouldn't send anything if I want to keep existing?
                        // But my API updates only if `poster_url` field is sent.
                        // Wait, my API Logic:
                        // "poster" -> saves file -> updates poster_url
                        // "poster_url" -> updates poster_url directly
                        
                        // If it's a web URL (http), we should send it as "poster_url" part?
                        // But I didn't add "poster_url" part to the API signature in MediaApi.kt!
                        // I only added "poster" as MultipartBody.Part.
                        
                        // Ah, the API implementation in comic.rs handles "poster_url" text field too!
                        // But MediaApi.kt needs to send it.
                        // I should've added @Part("poster_url") to MediaApi.kt if I wanted to support strings.
                        // But for now, user asked for Image Upload.
                        // If the user selects nothing, we just don't send the part?
                        // If the user keeps the existing URL, we don't need to re-send it.
                        // The issue is if the user WANTS to edit the URL manually (which I'm removing support for in UI, simpler).
                        // So, only send 'poster' part if it's a new image (content://).
                    }
                }

                repository.updateComicSeriesMetadataMultipart(
                    _uiState.value.name,
                    plotPart,
                    yearPart,
                    genresPart,
                    posterPart
                ).onSuccess {
                    _uiState.value = _uiState.value.copy(isSaving = false, success = true)
                }.onFailure { e ->
                    _uiState.value = _uiState.value.copy(isSaving = false, error = e.message)
                }

            } catch (e: Exception) {
                 _uiState.value = _uiState.value.copy(isSaving = false, error = e.message)
            }
        }
    }
    
    fun resetSuccess() {
        _uiState.value = _uiState.value.copy(success = false)
    }
}
