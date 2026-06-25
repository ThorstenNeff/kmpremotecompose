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
 * `PARTICLE_DEFINE` (opcode [Operations.PARTICLE_DEFINE]) — defines a particle system: per variable, an
 * initializer equation (a `PaintOperation`, hence group-B / draw side).
 *
 * Wire layout (mirrors upstream `ParticlesCreate.apply`/`read`): opcode byte + int `id` +
 * int `particleCount` + int `varCount` + `varCount`×{ int `varId` + int `equationLength` +
 * `equationLength`×float }. Floats may carry NaN-encoded ids; raw bits preserved.
 */
class ParticlesCreate(
    val id: Int,
    val particleCount: Int,
    val varIds: IntArray,
    val equations: Array<FloatArray>,
) : Operation {

    override val opcode: Int get() = Operations.PARTICLE_DEFINE

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(id)
        buffer.writeInt(particleCount)
        buffer.writeInt(varIds.size)
        for (i in varIds.indices) {
            buffer.writeInt(varIds[i])
            buffer.writeInt(equations[i].size)
            for (v in equations[i]) buffer.writeFloat(v)
        }
    }

    override fun dump(): String = "PARTICLE_DEFINE id=$id count=$particleCount vars=${varIds.size}"

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is ParticlesCreate &&
                id == other.id && particleCount == other.particleCount &&
                varIds.contentEquals(other.varIds) &&
                equations.size == other.equations.size &&
                equations.indices.all { rawEquals(equations[it], other.equations[it]) }
            )

    override fun hashCode(): Int {
        var h = id
        h = 31 * h + particleCount
        h = 31 * h + varIds.contentHashCode()
        for (e in equations) h = 31 * h + rawHash(e)
        return h
    }

    companion object : OperationReader {
        private fun rawEquals(a: FloatArray, b: FloatArray): Boolean {
            if (a.size != b.size) return false
            for (i in a.indices) if (a[i].toRawBits() != b[i].toRawBits()) return false
            return true
        }

        private fun rawHash(a: FloatArray): Int {
            var h = 1
            for (v in a) h = 31 * h + v.toRawBits()
            return h
        }

        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            val id = buffer.readInt()
            val particleCount = buffer.readInt()
            val varCount = buffer.readInt()
            val varIds = IntArray(varCount)
            val equations = Array(varCount) { FloatArray(0) }
            for (i in 0 until varCount) {
                varIds[i] = buffer.readInt()
                val equLen = buffer.readInt()
                equations[i] = FloatArray(equLen) { buffer.readFloat() }
            }
            operations += ParticlesCreate(id, particleCount, varIds, equations)
        }
    }
}
