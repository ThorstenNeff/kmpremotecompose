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

/**
 * `MODIFIER_BACKGROUND` (opcode [Operations.MODIFIER_BACKGROUND]) — component background fill.
 *
 * Wire layout (mirrors upstream `BackgroundModifierOperation.apply`/`read`, 37 bytes): opcode byte +
 * int `flags` + int `colorId` + int `reserve1` + int `reserve2` + float `r,g,b,a` + int `shapeType`.
 * The `COLOR_REF`-flagged colour-id remap is a Loom/macro runtime step that reads no wire bytes
 * (out of scope here). `r/g/b/a` may carry NaN-encoded ids; raw float bits preserved.
 */
class BackgroundModifier(
    val flags: Int,
    val colorId: Int,
    val reserve1: Int,
    val reserve2: Int,
    val r: Float,
    val g: Float,
    val b: Float,
    val a: Float,
    val shapeType: Int,
) : Operation, PaintOperation {

    override val opcode: Int get() = Operations.MODIFIER_BACKGROUND

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
     * Emit the component background (REM-37 E-Layout-2): a filled rect (or circle) at the measured
     * absolute bounds, in its own paint scope so it doesn't leak into sibling/child draws. Color comes
     * from the `colorId` ref when set, else the literal RGBA. Skipped when unmeasured (zero bounds).
     */
    override fun paint(context: RemoteContext, paint: PaintContext) {
        if (boundsW <= 0f || boundsH <= 0f) return
        val argb = resolveArgb(context)
        paint.savePaint()
        paint.applyPaint(PaintData.Builder().color(argb).style(STYLE_FILL).build())
        if (shapeType == SHAPE_CIRCLE) {
            paint.drawCircle(boundsX + boundsW / 2f, boundsY + boundsH / 2f, minOf(boundsW, boundsH) / 2f)
        } else {
            paint.drawRect(boundsX, boundsY, boundsX + boundsW, boundsY + boundsH)
        }
        paint.restorePaint()
    }

    private fun resolveArgb(context: RemoteContext): Int {
        if (colorId != 0) return context.getColor(colorId)
        fun ch(v: Float): Int {
            val c = if (v.isNaN()) context.getFloat(com.tneff.kmpremotecompose.remote.wire.WireTypes.idFromNan(v)) else v
            return (c.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        }
        return (ch(a) shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(flags)
        buffer.writeInt(colorId)
        buffer.writeInt(reserve1)
        buffer.writeInt(reserve2)
        buffer.writeFloat(r)
        buffer.writeFloat(g)
        buffer.writeFloat(b)
        buffer.writeFloat(a)
        buffer.writeInt(shapeType)
    }

    override fun dump(): String =
        "MODIFIER_BACKGROUND flags=$flags colorId=$colorId rgba=($r,$g,$b,$a) shape=$shapeType"

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is BackgroundModifier &&
                flags == other.flags && colorId == other.colorId &&
                reserve1 == other.reserve1 && reserve2 == other.reserve2 &&
                r.toRawBits() == other.r.toRawBits() && g.toRawBits() == other.g.toRawBits() &&
                b.toRawBits() == other.b.toRawBits() && a.toRawBits() == other.a.toRawBits() &&
                shapeType == other.shapeType
            )

    override fun hashCode(): Int {
        var h = flags
        h = 31 * h + colorId
        h = 31 * h + reserve1
        h = 31 * h + reserve2
        h = 31 * h + r.toRawBits()
        h = 31 * h + g.toRawBits()
        h = 31 * h + b.toRawBits()
        h = 31 * h + a.toRawBits()
        h = 31 * h + shapeType
        return h
    }

    companion object : OperationReader {
        /** Shape type (upstream `ShapeType`): 0 = rectangle, 1 = circle. */
        const val SHAPE_CIRCLE = 1
        /** PaintData style value (FILL=0, STROKE=1, FILL_AND_STROKE=2). */
        const val STYLE_FILL = 0

        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += BackgroundModifier(
                flags = buffer.readInt(),
                colorId = buffer.readInt(),
                reserve1 = buffer.readInt(),
                reserve2 = buffer.readInt(),
                r = buffer.readFloat(),
                g = buffer.readFloat(),
                b = buffer.readFloat(),
                a = buffer.readFloat(),
                shapeType = buffer.readInt(),
            )
        }
    }
}
