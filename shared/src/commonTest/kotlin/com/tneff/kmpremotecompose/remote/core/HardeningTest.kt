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
package com.tneff.kmpremotecompose.remote.core

import com.tneff.kmpremotecompose.remote.core.document.DocumentReader
import com.tneff.kmpremotecompose.remote.core.operations.OperationReader
import com.tneff.kmpremotecompose.remote.core.operations.Operations
import com.tneff.kmpremotecompose.remote.core.operations.Operations.Layer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

private val NOOP = OperationReader { _, _ -> }

/** Hardening fixes from the REM-3 review: header magic fail-closed + registry thread-safety. */
class HardeningTest {

    @AfterTest
    fun cleanup() {
        Operations.resetReaders()
    }

    // Valid map-form header (screenshottest.rc, 49 bytes); magic high word = 0x048C.
    private val goldenHeader = bytes(
        0x00,
        0x04, 0x8C, 0x00, 0x01,
        0x00, 0x00, 0x00, 0x01,
        0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x04,
        0x00, 0x05, 0x00, 0x04, 0x00, 0x00, 0x01, 0x40,
        0x00, 0x06, 0x00, 0x04, 0x00, 0x00, 0x01, 0xD6,
        0x0C, 0x09, 0x00, 0x04, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x0E, 0x00, 0x04, 0x00, 0x00, 0x02, 0x00,
    )

    // ---------------------------------------------------------------------------------------------
    // Fix 1: header magic is validated fail-closed.
    // ---------------------------------------------------------------------------------------------

    @Test
    fun header_withForeignMagic_isRejected() {
        // Corrupt the magic high word (0x04 -> 0x05): major becomes 0x058C0001 — still ≥ 0x10000 so
        // it takes the map-form path, but the magic no longer matches and must be rejected.
        val corrupt = goldenHeader.copyOf()
        corrupt[1] = 0x05
        val ex = assertFailsWith<IllegalStateException> { DocumentReader.inflate(corrupt) }
        assertTrue(ex.message!!.contains("magic"), "expected a magic error, got: ${ex.message}")
    }

    @Test
    fun header_withValidMagic_stillParses() {
        val doc = DocumentReader.inflate(goldenHeader)
        assertEquals(320, doc.width)
        assertEquals(470, doc.height)
    }

    // ---------------------------------------------------------------------------------------------
    // Fix 2: registry reads are pure (no stale per-read cache) and registration publishes atomically.
    // (Real multi-thread stress is not portably expressible in commonTest; this pins the structural
    // guarantees the copy-on-write snapshot provides.)
    // ---------------------------------------------------------------------------------------------

    @Test
    fun readerMap_isRecomputedAfterRegistration_noStaleCache() {
        Operations.resetReaders()
        Operations.register(Layer.V7_BASE, Operations.DATA_TEXT, NOOP)
        assertTrue(Operations.readerMapFor(7, 0).containsKey(Operations.DATA_TEXT))

        // A second registration must be visible immediately (the old per-read cache could go stale).
        Operations.register(Layer.V7_BASE, Operations.DATA_FLOAT, NOOP)
        val map = Operations.readerMapFor(7, 0)
        assertTrue(map.containsKey(Operations.DATA_TEXT))
        assertTrue(map.containsKey(Operations.DATA_FLOAT))
    }

    @Test
    fun readerMap_isStableAndIsolatedFromCallerMutation() {
        Operations.resetReaders()
        Operations.register(Layer.V7_BASE, Operations.DATA_TEXT, NOOP)
        val first = Operations.readerMapFor(7, 0)
        val second = Operations.readerMapFor(7, 0)
        assertEquals(first.keys, second.keys) // pure: repeated calls agree
        // Mutating a returned composed map must not affect the registry.
        (first as? MutableMap)?.clear()
        assertTrue(Operations.readerMapFor(7, 0).containsKey(Operations.DATA_TEXT))
    }

    @Test
    fun resetReaders_clearsTheSnapshot() {
        Operations.register(Layer.V7_BASE, Operations.DATA_TEXT, NOOP)
        assertTrue(Operations.readerMapFor(7, 0).containsKey(Operations.DATA_TEXT))
        Operations.resetReaders()
        assertFalse(Operations.readerMapFor(7, 0).containsKey(Operations.DATA_TEXT))
    }
}
