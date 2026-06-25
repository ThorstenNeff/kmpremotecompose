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
 * `COMPONENT_START` (opcode [Operations.COMPONENT_START]) — begins a component definition.
 *
 * Wire layout: opcode byte + int `type` + int `componentId` (mirrors upstream `ComponentStart`).
 */
class ComponentStart(val type: Int, val componentId: Int) : Operation {

    override val opcode: Int get() = Operations.COMPONENT_START

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(type)
        buffer.writeInt(componentId)
    }

    override fun dump(): String = "COMPONENT_START type=$type id=$componentId"

    override fun equals(other: Any?): Boolean =
        this === other || (other is ComponentStart && type == other.type && componentId == other.componentId)

    override fun hashCode(): Int = 31 * type + componentId

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += ComponentStart(buffer.readInt(), buffer.readInt())
        }
    }
}
