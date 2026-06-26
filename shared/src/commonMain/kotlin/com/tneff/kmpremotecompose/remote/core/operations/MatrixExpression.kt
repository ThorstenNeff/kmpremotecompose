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

import com.tneff.kmpremotecompose.remote.player.core.MatrixOperations
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.player.core.VariableSupport
import com.tneff.kmpremotecompose.remote.wire.WireBuffer
import com.tneff.kmpremotecompose.remote.wire.WireTypes

/**
 * A computed matrix (`MATRIX_EXPRESSION`): builds matrix [matrixId] of [type] from an RPN float
 * [expression] (REM-37 cube3d). Phase A resolves the expression's variable refs and evaluates it via
 * [MatrixOperations] into a row-major `FloatArray(16)` stored under [matrixId]; `MATRIX_VECTOR_MATH`
 * then transforms 3-vectors by it. Pure runtime — the wire `expression` is untouched (byte-safe).
 *
 * Wire layout: opcode, `int matrixId`, `int type`, `int count`, then `count` raw floats.
 * Layer: a V7 base always-on op (NOT in the API-6 set).
 */
class MatrixExpression(val matrixId: Int, val type: Int, val expression: FloatArray) :
    Operation, VariableSupport {

    override val opcode: Int get() = Operations.MATRIX_EXPRESSION

    // REM-37: render-only resolved copy of [expression] (variable NaNs → store values; operator NaNs kept).
    private var resolved: FloatArray = expression

    /** Resolve each non-operator variable NaN against the store (matrix operators pass through). */
    override fun updateVariables(context: RemoteContext) {
        var hasVar = false
        for (v in expression) if (v.isNaN() && !MatrixOperations.isOperator(v)) { hasVar = true; break }
        resolved = if (!hasVar) {
            expression
        } else {
            FloatArray(expression.size) { i ->
                val v = expression[i]
                if (v.isNaN() && !MatrixOperations.isOperator(v)) context.getFloat(WireTypes.idFromNan(v)) else v
            }
        }
    }

    /** Evaluate the matrix expression and store the result under [matrixId]. */
    override fun apply(context: RemoteContext) {
        context.loadMatrix(matrixId, MatrixOperations.eval(resolved))
    }

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(matrixId)
        buffer.writeInt(type)
        buffer.writeInt(expression.size)
        for (v in expression) buffer.writeFloat(v)
    }

    override fun dump(): String = "MATRIX_EXPRESSION id=$matrixId type=$type expr[${expression.size}]"

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is MatrixExpression && matrixId == other.matrixId && type == other.type &&
                expression.contentEquals(other.expression))

    override fun hashCode(): Int = 31 * (31 * matrixId + type) + expression.contentHashCode()

    companion object : OperationReader {
        /** Mirror of the upstream `Limits.MAX_EXPRESSION_SIZE`. */
        const val MAX_EXPRESSION_SIZE = 32

        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            val matrixId = buffer.readInt()
            val type = buffer.readInt()
            val count = buffer.readInt()
            if (count < 0 || count > MAX_EXPRESSION_SIZE) throw IllegalStateException("matrix expression too long: $count")
            operations += MatrixExpression(matrixId, type, FloatArray(count) { buffer.readFloat() })
        }
    }
}
