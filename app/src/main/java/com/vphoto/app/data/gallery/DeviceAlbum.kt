package com.vphoto.app.data.gallery

import android.net.Uri

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
