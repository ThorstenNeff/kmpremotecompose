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
 * A color constant (`COLOR_CONSTANT`): binds the ARGB [color] to [colorId].
 *
 * Wire layout: opcode, `int colorId`, `int color` (0xAARRGGBB).
 *
 * **Binding (REM-61):** a producer — Phase A loads the declared ARGB into the color store
 * ([RemoteContext.loadColor]) so consumers (`BackgroundModifier`/`BorderModifier`/`CoreText` and the
 * blend refs in `ColorExpression`) resolve `getColor(colorId)` to the document's color instead of the
 * fail-soft transparent default. Mirrors upstream `ColorConstant.apply` (implements `VariableProvider`).
 * These are the theme *fallback* values; a host theme override by name (`NamedVariable`) is deferred
 * (no name-registry consumer yet), so the constant is what renders.
 */
class ColorConstant(val colorId: Int, val color: Int) : Operation, VariableSupport {

    override val opcode: Int get() = Operations.COLOR_CONSTANT

    override fun apply(context: RemoteContext) {
        context.loadColor(colorId, color)
    }

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(colorId)
        buffer.writeInt(color)
    }

    override fun dump(): String = "COLOR_CONSTANT id=$colorId color=0x${(color.toLong() and 0xFFFFFFFFL).toString(16)}"

    override fun equals(other: Any?): Boolean =
        this === other || (other is ColorConstant && colorId == other.colorId && color == other.color)

    override fun hashCode(): Int = 31 * colorId + color

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            val colorId = buffer.readInt()
            val color = buffer.readInt()
            operations += ColorConstant(colorId, color)
        }
    }
}
