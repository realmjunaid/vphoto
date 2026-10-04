package com.vphoto.app.ui.gallery

import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vphoto.app.data.gallery.DeleteResult
import com.vphoto.app.data.gallery.DeviceAlbum
import com.vphoto.app.data.gallery.DeviceGalleryRepository
import com.vphoto.app.data.gallery.DevicePhotoRow
import com.vphoto.app.data.gallery.groupIntoAlbums
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    /** Pull-to-refresh spinner (content stays visible underneath). */
    val isRefreshing: Boolean = false,
    /** Name filter inside the open album. */
    val searchQuery: String = ""
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
                hasPermission = true
            )
        }
        // Paint first, sniff later: animated-WebP flags upgrade silently in background.
        refreshAnimatedFlags()
    }

    private suspend fun refreshAnimatedFlags() {
        val refreshed = repository.sniffAnimatedWebps(repository.cachedSnapshot())
        val selected = _uiState.value.selectedAlbum
        _uiState.update {
            it.copy(
                albums = groupIntoAlbums(refreshed),
                photos = if (selected != null) {
                    refreshed.filter { row ->
                        (row.bucketName.ifBlank { "Unknown" }) == selected ||
                            selected == DeviceGalleryRepository.ALL_PHOTOS_ALBUM
                    }
                } else {
                    emptyList()
                }
            )
        }
    }

    fun openAlbum(name: String?) {
        if (name == null) {
            _uiState.update { it.copy(selectedAlbum = null, photos = emptyList(), searchQuery = "") }
            return
        }
        // Cache is warm after the first scan: open instantly, no loading flash.
        val instant = _uiState.value.albums.isNotEmpty()
        _uiState.update { it.copy(selectedAlbum = name, isLoading = !instant, searchQuery = "") }
        viewModelScope.launch {
            val photos = repository.loadPhotos(name)
            _uiState.update { it.copy(photos = photos, isLoading = false) }
        }
    }

    fun backToAlbums() = openAlbum(null)

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    /** Photos of the open album matching the search box (empty query = everything). */
    fun visiblePhotos(photos: List<DevicePhotoRow>, query: String): List<DevicePhotoRow> =
        if (query.isBlank()) photos
        else photos.filter { it.name.contains(query.trim(), ignoreCase = true) }

    /**
     * Deletes items (single or batch). System consent dialogs surface through
     * [consentRequests]; the screen launches them and answers via [answerConsent].
     * [onDone] receives true when everything is gone.
     */
    private val _consentRequests = MutableSharedFlow<IntentSender>(extraBufferCapacity = 1)
    val consentRequests: SharedFlow<IntentSender> = _consentRequests.asSharedFlow()
    private var consentGate: CompletableDeferred<Boolean>? = null

    fun answerConsent(granted: Boolean) {
        consentGate?.complete(granted)
        consentGate = null
    }

    fun requestDelete(uris: List<Uri>, onDone: (Boolean, String) -> Unit) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            var remaining = uris
            repeat(uris.size + 2) {
                when (val result = repository.deleteMedia(remaining)) {
                    is DeleteResult.Deleted -> {
                        reload()
                        val label = if (uris.size > 1) "Deleted ${uris.size} items" else "Deleted"
                        onDone(true, label)
                        return@launch
                    }
                    is DeleteResult.NeedsConsent -> {
                        val gate = CompletableDeferred<Boolean>()
                        consentGate = gate
                        _consentRequests.emit(result.intentSender)
                        if (!gate.await()) {
                            onDone(false, "Delete cancelled")
                            return@launch
                        }
                        remaining = result.pending
                    }
                    is DeleteResult.Failed -> {
                        onDone(false, result.message)
                        return@launch
                    }
                }
            }
            onDone(false, "Delete failed")
        }
    }

    /** Rescans after a consent grant or a viewer delete. */
    fun refreshAfterDelete() {
        viewModelScope.launch { reload() }
    }

    /** Rows inside the given folders (for folder share/info/delete). */
    fun photosInFolders(names: Set<String>): List<DevicePhotoRow> {
        if (names.isEmpty()) return emptyList()
        val normalized = names.map { it.ifBlank { "Unknown" } }.toSet()
        return repository.cachedSnapshot()
            .filter { it.bucketName.ifBlank { "Unknown" } in normalized }
    }

    /** Global name search across every folder (home search icon). */
    fun searchAll(query: String): List<DevicePhotoRow> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return repository.cachedSnapshot().filter { it.name.contains(q, ignoreCase = true) }
    }
}
