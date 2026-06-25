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
import com.tneff.kmpremotecompose.remote.wire.WireBuffer

/**
 * `PARTICLE_COMPARE` (opcode [Operations.PARTICLE_COMPARE]) — per-particle conditional block (a
 * `PaintOperation` → group B). ANDROIDX + WIDGETS overlay op.
 *
 * Wire layout (mirrors upstream `ParticlesCompare.apply`/`read`): opcode byte + int `id` + short `flags`
 * + float `min` + float `max` + a length-prefixed `compare` float run + int `count1` + `count1`
 * length-prefixed float runs (`equations1`) + int `count2` + `count2` length-prefixed runs (`equations2`).
 * Each float run is `[int length][length×float]` (length 0 ⇔ null, symmetric). Floats may carry NaN ids.
 */
class ParticlesCompare(
    val id: Int,
    val flags: Int,
    val min: Float,
    val max: Float,
    val compare: FloatArray,
    val equations1: Array<FloatArray>,
    val equations2: Array<FloatArray>,
) : Operation {

    override val opcode: Int get() = Operations.PARTICLE_COMPARE

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(id)
        buffer.writeShort(flags)
        buffer.writeFloat(min)
        buffer.writeFloat(max)
        writeFloats(buffer, compare)
        buffer.writeInt(equations1.size)
        for (e in equations1) writeFloats(buffer, e)
        buffer.writeInt(equations2.size)
        for (e in equations2) writeFloats(buffer, e)
    }

    override fun dump(): String =
        "PARTICLE_COMPARE id=$id flags=$flags min=$min max=$max compare=${compare.size} " +
            "eq1=${equations1.size} eq2=${equations2.size}"

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is ParticlesCompare &&
                id == other.id && flags == other.flags &&
                min.toRawBits() == other.min.toRawBits() && max.toRawBits() == other.max.toRawBits() &&
                ParticlesLoop.rawEquals(compare, other.compare) &&
                equations1.size == other.equations1.size &&
                equations1.indices.all { ParticlesLoop.rawEquals(equations1[it], other.equations1[it]) } &&
                equations2.size == other.equations2.size &&
                equations2.indices.all { ParticlesLoop.rawEquals(equations2[it], other.equations2[it]) }
            )

    override fun hashCode(): Int {
        var h = id
        h = 31 * h + flags
        h = 31 * h + min.toRawBits()
        h = 31 * h + max.toRawBits()
        h = 31 * h + ParticlesLoop.rawHash(compare)
        for (e in equations1) h = 31 * h + ParticlesLoop.rawHash(e)
        for (e in equations2) h = 31 * h + ParticlesLoop.rawHash(e)
        return h
    }

    companion object : OperationReader {
        /** Upstream `writeFloats`: `[int length][length×float]` (a 0 length encodes null/empty). */
        private fun writeFloats(buffer: WireBuffer, values: FloatArray) {
            buffer.writeInt(values.size)
            for (v in values) buffer.writeFloat(v)
        }

        /** Upstream `readFloats`: reads `[int length][length×float]`. */
        private fun readFloats(buffer: WireBuffer): FloatArray =
            FloatArray(buffer.readInt()) { buffer.readFloat() }

        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            val id = buffer.readInt()
            val flags = buffer.readShort()
            val min = buffer.readFloat()
            val max = buffer.readFloat()
            val compare = readFloats(buffer)
            val equations1 = Array(buffer.readInt()) { readFloats(buffer) }
            val equations2 = Array(buffer.readInt()) { readFloats(buffer) }
            operations += ParticlesCompare(id, flags, min, max, compare, equations1, equations2)
        }
    }
}
