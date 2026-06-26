package com.tneff.kmpremotecompose

import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy

// REM-8/REM-34: render the bundled fixture selected by RcRouter (default or deep-link) from the bundle.
fun MainViewController() = ComposeUIViewController {
    RemoteComposeApp(loadRc = { name -> loadBundledRc(name) })
}

@OptIn(ExperimentalForeignApi::class)
private fun loadBundledRc(name: String): ByteArray {
    val bundle = NSBundle.mainBundle
    // Folder-reference (rc/<name>.rc) first, then flat fallback depending on how it's added to the target.
    val path = bundle.pathForResource(name, "rc", "rc")
        ?: bundle.pathForResource(name, "rc")
        ?: error("bundled rc not found: $name.rc")
    val data = NSData.dataWithContentsOfFile(path) ?: error("cannot read $path")
    val length = data.length.toInt()
    val bytes = ByteArray(length)
    if (length > 0) {
        bytes.usePinned { pinned -> memcpy(pinned.addressOf(0), data.bytes, data.length) }
    }
    return bytes
}
