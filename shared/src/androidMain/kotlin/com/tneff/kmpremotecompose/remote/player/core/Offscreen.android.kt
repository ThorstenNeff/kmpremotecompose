/*
 * Copyright 2026 The KmpRemoteCompose Authors
 * Licensed under the Apache License, Version 2.0.
 */
package com.tneff.kmpremotecompose.remote.player.core

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap

/** Android: a direct software-raster `Canvas(ImageBitmap)` — writes are immediate, no flush needed. */
private class AndroidOffscreen(width: Int, height: Int) : Offscreen {
    private val bitmap = ImageBitmap(width, height)
    override val canvas: Canvas = Canvas(bitmap)
    override fun snapshot(): ImageBitmap = bitmap
}

internal actual fun createOffscreen(width: Int, height: Int): Offscreen =
    AndroidOffscreen(width, height)
