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
 * `LAYOUT_COMPUTE` (opcode [Operations.LAYOUT_COMPUTE]) — a computed-layout operation (experimental
 * overlay op).
 *
 * Wire layout: opcode byte + int `type` + int `boundsId` + boolean `animateChanges` = 10 bytes
 * (mirrors upstream `LayoutComputeOperation.apply`/`read`).
 */
class LayoutCompute(
    val type: Int,
    val boundsId: Int,
    val animateChanges: Boolean,
) : Operation {

    override val opcode: Int get() = Operations.LAYOUT_COMPUTE

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(type)
        buffer.writeInt(boundsId)
        buffer.writeBoolean(animateChanges)
    }

    override fun dump(): String = "LAYOUT_COMPUTE type=$type boundsId=$boundsId animateChanges=$animateChanges"

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is LayoutCompute &&
                type == other.type && boundsId == other.boundsId && animateChanges == other.animateChanges
            )

    override fun hashCode(): Int = 31 * (31 * type + boundsId) + animateChanges.hashCode()

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += LayoutCompute(buffer.readInt(), buffer.readInt(), buffer.readBoolean())
        }
    }
}
