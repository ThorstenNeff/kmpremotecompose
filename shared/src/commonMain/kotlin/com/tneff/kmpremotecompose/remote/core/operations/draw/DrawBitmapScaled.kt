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
 * `DRAW_BITMAP_SCALED` (opcode [Operations.DRAW_BITMAP_SCALED]) — draws a bitmap from a src rect into a
 * dst rect with a scale type.
 *
 * Wire layout: opcode byte + int `imageId` + float src(l,t,r,b) + float dst(l,t,r,b) + int `scaleType`
 * + float `scaleFactor` + int `contentDescriptionId` = 49 bytes (mirrors upstream
 * `DrawBitmapScaled.apply`/`read`). Floats may carry NaN-encoded ids; raw bits preserved.
 */
class DrawBitmapScaled(
    val imageId: Int,
    val srcLeft: Float,
    val srcTop: Float,
    val srcRight: Float,
    val srcBottom: Float,
    val dstLeft: Float,
    val dstTop: Float,
    val dstRight: Float,
    val dstBottom: Float,
    val scaleType: Int,
    val scaleFactor: Float,
    val contentDescriptionId: Int,
) : Operation, PaintOperation {

    override val opcode: Int get() = Operations.DRAW_BITMAP_SCALED

    /**
     * REM-40: draw [imageId] from its src rect into the dst rect, aspect-adjusted by [scaleType]
     * (mirrors upstream `DrawBitmapScaled.paint` + `ImageScaling`): clip to the original dst, draw the
     * bitmap into the scaleType-adjusted (centered/letterboxed) dst. src/dst floats may be NaN var-refs
     * → resolved against the store. Fail-soft: an unknown bitmap id is a no-op in the delegate.
     */
    override fun paint(context: RemoteContext, paint: PaintContext) {
        fun r(v: Float) = if (v.isNaN() && !WireTypes.isOperationVariable(v)) context.getFloat(WireTypes.idFromNan(v)) else v
        val sl = r(srcLeft); val st = r(srcTop); val sr = r(srcRight); val sb = r(srcBottom)
        val dl = r(dstLeft); val dt = r(dstTop); val dr = r(dstRight); val db = r(dstBottom)
        val f = scaledDst(sl, st, sr, sb, dl, dt, dr, db, scaleType, scaleFactor)
        paint.save()
        paint.clipRect(dl, dt, dr, db)
        paint.drawBitmap(
            imageId,
            sl.toInt(), st.toInt(), sr.toInt(), sb.toInt(),
            f[0], f[1], f[2], f[3],
            contentDescriptionId,
        )
        paint.restore()
    }

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(imageId)
        buffer.writeFloat(srcLeft)
        buffer.writeFloat(srcTop)
        buffer.writeFloat(srcRight)
        buffer.writeFloat(srcBottom)
        buffer.writeFloat(dstLeft)
        buffer.writeFloat(dstTop)
        buffer.writeFloat(dstRight)
        buffer.writeFloat(dstBottom)
        buffer.writeInt(scaleType)
        buffer.writeFloat(scaleFactor)
        buffer.writeInt(contentDescriptionId)
    }

    override fun dump(): String =
        "DRAW_BITMAP_SCALED imageId=$imageId src=($srcLeft,$srcTop,$srcRight,$srcBottom) " +
            "dst=($dstLeft,$dstTop,$dstRight,$dstBottom) scaleType=$scaleType scaleFactor=$scaleFactor cdId=$contentDescriptionId"

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is DrawBitmapScaled &&
                imageId == other.imageId &&
                srcLeft.toRawBits() == other.srcLeft.toRawBits() && srcTop.toRawBits() == other.srcTop.toRawBits() &&
                srcRight.toRawBits() == other.srcRight.toRawBits() && srcBottom.toRawBits() == other.srcBottom.toRawBits() &&
                dstLeft.toRawBits() == other.dstLeft.toRawBits() && dstTop.toRawBits() == other.dstTop.toRawBits() &&
                dstRight.toRawBits() == other.dstRight.toRawBits() && dstBottom.toRawBits() == other.dstBottom.toRawBits() &&
                scaleType == other.scaleType && scaleFactor.toRawBits() == other.scaleFactor.toRawBits() &&
                contentDescriptionId == other.contentDescriptionId
            )

    override fun hashCode(): Int {
        var h = imageId
        h = 31 * h + srcLeft.toRawBits()
        h = 31 * h + srcTop.toRawBits()
        h = 31 * h + srcRight.toRawBits()
        h = 31 * h + srcBottom.toRawBits()
        h = 31 * h + dstLeft.toRawBits()
        h = 31 * h + dstTop.toRawBits()
        h = 31 * h + dstRight.toRawBits()
        h = 31 * h + dstBottom.toRawBits()
        h = 31 * h + scaleType
        h = 31 * h + scaleFactor.toRawBits()
        h = 31 * h + contentDescriptionId
        return h
    }

    companion object : OperationReader {
        // Scale types (upstream ImageScaling.SCALE_*).
        private const val SCALE_NONE = 0
        private const val SCALE_INSIDE = 1
        private const val SCALE_FILL_WIDTH = 2
        private const val SCALE_FILL_HEIGHT = 3
        private const val SCALE_FIT = 4
        private const val SCALE_CROP = 5
        private const val SCALE_FILL_BOUNDS = 6
        private const val SCALE_FIXED_SCALE = 7

        /**
         * Compute the scaleType-adjusted destination rect `[left, top, right, bottom]` (verbatim port of
         * upstream `ImageScaling.adjustDrawToType`): fit/crop/fill preserving or filling aspect, centered
         * within the original dst. Returns the original dst on a degenerate source.
         */
        fun scaledDst(
            sl: Float, st: Float, sr: Float, sb: Float,
            dl: Float, dt: Float, dr: Float, db: Float,
            type: Int, scale: Float,
        ): IntArray {
            val sw = (sr - sl).toInt()
            val sh = (sb - st).toInt()
            val width = dr - dl
            val height = db - dt
            val w = width.toInt()
            val h = height.toInt()
            var dw = w
            var dh = h
            var dLeft = 0
            var dRight = dw
            var dTop = 0
            var dBottom = dh
            if (sw != 0 && sh != 0) {
                when (type) {
                    SCALE_NONE -> { dh = sh; dw = sw }
                    SCALE_INSIDE ->
                        if (dh > sh && dw > sw) { dh = sh; dw = sw }
                        else if (sw * height > width * sh) dh = dw * sh / sw else dw = dh * sw / sh
                    SCALE_FILL_WIDTH -> dh = dw * sh / sw
                    SCALE_FILL_HEIGHT -> dw = dh * sw / sh
                    SCALE_FIXED_SCALE -> { dh = (sh * scale).toInt(); dw = (sw * scale).toInt() }
                    SCALE_FIT -> {
                        if (sw * height > width * sh) { dh = dw * sh / sw; dTop = (h - dh) / 2; dBottom = dh + dTop; return out(dLeft, dTop, dRight, dBottom, dl, dt) }
                        else { dw = dh * sw / sh; dLeft = (w - dw) / 2; dRight = dw + dLeft; return out(dLeft, dTop, dRight, dBottom, dl, dt) }
                    }
                    SCALE_CROP -> {
                        if (sw * height < width * sh) { dh = dw * sh / sw; dTop = (h - dh) / 2; dBottom = dh + dTop; return out(dLeft, dTop, dRight, dBottom, dl, dt) }
                        else { dw = dh * sw / sh; dLeft = (w - dw) / 2; dRight = dw + dLeft; return out(dLeft, dTop, dRight, dBottom, dl, dt) }
                    }
                    SCALE_FILL_BOUNDS -> return out(dLeft, dTop, dRight, dBottom, dl, dt) // dst as-is
                }
                // shared centering for NONE/INSIDE/FILL_*/FIXED:
                dTop = (h - dh) / 2; dBottom = dh + dTop; dLeft = (w - dw) / 2; dRight = dw + dLeft
            }
            return out(dLeft, dTop, dRight, dBottom, dl, dt)
        }

        private fun out(dLeft: Int, dTop: Int, dRight: Int, dBottom: Int, dl: Float, dt: Float): IntArray {
            val ol = dl.toInt(); val ot = dt.toInt()
            return intArrayOf(dLeft + ol, dTop + ot, dRight + ol, dBottom + ot)
        }

        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += DrawBitmapScaled(
                buffer.readInt(),
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                buffer.readInt(), buffer.readFloat(), buffer.readInt(),
            )
        }
    }
}
