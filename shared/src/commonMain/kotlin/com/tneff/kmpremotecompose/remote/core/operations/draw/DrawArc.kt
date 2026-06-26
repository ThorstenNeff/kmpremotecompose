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
 * `DRAW_ARC` (opcode [Operations.DRAW_ARC]) — draw an arc of the oval bounded by a rectangle.
 *
 * Wire layout: opcode byte + six big-endian floats `left, top, right, bottom, startAngle, sweepAngle`
 * (degrees; mirrors upstream `DrawArc` / `DrawBase6`, positional v1..v6). Floats may carry NaN ids.
 */
class DrawArc(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val startAngle: Float,
    val sweepAngle: Float,
) : PaintOperation, VariableSupport {

    override val opcode: Int get() = Operations.DRAW_ARC

    // REM-36 E3: NaN data-var coords resolved at render time; raw fields untouched (byte-safe).
    var rLeft: Float = left
    var rTop: Float = top
    var rRight: Float = right
    var rBottom: Float = bottom
    var rStartAngle: Float = startAngle
    var rSweepAngle: Float = sweepAngle

    override fun updateVariables(context: RemoteContext) {
        rLeft = context.resolveCoord(left)
        rTop = context.resolveCoord(top)
        rRight = context.resolveCoord(right)
        rBottom = context.resolveCoord(bottom)
        rStartAngle = context.resolveCoord(startAngle)
        rSweepAngle = context.resolveCoord(sweepAngle)
    }

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeFloat(left)
        buffer.writeFloat(top)
        buffer.writeFloat(right)
        buffer.writeFloat(bottom)
        buffer.writeFloat(startAngle)
        buffer.writeFloat(sweepAngle)
    }

    /** L2 render: dispatch to the geometry adapter via the paint context (REM-8). */
    override fun paint(context: RemoteContext, paint: PaintContext) {
        paint.drawArc(rLeft, rTop, rRight, rBottom, rStartAngle, rSweepAngle)
    }

    override fun dump(): String =
        "DRAW_ARC l=$left t=$top r=$right b=$bottom start=$startAngle sweep=$sweepAngle"

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is DrawArc &&
                left.toRawBits() == other.left.toRawBits() && top.toRawBits() == other.top.toRawBits() &&
                right.toRawBits() == other.right.toRawBits() && bottom.toRawBits() == other.bottom.toRawBits() &&
                startAngle.toRawBits() == other.startAngle.toRawBits() &&
                sweepAngle.toRawBits() == other.sweepAngle.toRawBits()
            )

    override fun hashCode(): Int {
        var h = left.hashCode()
        h = 31 * h + top.hashCode()
        h = 31 * h + right.hashCode()
        h = 31 * h + bottom.hashCode()
        h = 31 * h + startAngle.hashCode()
        h = 31 * h + sweepAngle.hashCode()
        return h
    }

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += DrawArc(
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
            )
        }
    }
}
