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
 * `DRAW_TEXT_ANCHOR` (opcode [Operations.DRAW_TEXT_ANCHOR]) — draw a text resource anchored at a
 * point with pan factors.
 *
 * Wire layout: opcode byte + int `textId` + float `x` + float `y` + float `panX` + float `panY` +
 * int `flags` = 25 bytes (mirrors upstream `DrawTextAnchored.apply`/`read`). The floats may carry
 * NaN-encoded ids; raw bits preserved.
 */
class DrawTextAnchored(
    val textId: Int,
    val x: Float,
    val y: Float,
    val panX: Float,
    val panY: Float,
    val flags: Int,
) : Operation {

    override val opcode: Int get() = Operations.DRAW_TEXT_ANCHOR

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(textId)
        buffer.writeFloat(x)
        buffer.writeFloat(y)
        buffer.writeFloat(panX)
        buffer.writeFloat(panY)
        buffer.writeInt(flags)
    }

    override fun dump(): String = "DRAW_TEXT_ANCHOR textId=$textId x=$x y=$y panX=$panX panY=$panY flags=$flags"

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is DrawTextAnchored &&
                textId == other.textId &&
                x.toRawBits() == other.x.toRawBits() && y.toRawBits() == other.y.toRawBits() &&
                panX.toRawBits() == other.panX.toRawBits() && panY.toRawBits() == other.panY.toRawBits() &&
                flags == other.flags
            )

    override fun hashCode(): Int {
        var h = textId
        h = 31 * h + x.toRawBits()
        h = 31 * h + y.toRawBits()
        h = 31 * h + panX.toRawBits()
        h = 31 * h + panY.toRawBits()
        h = 31 * h + flags
        return h
    }

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += DrawTextAnchored(
                textId = buffer.readInt(),
                x = buffer.readFloat(),
                y = buffer.readFloat(),
                panX = buffer.readFloat(),
                panY = buffer.readFloat(),
                flags = buffer.readInt(),
            )
        }
    }
}
