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
 * A color theme entry (`COLOR_THEME`): light/dark color mapping for [id]/[groupId].
 *
 * Wire layout: opcode, `int id`, `int groupId`, `short lightMode`, `short darkMode`,
 * `int lightModeFallback`, `int darkModeFallback`. This is a profile-overlay operation (androidx /
 * widgets), not part of the base set.
 *
 * REM-131: runtime is additive (wire form unchanged). [lightMode]/[darkMode] are the upstream
 * color-*group indices* (`mLightModeIndex`/`mDarkModeIndex`) used only when a named host color group
 * is bound (REM-68 territory, not wired here); the rendered value is the resolved fallback ARGB
 * (`lightModeFallback`/`darkModeFallback`) — exactly what upstream initializes `mLightMode`/`mDarkMode`
 * to and what its `apply` loads.
 */
class ColorTheme(
    val id: Int,
    val groupId: Int,
    val lightMode: Int,
    val darkMode: Int,
    val lightModeFallback: Int,
    val darkModeFallback: Int,
) : Operation, VariableSupport {

    override val opcode: Int get() = Operations.COLOR_THEME

    /**
     * Publish the themed color (upstream `ColorTheme.apply`): pick the light or dark fallback ARGB by
     * the context's active paint theme (default LIGHT), then [RemoteContext.loadColor] it under [id].
     */
    override fun apply(context: RemoteContext) {
        val color = if (context.getPaintTheme() == Theme.LIGHT) lightModeFallback else darkModeFallback
        context.loadColor(id, color)
    }

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
