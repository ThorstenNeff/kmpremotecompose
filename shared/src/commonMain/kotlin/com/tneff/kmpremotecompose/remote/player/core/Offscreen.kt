/*
 * Copyright 2026 The KmpRemoteCompose Authors
 * Licensed under the Apache License, Version 2.0.
 */
package com.tneff.kmpremotecompose.remote.player.core

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap

/**
 * REM-60: a platform offscreen draw target for render-to-bitmap (`DRAW_TO_BITMAP`).
 *
 * The previous impl drew into a bare `Canvas(ImageBitmap)` and read the same bitmap back via the tiled
 * `DRAW_BITMAP_SCALED` ops with **no flush in between**. On iOS/Skiko those writes go through a Skia
 * Surface that must be flushed before the bitmap reflects them, so the reads intermittently landed on an
 * empty offscreen (bit_draw2 → drawCount 0 → `rc-rendered` unset). Android's `Canvas(ImageBitmap)` is a
 * direct software raster (immediate), so it never showed the bug.
 *
 * [snapshot] is the seam: on iOS it does `makeImageSnapshot()` which forces the flush; on Android/jvm it
 * returns the raster bitmap as-is.
 */
internal interface Offscreen {
    val canvas: Canvas

    /** Force-flush pending draws and return the finished image (the iOS flush fix). */
    fun snapshot(): ImageBitmap
}

/**
 * Create a fresh [width]×[height] offscreen target. iOS = a Skia raster `Surface` (flushed via
 * [Offscreen.snapshot]); Android/jvm = a direct-raster `Canvas(ImageBitmap)`.
 */
internal expect fun createOffscreen(width: Int, height: Int): Offscreen
