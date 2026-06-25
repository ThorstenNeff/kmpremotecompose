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

import com.tneff.kmpremotecompose.remote.wire.WireBuffer

/**
 * Matrix/vector math (`MATRIX_VECTOR_MATH`): applies operation [type] using matrix [matrixId] to map
 * the [inputs] vector into the [outputs] id slots.
 *
 * Wire layout: opcode, `short type`, `int matrixId`, `int outCount`, `int[] outputs`,
 * `int inCount`, `float[] inputs`. Layer: a V7 base always-on op (NOT in the API-6 set).
 */
class MatrixVectorMath(
    val type: Int,
    val matrixId: Int,
    val outputs: IntArray,
    val inputs: FloatArray,
) : Operation {

    override val opcode: Int get() = Operations.MATRIX_VECTOR_MATH

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeShort(type)
        buffer.writeInt(matrixId)
        buffer.writeInt(outputs.size)
        for (v in outputs) buffer.writeInt(v)
        buffer.writeInt(inputs.size)
        for (v in inputs) buffer.writeFloat(v)
    }

    override fun dump(): String =
        "MATRIX_VECTOR_MATH type=$type matrixId=$matrixId out[${outputs.size}] in[${inputs.size}]"

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is MatrixVectorMath && type == other.type && matrixId == other.matrixId &&
                outputs.contentEquals(other.outputs) && inputs.contentEquals(other.inputs))

    override fun hashCode(): Int {
        var r = type
        r = 31 * r + matrixId
        r = 31 * r + outputs.contentHashCode()
        r = 31 * r + inputs.contentHashCode()
        return r
    }

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            val type = buffer.readShort()
            val matrixId = buffer.readInt()
            val outCount = buffer.readInt()
            if (outCount < 0) throw IllegalStateException("invalid output count: $outCount")
            val outputs = IntArray(outCount) { buffer.readInt() }
            val inCount = buffer.readInt()
            if (inCount < 0) throw IllegalStateException("invalid input count: $inCount")
            val inputs = FloatArray(inCount) { buffer.readFloat() }
            operations += MatrixVectorMath(type, matrixId, outputs, inputs)
        }
    }
}
