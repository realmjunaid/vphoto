package com.vphoto.app.ui.home

import android.content.Context
import android.net.Uri
import android.widget.Toast

/**
 * Home is now the auto device gallery ([com.vphoto.app.ui.gallery.DeviceGalleryScreen]):
 * all device photos show folder-wise right after install, no manual folder pick.
 * This file keeps the shared folder-pick helper below.
 */

const val ACCESS_MAY_NOT_PERSIST_MESSAGE = "VPhoto can open this folder now, but may lose access after a restart."

/**
 * The folder picker's result: persist access (warning with a Toast if the provider refuses),
 * then add the folder to Recents and open it, as before. Navigation stays synchronous.
 */
internal fun handlePickedFolder(
    context: Context,
    uri: Uri,
    takeAccess: (Uri) -> Boolean,
    onFolderPicked: (Uri) -> Unit,
    onFolderSelected: (Uri) -> Unit,
) {
    if (!takeAccess(uri)) {
        Toast.makeText(context, ACCESS_MAY_NOT_PERSIST_MESSAGE, Toast.LENGTH_LONG).show()
    }
    onFolderPicked(uri)
    onFolderSelected(uri)
}
