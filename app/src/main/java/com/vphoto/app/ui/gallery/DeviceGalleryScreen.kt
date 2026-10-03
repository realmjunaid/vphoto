package com.vphoto.app.ui.gallery

import android.app.Activity
import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.DrawableImage
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.decode.BitmapFactoryDecoder
import kotlin.math.abs
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Dimension
import coil3.size.Precision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.vphoto.app.data.gallery.DeviceAlbum
import com.vphoto.app.data.gallery.DevicePhotoRow
import com.vphoto.app.data.gallery.hasDeviceGalleryPermission
import com.vphoto.app.ui.theme.Black
import com.vphoto.app.ui.theme.TextDisabled
import com.vphoto.app.ui.theme.TextMuted
import com.vphoto.app.ui.theme.TextPrimary
import com.vphoto.app.ui.theme.TextSecondary
import com.vphoto.app.ui.theme.Violet400
import com.vphoto.app.ui.theme.Violet500

/**
 * Auto device gallery (Google Photos style): no manual folder pick.
 * Level 1 = folder-wise albums from MediaStore buckets.
 * Level 2 = photo grid of one album (vertical scroll). Tap a photo to open the
 * full view: a Drive-PDF style continuous vertical feed, every photo full-width.
 *
 * When [isHome] is true this is the app's home screen: no back button,
 * VPhoto branding + settings action in the top bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceGalleryScreen(
    onBack: () -> Unit,
    onSettingsClick: () -> Unit = {},
    isHome: Boolean = false,
    viewModel: DeviceGalleryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var viewerIndex by remember { mutableIntStateOf(-1) }
    // One continuous feed for everything: photos, GIFs, animated WebPs, videos.

    // System back: close viewer first, then go back to album list, then exit screen.
    BackHandler(enabled = viewerIndex >= 0) {
        viewerIndex = -1
    }
    BackHandler(enabled = viewerIndex < 0 && uiState.selectedAlbum != null) {
        viewModel.backToAlbums()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants.values.any { it } ||
            hasDeviceGalleryPermission(context)
        viewModel.refresh(granted)
    }

    LaunchedEffect(Unit) {
        val has = hasDeviceGalleryPermission(context)
        if (!has) {
            permissionLauncher.launch(viewModel.requiredPermissions())
        }
        viewModel.refresh(has)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Black,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isHome) {
                                uiState.selectedAlbum ?: "VPhoto"
                            } else {
                                uiState.selectedAlbum ?: "Device Photos"
                            },
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (uiState.selectedAlbum == null && uiState.totalCount > 0) {
                            Text(
                                text = "${uiState.totalCount} items • ${uiState.albums.size} folders (auto)",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (!isHome) {
                        IconButton(onClick = {
                            if (uiState.selectedAlbum != null) viewModel.backToAlbums() else onBack()
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    }
                },
                actions = {
                    if (isHome) {
                        IconButton(onClick = onSettingsClick) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Settings",
                                tint = TextSecondary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.pullRefresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
            when {
                !uiState.hasPermission && !uiState.isLoading -> PermissionPrompt(
                    onGrant = { permissionLauncher.launch(viewModel.requiredPermissions()) }
                )
                uiState.isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Violet500)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "Scanning device photos...", color = TextSecondary, fontSize = 13.sp)
                    }
                }
                uiState.selectedAlbum == null -> {
                    if (uiState.albums.isEmpty()) {
                        EmptyState(message = "No media found on this device")
                    } else {
                        AlbumGrid(
                            albums = uiState.albums,
                            totalCount = uiState.totalCount,
                            allPhotosCoverUri = uiState.allPhotosCoverUri,
                            onAlbumClick = { viewModel.openAlbum(it.name) },
                            onAllClick = { viewModel.openAlbum(com.vphoto.app.data.gallery.DeviceGalleryRepository.ALL_PHOTOS_ALBUM) }
                        )
                    }
                }
                else -> {
                    if (uiState.photos.isEmpty()) {
                        EmptyState(message = "No media in this folder")
                    } else {
                        PhotoGrid(
                            photos = uiState.photos,
                            onPhotoClick = { gridIndex -> viewerIndex = gridIndex }
                        )
                    }
                }
            }
            }
        }
    }

    // Full view: continuous vertical feed with everything, overlaid on the gallery.
    if (viewerIndex >= 0 && viewerIndex < uiState.photos.size) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Black)
        ) {
            DevicePhotoViewer(
                photos = uiState.photos,
                startIndex = viewerIndex,
                title = uiState.selectedAlbum ?: "",
                onDismiss = { viewerIndex = -1 }
            )
        }
    }
    } // root Box
}

@Composable
private fun PermissionPrompt(onGrant: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.PhotoLibrary,
                contentDescription = null,
                tint = TextDisabled,
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Allow photo access",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "VPhoto will auto-show all device photos folder-wise, like Google Photos. No manual folder pick needed.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onGrant,
                colors = ButtonDefaults.buttonColors(containerColor = Violet500)
            ) {
                Text("Allow access", color = Color.Black)
            }
        }
    }
}

/**
 * A thumbnail that never animates: GIF/animated WebP stays frozen on its first
 * frame (only the full-view middle image plays). Saves battery in grids.
 */
