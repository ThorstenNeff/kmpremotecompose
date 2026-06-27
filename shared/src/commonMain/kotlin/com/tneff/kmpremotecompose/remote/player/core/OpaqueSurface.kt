/*
 * Copyright 2026 The KmpRemoteCompose Authors
 * Licensed under the Apache License, Version 2.0.
 */
package com.tneff.kmpremotecompose.remote.player.core

import androidx.compose.ui.graphics.Canvas

/**
 * REM-56: render [block] onto an **opaque** surface, then composite to [screenCanvas]. An opaque (no
 * alpha channel) surface makes a SRC_OUT / CLEAR draw with a transparent source resolve to black —
 * matching upstream's opaque Android View canvas — so iOS (whose live Compose/Skia surface is alpha-
 * backed) renders blend-on-surface docs identically to Android.
 *
 * - Android / JVM: the destination canvas is already opaque → render direct (no buffer, no change).
 * - iOS: render into an opaque Skia raster surface (ColorAlphaType.OPAQUE), then blit the snapshot.
 *
 * [clearColor] (ARGB) fills the surface first (the bg behind the doc); only meaningful for the iOS
 * opaque path. Surface/compositing only — the document render path inside [block] is unchanged.
 */
expect fun renderOpaque(
    screenCanvas: Canvas,
    widthPx: Int,
    heightPx: Int,
    clearColor: Int,
    block: (Canvas) -> Unit,
)
