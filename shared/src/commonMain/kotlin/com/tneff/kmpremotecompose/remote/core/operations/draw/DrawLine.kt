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
import com.tneff.kmpremotecompose.remote.player.core.PaintContext
import com.tneff.kmpremotecompose.remote.player.core.PaintOperation
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.player.core.VariableSupport
import com.tneff.kmpremotecompose.remote.player.core.resolveCoord
import com.tneff.kmpremotecompose.remote.wire.WireBuffer

/**
 * `DRAW_LINE` (opcode [Operations.DRAW_LINE]) — draw a line between two points.
 *
 * Wire layout: opcode byte followed by four big-endian IEEE-754 floats `x1, y1, x2, y2`
 * (mirrors upstream `DrawLine` / `DrawBase4`). Floats may carry NaN-encoded ids; raw bits preserved.
 */
class DrawLine(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
) : PaintOperation, VariableSupport {

    override val opcode: Int get() = Operations.DRAW_LINE

    // REM-36 E3: NaN data-var coords resolved at render time; raw fields untouched (byte-safe).
    var rX1: Float = x1
    var rY1: Float = y1
    var rX2: Float = x2
    var rY2: Float = y2

    override fun updateVariables(context: RemoteContext) {
        rX1 = context.resolveCoord(x1)
        rY1 = context.resolveCoord(y1)
        rX2 = context.resolveCoord(x2)
        rY2 = context.resolveCoord(y2)
    }

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeFloat(x1)
        buffer.writeFloat(y1)
        buffer.writeFloat(x2)
        buffer.writeFloat(y2)
    }

    /** L2 render: dispatch to the geometry adapter via the paint context (REM-8). */
    override fun paint(context: RemoteContext, paint: PaintContext) {
        paint.drawLine(rX1, rY1, rX2, rY2)
    }

    override fun dump(): String = "DRAW_LINE x1=$x1 y1=$y1 x2=$x2 y2=$y2"

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is DrawLine &&
                    x1.toRawBits() == other.x1.toRawBits() && y1.toRawBits() == other.y1.toRawBits() &&
                    x2.toRawBits() == other.x2.toRawBits() && y2.toRawBits() == other.y2.toRawBits()
                )

    override fun hashCode(): Int =
        31 * (31 * (31 * x1.hashCode() + y1.hashCode()) + x2.hashCode()) + y2.hashCode()

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += DrawLine(buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat())
        }
    }
}
