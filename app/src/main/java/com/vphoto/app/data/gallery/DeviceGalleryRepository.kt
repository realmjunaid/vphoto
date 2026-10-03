package com.vphoto.app.data.gallery

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
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

/** MediaStore permissions for the auto device gallery (no manual folder pick needed). */
fun deviceGalleryPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
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

    /**
     * All device photos and videos. Empty when permission is missing —
     * the UI shows the permission prompt instead of crashing.
     */
    suspend fun loadAllPhotos(): List<DevicePhotoRow> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        val rows = mutableListOf<DevicePhotoRow>()
        loadImages(rows)
        loadVideos(rows)
        // Respect the Settings sort order (default A–Z); the queries stay newest-first.
        sortDevicePhotos(rows, appPreferences.settings.first().sortOrder)
    }

    private fun loadImages(rows: MutableList<DevicePhotoRow>) {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE
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
                            isAnimatedWebp = mime == "image/webp" && isAnimatedWebpUri(uri)
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
            MediaStore.Video.Media.DURATION
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
                            durationMs = try { cursor.getLong(durationCol) } catch (_: Exception) { 0L }
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
    private fun isAnimatedWebpUri(uri: android.net.Uri): Boolean {
        return try {
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

    /** Folder-wise albums (Google Photos style). */
    suspend fun loadAlbums(): List<DeviceAlbum> = groupIntoAlbums(loadAllPhotos())

    /** Photos of one album; null/blank/"All" returns everything. */
    suspend fun loadPhotos(albumName: String?): List<DevicePhotoRow> {
        val all = loadAllPhotos()
        if (albumName.isNullOrBlank() || albumName == ALL_PHOTOS_ALBUM) return all
        return all.filter { (it.bucketName.ifBlank { "Unknown" }) == albumName }
    }

    companion object {
        const val ALL_PHOTOS_ALBUM = "All Photos"
    }
}
