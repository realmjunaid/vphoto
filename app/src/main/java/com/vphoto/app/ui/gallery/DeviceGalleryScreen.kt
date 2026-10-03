package com.vphoto.app.ui.gallery

import android.app.Activity
import android.graphics.drawable.Animatable
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
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
import coil3.decode.BitmapFactoryDecoder
import kotlin.math.abs
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Dimension
import coil3.size.Precision
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
    var pagerIndex by remember { mutableIntStateOf(-1) }
    // Feed = photos, GIFs, static WebPs (continuous, as many as fit).
    // Videos + animated WebPs open one-by-one in the pager.
    val feedItems = remember(uiState.photos) { uiState.photos.filter { !it.isPagedItem } }
    val pagedItems = remember(uiState.photos) { uiState.photos.filter { it.isPagedItem } }

    // System back: close viewer first, then go back to album list, then exit screen.
    BackHandler(enabled = viewerIndex >= 0 || pagerIndex >= 0) {
        viewerIndex = -1
        pagerIndex = -1
    }
    BackHandler(enabled = viewerIndex < 0 && pagerIndex < 0 && uiState.selectedAlbum != null) {
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
                            onPhotoClick = { gridIndex ->
                                val item = uiState.photos.getOrNull(gridIndex) ?: return@PhotoGrid
                                if (item.isPagedItem) {
                                    pagerIndex = pagedItems.indexOf(item)
                                } else {
                                    viewerIndex = feedItems.indexOf(item)
                                }
                            }
                        )
                    }
                }
            }
            }
        }
    }

    // Full view: continuous vertical feed, overlaid on top of the gallery.
    if (viewerIndex >= 0 && viewerIndex < feedItems.size) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Black)
        ) {
            DevicePhotoViewer(
                photos = feedItems,
                startIndex = viewerIndex,
                title = uiState.selectedAlbum ?: "",
                onDismiss = { viewerIndex = -1 }
            )
        }
    }

    // One-by-one pager for videos + animated WebPs.
    if (pagerIndex >= 0 && pagerIndex < pagedItems.size) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Black)
        ) {
            PagedMediaViewer(
                items = pagedItems,
                startIndex = pagerIndex,
                title = uiState.selectedAlbum ?: "",
                onDismiss = { pagerIndex = -1 }
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
    // Shared zoom: zooming once zooms the whole feed; pan works in all directions.
    // Pan offset is per-photo (a shared pan would shove every photo sideways).
    var zoomScale by remember { mutableFloatStateOf(1f) }
    // Bumped whenever zoom returns to 1x: resets every photo's pan offset.
    var zoomEpoch by remember { mutableIntStateOf(0) }
    fun resetZoom() {
        if (zoomScale > 1f) {
            zoomScale = 1f
            zoomEpoch++
        }
    }
    // Single tap toggles all chrome (top bar + bottom title) for pure-image viewing.
    var uiVisible by remember { mutableStateOf(true) }
    // No autoplay while scrolling: only the explicitly opened/tapped photo plays,
    // like Google Photos. Starts with the photo tapped in the grid.
    var playingIndex by remember {
        mutableIntStateOf(startIndex.coerceIn(0, (photos.size - 1).coerceAtLeast(0)))
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
                    detectTapGestures(onTap = { uiVisible = !uiVisible })
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
                    val isPlaying = index == playingIndex
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
                    // This photo's own pan (resets with every zoom-out via zoomEpoch).
                    var zoomOffset by remember(photo.id, zoomEpoch) { mutableStateOf(Offset.Zero) }
                    // Base height for layout growth: when zoomed, this item occupies
                    // zoomScale x space, pushing neighbors — the photo truly fills more screen.
                    var baseHeightPx by remember(photo.id) { mutableIntStateOf(0) }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (zoomScale > 1f && baseHeightPx > 0) {
                                    Modifier.height(with(density) { (baseHeightPx * zoomScale).toDp() })
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                    Image(
                        painter = painter,
                        contentDescription = photo.name,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.DarkGray)
                            .onSizeChanged { baseHeightPx = it.height }
                            // Clip to this item: a zoomed photo must never bleed
                            // into its neighbors.
                            .clipToBounds()
                            .graphicsLayer {
                                scaleX = zoomScale
                                scaleY = zoomScale
                                // Grow downward from the top edge so the visual
                                // exactly fills the grown layout above.
                                transformOrigin = TransformOrigin(0.5f, 0f)
                                translationX = zoomOffset.x
                                translationY = zoomOffset.y
                            }
                            .pointerInput(photo.id) {
                                detectTapGestures(
                                    onTap = {
                                        // Tapping a photo plays it (chrome toggles too, same tap).
                                        playingIndex = index
                                    },
                                    onDoubleTap = {
                                        // Double-tap toggles 1x / 2.5x; always exits zoom cleanly.
                                        if (zoomScale > 1f) {
                                            resetZoom()
                                        } else {
                                            zoomScale = 2.5f
                                        }
                                    }
                                )
                            }
                            .pointerInput(photo.id, zoomEpoch) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        // Two fingers only: pinch-zoom the whole feed + pan this photo.
                                        // Single finger is never consumed here, so scrolling
                                        // stays alive even mid-zoom and while zoomed.
                                        if (event.changes.size >= 2) {
                                            val zoom = event.calculateZoom()
                                            val pan = event.calculatePan()
                                            val newScale = (zoomScale * zoom).coerceIn(1f, 10f)
                                            if (newScale <= 1f) {
                                                resetZoom()
                                            } else {
                                                zoomScale = newScale
                                                zoomOffset += pan
                                            }
                                            event.changes.forEach { it.consume() }
                                        }
                                    }
                                }
                            }
                    )
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
