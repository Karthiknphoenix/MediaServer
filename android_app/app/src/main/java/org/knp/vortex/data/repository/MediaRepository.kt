package org.knp.vortex.data.repository

import org.knp.vortex.data.remote.MediaApi
import org.knp.vortex.data.remote.MediaItemDto
import org.knp.vortex.data.remote.ProgressDto
import org.knp.vortex.data.remote.CreateLibraryRequest
import org.knp.vortex.data.remote.ListDirectoriesRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepository @Inject constructor(
    private val api: MediaApi
) {
    suspend fun getRecentlyAdded(): Result<List<MediaItemDto>> = runCatching {
        api.getRecentlyAdded()
    }

    suspend fun getLibraries() = runCatching { api.getLibraries() }

    suspend fun createLibrary(name: String, path: String, type: String) = runCatching {
        api.createLibrary(CreateLibraryRequest(name, path, type))
    }

    suspend fun listDirectories(path: String?) = runCatching { 
        api.listDirectories(ListDirectoriesRequest(path)) 
    }

    suspend fun scanLibraries() = runCatching { api.scanLibraries() }

    suspend fun deleteLibrary(id: Long) = runCatching { 
        val response = api.deleteLibrary(id)
        if (!response.isSuccessful) throw Exception("Delete failed: ${response.code()}")
    }

    suspend fun getLibraryMedia(id: Long) = runCatching { api.getLibraryMedia(id) }

    suspend fun browseLibrary(id: Long, path: String?) = runCatching { api.browseLibrary(id, path) }

    suspend fun getContinueWatching() = runCatching { api.getContinueWatching() }

    suspend fun getProgress(id: Long) = runCatching { api.getProgress(id) }

    suspend fun updateProgress(id: Long, position: Long, total: Long) = runCatching {
        api.updateProgress(id, ProgressDto(position, total))
    }

    suspend fun getMediaDetails(id: Long) = runCatching { api.getMediaDetails(id) }

    suspend fun refreshMetadata(id: Long) = runCatching { api.refreshMetadata(id) }

    suspend fun searchMetadata(query: String, mediaType: String?) = runCatching { api.searchMetadata(query, mediaType) }
    
    suspend fun searchLibrary(query: String, mediaType: String?) = runCatching { api.searchLibrary(query, mediaType) }

    suspend fun identifyMedia(id: Long, providerId: String, mediaType: String?) = runCatching {
        api.identifyMedia(id, org.knp.vortex.data.remote.IdentifyRequest(providerId, mediaType))
    }

    // TV Show methods
    suspend fun getSeries() = runCatching { api.getSeries() }
    
    suspend fun getSeriesSeasons(name: String) = runCatching { api.getSeriesSeasons(name) }
    
    suspend fun getSeasonEpisodes(name: String, num: Int) = runCatching { 
        api.getSeasonEpisodes(name, num) 
    }

    suspend fun getSeriesDetail(name: String) = runCatching { api.getSeriesDetail(name) }

    suspend fun refreshSeriesMetadata(name: String) = runCatching { api.refreshSeriesMetadata(name) }

    suspend fun identifySeries(name: String, providerId: String, mediaType: String?) = runCatching {
        api.identifySeries(name, org.knp.vortex.data.remote.IdentifyRequest(providerId, mediaType))
    }

    suspend fun getSettings() = runCatching { api.getSettings() }

    suspend fun updateRemoteSetting(key: String, value: String) = runCatching { 
        api.updateSetting(org.knp.vortex.data.remote.UpdateSettingRequest(key, value)) 
    }

    suspend fun resetDatabase() = runCatching { api.resetDatabase() }

    suspend fun getSubtitles(id: Long) = runCatching { api.getSubtitles(id) }

    suspend fun getBookPages(id: Long) = runCatching { api.getBookPages(id) }

    // Comic series methods
    suspend fun getComicSeries() = runCatching { api.getComicSeries() }
    
    suspend fun getComicSeriesDetail(name: String) = runCatching { api.getComicSeriesDetail(name) }
    
    suspend fun getComicChapters(name: String) = runCatching { api.getComicChapters(name) }

    suspend fun updateComicSeriesMetadata(
        name: String, 
        plot: String?,
        year: String?,
        genres: String?,
        posterUri: String? // URI or "null"
    ) = runCatching {
        // We need a way to get file bytes/stream here. 
        // Ideally, Repository should take the parts directly or context is needed to resolve URI.
        // HACK: For now, assuming ViewModel passes parts or we change architecture slightly.
        // Better: Pass content resolver to repository? Or handle file reading in ViewModel.
        // Let's assume ViewModel prepares the File/Bytes. 
        // Actually, easiest is to let ViewModel pass the parts or helper.
        // But Repository signature shouldn't depend on Android types ideally... but we already depend on context for other things? No.
        
        // Let's change signature to take RequestBody/MultipartBody.Part from caller (ViewModel)
        // OR better: Just accept the string/primitive types and a way to get the file.
        // I will change this method to accept the pre-built parts from ViewModel for simplicity in this refactor, 
        // although typically Repositories bridge domain to data.
        
        // Wait, I can't easily create MultipartBody.Part here without File/Bytes.
        // I'll update signature to accept what API needs.
        throw NotImplementedError("Please call overloads with parts")
    }

    suspend fun updateComicSeriesMetadataMultipart(
        name: String,
        plot: okhttp3.RequestBody?,
        year: okhttp3.RequestBody?,
        genres: okhttp3.RequestBody?,
        poster: okhttp3.MultipartBody.Part?
    ) = runCatching {
        api.updateComicSeriesMetadata(name, plot, year, genres, poster)
    }

    // Reading List methods
    suspend fun getReadingLists() = runCatching { api.getReadingLists() }

    suspend fun createReadingList(name: String) = runCatching { 
        api.createReadingList(org.knp.vortex.data.remote.CreateReadingListRequest(name)) 
    }

    suspend fun getReadingListDetails(id: Long) = runCatching { api.getReadingListDetails(id) }

    suspend fun addItemsToReadingList(listId: Long, mediaIds: List<Long>) = runCatching {
        api.addItemsToReadingList(listId, org.knp.vortex.data.remote.AddItemsToListRequest(mediaIds))
    }

    suspend fun deleteReadingList(id: Long) = runCatching { api.deleteReadingList(id) }
}
