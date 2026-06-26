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
package com.tneff.kmpremotecompose.remote.core.operations.layout

import com.tneff.kmpremotecompose.remote.core.operations.Operation
import com.tneff.kmpremotecompose.remote.core.operations.OperationReader
import com.tneff.kmpremotecompose.remote.core.operations.Operations
import com.tneff.kmpremotecompose.remote.core.operations.draw.PaintData
import com.tneff.kmpremotecompose.remote.player.core.PaintContext
import com.tneff.kmpremotecompose.remote.player.core.PaintOperation
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.wire.WireBuffer
import com.tneff.kmpremotecompose.remote.wire.WireTypes

/**
 * `MODIFIER_BORDER` (opcode [Operations.MODIFIER_BORDER]) — draws a border around a component.
 *
 * Wire layout: opcode byte + int `flags` + int `colorId` + int `reserve1` + int `reserve2` +
 * float `borderWidth` + float `roundedCorner` + float `r` + float `g` + float `b` + float `a` +
 * int `shapeType` (mirrors upstream `BorderModifierOperation.apply`/`read`).
 */
class BorderModifier(
    val flags: Int,
    val colorId: Int,
    val reserve1: Int,
    val reserve2: Int,
    val borderWidth: Float,
    val roundedCorner: Float,
    val r: Float,
    val g: Float,
    val b: Float,
    val a: Float,
    val shapeType: Int,
) : Operation, PaintOperation {

    override val opcode: Int get() = Operations.MODIFIER_BORDER

    // REM-37 E-Layout-2: render-only absolute draw bounds, set by the measure pass; not serialized.
    private var boundsX = 0f
    private var boundsY = 0f
    private var boundsW = 0f
    private var boundsH = 0f

    /** Called by [com.tneff.kmpremotecompose.remote.player.core.LayoutMeasure] with the measured bounds. */
    fun setBounds(x: Float, y: Float, w: Float, h: Float) {
        boundsX = x; boundsY = y; boundsW = w; boundsH = h
    }

    /**
     * Emit the component border (REM-37 E-Layout-2): a stroked rect/round-rect inset by half the stroke
     * width (upstream default mode — keeps the stroke inside the bounds), in its own paint scope. Skipped
     * when unmeasured. roundedCorner > 0 → round-rect.
     */
    override fun paint(context: RemoteContext, paint: PaintContext) {
        if (boundsW <= 0f || boundsH <= 0f) return
        val width = resolveValue(borderWidth, context)
        if (width <= 0f) return
        val argb = resolveArgb(context)
        val hs = width / 2f
        val left = boundsX + hs
        val top = boundsY + hs
        val right = boundsX + boundsW - hs
        val bottom = boundsY + boundsH - hs
        val radius = resolveValue(roundedCorner, context)
        paint.savePaint()
        paint.applyPaint(PaintData.Builder().color(argb).style(STYLE_STROKE).strokeWidth(width).build())
        if (radius > 0f) {
            paint.drawRoundRect(left, top, right, bottom, (radius - hs).coerceAtLeast(0f), (radius - hs).coerceAtLeast(0f))
        } else {
            paint.drawRect(left, top, right, bottom)
        }
        paint.restorePaint()
    }

    private fun resolveValue(v: Float, context: RemoteContext): Float =
        if (v.isNaN() && !WireTypes.isOperationVariable(v)) context.getFloat(WireTypes.idFromNan(v)) else v

    private fun resolveArgb(context: RemoteContext): Int {
        if (colorId != 0) return context.getColor(colorId)
        fun ch(v: Float): Int = (resolveValue(v, context).coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        return (ch(a) shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(flags)
        buffer.writeInt(colorId)
        buffer.writeInt(reserve1)
        buffer.writeInt(reserve2)
        buffer.writeFloat(borderWidth)
        buffer.writeFloat(roundedCorner)
        buffer.writeFloat(r)
        buffer.writeFloat(g)
        buffer.writeFloat(b)
        buffer.writeFloat(a)
        buffer.writeInt(shapeType)
    }

    override fun dump(): String =
        "MODIFIER_BORDER flags=$flags colorId=$colorId reserve1=$reserve1 reserve2=$reserve2 " +
            "borderWidth=$borderWidth roundedCorner=$roundedCorner r=$r g=$g b=$b a=$a " +
            "shapeType=$shapeType"

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is BorderModifier &&
                flags == other.flags && colorId == other.colorId &&
                reserve1 == other.reserve1 && reserve2 == other.reserve2 &&
                borderWidth.toRawBits() == other.borderWidth.toRawBits() &&
                roundedCorner.toRawBits() == other.roundedCorner.toRawBits() &&
                r.toRawBits() == other.r.toRawBits() && g.toRawBits() == other.g.toRawBits() &&
                b.toRawBits() == other.b.toRawBits() && a.toRawBits() == other.a.toRawBits() &&
                shapeType == other.shapeType
            )

    override fun hashCode(): Int {
        var h = flags
        h = 31 * h + colorId
        h = 31 * h + reserve1
        h = 31 * h + reserve2
        h = 31 * h + borderWidth.toRawBits()
        h = 31 * h + roundedCorner.toRawBits()
        h = 31 * h + r.toRawBits()
        h = 31 * h + g.toRawBits()
        h = 31 * h + b.toRawBits()
        h = 31 * h + a.toRawBits()
        h = 31 * h + shapeType
        return h
    }

    companion object : OperationReader {
        /** PaintData style value (FILL=0, STROKE=1, FILL_AND_STROKE=2). */
        const val STYLE_STROKE = 1

        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += BorderModifier(
                buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(),
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                buffer.readFloat(), buffer.readFloat(), buffer.readInt(),
            )
        }
    }
}
