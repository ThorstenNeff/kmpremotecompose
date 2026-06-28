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

/**
 * REM-78 follow-up — Op-level deferred/unsupported-feature tags collected during the paint pass
 * (`SHADER_UNSUPPORTED`, `SHADER_NO_SOURCE`, `STYLE_FILL_AND_STROKE`, `TEXTURE`, `FONT_AXIS`,
 * `PATH_EFFECT`, `GRADIENT_DEGENERATE`, `INVERSE_WINDING`, …). The third dispatch≠render
 * dimension after drawCount + pixel-diff: dispatch counted the op AND the pixel may even look OK
 * for fallback paths, but the op itself was deferred — surfaces feature-gap docs (shader, var-font,
 * texture) WITHOUT needing a pixel oracle.
 *
 * Lives in :shared because [GeometryPaintDelegate]'s `deferredPaintTags` is `internal` to its file.
 * Returns an empty set if the context has no geometry attached or the attached delegate is not a
 * [GeometryPaintDelegate] (e.g. a future stub geometry).
 */
fun deferredPaintTagsOf(pc: ComposePaintContext): Set<String> =
    (pc.geometry as? GeometryPaintDelegate)?.deferredPaintTags?.toSet().orEmpty()
