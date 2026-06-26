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

import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.player.core.VariableSupport
import com.tneff.kmpremotecompose.remote.wire.WireBuffer
import com.tneff.kmpremotecompose.remote.wire.WireTypes
import kotlin.math.pow

/**
 * A color expression (`COLOR_EXPRESSIONS`): a computed/interpolated color bound to [id].
 *
 * Wire layout: opcode, `int id`, then four raw `int` parameters. The parameters pack mode, channel
 * values and tween (as raw float bits or NaN-encoded ids) in a layout the player interprets; at the
 * wire level they are four opaque 32-bit words, which is all byte-compatibility needs.
 */
class ColorExpression(
    val id: Int,
    val param1: Int,
    val param2: Int,
    val param3: Int,
    val param4: Int,
) : Operation, VariableSupport {

    override val opcode: Int get() = Operations.COLOR_EXPRESSIONS

    // REM-37 ColorExpression eval — decode the wire params (mirrors upstream read(); resolveFloat is a
    // no-op for our non-Loom buffer, so float-bit params are the values/var-refs directly).
    private val mode = param1 and 0xFF

    // render-only resolved channels (NaN var-ref → store value); set in updateVariables, used in apply.
    private var c0 = Float.fromBits(param2)
    private var c1ch = Float.fromBits(param3)
    private var c2ch = Float.fromBits(param4)
    private var alphaRef = if (mode == IDARGB_MODE) WireTypes.asNan(param1 shr 16) else Float.NaN
    private var outTween = Float.fromBits(param4)

    /** Resolve each NaN-encoded channel/tween against the float store (literals pass through). */
    override fun updateVariables(context: RemoteContext) {
        fun r(v: Float) = if (v.isNaN()) context.getFloat(WireTypes.idFromNan(v)) else v
        when (mode) {
            HSV_MODE, ARGB_MODE, IDARGB_MODE -> {
                c0 = r(Float.fromBits(param2)); c1ch = r(Float.fromBits(param3)); c2ch = r(Float.fromBits(param4))
                if (mode == IDARGB_MODE) alphaRef = r(WireTypes.asNan(param1 shr 16))
            }
            else -> outTween = r(Float.fromBits(param4))
        }
    }

    /** Evaluate the color and load it under [id] (upstream `ColorExpression.apply`). */
    override fun apply(context: RemoteContext) {
        when (mode) {
            HSV_MODE -> {
                val alpha = (param1 shr 16) and 0xFF
                context.loadColor(id, (alpha shl 24) or (0xFFFFFF and hsvToRgb(c0, c1ch, c2ch)))
            }
            ARGB_MODE -> context.loadColor(id, toArgb((param1 shr 16) / 1024f, c0, c1ch, c2ch))
            IDARGB_MODE -> context.loadColor(id, toArgb(alphaRef, c0, c1ch, c2ch))
            else -> {
                // tween modes 0..3: bit0 → color1 is a colorId ref, bit1 → color2 is a colorId ref.
                val col1 = if (mode and 1 == 1) context.getColor(param2) else param2
                val col2 = if (mode and 2 == 2) context.getColor(param3) else param3
                context.loadColor(id, interpolateColor(col1, col2, outTween))
            }
        }
    }

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(id)
        buffer.writeInt(param1)
        buffer.writeInt(param2)
        buffer.writeInt(param3)
        buffer.writeInt(param4)
    }

    override fun dump(): String = "COLOR_EXPRESSIONS id=$id params=[$param1,$param2,$param3,$param4]"

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is ColorExpression && id == other.id &&
                param1 == other.param1 && param2 == other.param2 &&
                param3 == other.param3 && param4 == other.param4)

    override fun hashCode(): Int {
        var r = id
        r = 31 * r + param1
        r = 31 * r + param2
        r = 31 * r + param3
        r = 31 * r + param4
        return r
    }

    companion object : OperationReader {
        // Upstream ColorExpression modes (low byte of param1). 0..3 = two-color interpolate (bit0/bit1
        // select whether color1/color2 is a colorId ref); 4 = HSV, 5 = ARGB, 6 = ID-ARGB (alpha is an id).
        private const val HSV_MODE = 4
        private const val ARGB_MODE = 5
        private const val IDARGB_MODE = 6

        /** HSV → packed RGB (opaque), verbatim from upstream `Utils.hsvToRgb`. */
        private fun hsvToRgb(hue: Float, saturation: Float, value: Float): Int {
            val h = (hue * 6).toInt()
            val f = hue * 6 - h
            val p = (0.5f + 255 * value * (1 - saturation)).toInt()
            val q = (0.5f + 255 * value * (1 - f * saturation)).toInt()
            val t = (0.5f + 255 * value * (1 - (1 - f) * saturation)).toInt()
            val v = (0.5f + 255 * value).toInt()
            return when (h) {
                0 -> -0x1000000 or ((v shl 16) + (t shl 8) + p)
                1 -> -0x1000000 or ((q shl 16) + (v shl 8) + p)
                2 -> -0x1000000 or ((p shl 16) + (v shl 8) + t)
                3 -> -0x1000000 or ((p shl 16) + (q shl 8) + v)
                4 -> -0x1000000 or ((t shl 16) + (p shl 8) + v)
                5 -> -0x1000000 or ((v shl 16) + (p shl 8) + q)
                else -> 0
            }
        }

        /** Pack ARGB float channels [0,1] into an int, verbatim from upstream `Utils.toARGB`. */
        private fun toArgb(alpha: Float, red: Float, green: Float, blue: Float): Int {
            val a = (alpha * 255f + 0.5f).toInt()
            val r = (red * 255f + 0.5f).toInt()
            val g = (green * 255f + 0.5f).toInt()
            val b = (blue * 255f + 0.5f).toInt()
            return (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        private fun clamp(v: Int) = v.coerceIn(0, 255)

        /** Gamma-2.2 interpolation between two ARGB ints, verbatim from upstream `Utils.interpolateColor`. */
        private fun interpolateColor(c1: Int, c2: Int, t: Float): Int {
            if (t.isNaN() || t == 0.0f) return c1
            if (t == 1.0f) return c2
            val c1fa = (0xFF and (c1 shr 24)) / 255f
            val c1fr = ((0xFF and (c1 shr 16)) / 255f).toDouble().pow(2.2).toFloat()
            val c1fg = ((0xFF and (c1 shr 8)) / 255f).toDouble().pow(2.2).toFloat()
            val c1fb = ((0xFF and c1) / 255f).toDouble().pow(2.2).toFloat()
            val c2fa = (0xFF and (c2 shr 24)) / 255f
            val c2fr = ((0xFF and (c2 shr 16)) / 255f).toDouble().pow(2.2).toFloat()
            val c2fg = ((0xFF and (c2 shr 8)) / 255f).toDouble().pow(2.2).toFloat()
            val c2fb = ((0xFF and c2) / 255f).toDouble().pow(2.2).toFloat()
            val fr = c1fr + t * (c2fr - c1fr)
            val fg = c1fg + t * (c2fg - c1fg)
            val fb = c1fb + t * (c2fb - c1fb)
            val fa = c1fa + t * (c2fa - c1fa)
            val outR = clamp((fr.toDouble().pow(1.0 / 2.2).toFloat() * 255f).toInt())
            val outG = clamp((fg.toDouble().pow(1.0 / 2.2).toFloat() * 255f).toInt())
            val outB = clamp((fb.toDouble().pow(1.0 / 2.2).toFloat() * 255f).toInt())
            val outA = clamp((fa * 255f).toInt())
            return (outA shl 24) or (outR shl 16) or (outG shl 8) or outB
        }

        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += ColorExpression(
                buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(),
            )
        }
    }
}
