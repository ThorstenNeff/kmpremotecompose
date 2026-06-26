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
class PaintData(val values: IntArray) : PaintOperation {

    override val opcode: Int get() = Operations.PAINT_VALUES

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(values.size)
        for (v in values) buffer.writeInt(v)
    }

    /** L2 render: dispatch to the geometry adapter via the paint context (REM-8). */
    override fun paint(context: RemoteContext, paint: PaintContext) {
        paint.applyPaint(this)
    }

    override fun dump(): String = "PAINT_VALUES count=${values.size}"

    override fun equals(other: Any?): Boolean =
        this === other || (other is PaintData && values.contentEquals(other.values))

    override fun hashCode(): Int = values.contentHashCode()

    companion object : OperationReader {

        /** Upstream's `readBundle` corruption guard: a bundle may hold at most this many ints. */
        const val MAX_BUNDLE_INTS: Int = 1024

        // Attribute tags (subset, verified against upstream PaintBundle).
        const val COLOR: Int = 4
        const val STROKE_WIDTH: Int = 5
        const val STROKE_CAP: Int = 7
        const val STYLE: Int = 8

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

        fun build(): PaintData = PaintData(ints.toIntArray())
    }
}
