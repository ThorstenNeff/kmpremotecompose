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
 * `LAYOUT_BOX` (opcode [Operations.LAYOUT_BOX]) — a box layout container.
 *
 * Wire layout: opcode byte + int `componentId` + int `animationId` + int `horizontalPositioning` +
 * int `verticalPositioning` (mirrors upstream `BoxLayout.apply`/`read`, all four ints; remaining
 * geometry fields are runtime-only).
 */
class BoxLayout(
    val componentId: Int,
    val animationId: Int,
    val horizontalPositioning: Int,
    val verticalPositioning: Int,
) : Operation {

    override val opcode: Int get() = Operations.LAYOUT_BOX

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(componentId)
        buffer.writeInt(animationId)
        buffer.writeInt(horizontalPositioning)
        buffer.writeInt(verticalPositioning)
    }

    override fun dump(): String =
        "LAYOUT_BOX id=$componentId anim=$animationId hPos=$horizontalPositioning vPos=$verticalPositioning"

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is BoxLayout &&
                componentId == other.componentId && animationId == other.animationId &&
                horizontalPositioning == other.horizontalPositioning &&
                verticalPositioning == other.verticalPositioning
            )

    override fun hashCode(): Int =
        31 * (31 * (31 * componentId + animationId) + horizontalPositioning) + verticalPositioning

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += BoxLayout(buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt())
        }
    }
}
