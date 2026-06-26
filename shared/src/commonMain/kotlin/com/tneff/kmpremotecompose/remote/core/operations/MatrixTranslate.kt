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

/** Translate the canvas matrix (`MATRIX_TRANSLATE`). Wire layout: opcode, `float x`, `float y`. */
class MatrixTranslate(val translateX: Float, val translateY: Float) : PaintOperation, VariableSupport {
    override val opcode: Int get() = Operations.MATRIX_TRANSLATE

    // REM-36 E3: NaN data-variable coords resolved at render time; raw fields untouched (byte-safe).
    /** Render-resolved [translateX] (REM-36 E3). */
    var rTranslateX: Float = translateX
    /** Render-resolved [translateY] (REM-36 E3). */
    var rTranslateY: Float = translateY

    override fun updateVariables(context: RemoteContext) {
        rTranslateX = context.resolveCoord(translateX)
        rTranslateY = context.resolveCoord(translateY)
    }

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeFloat(translateX)
        buffer.writeFloat(translateY)
    }

    /** L2 render: drive the canvas transform via the paint context (REM-33). */
    override fun paint(context: RemoteContext, paint: PaintContext) {
        paint.matrixTranslate(rTranslateX, rTranslateY)
    }

    override fun dump(): String = "MATRIX_TRANSLATE x=$translateX y=$translateY"

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is MatrixTranslate &&
                translateX.toRawBits() == other.translateX.toRawBits() &&
                translateY.toRawBits() == other.translateY.toRawBits())

    override fun hashCode(): Int = 31 * translateX.toRawBits() + translateY.toRawBits()

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += MatrixTranslate(buffer.readFloat(), buffer.readFloat())
        }
    }
}
