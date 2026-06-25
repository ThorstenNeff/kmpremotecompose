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
package com.tneff.kmpremotecompose.remote.core.operations

import com.tneff.kmpremotecompose.remote.wire.WireBuffer

/**
 * Binds a runtime value to a component property (`COMPONENT_VALUE`): exposes a [valueId] for the
 * given [type] of [componentId] (e.g. a measured size of a component) so the document can react to it.
 *
 * Wire layout: opcode, `int type`, `int componentId`, `int valueId`.
 */
class ComponentValue(val type: Int, val componentId: Int, val valueId: Int) : Operation {

    override val opcode: Int get() = Operations.COMPONENT_VALUE

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(type)
        buffer.writeInt(componentId)
        buffer.writeInt(valueId)
    }

    override fun dump(): String = "COMPONENT_VALUE type=$type component=$componentId value=$valueId"

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is ComponentValue && type == other.type && componentId == other.componentId && valueId == other.valueId)

    override fun hashCode(): Int = 31 * (31 * type + componentId) + valueId

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += ComponentValue(buffer.readInt(), buffer.readInt(), buffer.readInt())
        }
    }
}
