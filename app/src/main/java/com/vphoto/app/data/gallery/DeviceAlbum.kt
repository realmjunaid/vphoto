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
    val size: Long,
    val mimeType: String = "",
    val durationMs: Long = 0L,
    val isAnimatedWebp: Boolean = false
) {
    /** GIF always animates; WebP may be animated — both decode cheaper for smooth playback. */
    val needsLightDecode: Boolean
        get() = mimeType == "image/gif" || mimeType == "image/webp"

    val isVideo: Boolean
        get() = mimeType.startsWith("video/")

    /** True for pager items (one-by-one view): videos and animated WebPs. */
    val isPagedItem: Boolean
        get() = isVideo || isAnimatedWebp
}

/**
 * Sniffs a WebP header for the VP8X animation flag. Animated WebPs always use the
 * VP8X container with bit 1 of the feature-flags byte (offset 20) set.
 */
fun isAnimatedWebpHeader(header: ByteArray): Boolean {
    if (header.size < 21) return false
    fun tag(offset: Int, text: String): Boolean =
        header[offset] == text[0].code.toByte() &&
            header[offset + 1] == text[1].code.toByte() &&
            header[offset + 2] == text[2].code.toByte() &&
            header[offset + 3] == text[3].code.toByte()
    if (!tag(0, "RIFF") || !tag(8, "WEBP") || !tag(12, "VP8X")) return false
    return (header[20].toInt() and 0x02) != 0
}

/**
 * Sorts flat MediaStore rows per the user's Settings order (same options as the viewer).
 */
fun sortDevicePhotos(rows: List<DevicePhotoRow>, order: SortOrder): List<DevicePhotoRow> =
    when (order) {
        SortOrder.NAME_ASC -> rows.sortedBy { it.name.lowercase() }
        SortOrder.NAME_DESC -> rows.sortedByDescending { it.name.lowercase() }
        SortOrder.DATE_NEWEST -> rows.sortedByDescending { it.dateModified }
        SortOrder.DATE_OLDEST -> rows.sortedBy { it.dateModified }
        SortOrder.SIZE_LARGEST -> rows.sortedByDescending { it.size }
        SortOrder.SIZE_SMALLEST -> rows.sortedBy { it.size }
        SortOrder.TYPE_VIDEO_FIRST ->
            rows.sortedWith(compareByDescending<DevicePhotoRow> { it.isVideo }.thenByDescending { it.dateModified })
        SortOrder.TYPE_IMAGE_FIRST ->
            rows.sortedWith(compareBy<DevicePhotoRow> { it.isVideo }.thenByDescending { it.dateModified })
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
