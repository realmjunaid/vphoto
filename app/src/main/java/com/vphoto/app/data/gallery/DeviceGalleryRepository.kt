package com.vphoto.app.data.gallery

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.vphoto.app.data.preferences.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of a MediaStore delete request. */
sealed interface DeleteResult {
    data object Deleted : DeleteResult
    /** System consent dialog required (Android 10+): launch the sender, then retry on OK. */
    data class NeedsConsent(val intentSender: android.content.IntentSender) : DeleteResult
    data class Failed(val message: String) : DeleteResult
}

/** MediaStore permissions for the auto device gallery (no manual folder pick needed). */
fun deviceGalleryPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    } else {
        // Pre-Q deletes go through the filesystem permission, not a consent dialog.
        arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

fun hasDeviceGalleryPermission(context: Context): Boolean =
    deviceGalleryPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

@Singleton
class DeviceGalleryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences
) {
    fun hasPermission(): Boolean = hasDeviceGalleryPermission(context)

    fun requiredPermissions(): Array<String> = deviceGalleryPermissions()

    /** In-memory snapshot: album switches filter this instantly (no rescan, no spinner). */
    private var cachedPhotos: List<DevicePhotoRow>? = null

    /**
     * All device photos and videos. Empty when permission is missing —
     * the UI shows the permission prompt instead of crashing.
     * [fresh] rescans MediaStore (first load / pull-to-refresh); otherwise the cache is used.
     */
    suspend fun loadAllPhotos(fresh: Boolean = false): List<DevicePhotoRow> = withContext(Dispatchers.IO) {
        if (!hasPermission()) {
            cachedPhotos = null
            return@withContext emptyList()
        }
        if (!fresh) {
            cachedPhotos?.let { return@withContext it }
        }
        val rows = mutableListOf<DevicePhotoRow>()
        loadImages(rows)
        loadVideos(rows)
        // Respect the Settings sort order (default A–Z); the queries stay newest-first.
        sortDevicePhotos(rows, appPreferences.settings.first().sortOrder)
            .also { cachedPhotos = it }
    }

    private fun loadImages(rows: MutableList<DevicePhotoRow>) {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
        try {
            context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val widthCol = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(collection, id)
                    val mime = cursor.getString(mimeCol) ?: ""
                    rows.add(
                        DevicePhotoRow(
                            id = id,
                            uri = uri,
                            name = cursor.getString(nameCol) ?: "Image",
                            bucketName = cursor.getString(bucketCol)?.takeIf { it.isNotBlank() } ?: "Unknown",
                            dateModified = cursor.getLong(dateCol),
                            size = try { cursor.getLong(sizeCol) } catch (_: Exception) { 0L },
                            mimeType = mime,
                            width = if (widthCol >= 0) try { cursor.getInt(widthCol) } catch (_: Exception) { 0 } else 0,
                            height = if (heightCol >= 0) try { cursor.getInt(heightCol) } catch (_: Exception) { 0 } else 0
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
            return
        } catch (_: Exception) {
            return
        }
    }

    private fun loadVideos(rows: MutableList<DevicePhotoRow>) {
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT
        )
        val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
        try {
            context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val widthCol = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    // Offset video IDs so album/grid keys never collide with image IDs.
                    rows.add(
                        DevicePhotoRow(
                            id = -(id + 1),
                            uri = ContentUris.withAppendedId(collection, id),
                            name = cursor.getString(nameCol) ?: "Video",
                            bucketName = cursor.getString(bucketCol)?.takeIf { it.isNotBlank() } ?: "Unknown",
                            dateModified = cursor.getLong(dateCol),
                            size = try { cursor.getLong(sizeCol) } catch (_: Exception) { 0L },
                            mimeType = cursor.getString(mimeCol) ?: "video/*",
                            durationMs = try { cursor.getLong(durationCol) } catch (_: Exception) { 0L },
                            width = if (widthCol >= 0) try { cursor.getInt(widthCol) } catch (_: Exception) { 0 } else 0,
                            height = if (heightCol >= 0) try { cursor.getInt(heightCol) } catch (_: Exception) { 0 } else 0
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
            return
        } catch (_: Exception) {
            return
        }
    }

    /** Reads the first bytes of a WebP file and checks the VP8X animation flag. */
    private fun isAnimatedWebpUri(uri: android.net.Uri): Boolean {        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val header = ByteArray(24)
                var read = 0
                while (read < header.size) {
                    val n = input.read(header, read, header.size - read)
                    if (n <= 0) break
                    read += n
                }
                isAnimatedWebpHeader(header)
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    /** Folder-wise albums (Google Photos style). Always a fresh scan. */
    suspend fun loadAlbums(): List<DeviceAlbum> = groupIntoAlbums(loadAllPhotos(fresh = true))

    /** Photos of one album; null/blank/"All" returns everything. Served from cache — instant. */
    suspend fun loadPhotos(albumName: String?): List<DevicePhotoRow> {
        val all = loadAllPhotos(fresh = false)
        if (albumName.isNullOrBlank() || albumName == ALL_PHOTOS_ALBUM) return all
        return all.filter { (it.bucketName.ifBlank { "Unknown" }) == albumName }
    }

    /** The current in-memory snapshot (for background flag refreshes). */
    fun cachedSnapshot(): List<DevicePhotoRow> = cachedPhotos.orEmpty()

    /**
     * Deletes one item from the device. On Android 10+ the system may demand
     * user consent ([DeleteResult.NeedsConsent]): launch the sender and retry on OK.
     * The cache is dropped on success so the next load rescans.
     */
    suspend fun deleteMedia(uri: Uri): DeleteResult = withContext(Dispatchers.IO) {
        try {
            val rows = context.contentResolver.delete(uri, null, null)
            if (rows > 0) {
                cachedPhotos = null
                DeleteResult.Deleted
            } else {
                DeleteResult.Failed("File not found")
            }
        } catch (e: RecoverableSecurityException) {
            DeleteResult.NeedsConsent(e.userAction.actionIntent.intentSender)
        } catch (e: SecurityException) {
            DeleteResult.Failed(e.message ?: "Permission denied")
        } catch (e: Exception) {
            DeleteResult.Failed(e.message ?: "Delete failed")
        }
    }

    /**
     * Fills in animated-WebP flags after the first paint: opening one stream per
     * WebP is slow, so it never blocks the initial scan. Returns new row copies.
     */
    suspend fun sniffAnimatedWebps(rows: List<DevicePhotoRow>): List<DevicePhotoRow> =
        withContext(Dispatchers.IO) {
            if (rows.none { it.mimeType == "image/webp" && !it.isAnimatedWebp }) return@withContext rows
            val refreshed = rows.map { row ->
                if (row.mimeType == "image/webp" && !row.isAnimatedWebp && isAnimatedWebpUri(row.uri)) {
                    row.copy(isAnimatedWebp = true)
                } else {
                    row
                }
            }
            // Keep the cache in sync so album switches stay instant AND flagged.
            cachedPhotos?.let { cached ->
                if (cached.size == refreshed.size) cachedPhotos = refreshed
            }
            refreshed
        }

    companion object {
        const val ALL_PHOTOS_ALBUM = "All Photos"
    }
}
