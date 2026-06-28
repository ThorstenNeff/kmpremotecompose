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
 * REM-76 (Epic C wasmJs): Compose for Web (wasmJs) is Skiko (CanvasKit) like iOS → 1:1 the iOS impl (REM-56).
 * The live web surface is alpha-backed, so a SRC_OUT/CLEAR with a transparent source punches a hole instead
 * of compositing to black. Render into an explicitly OPAQUE Skia raster surface (RGB_888X has no alpha channel
 * → α=0 resolves to opaque black, matching the upstream opaque View canvas) then blit the snapshot.
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
    val surface = Surface.makeRaster(ImageInfo(widthPx, heightPx, ColorType.RGB_888X, ColorAlphaType.OPAQUE))
    surface.canvas.clear(clearColor)
    block(surface.canvas.asComposeCanvas())
    screenCanvas.drawImage(surface.makeImageSnapshot().toComposeImageBitmap(), Offset.Zero, Paint())
}
