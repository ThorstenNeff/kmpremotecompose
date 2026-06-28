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
 * REM-76 (Epic C wasmJs): Compose for Web (wasmJs) is Skiko-backed (CanvasKit) like iOS/Desktop → the same
 * `org.jetbrains.skia` API → 1:1 the iOS impl (REM-60). Draw into a Skia raster Surface whose snapshot() =
 * makeImageSnapshot() forces the flush before the tiled DRAW_BITMAP_SCALED reads (a bare Canvas(ImageBitmap)
 * leaves the writes unflushed on Skiko). N32/PREMUL keeps the alpha channel.
 */
private class WasmJsOffscreen(width: Int, height: Int) : Offscreen {
    private val surface = Surface.makeRaster(ImageInfo(width, height, ColorType.N32, ColorAlphaType.PREMUL))
    override val canvas: Canvas = surface.canvas.asComposeCanvas()
    override fun snapshot(): ImageBitmap = surface.makeImageSnapshot().toComposeImageBitmap()
}

internal actual fun createOffscreen(width: Int, height: Int): Offscreen =
    WasmJsOffscreen(width, height)
