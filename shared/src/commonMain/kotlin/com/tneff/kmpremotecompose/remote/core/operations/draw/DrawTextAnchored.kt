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

import com.tneff.kmpremotecompose.remote.core.operations.Operation
import com.tneff.kmpremotecompose.remote.core.operations.OperationReader
import com.tneff.kmpremotecompose.remote.core.operations.Operations
import com.tneff.kmpremotecompose.remote.player.core.PaintContext
import com.tneff.kmpremotecompose.remote.player.core.PaintOperation
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.wire.WireBuffer
import com.tneff.kmpremotecompose.remote.wire.WireTypes

/**
 * `DRAW_TEXT_ANCHOR` (opcode [Operations.DRAW_TEXT_ANCHOR]) — draw a text resource anchored at a
 * point with pan factors.
 *
 * Wire layout: opcode byte + int `textId` + float `x` + float `y` + float `panX` + float `panY` +
 * int `flags` = 25 bytes (mirrors upstream `DrawTextAnchored.apply`/`read`). The floats may carry
 * NaN-encoded ids; raw bits preserved.
 */
class DrawTextAnchored(
    val textId: Int,
    val x: Float,
    val y: Float,
    val panX: Float,
    val panY: Float,
    val flags: Int,
) : PaintOperation {

    override val opcode: Int get() = Operations.DRAW_TEXT_ANCHOR

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(textId)
        buffer.writeFloat(x)
        buffer.writeFloat(y)
        buffer.writeFloat(panX)
        buffer.writeFloat(panY)
        buffer.writeInt(flags)
    }

    /**
     * Render anchored at `(x, y)` by pan factors (upstream `DrawTextAnchored.paint`): measure the run,
     * offset by `panX` (-1=left … 1=right) / `panY` (-1=top … 1=bottom; NaN ⇒ keep `y`), then
     * [PaintContext.drawTextRun]. Offsets mirror upstream `getHorizontalOffset`/`getVerticalOffset`
     * (box size 0 → text-relative). `BASELINE_RELATIVE` centres on the baseline.
     */
    override fun paint(context: RemoteContext, paint: PaintContext) {
        val measureFlags = if (flags and ANCHOR_MONOSPACE_MEASURE != 0) {
            PaintContext.TEXT_MEASURE_MONOSPACE_WIDTH
        } else {
            0
        }
        // Resolve NaN-encoded variable refs for the anchor position (REM-54): clock/chart number text
        // anchors at computed var-coords (e.g. jclock2 x→id92=250). Without this the raw NaN passes
        // through → NaN position → text drawn off-screen/invisible. panX/panY are pan factors, not
        // coord refs (panY NaN keeps its sentinel meaning below), so they are not resolved here.
        val rx = if (x.isNaN()) context.getFloat(WireTypes.idFromNan(x)) else x
        val ry = if (y.isNaN()) context.getFloat(WireTypes.idFromNan(y)) else y
        val bounds = FloatArray(4)
        paint.getTextBounds(textId, 0, -1, measureFlags, bounds)
        val textWidth = bounds[2] - bounds[0]
        val textHeight = bounds[3] - bounds[1]
        val hOffset = -textWidth * (1f + panX) / 2f - bounds[0]
        val px = rx + hOffset
        val py = if (panY.isNaN()) {
            ry
        } else {
            val baselineRelative = flags and BASELINE_RELATIVE != 0
            val vOffset = -textHeight * (1f - panY) / 2f +
                if (baselineRelative) textHeight / 2f else -bounds[1]
            ry + vOffset
        }
        val rtl = flags and ANCHOR_TEXT_RTL == 1
        val windowWidth = context.getFloat(RemoteContext.ID_WINDOW_WIDTH)
        val windowHeight = context.getFloat(RemoteContext.ID_WINDOW_HEIGHT)
        // REM-160 vertical-overflow policy: a single line ENTIRELY off-screen vertically (and that fits) is
        // nudged back into view (it rendered nothing before). Partially-visible text is left untouched → no
        // golden churn. Single-line text has no vertical ellipsis — repositioning is the policy.
        val cpy = clampBaselineIntoView(py, bounds[1], bounds[3], windowHeight)
        // REM-156/REM-160 overflow policy: long anchored text used to run off the surface edge with no
        // wrap/ellipsis/indicator (audit-P1). REM-156 handled only right-overflow with px ≥ 0; REM-160
        // generalizes to ALL directions. The right-overflow-only branch is byte-identical to REM-156 so its
        // already-rebaselined goldens don't re-shift; only left/centered overflow (px < 0, which REM-156
        // skipped → edgeless clip) is new. Guarded so the fitting case (and unseeded window → 0) takes the
        // unchanged plain path → golden-safe for non-overflowing text.
        if (windowWidth > 0f) {
            val overLeft = px < 0f
            val overRight = px + textWidth > windowWidth
            when {
                // Wider than the whole window (centered / both-sides overflow): fill it from the left edge,
                // trailing-ellipsize (keep the start of the string).
                overLeft && overRight ->
                    paint.drawTextRunClipped(textId, 0, -1, 0f, cpy, rtl, windowWidth, leadingEllipsis = false)
                // Right overflow only — UNCHANGED from REM-156: keep left edge px, trailing ellipsis.
                overRight ->
                    paint.drawTextRunClipped(textId, 0, -1, px, cpy, rtl, windowWidth - px, leadingEllipsis = false)
                // Left overflow only (REM-160 new): keep the right edge, leading-ellipsize, visible from x=0.
                overLeft ->
                    paint.drawTextRunClipped(textId, 0, -1, 0f, cpy, rtl, px + textWidth, leadingEllipsis = true)
                else ->
                    paint.drawTextRun(textId, 0, -1, 0, 1, px, cpy, rtl)
            }
        } else {
            paint.drawTextRun(textId, 0, -1, 0, 1, px, cpy, rtl)
        }
    }

    /**
     * REM-160 vertical-overflow clamp: if [windowHeight] is known and the line's vertical band
     * `[py + top, py + bottom]` (top = `bounds[1]` ≤ 0, bottom = `bounds[3]` ≥ 0) sits ENTIRELY above
     * (band fully < 0) or below (fully > [windowHeight]) the surface, shift the baseline minimally so the
     * band re-enters view. Partially-visible (or unknown height) ⇒ unchanged (no golden churn).
     */
    private fun clampBaselineIntoView(py: Float, top: Float, bottom: Float, windowHeight: Float): Float {
        if (windowHeight <= 0f) return py
        val bandTop = py + top
        val bandBottom = py + bottom
        return when {
            bandBottom < 0f -> py - bandBottom                      // fully above → bottom edge to 0
            bandTop > windowHeight -> py - (bandTop - windowHeight) // fully below → top edge to windowHeight
            else -> py
        }
    }

    override fun dump(): String = "DRAW_TEXT_ANCHOR textId=$textId x=$x y=$y panX=$panX panY=$panY flags=$flags"

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is DrawTextAnchored &&
                textId == other.textId &&
                x.toRawBits() == other.x.toRawBits() && y.toRawBits() == other.y.toRawBits() &&
                panX.toRawBits() == other.panX.toRawBits() && panY.toRawBits() == other.panY.toRawBits() &&
                flags == other.flags
            )

    override fun hashCode(): Int {
        var h = textId
        h = 31 * h + x.toRawBits()
        h = 31 * h + y.toRawBits()
        h = 31 * h + panX.toRawBits()
        h = 31 * h + panY.toRawBits()
        h = 31 * h + flags
        return h
    }

    companion object : OperationReader {
        /** Upstream anchor flag bits. */
        const val ANCHOR_TEXT_RTL = 1
        const val ANCHOR_MONOSPACE_MEASURE = 2
        const val BASELINE_RELATIVE = 8

        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += DrawTextAnchored(
                textId = buffer.readInt(),
                x = buffer.readFloat(),
                y = buffer.readFloat(),
                panX = buffer.readFloat(),
                panY = buffer.readFloat(),
                flags = buffer.readInt(),
            )
        }
    }
}
