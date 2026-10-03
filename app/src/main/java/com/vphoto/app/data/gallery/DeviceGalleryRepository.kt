package com.vphoto.app.data.gallery

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** MediaStore permissions for the auto device gallery (no manual folder pick needed). */
fun deviceGalleryPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

fun hasDeviceGalleryPermission(context: Context): Boolean =
    deviceGalleryPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

@Singleton
class DeviceGalleryRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun hasPermission(): Boolean = hasDeviceGalleryPermission(context)

    fun requiredPermissions(): Array<String> = deviceGalleryPermissions()

    /**
     * All device images, newest first. Empty when permission is missing —
     * the UI shows the permission prompt instead of crashing.
     */
    suspend fun loadAllPhotos(): List<DevicePhotoRow> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        val rows = mutableListOf<DevicePhotoRow>()
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.SIZE
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
        try {
            context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(collection, id)
                    rows.add(
                        DevicePhotoRow(
                            id = id,
                            uri = uri,
                            name = cursor.getString(nameCol) ?: "Image",
                            bucketName = cursor.getString(bucketCol)?.takeIf { it.isNotBlank() } ?: "Unknown",
                            dateModified = cursor.getLong(dateCol),
                            size = try { cursor.getLong(sizeCol) } catch (_: Exception) { 0L }
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
            return@withContext emptyList()
        } catch (_: Exception) {
            return@withContext emptyList()
        }
        rows
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
