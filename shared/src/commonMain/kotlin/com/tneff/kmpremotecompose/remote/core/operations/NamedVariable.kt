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
 * A named variable (`NAMED_VARIABLE`): binds a name (and a type) to [varId].
 *
 * Wire layout: opcode, `int varId`, `int varType`, length-prefixed UTF-8 name.
 */
class NamedVariable(val varId: Int, val varType: Int, val name: String) : Operation {

    override val opcode: Int get() = Operations.NAMED_VARIABLE

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(varId)
        buffer.writeInt(varType)
        buffer.writeUTF8(name)
    }

    override fun dump(): String = "NAMED_VARIABLE id=$varId type=$varType name=\"$name\""

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is NamedVariable && varId == other.varId && varType == other.varType && name == other.name)

    override fun hashCode(): Int = 31 * (31 * varId + varType) + name.hashCode()

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            val varId = buffer.readInt()
            val varType = buffer.readInt()
            val name = buffer.readUTF8(com.tneff.kmpremotecompose.remote.wire.WireTypes.MAX_STRING_SIZE)
            operations += NamedVariable(varId, varType, name)
        }
    }
}
