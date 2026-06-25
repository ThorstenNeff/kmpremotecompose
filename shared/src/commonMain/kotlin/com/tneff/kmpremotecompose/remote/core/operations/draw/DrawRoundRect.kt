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
 * `DRAW_ROUND_RECT` (opcode [Operations.DRAW_ROUND_RECT]) — rounded rectangle.
 *
 * Wire layout: opcode byte + six big-endian floats `left, top, right, bottom, radiusX, radiusY`
 * (mirrors upstream `DrawRoundRect` / `DrawBase6`, positional v1..v6). Floats may carry NaN ids.
 */
class DrawRoundRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val radiusX: Float,
    val radiusY: Float,
) : Operation {

    override val opcode: Int get() = Operations.DRAW_ROUND_RECT

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeFloat(left)
        buffer.writeFloat(top)
        buffer.writeFloat(right)
        buffer.writeFloat(bottom)
        buffer.writeFloat(radiusX)
        buffer.writeFloat(radiusY)
    }

    override fun dump(): String = "DRAW_ROUND_RECT l=$left t=$top r=$right b=$bottom rx=$radiusX ry=$radiusY"

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is DrawRoundRect &&
                left == other.left && top == other.top && right == other.right &&
                bottom == other.bottom && radiusX == other.radiusX && radiusY == other.radiusY
            )

    override fun hashCode(): Int {
        var h = left.hashCode()
        h = 31 * h + top.hashCode()
        h = 31 * h + right.hashCode()
        h = 31 * h + bottom.hashCode()
        h = 31 * h + radiusX.hashCode()
        h = 31 * h + radiusY.hashCode()
        return h
    }

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += DrawRoundRect(
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
            )
        }
    }
}
