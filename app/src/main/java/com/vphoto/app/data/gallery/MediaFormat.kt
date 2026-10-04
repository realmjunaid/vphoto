package com.vphoto.app.data.gallery

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** "2.4 MB" / "812 KB" style sizes for the info sheet. */
internal fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "—"
    val kb = bytes / 1024.0
    if (kb < 1024) return "${if (kb >= 100) kb.toInt() else String.format(Locale.US, "%.1f", kb)} KB"
    val mb = kb / 1024.0
    if (mb < 1024) return "${if (mb >= 100) mb.toInt() else String.format(Locale.US, "%.1f", mb)} MB"
    val gb = mb / 1024.0
    return "${String.format(Locale.US, "%.2f", gb)} GB"
}

/** "4032 × 3024" or "—" when dimensions are unknown. */
internal fun formatResolution(width: Int, height: Int): String =
    if (width > 0 && height > 0) "$width × $height" else "—"

/** "12 May 2025, 14:30" style timestamps for the info sheet. */
internal fun formatMediaDate(epochSeconds: Long): String {
    if (epochSeconds <= 0) return "—"
    return try {
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            .format(Date(epochSeconds * 1000))
    } catch (_: Exception) {
        "—"
    }
}
