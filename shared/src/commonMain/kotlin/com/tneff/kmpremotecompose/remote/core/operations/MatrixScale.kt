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
 * Scale the canvas matrix about a center (`MATRIX_SCALE`).
 * Wire layout: opcode, `float scaleX`, `float scaleY`, `float centerX`, `float centerY`.
 */
class MatrixScale(
    val scaleX: Float,
    val scaleY: Float,
    val centerX: Float,
    val centerY: Float,
) : PaintOperation, VariableSupport {
    override val opcode: Int get() = Operations.MATRIX_SCALE

    // REM-36 E3: NaN data-variable coords resolved at render time; raw fields untouched (byte-safe).
    /** Render-resolved [scaleX] (REM-36 E3). */
    var rScaleX: Float = scaleX
    /** Render-resolved [scaleY] (REM-36 E3). */
    var rScaleY: Float = scaleY
    /** Render-resolved [centerX] (REM-36 E3). */
    var rCenterX: Float = centerX
    /** Render-resolved [centerY] (REM-36 E3). */
    var rCenterY: Float = centerY

    override fun updateVariables(context: RemoteContext) {
        rScaleX = context.resolveCoord(scaleX)
        rScaleY = context.resolveCoord(scaleY)
        rCenterX = context.resolveCoord(centerX)
        rCenterY = context.resolveCoord(centerY)
    }

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeFloat(scaleX)
        buffer.writeFloat(scaleY)
        buffer.writeFloat(centerX)
        buffer.writeFloat(centerY)
    }

    /** L2 render: drive the canvas transform via the paint context (REM-33). */
    override fun paint(context: RemoteContext, paint: PaintContext) {
        paint.matrixScale(rScaleX, rScaleY, rCenterX, rCenterY)
    }

    override fun dump(): String = "MATRIX_SCALE s=[$scaleX,$scaleY] c=[$centerX,$centerY]"

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is MatrixScale &&
                scaleX.toRawBits() == other.scaleX.toRawBits() &&
                scaleY.toRawBits() == other.scaleY.toRawBits() &&
                centerX.toRawBits() == other.centerX.toRawBits() &&
                centerY.toRawBits() == other.centerY.toRawBits())

    override fun hashCode(): Int {
        var r = scaleX.toRawBits()
        r = 31 * r + scaleY.toRawBits()
        r = 31 * r + centerX.toRawBits()
        r = 31 * r + centerY.toRawBits()
        return r
    }

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += MatrixScale(buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat())
        }
    }
}
