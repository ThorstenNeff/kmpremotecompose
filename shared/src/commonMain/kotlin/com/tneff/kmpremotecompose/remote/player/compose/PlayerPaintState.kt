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
package com.tneff.kmpremotecompose.remote.player.compose

import androidx.compose.ui.graphics.Paint

/**
 * REM-32 (Flag-2, option A) — the **shared** player paint state: the one [Paint] the geometry adapter
 * (L2-S2, dev-2) draws with, plus the text attributes ([textSizePx], [typefaceId]) the text renderer
 * (L2-S3, dev-1) reads. [ComposePaintContext] holds one instance and hands it to both halves, so a
 * `PAINT_VALUES` bundle and `savePaint`/`restorePaint` affect geometry and text consistently — and
 * `TEXT_SIZE`/`TYPEFACE` are applied instead of being dropped in the applier's `deferred` set.
 *
 * `save`/`restore` snapshot **all three** fields together (upstream `savePaint` stacks the whole paint).
 *
 * 🚩 Contract note (flagged to PO): the upstream `TYPEFACE` op also carries weight/italic/ttf bits
 * (packed in the command's high half); [typefaceId] captures only the font id. If dev-1's text needs
 * weight/italic this state grows by two fields — coordinate before S3-final.
 */
class PlayerPaintState {
    /** The geometry paint (color/stroke/style/alpha/blend/shader/…). Replaced wholesale on restore. */
    var paint: Paint = Paint()

    /** Text size in pixels (`TEXT_SIZE`); read by the text renderer (L2-S3). */
    var textSizePx: Float = DEFAULT_TEXT_SIZE_PX

    /** Font id (`TYPEFACE` font-type / id); read by the text renderer (L2-S3). `0` = default. */
    var typefaceId: Int = DEFAULT_TYPEFACE_ID

    /** Font style (REM-37: `0` = normal, `1` = italic); read by the text renderer (deriveTextStyle, dev-1). */
    var fontStyle: Int = DEFAULT_FONT_STYLE

    /** Font weight (REM-37: CSS 100–900; `0` = renderer default); read by the text renderer (dev-1). */
    var fontWeight: Int = DEFAULT_FONT_WEIGHT

    private val stack = ArrayDeque<Snapshot>()

    /** Push a snapshot of all fields (upstream `savePaint`). */
    fun save() {
        stack.addLast(Snapshot(paint.copyOf(), textSizePx, typefaceId, fontStyle, fontWeight))
    }

    /** Pop the last snapshot, restoring all fields (upstream `restorePaint`); no-op if empty. */
    fun restore() {
        val s = stack.removeLastOrNull() ?: return
        paint = s.paint
        textSizePx = s.textSizePx
        typefaceId = s.typefaceId
        fontStyle = s.fontStyle
        fontWeight = s.fontWeight
    }

    /** Reset to defaults (upstream `reset`). */
    fun reset() {
        paint = Paint()
        textSizePx = DEFAULT_TEXT_SIZE_PX
        typefaceId = DEFAULT_TYPEFACE_ID
        fontStyle = DEFAULT_FONT_STYLE
        fontWeight = DEFAULT_FONT_WEIGHT
    }

    private class Snapshot(
        val paint: Paint, val textSizePx: Float, val typefaceId: Int, val fontStyle: Int, val fontWeight: Int,
    )

    companion object {
        const val DEFAULT_TEXT_SIZE_PX: Float = 16f
        const val DEFAULT_TYPEFACE_ID: Int = 0
        const val DEFAULT_FONT_STYLE: Int = 0
        const val DEFAULT_FONT_WEIGHT: Int = 0
    }
}
