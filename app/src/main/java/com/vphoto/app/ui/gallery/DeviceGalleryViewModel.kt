package com.vphoto.app.ui.gallery

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vphoto.app.data.gallery.DeviceAlbum
import com.vphoto.app.data.gallery.DeviceGalleryRepository
import com.vphoto.app.data.gallery.DevicePhotoRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeviceGalleryUiState(
    val albums: List<DeviceAlbum> = emptyList(),
    val photos: List<DevicePhotoRow> = emptyList(),
    /** null = album list (folder-wise); non-null = inside one folder. */
    val selectedAlbum: String? = null,
    val isLoading: Boolean = true,
    val hasPermission: Boolean = false,
    val totalCount: Int = 0,
    /** Cover for the "All Photos" tile: the single newest photo on device. */
    val allPhotosCoverUri: Uri? = null,
    /** Pull-to-refresh spinner (content stays visible underneath). */
    val isRefreshing: Boolean = false
)

@HiltViewModel
class DeviceGalleryViewModel @Inject constructor(
    private val repository: DeviceGalleryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceGalleryUiState())
    val uiState: StateFlow<DeviceGalleryUiState> = _uiState.asStateFlow()

    // No silent background check on launch: the app is fully offline.

    fun requiredPermissions(): Array<String> = repository.requiredPermissions()

    /** Called on screen entry and after the permission dialog resolves. */
    fun refresh(hasPermission: Boolean) {
        _uiState.update { it.copy(hasPermission = hasPermission) }
        if (!hasPermission) {
            _uiState.update { it.copy(isLoading = false, albums = emptyList(), photos = emptyList()) }
            return
        }
        loadAlbums()
    }

    fun loadAlbums() {
        _uiState.update { it.copy(isLoading = true, isRefreshing = false) }
        viewModelScope.launch { reload() }
    }

    /** Pull-to-refresh: picks up new photos/folders without the full loading screen. */
    fun pullRefresh() {
        val state = _uiState.value
        if (state.isLoading || state.isRefreshing || !state.hasPermission) return
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch { reload() }
    }

    private suspend fun reload() {
        val albums = repository.loadAlbums()
        val total = albums.sumOf { it.count }
        val allCover = albums.maxByOrNull { it.latestDateModified }?.coverUri
        // Keep the open album in sync (e.g. after a refresh).
        val selected = _uiState.value.selectedAlbum
        val photos = if (selected != null) repository.loadPhotos(selected) else emptyList()
        _uiState.update {
            it.copy(
                albums = albums,
                photos = photos,
                isLoading = false,
                isRefreshing = false,
                totalCount = total,
                allPhotosCoverUri = allCover,
                hasPermission = true
            )
        }
    }

    fun openAlbum(name: String?) {
        if (name == null) {
            _uiState.update { it.copy(selectedAlbum = null, photos = emptyList()) }
            return
        }
        _uiState.update { it.copy(selectedAlbum = name, isLoading = true) }
        viewModelScope.launch {
            val photos = repository.loadPhotos(name)
            _uiState.update { it.copy(photos = photos, isLoading = false) }
        }
    }

    fun backToAlbums() = openAlbum(null)
}
