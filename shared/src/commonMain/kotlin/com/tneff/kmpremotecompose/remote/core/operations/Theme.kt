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
 * Select the document's color theme (`THEME`): light/dark/system as the [theme] code.
 *
 * Wire layout: opcode, `int theme`.
 */
class Theme(val theme: Int) : Operation {

    override val opcode: Int get() = Operations.THEME

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(theme)
    }

    override fun dump(): String = "THEME theme=$theme"

    override fun equals(other: Any?): Boolean = this === other || (other is Theme && theme == other.theme)

    override fun hashCode(): Int = theme

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += Theme(buffer.readInt())
        }
    }
}
