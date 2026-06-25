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
package com.tneff.kmpremotecompose.remote.core.operations.layout

import com.tneff.kmpremotecompose.remote.core.operations.Operation
import com.tneff.kmpremotecompose.remote.core.operations.OperationReader
import com.tneff.kmpremotecompose.remote.core.operations.Operations
import com.tneff.kmpremotecompose.remote.wire.WireBuffer

/**
 * `IMPULSE_START` (opcode [Operations.IMPULSE_START]) — starts an impulse (timeline) container block;
 * child operations follow as separate ops until the matching container end.
 *
 * Wire layout: opcode byte + float `duration` + float `startAt` = 9 bytes (mirrors upstream
 * `ImpulseOperation.apply`/`read`). Floats may carry NaN-encoded ids; raw bits preserved.
 */
class ImpulseStart(
    val duration: Float,
    val startAt: Float,
) : Operation {

    override val opcode: Int get() = Operations.IMPULSE_START

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeFloat(duration)
        buffer.writeFloat(startAt)
    }

    override fun dump(): String = "IMPULSE_START duration=$duration startAt=$startAt"

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is ImpulseStart &&
                duration.toRawBits() == other.duration.toRawBits() && startAt.toRawBits() == other.startAt.toRawBits()
            )

    override fun hashCode(): Int = 31 * duration.toRawBits() + startAt.toRawBits()

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += ImpulseStart(buffer.readFloat(), buffer.readFloat())
        }
    }
}
