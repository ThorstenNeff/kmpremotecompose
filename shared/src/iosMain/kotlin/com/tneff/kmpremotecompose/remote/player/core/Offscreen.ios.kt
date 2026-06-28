/*
 * Copyright 2026 The KmpRemoteCompose Authors
 * Licensed under the Apache License, Version 2.0.
 */
package com.tneff.kmpremotecompose.remote.player.core

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Surface

/**
 * iOS: draw into a Skia raster `Surface`; [snapshot]'s `makeImageSnapshot()` forces the flush before the
 * tiled `DRAW_BITMAP_SCALED` reads the result — the fix for the intermittently-empty offscreen on iOS.
 * N32/PREMUL keeps the alpha channel (offscreen content may be partly transparent before it is blitted).
 */
private class IosOffscreen(width: Int, height: Int) : Offscreen {
    private val surface = Surface.makeRaster(ImageInfo(width, height, ColorType.N32, ColorAlphaType.PREMUL))
    override val canvas: Canvas = surface.canvas.asComposeCanvas()
    override fun snapshot(): ImageBitmap = surface.makeImageSnapshot().toComposeImageBitmap()
}

internal actual fun createOffscreen(width: Int, height: Int): Offscreen =
    IosOffscreen(width, height)
