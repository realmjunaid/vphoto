package com.vphoto.app

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vphoto.app.data.gallery.DevicePhotoRow
import com.vphoto.app.data.gallery.groupIntoAlbums
import com.vphoto.app.data.gallery.sortDevicePhotos
import com.vphoto.app.data.preferences.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Auto device gallery grouping: flat MediaStore rows become folder-wise albums
 * (Google Photos style), cover = latest photo, sorted newest album first.
 */
@RunWith(AndroidJUnit4::class)
class DeviceAlbumTest {

    private fun row(id: Long, bucket: String, date: Long): DevicePhotoRow =
        DevicePhotoRow(
            id = id,
            uri = Uri.parse("content://media/external/images/media/$id"),
            name = "IMG_$id.jpg",
            bucketName = bucket,
            dateModified = date,
            size = 1000L
        )

    @Test
    fun emptyRows_yieldNoAlbums() {
        assertTrue(groupIntoAlbums(emptyList()).isEmpty())
    }

    @Test
    fun rows_groupByBucketWithCountsAndLatestCover() {
        val rows = listOf(
            row(1, "Camera", 1000L),
            row(2, "Camera", 3000L),
            row(3, "Download", 2000L)
        )
        val albums = groupIntoAlbums(rows)
        assertEquals(2, albums.size)
        // Newest album first: Camera (3000) before Download (2000).
        assertEquals("Camera", albums[0].name)
        assertEquals(2, albums[0].count)
        assertEquals("content://media/external/images/media/2", albums[0].coverUri.toString())
        assertEquals("Download", albums[1].name)
        assertEquals(1, albums[1].count)
    }

    @Test
    fun blankBucket_fallsBackToUnknown() {
        val albums = groupIntoAlbums(listOf(row(1, "", 1000L)))
        assertEquals(1, albums.size)
        assertEquals("Unknown", albums[0].name)
    }

    @Test
    fun albums_alwaysDateWiseRegardlessOfRowOrder() {
        // Home folders never follow Settings sort: newest activity first, always.
        val rows = listOf(
            row(1, "Camera", 1000L),
            row(2, "Camera", 3000L),
            row(3, "Download", 2000L)
        )
        val forward = groupIntoAlbums(rows).map { it.name }
        val reversed = groupIntoAlbums(rows.reversed()).map { it.name }
        val shuffled = groupIntoAlbums(listOf(rows[2], rows[0], rows[1])).map { it.name }
        assertEquals(listOf("Camera", "Download"), forward)
        assertEquals(forward, reversed)
        assertEquals(forward, shuffled)
    }

    @Test
    fun sort_nameAsc_ordersCaseInsensitively() {        val rows = listOf(
            DevicePhotoRow(1, Uri.parse("content://m/1"), "b.jpg", "C", 1000L, 10L),
            DevicePhotoRow(2, Uri.parse("content://m/2"), "A.jpg", "C", 3000L, 30L),
            DevicePhotoRow(3, Uri.parse("content://m/3"), "c.jpg", "C", 2000L, 20L),
        )
        val names = sortDevicePhotos(rows, SortOrder.NAME_ASC).map { it.name }
        assertEquals(listOf("A.jpg", "b.jpg", "c.jpg"), names)
        val newestFirst = sortDevicePhotos(rows, SortOrder.DATE_NEWEST).map { it.id }
        assertEquals(listOf(2L, 3L, 1L), newestFirst)
        val smallestFirst = sortDevicePhotos(rows, SortOrder.SIZE_SMALLEST).map { it.id }
        assertEquals(listOf(1L, 3L, 2L), smallestFirst)
    }
}
