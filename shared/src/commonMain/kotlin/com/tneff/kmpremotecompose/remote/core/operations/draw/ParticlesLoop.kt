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
 * `PARTICLE_LOOP` (opcode [Operations.PARTICLE_LOOP]) — per-particle loop block (a `PaintOperation` →
 * group B), sibling of [ParticlesCreate].
 *
 * Wire layout (mirrors upstream `ParticlesLoop.apply`/`read`): opcode byte + int `id` + int
 * `restartLength` + `restartLength`×float + int `varCount` + `varCount`×{ int `equationLength` +
 * `equationLength`×float }. A null restart writes length 0; floats may carry NaN-encoded ids.
 */
class ParticlesLoop(
    val id: Int,
    val restart: FloatArray,
    val equations: Array<FloatArray>,
) : Operation {

    override val opcode: Int get() = Operations.PARTICLE_LOOP

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(id)
        buffer.writeInt(restart.size)
        for (v in restart) buffer.writeFloat(v)
        buffer.writeInt(equations.size)
        for (e in equations) {
            buffer.writeInt(e.size)
            for (v in e) buffer.writeFloat(v)
        }
    }

    override fun dump(): String = "PARTICLE_LOOP id=$id restart=${restart.size} vars=${equations.size}"

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is ParticlesLoop &&
                id == other.id && rawEquals(restart, other.restart) &&
                equations.size == other.equations.size &&
                equations.indices.all { rawEquals(equations[it], other.equations[it]) }
            )

    override fun hashCode(): Int {
        var h = id
        h = 31 * h + rawHash(restart)
        for (e in equations) h = 31 * h + rawHash(e)
        return h
    }

    companion object : OperationReader {
        internal fun rawEquals(a: FloatArray, b: FloatArray): Boolean {
            if (a.size != b.size) return false
            for (i in a.indices) if (a[i].toRawBits() != b[i].toRawBits()) return false
            return true
        }

        internal fun rawHash(a: FloatArray): Int {
            var h = 1
            for (v in a) h = 31 * h + v.toRawBits()
            return h
        }

        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            val id = buffer.readInt()
            val restart = FloatArray(buffer.readInt()) { buffer.readFloat() }
            val varCount = buffer.readInt()
            val equations = Array(varCount) { FloatArray(buffer.readInt()) { buffer.readFloat() } }
            operations += ParticlesLoop(id, restart, equations)
        }
    }
}
