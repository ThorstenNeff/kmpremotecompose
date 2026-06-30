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
        // REM-156 overflow policy: long anchored text used to run off the surface edge with no
        // wrap/ellipsis/indicator (audit-P1). When the run, placed at its left edge px, would extend past
        // the document's right edge (ID_WINDOW_WIDTH, seeded per render), draw it ellipsized to the width
        // that remains (px → doc-right) instead of clipping edgelessly. Guarded so the common fitting case
        // (and any render where the window width isn't seeded → 0) takes the unchanged path → golden-safe.
        // Left-edge (px < 0) / vertical overflow keep today's behavior (flagged follow-up: leading-ellipsis).
        val windowWidth = context.getFloat(RemoteContext.ID_WINDOW_WIDTH)
        if (windowWidth > 0f && px >= 0f && px + textWidth > windowWidth) {
            paint.drawTextRunClipped(textId, 0, -1, px, py, rtl, windowWidth - px)
        } else {
            paint.drawTextRun(textId, 0, -1, 0, 1, px, py, rtl)
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
