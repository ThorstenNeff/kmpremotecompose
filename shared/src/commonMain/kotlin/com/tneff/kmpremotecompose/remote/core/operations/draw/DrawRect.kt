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
import com.tneff.kmpremotecompose.remote.wire.WireBuffer

/**
 * `DRAW_RECT` (opcode [Operations.DRAW_RECT]) — draw a rectangle by its edges.
 *
 * Wire layout: opcode byte followed by four big-endian IEEE-754 floats `left, top, right, bottom`
 * (mirrors upstream `DrawRect` / `DrawBase4`). Floats may carry NaN-encoded ids; raw bits preserved.
 */
class DrawRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) : PaintOperation {

    override val opcode: Int get() = Operations.DRAW_RECT

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeFloat(left)
        buffer.writeFloat(top)
        buffer.writeFloat(right)
        buffer.writeFloat(bottom)
    }

    /** L2 render: dispatch to the geometry adapter via the paint context (REM-8). */
    override fun paint(context: RemoteContext, paint: PaintContext) {
        paint.drawRect(left, top, right, bottom)
    }

    override fun dump(): String = "DRAW_RECT l=$left t=$top r=$right b=$bottom"

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is DrawRect &&
                    left.toRawBits() == other.left.toRawBits() && top.toRawBits() == other.top.toRawBits() &&
                    right.toRawBits() == other.right.toRawBits() && bottom.toRawBits() == other.bottom.toRawBits()
                )

    override fun hashCode(): Int =
        31 * (31 * (31 * left.hashCode() + top.hashCode()) + right.hashCode()) + bottom.hashCode()

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += DrawRect(buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat())
        }
    }
}
