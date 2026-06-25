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
 * `DRAW_CIRCLE` (opcode [Operations.DRAW_CIRCLE]) — draw a circle by centre and radius.
 *
 * Wire layout: opcode byte followed by three big-endian IEEE-754 floats `centerX, centerY, radius`
 * (mirrors upstream `DrawCircle` / `DrawBase3`). Each float may instead carry a NaN-encoded variable
 * id; `WireBuffer` preserves the raw bits either way, so the byte layout is identical (see
 * [com.tneff.kmpremotecompose.remote.wire.WireTypes.asNan]).
 */
class DrawCircle(
    val centerX: Float,
    val centerY: Float,
    val radius: Float,
) : Operation {

    override val opcode: Int get() = Operations.DRAW_CIRCLE

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeFloat(centerX)
        buffer.writeFloat(centerY)
        buffer.writeFloat(radius)
    }

    override fun dump(): String = "DRAW_CIRCLE cx=$centerX cy=$centerY r=$radius"

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is DrawCircle && centerX == other.centerX && centerY == other.centerY && radius == other.radius)

    override fun hashCode(): Int = (31 * (31 * centerX.hashCode() + centerY.hashCode())) + radius.hashCode()

    /** Decode side (opcode already consumed by the dispatch loop). */
    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += DrawCircle(buffer.readFloat(), buffer.readFloat(), buffer.readFloat())
        }
    }
}
