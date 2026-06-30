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

    /**
     * REM-158: the resolved `TYPEFACE` **name** when the op carried a `DATA_TEXT` name id (upstream:
     * `font_type > 10 && !ttf`), else `null` (the generic-enum case uses [typefaceId]). Read by the text
     * renderer's [NamedFontResolver] to pick a bundled/generic [androidx.compose.ui.text.font.FontFamily].
     */
    var typefaceName: String? = DEFAULT_TYPEFACE_NAME

    /** Font style (REM-37: `0` = normal, `1` = italic); read by the text renderer (deriveTextStyle, dev-1). */
    var fontStyle: Int = DEFAULT_FONT_STYLE

    /** Font weight (REM-37: CSS 100–900; `0` = renderer default); read by the text renderer (dev-1). */
    var fontWeight: Int = DEFAULT_FONT_WEIGHT

    /**
     * REM-94: `STYLE = FILL_AND_STROKE` (2) has no single CMP [androidx.compose.ui.graphics.PaintingStyle]
     * (only Fill/Stroke). When set, the geometry adapter draws each shape **twice** — a fill pass then a
     * stroke pass — matching upstream `Paint.Style.FILL_AND_STROKE`. `paint.style` carries Fill (the fill
     * pass default); this flag drives the extra stroke pass.
     */
    var fillAndStroke: Boolean = DEFAULT_FILL_AND_STROKE

    private val stack = ArrayDeque<Snapshot>()

    /** Push a snapshot of all fields (upstream `savePaint`). */
    fun save() {
        stack.addLast(
            Snapshot(paint.copyOf(), textSizePx, typefaceId, typefaceName, fontStyle, fontWeight, fillAndStroke),
        )
    }

    /** Pop the last snapshot, restoring all fields (upstream `restorePaint`); no-op if empty. */
    fun restore() {
        val s = stack.removeLastOrNull() ?: return
        paint = s.paint
        textSizePx = s.textSizePx
        typefaceId = s.typefaceId
        typefaceName = s.typefaceName
        fontStyle = s.fontStyle
        fontWeight = s.fontWeight
        fillAndStroke = s.fillAndStroke
    }

    /** Reset to defaults (upstream `reset`). */
    fun reset() {
        paint = Paint()
        textSizePx = DEFAULT_TEXT_SIZE_PX
        typefaceId = DEFAULT_TYPEFACE_ID
        typefaceName = DEFAULT_TYPEFACE_NAME
        fontStyle = DEFAULT_FONT_STYLE
        fontWeight = DEFAULT_FONT_WEIGHT
        fillAndStroke = DEFAULT_FILL_AND_STROKE
    }

    private class Snapshot(
        val paint: Paint, val textSizePx: Float, val typefaceId: Int, val typefaceName: String?,
        val fontStyle: Int, val fontWeight: Int,
        val fillAndStroke: Boolean,
    )

    companion object {
        const val DEFAULT_TEXT_SIZE_PX: Float = 16f
        const val DEFAULT_TYPEFACE_ID: Int = 0
        val DEFAULT_TYPEFACE_NAME: String? = null
        const val DEFAULT_FONT_STYLE: Int = 0
        const val DEFAULT_FONT_WEIGHT: Int = 0
        const val DEFAULT_FILL_AND_STROKE: Boolean = false
    }
}
