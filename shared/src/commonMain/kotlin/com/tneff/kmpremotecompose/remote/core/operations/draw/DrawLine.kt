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
) : Operation {

    override val opcode: Int get() = Operations.DRAW_LINE

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeFloat(x1)
        buffer.writeFloat(y1)
        buffer.writeFloat(x2)
        buffer.writeFloat(y2)
    }

    override fun dump(): String = "DRAW_LINE x1=$x1 y1=$y1 x2=$x2 y2=$y2"

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is DrawLine && x1 == other.x1 && y1 == other.y1 && x2 == other.x2 && y2 == other.y2)

    override fun hashCode(): Int =
        31 * (31 * (31 * x1.hashCode() + y1.hashCode()) + x2.hashCode()) + y2.hashCode()

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += DrawLine(buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat())
        }
    }
}
