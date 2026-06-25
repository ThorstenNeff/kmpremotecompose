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
package com.tneff.kmpremotecompose.remote.core.document

import com.tneff.kmpremotecompose.remote.core.debug.OpSpan
import com.tneff.kmpremotecompose.remote.core.operations.Header
import com.tneff.kmpremotecompose.remote.core.operations.Operation
import com.tneff.kmpremotecompose.remote.core.operations.Operations
import com.tneff.kmpremotecompose.remote.wire.WireBuffer

/**
 * Decodes a `.rc` byte stream into a [RemoteComposeDocument] by walking the operation stream.
 *
 * Flow: read the header first (it is always opcode [Operations.HEADER] and carries the
 * `DOC_PROFILES` mask), then select the decode map from `(apiLevel, profiles)` and run the inflate
 * loop — one byte of opcode, dispatch to the registered [com.tneff.kmpremotecompose.remote.core.operations.OperationReader],
 * repeat until the buffer is empty. An unregistered opcode is a hard error reporting the byte offset
 * (fail closed): a document may only contain operations valid for its declared version and profiles.
 */
object DocumentReader {

    /** Upper bound on operations per document, guarding against malformed/hostile input. */
    const val MAX_OP_COUNT: Int = 20_000

    /** Decode [bytes] into a document. */
    fun inflate(bytes: ByteArray): RemoteComposeDocument = inflateInternal(bytes, trace = null)

    /**
     * Decode [bytes] and also return the per-operation byte map (opcode, byte range, field dump) —
     * the debugging trace used to diff against the reference oracle.
     */
    fun inflateWithTrace(bytes: ByteArray): Pair<RemoteComposeDocument, List<OpSpan>> {
        val spans = mutableListOf<OpSpan>()
        val doc = inflateInternal(bytes, trace = spans)
        return doc to spans
    }

    private fun inflateInternal(bytes: ByteArray, trace: MutableList<OpSpan>?): RemoteComposeDocument {
        val buffer = WireBuffer.fromBytes(bytes)
        val operations = mutableListOf<Operation>()
        if (!buffer.available()) return RemoteComposeDocument(operations)

        // Header first — it determines the profile-gated decode map for everything after it.
        val headerStart = buffer.byteIndex
        val headerOpcode = buffer.readByte()
        if (headerOpcode != Operations.HEADER) {
            throw IllegalStateException("document does not start with a header (opcode $headerOpcode)")
        }
        Header.read(buffer, operations)
        trace?.add(spanFor(headerOpcode, headerStart, buffer.byteIndex, operations.last()))

        val header = operations.first() as Header
        val readerMap = Operations.readerMapFor(header.apiLevel, header.profiles)

        var count = 0
        while (buffer.available()) {
            if (++count > MAX_OP_COUNT) {
                throw IllegalStateException("operation count exceeds limit ($MAX_OP_COUNT)")
            }
            val start = buffer.byteIndex
            val opcode = buffer.readByte()
            val reader = readerMap[opcode]
                ?: throw IllegalStateException(
                    "unknown opcode $opcode (${Operations.name(opcode)}) at byte $start",
                )
            reader.read(buffer, operations)
            trace?.add(spanFor(opcode, start, buffer.byteIndex, operations.last()))
        }
        return RemoteComposeDocument(operations)
    }

    private fun spanFor(opcode: Int, start: Int, end: Int, op: Operation): OpSpan =
        OpSpan(opcode, Operations.name(opcode), start, end, op.dump())
}
