package com.tneff.kmpremotecompose

import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy

// REM-8: render the bundled default fixture from the iOS app bundle (path A, no params).
fun MainViewController() = ComposeUIViewController {
    RemoteComposeApp { loadBundledRc("procedure_simple1") }
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
