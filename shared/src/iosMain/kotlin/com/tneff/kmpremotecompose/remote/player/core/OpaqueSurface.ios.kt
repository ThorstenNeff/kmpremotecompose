/*
 * Copyright 2026 The KmpRemoteCompose Authors
 * Licensed under the Apache License, Version 2.0.
 */
package com.tneff.kmpremotecompose.remote.player.core

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Surface

/**
 * REM-56: iOS's live Compose/Skia surface is alpha-backed, so SRC_OUT/CLEAR-with-transparent punches a
 * hole instead of compositing to black. Render into an explicitly OPAQUE Skia raster surface
 * (ColorAlphaType.OPAQUE) then blit the snapshot — matching upstream's opaque Android View canvas.
 */
actual fun renderOpaque(
    screenCanvas: Canvas,
    widthPx: Int,
    heightPx: Int,
    clearColor: Int,
    block: (Canvas) -> Unit,
) {
    if (widthPx <= 0 || heightPx <= 0) {
        block(screenCanvas)
        return
    }
    // RGB_888X has NO alpha channel: a SRC_OUT/CLEAR with a transparent source can't store α=0, so it
    // resolves to opaque black (matching upstream's opaque View canvas) instead of a transparent hole
    // (N32/RGBA keeps the alpha channel → the hole stays transparent → blits as white).
    val surface = Surface.makeRaster(ImageInfo(widthPx, heightPx, ColorType.RGB_888X, ColorAlphaType.OPAQUE))
    surface.canvas.clear(clearColor)
    block(surface.canvas.asComposeCanvas())
    screenCanvas.drawImage(surface.makeImageSnapshot().toComposeImageBitmap(), Offset.Zero, Paint())
}
