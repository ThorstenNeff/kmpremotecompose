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
 * A single decoded operation of a RemoteCompose `.rc` document.
 *
 * An operation knows how to serialize itself back to the wire ([write]) and to describe itself for
 * debugging ([dump]). The decode side lives in the operation's companion as an [OperationReader] so
 * that decoding can run without first allocating an instance.
 *
 * Byte-compatibility rule: [write] is the source of truth for field order; the matching
 * [OperationReader] must read exactly the same fields in the same order. Every operation carries a
 * round-trip test (write → read → write must be byte-identical).
 */
interface Operation {

    /** The 1-byte opcode that prefixes this operation on the wire (0..255). */
    val opcode: Int

    /** Serialize this operation — opcode byte followed by its fields — into [buffer]. */
    fun write(buffer: WireBuffer)

    /** A human-readable, single-line description of the operation and its fields (debug only). */
    fun dump(): String
}

/**
 * The decode counterpart of an [Operation] (the upstream "CompanionOperation"): reads one operation
 * from the buffer — the opcode byte has already been consumed by the dispatch loop — and appends the
 * decoded [Operation] to [operations].
 */
fun interface OperationReader {
    fun read(buffer: WireBuffer, operations: MutableList<Operation>)
}
