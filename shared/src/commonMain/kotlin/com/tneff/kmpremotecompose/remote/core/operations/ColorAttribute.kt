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
package com.tneff.kmpremotecompose.remote.core.operations

import com.tneff.kmpremotecompose.remote.player.core.PaintContext
import com.tneff.kmpremotecompose.remote.player.core.PaintOperation
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.wire.WireBuffer
import kotlin.math.max
import kotlin.math.min

/**
 * A derived color attribute (`ATTRIBUTE_COLOR`): extracts a channel/attribute [type] from the color
 * [colorId] into [id].
 *
 * Wire layout: opcode, `int id`, `int colorId`, `short type`.
 *
 * REM-131: runtime is additive (wire form unchanged). Like upstream, the extraction runs at *paint*
 * time ([paint]) — the source [colorId] is produced in the variable phase (ColorConstant /
 * ColorExpression / ColorTheme), so it is resolved by the time we read it. The op draws nothing; it
 * only publishes a float, so it does not affect the draw-count gate.
 */
class ColorAttribute(val id: Int, val colorId: Int, val type: Int) : PaintOperation {
    override val opcode: Int get() = Operations.ATTRIBUTE_COLOR

    /**
     * Read the source color and store the requested channel as a float (upstream `ColorAttribute.paint`).
     * The [paint] context is unused — this op computes a value, it does not draw.
     */
    override fun paint(context: RemoteContext, paint: PaintContext) {
        val color = context.getColor(colorId)
        val value = when (type and 0xFF) {
            COLOR_HUE -> hueOf(color)
            COLOR_SATURATION -> saturationOf(color)
            COLOR_BRIGHTNESS -> brightnessOf(color)
            COLOR_RED -> ((color shr 16) and 0xFF) / 255f
            COLOR_GREEN -> ((color shr 8) and 0xFF) / 255f
            COLOR_BLUE -> (color and 0xFF) / 255f
            COLOR_ALPHA -> ((color shr 24) and 0xFF) / 255f
            else -> return
        }
        context.loadFloat(id, value)
    }

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(id)
        buffer.writeInt(colorId)
        buffer.writeShort(type)
    }

    override fun dump(): String = "ATTRIBUTE_COLOR id=$id colorId=$colorId type=$type"

    override fun equals(other: Any?): Boolean =
        this === other || (other is ColorAttribute && id == other.id && colorId == other.colorId && type == other.type)

    override fun hashCode(): Int = 31 * (31 * id + colorId) + type

    companion object : OperationReader {
        // Channel/attribute codes, verbatim from upstream ColorAttribute.*
        private const val COLOR_HUE = 0
        private const val COLOR_SATURATION = 1
        private const val COLOR_BRIGHTNESS = 2
        private const val COLOR_RED = 3
        private const val COLOR_GREEN = 4
        private const val COLOR_BLUE = 5
        private const val COLOR_ALPHA = 6

        /** Hue in [0,1] (upstream `Utils.getHue`, verbatim). */
        private fun hueOf(argb: Int): Float {
            val rf = ((argb shr 16) and 0xFF) / 255f
            val gf = ((argb shr 8) and 0xFF) / 255f
            val bf = (argb and 0xFF) / 255f
            val mx = max(rf, max(gf, bf))
            val mn = min(rf, min(gf, bf))
            val delta = mx - mn
            var h: Float
            if (mx == mn) {
                h = 0f
            } else {
                h = when (mx) {
                    rf -> ((gf - bf) / delta) % 6f
                    gf -> ((bf - rf) / delta) + 2f
                    else -> ((rf - gf) / delta) + 4f
                }
            }
            h = (h * 60f) % 360f
            if (h < 0) h += 360f
            return h / 360f
        }

        /** Saturation in [0,1] (upstream `Utils.getSaturation`, verbatim). */
        private fun saturationOf(argb: Int): Float {
            val rf = ((argb shr 16) and 0xFF) / 255f
            val gf = ((argb shr 8) and 0xFF) / 255f
            val bf = (argb and 0xFF) / 255f
            val mx = max(rf, max(gf, bf))
            val mn = min(rf, min(gf, bf))
            return if (mx == mn) 0f else (mx - mn) / mx
        }

        /** Brightness/value in [0,1] (upstream `Utils.getBrightness`, verbatim = max channel). */
        private fun brightnessOf(argb: Int): Float {
            val rf = ((argb shr 16) and 0xFF) / 255f
            val gf = ((argb shr 8) and 0xFF) / 255f
            val bf = (argb and 0xFF) / 255f
            return max(rf, max(gf, bf))
        }

        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += ColorAttribute(buffer.readInt(), buffer.readInt(), buffer.readShort())
        }
    }
}