@Composable
internal fun StaticGalleryImage(
    uri: android.net.Uri,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    // BitmapFactoryDecoder decodes the first frame only: GIF/animated WebP can
    // never animate here (the stop() below is just belt-and-braces).
    val context = LocalContext.current
    val request = remember(uri) {
        ImageRequest.Builder(context)
            .data(uri)
            .decoderFactory(BitmapFactoryDecoder.Factory())
            .build()
    }
    val painter = rememberAsyncImagePainter(model = request)
    // NOTE: painter.state is a StateFlow — it must be collected, plain reads never update.
    val painterState by painter.state.collectAsState()
    LaunchedEffect(painterState) {
        ((painterState as? AsyncImagePainter.State.Success)?.result?.image as? DrawableImage)
            ?.drawable?.let { (it as? Animatable)?.stop() }
    }
    Image(
        painter = painter,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier
    )
}

@Composable
private fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.Image,
                contentDescription = null,
                tint = TextDisabled,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = message, color = TextMuted)
        }
    }
}

@Composable
private fun AlbumGrid(
    albums: List<DeviceAlbum>,
    totalCount: Int,
    allPhotosCoverUri: android.net.Uri?,
    onAlbumClick: (DeviceAlbum) -> Unit,
    onAllClick: () -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .animateItem()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1E1E1E))
                    .clickable(onClick = onAllClick)
                    .padding(12.dp)
            ) {
                if (allPhotosCoverUri != null) {
                    StaticGalleryImage(
                        uri = allPhotosCoverUri,
                        contentDescription = "All Photos",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.4f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.DarkGray)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.4f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Violet500.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PhotoLibrary,
                            contentDescription = null,
                            tint = Violet400,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "All Photos",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(text = "$totalCount items", color = TextMuted, fontSize = 12.sp)
            }
        }
        items(albums, key = { it.name }) { album ->
            Column(
                modifier = Modifier
                    .animateItem()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1E1E1E))
                    .clickable(onClick = { onAlbumClick(album) })
                    .padding(12.dp)
            ) {
                StaticGalleryImage(
                    uri = album.coverUri,
                    contentDescription = album.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.4f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.DarkGray)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Folder,
                        contentDescription = null,
                        tint = Violet400,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = album.name,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(text = "${album.count} items", color = TextMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun PhotoGrid(photos: List<DevicePhotoRow>, onPhotoClick: (Int) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(photos.size, key = { photos[it].id }) { index ->
            val photo = photos[index]
            Box(
                modifier = Modifier
                    .animateItem()
                    .aspectRatio(1f)
                    .background(Color.DarkGray)
                    .clickable { onPhotoClick(index) }
            ) {
                StaticGalleryImage(
                    uri = photo.uri,
                    contentDescription = photo.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (photo.isVideo) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(20.dp)
                    )
                    if (photo.durationMs > 0) {
                        Text(
                            text = formatGridDuration(photo.durationMs),
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 10.sp,
                            style = MaterialTheme.typography.labelSmall.copy(
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.8f),
                                    offset = Offset(1f, 1f),
                                    blurRadius = 3f
                                )
                            ),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(4.dp)
                        )
                    }
                }
                // GIF / animated-WebP chip: proves the app recognized it as playable.
                if (!photo.isVideo && (photo.mimeType == "image/gif" || photo.isAnimatedWebp)) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "GIF",
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

private fun formatGridDuration(millis: Long): String {
    if (millis <= 0) return "0:00"
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

/**
 * One video inside the continuous feed: full-width like every photo, as many
 * per screen as fit. Tapping binds the shared player and plays; everything
 * else stays paused. Aspect is learned from the video once it plays.
 */
@Composable
private fun FeedVideoItem(
    photo: DevicePhotoRow,
    isPlaying: Boolean,
    uiVisible: Boolean,
    exoPlayer: ExoPlayer,
    videoAspects: MutableMap<Long, Float>,
    onTogglePlay: () -> Unit
) {
    val aspect = videoAspects[photo.id] ?: (16f / 9f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .background(Color.Black)
            .clipToBounds()
    ) {
        if (!isPlaying) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Play video",
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(56.dp)
            )
            if (photo.durationMs > 0) {
                Text(
                    text = formatGridDuration(photo.durationMs),
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 11.sp,
                    style = MaterialTheme.typography.labelSmall.copy(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.8f),
                            offset = Offset(1f, 1f),
                            blurRadius = 3f
                        )
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                )
            }
        }
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                playerView.player = if (isPlaying) exoPlayer else null
            },
            onRelease = { playerView -> playerView.player = null },
            modifier = Modifier.fillMaxSize()
        )
        // Transparent tap layer above the player (Android views swallow touches,
        // so without this, taps on a video never toggle playback).
        // Double-tap anywhere toggles the chrome via the feed's own detector.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(photo.id) {
                    detectTapGestures(
                        onTap = { onTogglePlay() }
                    )
                }
        )
        if (uiVisible) {
            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = 8.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DevicePhotoViewer(
    photos: List<DevicePhotoRow>,
    startIndex: Int,
    title: String,
    onDismiss: () -> Unit
) {
    // Drive-PDF style: one continuous vertical feed, every photo full-width back to back,
    // starting at the tapped photo. Square photos stack several per screen.
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = startIndex.coerceIn(0, (photos.size - 1).coerceAtLeast(0))
    )
    // Side counter + center detection: the item closest to the viewport middle.
    // Only that item plays (animated WebP/GIF); the rest stay paused.
    val currentIndex by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { item ->
                abs((item.offset + item.size / 2) - viewportCenter)
            }?.index ?: 0
        }
    }
    // Decode at 2x screen width (aspect kept): visually identical on-screen,
    // but a fraction of the memory — this is what makes the feed butter smooth.
    // Full originals stay untouched in storage; nothing is compressed there.
    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val targetWidthPx = remember(configuration, density) {
        with(density) { (configuration.screenWidthDp.dp * 2).roundToPx() }.coerceAtLeast(1)
    }
    // Double-tap toggles all chrome (top bar + bottom title) for pure-image viewing.
    var uiVisible by remember { mutableStateOf(true) }
    // No autoplay while scrolling: only the explicitly opened/tapped photo plays,
    // like Google Photos. Starts with the photo tapped in the grid.
    var playingIndex by remember {
        mutableIntStateOf(startIndex.coerceIn(0, (photos.size - 1).coerceAtLeast(0)))
    }
    // One shared player for inline videos: the tapped video binds and plays,
    // everything else stays paused. Aspect is learned per video once it plays.
    val videoAspects = remember { mutableStateMapOf<Long, Float>() }
    var playingVideoId by remember { mutableLongStateOf(-1L) }
    val exoPlayer = remember(context) { ExoPlayer.Builder(context).build() }
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoAspects[playingVideoId] =
                        videoSize.width.toFloat() / videoSize.height.toFloat()
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
            exoPlayer.release()
        }
    }
    LaunchedEffect(playingIndex) {
        val item = photos.getOrNull(playingIndex)
        if (item?.isVideo == true) {
            playingVideoId = item.id
            exoPlayer.setMediaItem(androidx.media3.common.MediaItem.fromUri(item.uri))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        } else {
            playingVideoId = -1L
            exoPlayer.pause()
        }
    }
    // Immersive mode: hiding the UI also hides status + navigation bars,
    // so the whole display shows only images. Restored on dismiss.
    val view = LocalView.current
    LaunchedEffect(uiVisible) {
        val activity = view.context as? Activity ?: return@LaunchedEffect
        val controller = WindowInsetsControllerCompat(activity.window, view)
        if (uiVisible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            (view.context as? Activity)?.let { activity ->
                WindowInsetsControllerCompat(activity.window, view)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    Scaffold(
        containerColor = Color.Black,
        topBar = {
            AnimatedVisibility(
                visible = uiVisible,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = title.ifBlank { "Photos" },
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${photos.size} photos • scroll vertically",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black.copy(alpha = 0.6f))
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { uiVisible = !uiVisible })
                }
        ) {
            LazyColumn(
                state = listState,
                // The feed always scrolls — even while zoomed. Zoom/pan use two fingers
                // only, so one-finger scrolls never fight the zoom gesture.
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(photos, key = { _, photo -> photo.id }) { index, photo ->
                    val isPlaying = index == playingIndex
                    if (photo.isVideo) {
                        FeedVideoItem(
                            photo = photo,
                            isPlaying = isPlaying,
                            uiVisible = uiVisible,
                            exoPlayer = exoPlayer,
                            videoAspects = videoAspects,
                            onTogglePlay = { playingIndex = if (isPlaying) -1 else index }
                        )
                    } else {
                    // Animated formats decode at 1x screen width: every frame is
                    // CPU-decoded, so a lighter decode is what makes playback smooth.
                    // Static photos keep 2x for sharp 10x zooming.
                    // INEXACT precision forces the fast power-of-2 decode path
                    // instead of a slow full-size decode + software scale-down.
                    val decodeWidth = if (photo.needsLightDecode) targetWidthPx / 2 else targetWidthPx
                    val request = remember(photo.id, decodeWidth) {
                        ImageRequest.Builder(context)
                            .data(photo.uri)
                            .size(Dimension.Pixels(decodeWidth), Dimension.Undefined)
                            .precision(Precision.INEXACT)
                            .crossfade(false)
                            .build()
                    }
                    val painter = rememberAsyncImagePainter(model = request)
                    // NOTE: painter.state is a StateFlow — it must be collected.
                    val painterState by painter.state.collectAsState()
                    // Only the opened/tapped photo plays; scrolling never auto-starts others.
                    LaunchedEffect(painterState, isPlaying) {
                        val drawable = ((painterState as? AsyncImagePainter.State.Success)
                            ?.result?.image as? DrawableImage)
                            ?.drawable as? Animatable
                            ?: return@LaunchedEffect
                        if (isPlaying) {
                            if (!drawable.isRunning) drawable.start()
                        } else {
                            drawable.stop()
                        }
                    }
                    // Direct platform decode for animated formats: ImageDecoder always
                    // returns a real AnimatedImageDrawable (no Coil pipeline ambiguity).
                    // Static photos keep the Coil painter above.
                    val useDirectDecode = Build.VERSION.SDK_INT >= 28 &&
                        (photo.mimeType == "image/gif" || photo.isAnimatedWebp)
                    var directDrawable by remember(photo.id) { mutableStateOf<Drawable?>(null) }
                    LaunchedEffect(photo.id, useDirectDecode) {
                        directDrawable = null
                        if (!useDirectDecode) return@LaunchedEffect
                        directDrawable = withContext(Dispatchers.IO) {
                            try {
                                val source = ImageDecoder.createSource(context.contentResolver, photo.uri)
                                ImageDecoder.decodeDrawable(source)
                            } catch (_: Exception) {
                                null
                            }
                        }
                    }
                    LaunchedEffect(directDrawable, isPlaying) {
                        val anim = directDrawable as? Animatable ?: return@LaunchedEffect
                        if (isPlaying) {
                            if (!anim.isRunning) anim.start()
                        } else {
                            anim.stop()
                        }
                    }
                    DisposableEffect(directDrawable) {
                        onDispose { (directDrawable as? Animatable)?.stop() }
                    }
                    // Tap a photo to play it (GIF/WebP/video). Chrome toggles
                    // with double-tap only.
                    val tapModifier = Modifier
                        .fillMaxWidth()
                        .background(Color.DarkGray)
                        .pointerInput(photo.id) {
                            detectTapGestures(
                                onTap = { playingIndex = index }
                            )
                        }
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                    if (directDrawable != null) {
                        // Animated GIF/WebP in a platform ImageView: animation
                        // callbacks are native, so playback just works.
                        AndroidView(
                            factory = { ctx ->
                                android.widget.ImageView(ctx).apply {
                                    adjustViewBounds = true
                                    scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                                }
                            },
                            update = { view ->
                                if (view.drawable !== directDrawable) {
                                    view.setImageDrawable(directDrawable)
                                }
                            },
                            modifier = tapModifier
                        )
                    } else {
                        Image(
                            painter = painter,
                            contentDescription = photo.name,
                            contentScale = ContentScale.FillWidth,
                            modifier = tapModifier
                        )
                    }
                    // Explicit play/pause for animated photos (tapping the GIF itself
                    // also plays it). Visible only with the chrome.
                    if (uiVisible && (photo.mimeType == "image/gif" || photo.isAnimatedWebp)) {
                        val playing = index == playingIndex
                        IconButton(
                            onClick = { playingIndex = if (playing) -1 else index },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 8.dp, bottom = 8.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (playing) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    }
                    }
                }
            }
            // Small side counter, plain text, no background — e.g. 23/100.
            if (photos.isNotEmpty()) {
                Text(
                    text = "${(currentIndex + 1).coerceAtMost(photos.size)} / ${photos.size}",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.labelSmall.copy(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.8f),
                            offset = Offset(1f, 1f),
                            blurRadius = 3f
                        )
                    ),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 6.dp)
                )
            }
            // Small title of the current photo at the bottom; fades with pure-image mode.
            AnimatedVisibility(
                visible = uiVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                photos.getOrNull(currentIndex)?.let { current ->
                    Text(
                        text = current.name,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall.copy(
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.8f),
                                offset = Offset(1f, 1f),
                                blurRadius = 3f
                            )
                        ),
                        modifier = Modifier.padding(horizontal = 48.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }
}
