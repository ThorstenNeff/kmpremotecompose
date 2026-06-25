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
 * `CLIP_RECT` (opcode [Operations.CLIP_RECT]) — clips the canvas to a rectangle (the draw op, distinct
 * from `MODIFIER_CLIP_RECT` 108).
 *
 * Wire layout: opcode byte + float `x1` + float `y1` + float `x2` + float `y2` = 17 bytes (mirrors
 * upstream `ClipRect` / `DrawBase4`). Floats may carry NaN-encoded ids; raw bits preserved.
 */
class ClipRect(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
) : Operation {

    override val opcode: Int get() = Operations.CLIP_RECT

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeFloat(x1)
        buffer.writeFloat(y1)
        buffer.writeFloat(x2)
        buffer.writeFloat(y2)
    }

    override fun dump(): String = "CLIP_RECT x1=$x1 y1=$y1 x2=$x2 y2=$y2"

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is ClipRect &&
                x1.toRawBits() == other.x1.toRawBits() && y1.toRawBits() == other.y1.toRawBits() &&
                x2.toRawBits() == other.x2.toRawBits() && y2.toRawBits() == other.y2.toRawBits()
            )

    override fun hashCode(): Int {
        var h = x1.toRawBits()
        h = 31 * h + y1.toRawBits()
        h = 31 * h + x2.toRawBits()
        h = 31 * h + y2.toRawBits()
        return h
    }

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += ClipRect(buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat())
        }
    }
}
