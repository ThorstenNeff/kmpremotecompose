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

/**
 * Rotate the canvas matrix about a pivot (`MATRIX_ROTATE`).
 * Wire layout: opcode, `float rotate`, `float pivotX`, `float pivotY`.
 */
class MatrixRotate(val rotate: Float, val pivotX: Float, val pivotY: Float) : PaintOperation, VariableSupport {
    override val opcode: Int get() = Operations.MATRIX_ROTATE

    // REM-36 E3: NaN data-variable coords resolved at render time; raw fields untouched (byte-safe).
    /** Render-resolved [rotate] (REM-36 E3). */
    var rRotate: Float = rotate
    /** Render-resolved [pivotX] (REM-36 E3). */
    var rPivotX: Float = pivotX
    /** Render-resolved [pivotY] (REM-36 E3). */
    var rPivotY: Float = pivotY

    override fun updateVariables(context: RemoteContext) {
        rRotate = context.resolveCoord(rotate)
        rPivotX = context.resolveCoord(pivotX)
        rPivotY = context.resolveCoord(pivotY)
    }

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeFloat(rotate)
        buffer.writeFloat(pivotX)
        buffer.writeFloat(pivotY)
    }

    /** L2 render: drive the canvas transform via the paint context (REM-33). */
    override fun paint(context: RemoteContext, paint: PaintContext) {
        paint.matrixRotate(rRotate, rPivotX, rPivotY)
    }

    override fun dump(): String = "MATRIX_ROTATE r=$rotate pivot=[$pivotX,$pivotY]"

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is MatrixRotate &&
                rotate.toRawBits() == other.rotate.toRawBits() &&
                pivotX.toRawBits() == other.pivotX.toRawBits() &&
                pivotY.toRawBits() == other.pivotY.toRawBits())

    override fun hashCode(): Int {
        var r = rotate.toRawBits()
        r = 31 * r + pivotX.toRawBits()
        r = 31 * r + pivotY.toRawBits()
        return r
    }

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += MatrixRotate(buffer.readFloat(), buffer.readFloat(), buffer.readFloat())
        }
    }
}
