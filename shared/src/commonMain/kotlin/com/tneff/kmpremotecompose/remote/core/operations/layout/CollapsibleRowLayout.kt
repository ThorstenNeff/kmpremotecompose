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
 * `LAYOUT_COLLAPSIBLE_ROW` (opcode [Operations.LAYOUT_COLLAPSIBLE_ROW]) — a collapsible row container.
 *
 * Wire layout: opcode byte + int `componentId` + int `animationId` + int `horizontalPositioning` +
 * int `verticalPositioning` + float `spacedBy` = 21 bytes (mirrors upstream `CollapsibleRowLayout`,
 * which extends `RowLayout` — same fields).
 */
class CollapsibleRowLayout(
    val componentId: Int,
    val animationId: Int,
    val horizontalPositioning: Int,
    val verticalPositioning: Int,
    val spacedBy: Float,
) : Operation {

    override val opcode: Int get() = Operations.LAYOUT_COLLAPSIBLE_ROW

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(componentId)
        buffer.writeInt(animationId)
        buffer.writeInt(horizontalPositioning)
        buffer.writeInt(verticalPositioning)
        buffer.writeFloat(spacedBy)
    }

    override fun dump(): String =
        "LAYOUT_COLLAPSIBLE_ROW id=$componentId anim=$animationId hPos=$horizontalPositioning " +
            "vPos=$verticalPositioning spacedBy=$spacedBy"

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is CollapsibleRowLayout &&
                componentId == other.componentId && animationId == other.animationId &&
                horizontalPositioning == other.horizontalPositioning &&
                verticalPositioning == other.verticalPositioning &&
                spacedBy.toRawBits() == other.spacedBy.toRawBits()
            )

    override fun hashCode(): Int {
        var h = componentId
        h = 31 * h + animationId
        h = 31 * h + horizontalPositioning
        h = 31 * h + verticalPositioning
        h = 31 * h + spacedBy.toRawBits()
        return h
    }

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += CollapsibleRowLayout(
                buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readFloat(),
            )
        }
    }
}
