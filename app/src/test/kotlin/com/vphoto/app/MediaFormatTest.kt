package com.vphoto.app

import com.vphoto.app.data.gallery.formatBytes
import com.vphoto.app.data.gallery.formatMediaDate
import com.vphoto.app.data.gallery.formatResolution
import org.junit.Assert.assertEquals
import org.junit.Test

/** Formatting used by the details sheet: size, resolution and date. */
class MediaFormatTest {

    @Test
    fun formatBytes_scalesUnits() {
        assertEquals("—", formatBytes(0))
        assertEquals("—", formatBytes(-5))
        assertEquals("512 KB", formatBytes(512 * 1024L))
        assertEquals("2.4 MB", formatBytes((2.4 * 1024 * 1024).toLong()))
        assertEquals("812 KB", formatBytes(812 * 1024L))
    }

    @Test
    fun formatResolution_showsDimensionsOrDash() {
        assertEquals("4032 × 3024", formatResolution(4032, 3024))
        assertEquals("—", formatResolution(0, 0))
        assertEquals("—", formatResolution(100, 0))
    }

    @Test
    fun formatMediaDate_handlesBadInput() {
        assertEquals("—", formatMediaDate(0))
        assertEquals("—", formatMediaDate(-1))
    }
}
