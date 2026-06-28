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
import com.tneff.kmpremotecompose.remote.wire.WireTypes

/**
 * A named id map (`ID_MAP`): maps string keys to ids (with a per-entry type tag) under [id].
 *
 * Wire layout: opcode, `int id`, `int count`, then for each entry a length-prefixed UTF-8 name, a
 * `byte` type and an `int` id. Entry order is preserved for byte-exact round-tripping.
 */
class DataMapIds(val id: Int, val entries: List<Entry>) : Operation {

    /**
     * One id-map entry: a [name], a [type] tag (`STRING=0, INT=1, FLOAT=2` — verified against
     * upstream `DataMapIds.java:44-46`) and the referenced [valueId]. [type] is written as a
     * single wire byte.
     */
    data class Entry(val name: String, val type: Int, val valueId: Int)

    override val opcode: Int get() = Operations.ID_MAP

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(id)
        buffer.writeInt(entries.size)
        for (e in entries) {
            buffer.writeUTF8(e.name)
            buffer.writeByte(e.type)
            buffer.writeInt(e.valueId)
        }
    }

    override fun dump(): String = "ID_MAP id=$id entries=${entries.size}"

    override fun equals(other: Any?): Boolean =
        this === other || (other is DataMapIds && id == other.id && entries == other.entries)

    override fun hashCode(): Int = 31 * id + entries.hashCode()

    companion object : OperationReader {
        /** Mirror of the upstream `Limits.MAX_DATA_MAP_SIZE`. */
        const val MAX_DATA_MAP_SIZE = 2000

        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            val id = buffer.readInt()
            val count = buffer.readInt()
            if (count < 0 || count > MAX_DATA_MAP_SIZE) throw IllegalStateException("id map too large: $count")
            val entries = ArrayList<Entry>(count)
            repeat(count) {
                val name = buffer.readUTF8(WireTypes.MAX_STRING_SIZE)
                val type = buffer.readByte()
                val valueId = buffer.readInt()
                entries += Entry(name, type, valueId)
            }
            operations += DataMapIds(id, entries)
        }
    }
}
