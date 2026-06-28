/*
 * Copyright 2026 The KmpRemoteCompose Authors
 * Licensed under the Apache License, Version 2.0.
 */
package com.tneff.kmpremotecompose.remote.player.compose

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.text.font.FontFamily
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext

/**
 * REM-78 — public test-harness wiring for the Desktop render sweep (`:desktopApp:desktopRenderSweep`).
 *
 * Builds a [ComposePaintContext] with the [GeometryPaintDelegate] attached (the same wiring the
 * production [com.tneff.kmpremotecompose.RemoteComposeApp] performs inline). The delegate stays
 * `internal` to its file; this helper is the single sanctioned cross-module entry-point so the
 * Desktop sweep harness in `:desktopApp` does not need to construct internal types directly.
 *
 * Production code does NOT use this — `RemoteComposeApp` builds the context inline by design (the
 * delegate seam stays free of factory indirection on the hot path).
 */
fun composePaintContextWithGeometry(
    context: RemoteContext,
    canvas: Canvas,
    fontFamilyResolver: FontFamily.Resolver?,
): ComposePaintContext {
    val pc = ComposePaintContext(context, canvas, fontFamilyResolver = fontFamilyResolver)
    pc.geometry = GeometryPaintDelegate(context, canvas, pc.paintState)
    return pc
}
