package com.vphoto.app

import android.net.Uri
import com.vphoto.app.data.gallery.DevicePhotoRow
import com.vphoto.app.data.gallery.groupIntoAlbums
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Auto device gallery grouping: flat MediaStore rows become folder-wise albums
 * (Google Photos style), cover = latest photo, sorted newest album first.
 */
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
}
