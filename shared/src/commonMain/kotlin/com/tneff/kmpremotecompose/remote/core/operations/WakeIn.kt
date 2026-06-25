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
 * Schedule a re-render (`WAKE_IN`): wake the document after [wake] seconds.
 *
 * Wire layout: opcode, `float wake` (raw bits). Profile-overlay op (androidx + widgets).
 */
class WakeIn(val wake: Float) : Operation {
    override val opcode: Int get() = Operations.WAKE_IN

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeFloat(wake)
    }

    override fun dump(): String = "WAKE_IN wake=$wake"

    override fun equals(other: Any?): Boolean =
        this === other || (other is WakeIn && wake.toRawBits() == other.wake.toRawBits())

    override fun hashCode(): Int = wake.toRawBits()

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += WakeIn(buffer.readFloat())
        }
    }
}
