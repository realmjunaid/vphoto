package com.vphoto.app.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vphoto.app.data.preferences.AppPreferences
import com.vphoto.app.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecentFolder(
    val uri: Uri,
    val displayName: String,
    val lastIndex: Int
)

data class HomeUiState(
    val recentFolders: List<RecentFolder> = emptyList(),
    val isLoading: Boolean = false,
    val scanningFolderName: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val settings: StateFlow<com.vphoto.app.data.preferences.AppSettings> = appPreferences.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.vphoto.app.data.preferences.AppSettings())

    init {
        viewModelScope.launch {
            appPreferences.recentFolders.collect { entries ->
                val folders = entries.mapNotNull { entry ->
                    appPreferences.parseRecentFolderEntry(entry)?.let { info ->
                        RecentFolder(Uri.parse(info.uri), info.name, info.lastIndex)
                    }
                }
                _uiState.update { it.copy(recentFolders = folders) }
            }
        }
    }

    /** Persists access to a picked folder; `false` if the provider doesn't offer a persistable grant. */
    fun takeAccess(treeUri: Uri): Boolean = mediaRepository.takePersistableAccess(treeUri)

    /** Adds the picked folder to Recents (its name is looked up off the main thread). */
    fun onFolderPicked(treeUri: Uri) {
        viewModelScope.launch {
            mediaRepository.addPickedFolder(treeUri)
        }
    }

    fun removeRecentFolder(folder: RecentFolder) {
        viewModelScope.launch {
            appPreferences.removeRecentFolder(folder.uri.toString())
        }
    }
}
