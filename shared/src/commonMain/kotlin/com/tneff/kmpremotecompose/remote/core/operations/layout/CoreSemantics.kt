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
 * `ACCESSIBILITY_SEMANTICS` (opcode [Operations.ACCESSIBILITY_SEMANTICS]) — accessibility metadata
 * for a component.
 *
 * Wire layout (mirrors upstream `CoreSemantics.apply`/`read`, 17 bytes): opcode byte + int
 * `contentDescriptionId` + **byte** `role` + int `textId` + int `stateDescriptionId` + **byte** `mode`
 * + boolean `enabled` + boolean `clickable`. `role`/`mode` are enum ordinals on the wire (a single
 * byte each); carried as raw ints here.
 */
class CoreSemantics(
    val contentDescriptionId: Int,
    val role: Int,
    val textId: Int,
    val stateDescriptionId: Int,
    val mode: Int,
    val enabled: Boolean,
    val clickable: Boolean,
) : Operation {

    override val opcode: Int get() = Operations.ACCESSIBILITY_SEMANTICS

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(contentDescriptionId)
        buffer.writeByte(role)
        buffer.writeInt(textId)
        buffer.writeInt(stateDescriptionId)
        buffer.writeByte(mode)
        buffer.writeBoolean(enabled)
        buffer.writeBoolean(clickable)
    }

    override fun dump(): String =
        "ACCESSIBILITY_SEMANTICS contentDescId=$contentDescriptionId role=$role textId=$textId " +
            "stateDescId=$stateDescriptionId mode=$mode enabled=$enabled clickable=$clickable"

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is CoreSemantics &&
                contentDescriptionId == other.contentDescriptionId && role == other.role &&
                textId == other.textId && stateDescriptionId == other.stateDescriptionId &&
                mode == other.mode && enabled == other.enabled && clickable == other.clickable
            )

    override fun hashCode(): Int {
        var h = contentDescriptionId
        h = 31 * h + role
        h = 31 * h + textId
        h = 31 * h + stateDescriptionId
        h = 31 * h + mode
        h = 31 * h + enabled.hashCode()
        h = 31 * h + clickable.hashCode()
        return h
    }

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += CoreSemantics(
                contentDescriptionId = buffer.readInt(),
                role = buffer.readByte(),
                textId = buffer.readInt(),
                stateDescriptionId = buffer.readInt(),
                mode = buffer.readByte(),
                enabled = buffer.readBoolean(),
                clickable = buffer.readBoolean(),
            )
        }
    }
}
