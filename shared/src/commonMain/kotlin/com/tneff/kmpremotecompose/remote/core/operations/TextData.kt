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

import com.tneff.kmpremotecompose.remote.player.core.PaintContext
import com.tneff.kmpremotecompose.remote.player.core.PaintOperation
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.wire.WireBuffer
import com.tneff.kmpremotecompose.remote.wire.WireTypes

/**
 * String data (`DATA_TEXT`): registers [text] under [id] for later reference by drawing/text ops.
 *
 * Wire layout: opcode, `int id`, then a length-prefixed UTF-8 string.
 */
class TextData(val id: Int, val text: String) : PaintOperation {

    override val opcode: Int get() = Operations.DATA_TEXT

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(id)
        buffer.writeUTF8(text)
    }

    /** Register the string into the player context so text-draw ops can resolve it by [id]. */
    override fun paint(context: RemoteContext, paint: PaintContext) {
        context.putText(id, text)
    }

    override fun dump(): String = "DATA_TEXT id=$id text=\"$text\""

    override fun equals(other: Any?): Boolean =
        this === other || (other is TextData && id == other.id && text == other.text)

    override fun hashCode(): Int = 31 * id + text.hashCode()

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            val id = buffer.readInt()
            val text = buffer.readUTF8(WireTypes.MAX_STRING_SIZE)
            operations += TextData(id, text)
        }
    }
}
