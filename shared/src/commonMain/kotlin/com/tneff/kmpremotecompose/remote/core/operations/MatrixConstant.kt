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
 * A literal matrix (`MATRIX_CONSTANT`): binds the (1..16-float, row-major) [values] vector to
 * [matrixId] under [type] (currently always 0). Mirrors upstream `MatrixConstant`
 * (`androidx/compose/remote/core/operations/matrix/MatrixConstant.java:56-179`).
 *
 * Wire layout: opcode byte + int `matrixId` + int `type` + int `count` + `count` raw floats.
 * Layer: V7 base always-on (NOT in the API-6 set). [count] is bounded to `0..16` on read
 * (upstream corruption guard at `MatrixConstant.java:130`).
 *
 * The 4×4 identity matrix is the most common literal (`values = [1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1]`).
 * Floats may be NaN-encoded variable refs — raw bits preserved verbatim.
 */
class MatrixConstant(val matrixId: Int, val type: Int, val values: FloatArray) : Operation {

    override val opcode: Int get() = Operations.MATRIX_CONSTANT

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(matrixId)
        buffer.writeInt(type)
        buffer.writeInt(values.size)
        for (v in values) buffer.writeFloat(v)
    }

    // Render-side `apply` not wired in REM-115 (creation-only story). The runtime impl lands when
    // the matrix-constant render path is needed by a fixture or test that drives the player.

    override fun dump(): String = "MATRIX_CONSTANT id=$matrixId type=$type values[${values.size}]"

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is MatrixConstant && matrixId == other.matrixId && type == other.type &&
                values.contentEquals(other.values))

    override fun hashCode(): Int = 31 * (31 * matrixId + type) + values.contentHashCode()

    companion object : OperationReader {
        /** Mirror of the upstream corruption guard (`MatrixConstant.java:130`). */
        const val MAX_MATRIX_LENGTH: Int = 16

        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            val matrixId = buffer.readInt()
            val type = buffer.readInt()
            val count = buffer.readInt()
            if (count < 0 || count > MAX_MATRIX_LENGTH) {
                throw IllegalStateException("Invalid matrix length: $count (corrupt buffer)")
            }
            operations += MatrixConstant(matrixId, type, FloatArray(count) { buffer.readFloat() })
        }
    }
}
