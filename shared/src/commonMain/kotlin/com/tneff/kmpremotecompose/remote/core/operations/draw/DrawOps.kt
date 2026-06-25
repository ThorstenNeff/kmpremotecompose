/*
 * Copyright 2026 The KmpRemoteCompose Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tneff.kmpremotecompose.remote.core.operations.draw

import com.tneff.kmpremotecompose.remote.core.operations.Operations

/**
 * Registration entry point for the REM-5 (op-group B) draw operations.
 *
 * All of these are profile-independent base primitives (present in the API-6 map and the API-7 base
 * map upstream), so they register via [Operations.registerInBase] — the blessed path that registers
 * into V6 + V7_BASE **and** validates layer membership (catches a wrong-layer registration instead
 * of letting it slip to the reconcile test). Aggregated by `Rem5Ops`, triggered by `Builtins`.
 */
object DrawOps {

    /** Register all REM-5 draw-op readers into the base layers (membership-validated). */
    fun register() {
        Operations.registerInBase(Operations.DRAW_CIRCLE, DrawCircle)
        Operations.registerInBase(Operations.DRAW_RECT, DrawRect)
        Operations.registerInBase(Operations.DRAW_LINE, DrawLine)
        Operations.registerInBase(Operations.DRAW_OVAL, DrawOval)
        Operations.registerInBase(Operations.DRAW_ROUND_RECT, DrawRoundRect)
        Operations.registerInBase(Operations.DRAW_ARC, DrawArc)
        Operations.registerInBase(Operations.DRAW_SECTOR, DrawSector)
        Operations.registerInBase(Operations.PAINT_VALUES, PaintData)
        // P1 text/path draws.
        Operations.registerInBase(Operations.DRAW_TEXT_RUN, DrawText)
        Operations.registerInBase(Operations.DATA_PATH, PathData)
        Operations.registerInBase(Operations.DRAW_PATH, DrawPath)
        Operations.registerInBase(Operations.DRAW_TEXT_ANCHOR, DrawTextAnchored) // REM-16 (F3)
        // REM-24 R3 path family: PATH_CREATE + PATH_TWEEN (base), PATH_EXPRESSION (ANDROIDX + WIDGETS overlay).
        Operations.registerInBase(Operations.PATH_CREATE, PathCreate)
        Operations.registerInBase(Operations.PATH_TWEEN, PathTween)
        Operations.registerInOverlay(Operations.Layer.V7_ANDROIDX, Operations.PATH_EXPRESSION, PathExpression)
        Operations.registerInOverlay(Operations.Layer.V7_WIDGETS, Operations.PATH_EXPRESSION, PathExpression)
        // REM-25 R4: CLIP_RECT (39, base — draw clip ≠ MODIFIER_CLIP_RECT 108) + DRAW_BITMAP_SCALED (149, base)
        // + DRAW_TO_BITMAP (190, ANDROIDX + WIDGETS overlay).
        Operations.registerInBase(Operations.CLIP_RECT, ClipRect)
        Operations.registerInBase(Operations.DRAW_BITMAP_SCALED, DrawBitmapScaled)
        Operations.registerInOverlay(Operations.Layer.V7_ANDROIDX, Operations.DRAW_TO_BITMAP, DrawToBitmap)
        Operations.registerInOverlay(Operations.Layer.V7_WIDGETS, Operations.DRAW_TO_BITMAP, DrawToBitmap)
        // REM-26 R5 path/draw family (all base).
        Operations.registerInBase(Operations.PATH_ADD, PathAppend)
        Operations.registerInBase(Operations.DRAW_TWEEN_PATH, DrawTweenPath)
        Operations.registerInBase(Operations.DRAW_TEXT_ON_PATH, DrawTextOnPath)
        Operations.registerInBase(Operations.DRAW_CONTENT, DrawContent)
        // REM-28 R6 tail (all base): CLIP_PATH (38, ≠ CLIP_RECT 39), DRAW_BITMAP_FONT_TEXT_RUN (48),
        // PARTICLE_DEFINE (161, ParticlesCreate — PaintOperation → group B).
        Operations.registerInBase(Operations.CLIP_PATH, ClipPath)
        Operations.registerInBase(Operations.DRAW_BITMAP_FONT_TEXT_RUN, DrawBitmapFontText)
        Operations.registerInBase(Operations.PARTICLE_DEFINE, ParticlesCreate)
    }
}
