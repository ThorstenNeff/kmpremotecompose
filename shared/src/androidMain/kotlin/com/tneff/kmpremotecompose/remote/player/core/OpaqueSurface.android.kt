/*
 * Copyright 2026 The KmpRemoteCompose Authors
 * Licensed under the Apache License, Version 2.0.
 */
package com.tneff.kmpremotecompose.remote.player.core

import androidx.compose.ui.graphics.Canvas

/** REM-56: Android's Compose/View canvas is already opaque (SRC_OUT→black) — render direct, no buffer. */
actual fun renderOpaque(
    screenCanvas: Canvas,
    widthPx: Int,
    heightPx: Int,
    clearColor: Int,
    block: (Canvas) -> Unit,
) = block(screenCanvas)
