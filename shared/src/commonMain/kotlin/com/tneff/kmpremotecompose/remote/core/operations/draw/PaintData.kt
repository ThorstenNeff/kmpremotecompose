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
import com.tneff.kmpremotecompose.remote.player.core.VariableSupport
import com.tneff.kmpremotecompose.remote.wire.WireBuffer
import com.tneff.kmpremotecompose.remote.wire.WireTypes

/**
 * `PAINT_VALUES` (opcode [Operations.PAINT_VALUES]) — a paint bundle.
 *
 * **Wire layout (the whole format):** opcode byte + int `count` + `count` big-endian ints. Upstream's
 * `PaintBundle.writeBundle/readBundle` is exactly this: a length-prefixed raw int array. All the paint
 * complexity (attribute tags, colours, gradients, shaders…) lives in how the int array is *populated*
 * — floats are stored as raw int bits, sub-values are packed into the high 16 bits of a tag int — but
 * none of that changes the wire framing. Carrying the array verbatim therefore round-trips **any**
 * paint bundle byte-exact, which is what conformance (decode→re-encode) needs.
 *
 * `count` is bounded to `0..`[MAX_BUNDLE_INTS] on read (mirrors upstream's corruption guard).
 *
 * The id-remap step (`PaintBundle.resolveIds`) is a Loom/macro-expansion runtime concern that reads
 * no wire bytes; it is out of REM-5 scope. A semantic builder for the common scalar attributes is
 * provided ([Builder]); the full attribute/gradient/shader set is deferred (creation layer).
 */
class PaintData(val values: IntArray) : PaintOperation, VariableSupport {

    override val opcode: Int get() = Operations.PAINT_VALUES

