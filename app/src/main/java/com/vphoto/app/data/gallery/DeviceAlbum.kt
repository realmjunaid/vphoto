package com.vphoto.app.data.gallery

import android.net.Uri
import com.vphoto.app.data.preferences.SortOrder

/** One device folder (MediaStore bucket), like Google Photos groups. */
data class DeviceAlbum(
    val name: String,
    val count: Int,
    val coverUri: Uri,
    val latestDateModified: Long = 0L
)

/** One image row from MediaStore. Pure data so grouping is unit-testable. */
data class DevicePhotoRow(
    val id: Long,
    val uri: Uri,
    val name: String,
    val bucketName: String,
    val dateModified: Long,
    val size: Long
)

/**
 * Sorts flat MediaStore rows per the user's Settings order (same options as the viewer).
 * The gallery is images-only, so the video/image-first orders fall back to newest-first.
 */
fun sortDevicePhotos(rows: List<DevicePhotoRow>, order: SortOrder): List<DevicePhotoRow> =
    when (order) {
        SortOrder.NAME_ASC -> rows.sortedBy { it.name.lowercase() }
        SortOrder.NAME_DESC -> rows.sortedByDescending { it.name.lowercase() }
        SortOrder.DATE_NEWEST -> rows.sortedByDescending { it.dateModified }
        SortOrder.DATE_OLDEST -> rows.sortedBy { it.dateModified }
        SortOrder.SIZE_LARGEST -> rows.sortedByDescending { it.size }
        SortOrder.SIZE_SMALLEST -> rows.sortedBy { it.size }
        SortOrder.TYPE_VIDEO_FIRST,
        SortOrder.TYPE_IMAGE_FIRST -> rows.sortedByDescending { it.dateModified }
    }

/**
 * Groups flat MediaStore rows into albums (folder-wise).
 * One album per [DevicePhotoRow.bucketName], cover = latest photo.
 * Sorted by latest photo first, so Camera/DCIM surface at the top like Google Photos.
 */
fun groupIntoAlbums(rows: List<DevicePhotoRow>): List<DeviceAlbum> {
    if (rows.isEmpty()) return emptyList()
    return rows.groupBy { it.bucketName.ifBlank { "Unknown" } }
        .map { (bucket, photos) ->
            val latest = photos.maxByOrNull { it.dateModified }
            DeviceAlbum(
                name = bucket,
                count = photos.size,
                coverUri = latest?.uri ?: photos.first().uri,
                latestDateModified = latest?.dateModified ?: 0L
            )
        }
        .sortedByDescending { it.latestDateModified }
}
