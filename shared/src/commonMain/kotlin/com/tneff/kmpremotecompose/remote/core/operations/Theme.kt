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
 * Select the document's color theme (`THEME`): light/dark/system as the [theme] code.
 *
 * Wire layout: opcode, `int theme`.
 *
 * REM-131: runtime is additive (wire form unchanged). [apply] publishes the section theme into the
 * context (upstream `Theme.apply` → `context.setTheme`). It is the infrastructure half of the
 * theme/color cluster; no paint gate consumes it yet, so for documents that only carry THEME (and no
 * [ColorTheme]) this is a deliberate no-op on render — "no regress" is the acceptance bar there.
 */
class Theme(val theme: Int) : Operation, VariableSupport {

    override val opcode: Int get() = Operations.THEME

    /** Publish the section theme (upstream `Theme.apply` → `context.setTheme(mTheme)`). */
    override fun apply(context: RemoteContext) {
        context.setTheme(theme)
    }

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(theme)
    }

    override fun dump(): String = "THEME theme=$theme"

    override fun equals(other: Any?): Boolean = this === other || (other is Theme && theme == other.theme)

    override fun hashCode(): Int = theme

    companion object : OperationReader {
        // Theme mode codes, verbatim from upstream Theme.* — also mirrored as literals in RemoteContext.
        const val SYSTEM = 0
        const val UNSPECIFIED = -1
        const val DARK = -2
        const val LIGHT = -3

        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += Theme(buffer.readInt())
        }
    }
}
