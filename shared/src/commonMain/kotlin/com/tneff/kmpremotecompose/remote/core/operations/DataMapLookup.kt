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

import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.player.core.VariableSupport
import com.tneff.kmpremotecompose.remote.wire.WireBuffer

/**
 * Look up a value in a data map by string key (`DATA_MAP_LOOKUP`): `id = dataMap[keyString]`.
 *
 * Wire layout: opcode, `int id`, `int dataMapId`, `int keyStringId`.
 *
 * **REM-139 S1:** upstream `DataMapLookup.apply` resolves the key string in the [DataMapIds] under
 * [dataMapId] and loads the matched entry's value into [id]. Phase-A producer; [write]/[read] untouched
 * (§2). STRING/FLOAT are corpus-exercised; INT/LONG/BOOLEAN are corpus-absent → loud-guard (no silent
 * mis-resolve — they need an integer store / object constants we don't model yet).
 */
class DataMapLookup(val id: Int, val dataMapId: Int, val keyStringId: Int) : Operation, VariableSupport {
    override val opcode: Int get() = Operations.DATA_MAP_LOOKUP

    /** Resolve `id = dataMap[keyString]` into the store (producer side, upstream `apply`). */
    override fun apply(context: RemoteContext) {
        val key = context.getText(keyStringId) ?: return
        val map = context.getFromId(dataMapId) as? DataMapIds ?: return
        val entry = map.lookup(key) ?: return
        when (entry.type) {
            DataMapIds.TYPE_STRING -> context.getText(entry.valueId)?.let { context.putText(id, it) }
            DataMapIds.TYPE_FLOAT -> context.loadFloat(id, context.getFloat(entry.valueId))
            else -> throw IllegalArgumentException(
                "DATA_MAP_LOOKUP entry type ${entry.type} is corpus-absent and unimplemented (REM-139 S1 loud-guard)",
            )
        }
    }

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(id)
        buffer.writeInt(dataMapId)
        buffer.writeInt(keyStringId)
    }

    override fun dump(): String = "DATA_MAP_LOOKUP id=$id dataMapId=$dataMapId key=$keyStringId"

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is DataMapLookup && id == other.id && dataMapId == other.dataMapId && keyStringId == other.keyStringId)

    override fun hashCode(): Int = 31 * (31 * id + dataMapId) + keyStringId

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += DataMapLookup(buffer.readInt(), buffer.readInt(), buffer.readInt())
        }
    }
}
