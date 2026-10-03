package com.vphoto.app.ui.gallery

import android.app.Activity
import android.graphics.drawable.Animatable
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.DrawableImage
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import com.vphoto.app.data.gallery.DevicePhotoRow
import com.vphoto.app.ui.theme.Black

/**
 * One-by-one full view for videos and animated WebPs: a vertical pager, one item
 * per screen. Videos play with ExoPlayer, WebPs animate — only the current page.
 * Photos and GIFs stay in the continuous feed ([DevicePhotoViewer]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PagedMediaViewer(
    items: List<DevicePhotoRow>,
    startIndex: Int,
    title: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
        pageCount = { items.size }
    )
    val currentPage = pagerState.currentPage
    var uiVisible by remember { mutableStateOf(true) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var muted by remember { mutableStateOf(false) }

    BackHandler { onDismiss() }

    // Immersive mode, same as the feed viewer.
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

    // One player for the whole pager; only the current video page is bound to it.
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply { volume = 1f }
    }
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.stop()
            exoPlayer.release()
        }
    }
    LaunchedEffect(muted) { exoPlayer.volume = if (muted) 0f else 1f }
    LaunchedEffect(currentPage) {
        val item = items.getOrNull(currentPage)
        if (item?.isVideo == true) {
            exoPlayer.setMediaItem(MediaItem.fromUri(item.uri))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        } else {
            exoPlayer.pause()
        }
    }

    val currentItem = items.getOrNull(currentPage)

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
                                text = "${currentPage + 1} / ${items.size}",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (title.isNotBlank()) {
                                Text(
                                    text = title,
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
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
                    actions = {
                        if (currentItem?.isVideo == true) {
                            IconButton(onClick = { muted = !muted }) {
                                Icon(
                                    imageVector = if (muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                                    contentDescription = if (muted) "Unmute" else "Mute",
                                    tint = Color.White
                                )
                            }
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
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1
            ) { page ->
                val item = items[page]
                val isCurrent = page == currentPage
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Black)
                        .clipToBounds()
                ) {
                    if (item.isVideo) {
                        // Frame thumbnail underneath; the player binds only on the current page.
                        StaticGalleryImage(
                            uri = item.uri,
                            contentDescription = item.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
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
                                playerView.player = if (isCurrent) exoPlayer else null
                            },
                            onRelease = { playerView -> playerView.player = null },
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = zoomScale
                                    scaleY = zoomScale
                                }
                        )
                        if (!isCurrent) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(56.dp)
                            )
                        }
                    } else {
                        val painter = rememberAsyncImagePainter(model = item.uri)
                        val painterState = painter.state
                        LaunchedEffect(painterState, isCurrent) {
                            val drawable = ((painterState as? AsyncImagePainter.State.Success)
                                ?.result?.image as? DrawableImage)
                                ?.drawable as? Animatable
                                ?: return@LaunchedEffect
                            if (isCurrent) {
                                if (!drawable.isRunning) drawable.start()
                            } else {
                                drawable.stop()
                            }
                        }
                        var zoomOffset by remember(item.id) { mutableStateOf(Offset.Zero) }
                        Image(
                            painter = painter,
                            contentDescription = item.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = zoomScale
                                    scaleY = zoomScale
                                    translationX = zoomOffset.x
                                    translationY = zoomOffset.y
                                }
                                .pointerInput(item.id) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            if (event.changes.size >= 2) {
                                                val zoom = event.calculateZoom()
                                                val pan = event.calculatePan()
                                                val newScale =
                                                    (zoomScale * zoom).coerceIn(1f, 10f)
                                                if (newScale <= 1f) {
                                                    zoomScale = 1f
                                                    zoomOffset = Offset.Zero
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

            // Side counter, plain text.
            if (items.isNotEmpty()) {
                Text(
                    text = "${(currentPage + 1).coerceAtMost(items.size)} / ${items.size}",
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

            // Bottom title + duration; fades with pure-view mode.
            AnimatedVisibility(
                visible = uiVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                currentItem?.let { current ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 48.dp, vertical = 12.dp)
                    ) {
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
                            )
                        )
                        if (current.isVideo && current.durationMs > 0) {
                            Text(
                                text = formatPagedDuration(current.durationMs),
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatPagedDuration(millis: Long): String {
    if (millis <= 0) return "0:00"
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
