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
package com.tneff.kmpremotecompose.remote.core.debug

/**
 * One operation's footprint in a decoded `.rc` stream: its opcode, the byte range it occupied
 * (`[byteStart, byteEnd)`, opcode byte inclusive), and a one-line field dump.
 *
 * A list of these is exactly the byte map of a document — the primary tool for diffing a
 * KMP-produced `.rc` against the reference oracle: the first span whose bytes differ pinpoints the
 * first divergent operation for a byte-equality failure.
 */
data class OpSpan(
    val opcode: Int,
    val name: String,
    val byteStart: Int,
    val byteEnd: Int,
    val fields: String,
) {
    /** Number of bytes this operation occupies on the wire (opcode byte included). */
    val byteLength: Int get() = byteEnd - byteStart

    override fun toString(): String = "@$byteStart..$byteEnd [$opcode $name] $fields"
}
