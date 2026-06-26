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
import com.tneff.kmpremotecompose.remote.player.core.VariableSupport
import com.tneff.kmpremotecompose.remote.player.core.resolveCoord
import com.tneff.kmpremotecompose.remote.wire.WireBuffer

/** Skew the canvas matrix (`MATRIX_SKEW`). Wire layout: opcode, `float skewX`, `float skewY`. */
class MatrixSkew(val skewX: Float, val skewY: Float) : PaintOperation, VariableSupport {
    override val opcode: Int get() = Operations.MATRIX_SKEW

    // REM-36 E3: NaN data-variable coords resolved at render time; raw fields untouched (byte-safe).
    /** Render-resolved [skewX] (REM-36 E3). */
    var rSkewX: Float = skewX
    /** Render-resolved [skewY] (REM-36 E3). */
    var rSkewY: Float = skewY

    override fun updateVariables(context: RemoteContext) {
        rSkewX = context.resolveCoord(skewX)
        rSkewY = context.resolveCoord(skewY)
    }

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeFloat(skewX)
        buffer.writeFloat(skewY)
    }

    /** L2 render: drive the canvas transform via the paint context (REM-33). */
    override fun paint(context: RemoteContext, paint: PaintContext) {
        paint.matrixSkew(rSkewX, rSkewY)
    }

    override fun dump(): String = "MATRIX_SKEW x=$skewX y=$skewY"

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is MatrixSkew &&
                skewX.toRawBits() == other.skewX.toRawBits() &&
                skewY.toRawBits() == other.skewY.toRawBits())

    override fun hashCode(): Int = 31 * skewX.toRawBits() + skewY.toRawBits()

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += MatrixSkew(buffer.readFloat(), buffer.readFloat())
        }
    }
}