    // Render-only resolved bundle (REM-37): [values] with NaN-encoded variable refs in float slots
    // (gradient geometry/stops, stroke width/miter, alpha, text size, …) replaced by their resolved
    // values. Defaults to [values]; rebuilt in [updateVariables]. Never serialized — [write] uses [values].
    private var resolved: IntArray = values

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(values.size)
        for (v in values) buffer.writeInt(v)
    }

    /**
     * Resolve NaN-encoded variable refs in the bundle's float slots (REM-37). Upstream `PaintBundle`
     * resolves these (raw `mArray` → resolved `mOutArray`) before applying the paint; without it a
     * gradient whose geometry coords are variable refs is built with NaN offsets → renders empty on
     * Android (Skiko tolerates NaN and fills degenerately, so the two platforms diverge). Resolving here
     * fixes the render on **both** platforms (they then build the gradient from real coords and match).
     */
    override fun updateVariables(context: RemoteContext) {
        resolved = resolveBundle(values, context)
    }

    /** L2 render: dispatch to the geometry adapter via the paint context (REM-8) using resolved values. */
    override fun paint(context: RemoteContext, paint: PaintContext) {
        paint.applyPaint(if (resolved === values) this else PaintData(resolved))
    }

    override fun dump(): String = "PAINT_VALUES count=${values.size}"

    override fun equals(other: Any?): Boolean =
        this === other || (other is PaintData && values.contentEquals(other.values))

    override fun hashCode(): Int = values.contentHashCode()

    companion object : OperationReader {

        /** Upstream's `readBundle` corruption guard: a bundle may hold at most this many ints. */
        const val MAX_BUNDLE_INTS: Int = 1024

        // Attribute tags (verified against upstream PaintBundle). The Builder uses a subset; the full set
        // is needed to walk the bundle grammar for variable resolution ([resolveBundle]).
        const val TEXT_SIZE: Int = 1
        const val COLOR: Int = 4
        const val STROKE_WIDTH: Int = 5
        const val STROKE_MITER: Int = 6
        const val STROKE_CAP: Int = 7
        const val STYLE: Int = 8
        const val SHADER: Int = 9
        const val IMAGE_FILTER_QUALITY: Int = 10
        const val GRADIENT: Int = 11
        const val ALPHA: Int = 12
        const val COLOR_FILTER: Int = 13
        const val ANTI_ALIAS: Int = 14
        const val STROKE_JOIN: Int = 15
        const val TYPEFACE: Int = 16
        const val FILTER_BITMAP: Int = 17
        const val BLEND_MODE: Int = 18
        const val COLOR_ID: Int = 19
        const val COLOR_FILTER_ID: Int = 20
        const val CLEAR_COLOR_FILTER: Int = 21
        const val SHADER_MATRIX: Int = 22
        const val FONT_AXIS: Int = 23
        const val TEXTURE: Int = 24
        const val PATH_EFFECT: Int = 25
        const val FALLBACK_TYPEFACE: Int = 26
        private const val LINEAR_GRADIENT = 0
        private const val RADIAL_GRADIENT = 1
        private const val SWEEP_GRADIENT = 2

        /**
         * Resolve NaN-encoded variable refs in [v]'s **float** slots against [context] (REM-37), returning
         * a resolved copy (or [v] unchanged when nothing resolves). Walks the exact same bundle grammar +
         * cursor as [com.tneff.kmpremotecompose.remote.player.compose.PaintBundleApplier] — slot-count
         * fidelity is mandatory (a wrong advance desyncs the rest). Only float-typed args are resolvable
         * (stroke width/miter, alpha, text size, gradient stops + geometry, font-axis, path-effect); mode/
         * style/tag ints and colours are left as-is. Mirrors upstream `PaintBundle.registerVars`/`resolveIds`.
         */
        private fun resolveBundle(v: IntArray, context: RemoteContext): IntArray {
            var out: IntArray? = null
            fun rf(idx: Int) { // resolve a float slot in place (lazy-copy on first change)
                if (idx >= v.size) return
                val f = Float.fromBits(v[idx])
                if (f.isNaN() && !WireTypes.isOperationVariable(f)) {
                    if (out == null) out = v.copyOf()
                    out!![idx] = context.getFloat(WireTypes.idFromNan(f)).toRawBits()
                }
            }
            fun rdAt(idx: Int): Int = if (idx < v.size) v[idx] else 0
            var i = 0
            while (i < v.size) {
                val cmd = v[i++]
                when (cmd and 0xFFFF) {
                    STROKE_WIDTH, STROKE_MITER, ALPHA, TEXT_SIZE -> { rf(i); i++ } // 1 float arg
                    // 1 non-float arg (colour / colour-id / typeface / shader handle) — left as-is:
                    COLOR, COLOR_ID, COLOR_FILTER, COLOR_FILTER_ID, SHADER, SHADER_MATRIX,
                    TYPEFACE, FALLBACK_TYPEFACE -> i++
                    // packed (value in cmd's high half) — 0 trailing args:
                    STROKE_CAP, STROKE_JOIN, STYLE, ANTI_ALIAS, BLEND_MODE, FILTER_BITMAP,
                    IMAGE_FILTER_QUALITY, CLEAR_COLOR_FILTER -> {}
                    GRADIENT -> {
                        val type = cmd shr 16
                        val colorLen = 0xFF and rdAt(i); i++ // control (low byte = colour count)
                        i += colorLen // colours (ARGB/id ints — not NaN-float resolvable)
                        val stopsLen = rdAt(i); i++
                        if (stopsLen > 0 && colorLen > 0) repeat(colorLen) { rf(i); i++ } // stops = floats
                        if (colorLen > 0) when (type) { // geometry only when colours present (upstream)
                            LINEAR_GRADIENT -> { rf(i); i++; rf(i); i++; rf(i); i++; rf(i); i++; i++ } // 4 coords + tile
                            RADIAL_GRADIENT -> { rf(i); i++; rf(i); i++; rf(i); i++; i++ } // cx,cy,radius + tile
                            SWEEP_GRADIENT -> { rf(i); i++; rf(i); i++ } // cx,cy
                        }
                    }
                    FONT_AXIS -> repeat(cmd shr 16) { i++; rf(i); i++ } // per axis: tag int + float
                    TEXTURE -> i += 3
                    PATH_EFFECT -> repeat(cmd shr 16) { rf(i); i++ } // floats
                    else -> return out ?: v // unknown tag → can't know width; stop (matches applier)
                }
            }
            return out ?: v
        }

        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            val count = buffer.readInt()
            require(count in 0..MAX_BUNDLE_INTS) { "corrupt paint bundle length=$count" }
            operations += PaintData(IntArray(count) { buffer.readInt() })
        }
    }

    /**
     * Minimal builder for the common **scalar** paint attributes (verified encodings). Two-int
     * attributes write `[tag, value]`; packed attributes write `[tag | (value shl 16)]`. The full
     * bundle (gradients, shaders, typefaces, colour filters, path effects, font axes) is deferred —
     * for those, construct [PaintData] from a raw [IntArray].
     */
    class Builder {
        private val ints = ArrayList<Int>()

        /** ARGB colour: `[COLOR, color]`. */
        fun color(argb: Int): Builder = apply { ints += COLOR; ints += argb }

        /** Stroke width (float stored as raw bits): `[STROKE_WIDTH, bits]`. */
        fun strokeWidth(width: Float): Builder = apply { ints += STROKE_WIDTH; ints += width.toRawBits() }

        /** Paint style, packed: `[STYLE | (style shl 16)]`. */
        fun style(style: Int): Builder = apply { ints += STYLE or (style shl 16) }

        /** Stroke cap, packed: `[STROKE_CAP | (cap shl 16)]`. */
        fun strokeCap(cap: Int): Builder = apply { ints += STROKE_CAP or (cap shl 16) }

        /** Text size in px (float as raw bits): `[TEXT_SIZE, bits]` → PlayerPaintState.textSizePx (REM-37). */
        fun textSize(px: Float): Builder = apply { ints += TEXT_SIZE; ints += px.toRawBits() }

        fun build(): PaintData = PaintData(ints.toIntArray())
    }
}
