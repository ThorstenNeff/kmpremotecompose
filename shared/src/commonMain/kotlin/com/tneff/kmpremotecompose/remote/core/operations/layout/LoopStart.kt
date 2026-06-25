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
 * `LOOP_START` (opcode [Operations.LOOP_START]) — begins a loop binding an index variable.
 *
 * Wire layout: opcode byte + int `indexId` + float `from` + float `step` + float `until` = 17 bytes
 * (mirrors upstream `LoopOperation.apply`/`read`). The floats may carry NaN-encoded ids; raw bits
 * preserved.
 */
class LoopStart(
    val indexId: Int,
    val from: Float,
    val step: Float,
    val until: Float,
) : Operation {

    override val opcode: Int get() = Operations.LOOP_START

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(indexId)
        buffer.writeFloat(from)
        buffer.writeFloat(step)
        buffer.writeFloat(until)
    }

    override fun dump(): String = "LOOP_START indexId=$indexId from=$from step=$step until=$until"

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is LoopStart &&
                indexId == other.indexId &&
                from.toRawBits() == other.from.toRawBits() && step.toRawBits() == other.step.toRawBits() &&
                until.toRawBits() == other.until.toRawBits()
            )

    override fun hashCode(): Int {
        var h = indexId
        h = 31 * h + from.toRawBits()
        h = 31 * h + step.toRawBits()
        h = 31 * h + until.toRawBits()
        return h
    }

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += LoopStart(buffer.readInt(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat())
        }
    }
}
