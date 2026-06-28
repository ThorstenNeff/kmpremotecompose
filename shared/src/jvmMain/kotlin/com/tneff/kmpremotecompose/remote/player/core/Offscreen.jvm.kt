/*
 * Copyright 2026 The KmpRemoteCompose Authors
 * Licensed under the Apache License, Version 2.0.
 */
package com.tneff.kmpremotecompose.remote.player.core

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap

/**
 * jvm is the headless conformance harness only — bitmap docs are not rendered there (Skiko can't decode
 * bitmaps in headless), so this path is never exercised. Direct raster keeps parity with Android and
 * never needs the Skia flush.
 */
private class JvmOffscreen(width: Int, height: Int) : Offscreen {
    private val bitmap = ImageBitmap(width, height)
    override val canvas: Canvas = Canvas(bitmap)
    override fun snapshot(): ImageBitmap = bitmap
}

internal actual fun createOffscreen(width: Int, height: Int): Offscreen =
    JvmOffscreen(width, height)
