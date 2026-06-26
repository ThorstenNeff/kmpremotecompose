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
package com.tneff.kmpremotecompose.remote.player.core

import kotlin.math.max
import kotlin.math.min

/**
 * `RootContentBehavior` **doc→surface scaling** (REM-36) — ports upstream `CoreDocument.computeScale`
 * / `computeTranslate`. The document authors its content in its own **doc-space** (header `width`x
 * `height`); the player renders into a possibly-different **surface** box. For `SIZING_SCALE`, the
 * player scales doc→surface (per the scale [mode]) and aligns the scaled content (per [alignment]) —
 * otherwise content renders 1:1 in doc-space (wrong proportions for a 600-doc in a 924-surface).
 *
 * `SIZING_LAYOUT` (surface-dim + reflow) is **deferred**; this covers the `SIZING_SCALE` quick-check.
 * Scope: scale/align only — pure math, byte-irrelevant.
 */
object ContentScaling {

    // upstream RootContentBehavior constants.
    const val SIZING_LAYOUT = 1
    const val SIZING_SCALE = 2

    const val SCALE_INSIDE = 1
    const val SCALE_FILL_WIDTH = 2
    const val SCALE_FILL_HEIGHT = 3
    const val SCALE_FIT = 4
    const val SCALE_CROP = 5
    const val SCALE_FILL_BOUNDS = 6

    const val ALIGNMENT_TOP = 1
    const val ALIGNMENT_VERTICAL_CENTER = 2
    const val ALIGNMENT_BOTTOM = 4
    const val ALIGNMENT_START = 16
    const val ALIGNMENT_HORIZONTAL_CENTER = 32
    const val ALIGNMENT_END = 64

    /**
     * The (scaleX, scaleY) to map doc-space ([docW]x[docH]) into the surface ([surfaceW]x[surfaceH])
     * for [sizing]/[mode] (upstream `computeScale`). Returns (1,1) when not `SIZING_SCALE` or dims are
     * non-positive. All modes are uniform except `SCALE_FILL_BOUNDS` (non-uniform stretch).
     */
    fun computeScale(
        surfaceW: Float, surfaceH: Float,
        docW: Float, docH: Float,
        sizing: Int, mode: Int,
    ): Pair<Float, Float> {
        if (sizing != SIZING_SCALE || docW <= 0f || docH <= 0f) return 1f to 1f
        val sx = surfaceW / docW
        val sy = surfaceH / docH
        return when (mode) {
            SCALE_INSIDE -> min(1f, min(sx, sy)).let { it to it }
            SCALE_FIT -> min(sx, sy).let { it to it }
            SCALE_FILL_WIDTH -> sx to sx
            SCALE_FILL_HEIGHT -> sy to sy
            SCALE_CROP -> max(sx, sy).let { it to it }
            SCALE_FILL_BOUNDS -> sx to sy
            else -> 1f to 1f
        }
    }

    /**
     * The (translateX, translateY) that aligns the scaled content within the surface (upstream
     * `computeTranslate`): horizontal bits `& 0xF0`, vertical bits `& 0xF`. Default = START/TOP (0).
     */
    fun computeTranslate(
        surfaceW: Float, surfaceH: Float,
        scaleX: Float, scaleY: Float,
        docW: Float, docH: Float,
        alignment: Int,
    ): Pair<Float, Float> {
        val contentW = docW * scaleX
        val contentH = docH * scaleY
        val tx = when (alignment and 0xF0) {
            ALIGNMENT_HORIZONTAL_CENTER -> (surfaceW - contentW) / 2f
            ALIGNMENT_END -> surfaceW - contentW
            else -> 0f
        }
        val ty = when (alignment and 0xF) {
            ALIGNMENT_VERTICAL_CENTER -> (surfaceH - contentH) / 2f
            ALIGNMENT_BOTTOM -> surfaceH - contentH
            else -> 0f
        }
        return tx to ty
    }
}
