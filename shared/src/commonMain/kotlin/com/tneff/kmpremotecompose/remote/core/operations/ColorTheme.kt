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
 * A color theme entry (`COLOR_THEME`): light/dark color mapping for [id]/[groupId].
 *
 * Wire layout: opcode, `int id`, `int groupId`, `short lightMode`, `short darkMode`,
 * `int lightModeFallback`, `int darkModeFallback`. This is a profile-overlay operation (androidx /
 * widgets), not part of the base set.
 */
class ColorTheme(
    val id: Int,
    val groupId: Int,
    val lightMode: Int,
    val darkMode: Int,
    val lightModeFallback: Int,
    val darkModeFallback: Int,
) : Operation {

    override val opcode: Int get() = Operations.COLOR_THEME

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(id)
        buffer.writeInt(groupId)
        buffer.writeShort(lightMode)
        buffer.writeShort(darkMode)
        buffer.writeInt(lightModeFallback)
        buffer.writeInt(darkModeFallback)
    }

    override fun dump(): String =
        "COLOR_THEME id=$id group=$groupId light=$lightMode dark=$darkMode fb=[$lightModeFallback,$darkModeFallback]"

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is ColorTheme && id == other.id && groupId == other.groupId &&
                lightMode == other.lightMode && darkMode == other.darkMode &&
                lightModeFallback == other.lightModeFallback && darkModeFallback == other.darkModeFallback)

    override fun hashCode(): Int {
        var r = id
        r = 31 * r + groupId
        r = 31 * r + lightMode
        r = 31 * r + darkMode
        r = 31 * r + lightModeFallback
        r = 31 * r + darkModeFallback
        return r
    }

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += ColorTheme(
                id = buffer.readInt(),
                groupId = buffer.readInt(),
                lightMode = buffer.readShort(),
                darkMode = buffer.readShort(),
                lightModeFallback = buffer.readInt(),
                darkModeFallback = buffer.readInt(),
            )
        }
    }
}
