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
package com.tneff.kmpremotecompose.remote.creation

import com.tneff.kmpremotecompose.remote.core.document.DocumentReader
import com.tneff.kmpremotecompose.remote.core.operations.draw.PathData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * REM-103 (FC-text-path-effects gate) — byte-anchors for the one-shot raw-float `DATA_PATH`
 * emitter [addPathData]. Mirrors the REM-92 NamedVariable handcrafted-oracle pattern: corpus
 * `procedure_text_path_effects.rc` bakes geometry as a single `DATA_PATH` op (count=2141 floats),
 * not as `PATH_CREATE` + N `PATH_ADD` — those are wire-incompatible op-forms. test-2's full
 * byte-closure of that fixture is unblocked once the helper is in place.
 *
 * Source-grounded against `./androidx`:
 *  - `RemoteComposeWriter.java:1690-1727` → `addPathData(float[])` allocates id, delegates
 *  - `RemoteComposeBuffer.java:883-901` → `PathData.apply(buffer, id, data)` writes the wire
 *  - `RemoteComposeBuffer.java:895` → winding overload OR'd into high byte: `id | (winding << 24)`
 */
class AddPathDataTest {

    @Test
    fun addPathData_allocatesPlainId_andEmitsSingleDataPathOp() {
        var pathId = -1
        val bytes = document(width = 100, height = 100, contentDescription = "Clock") {
            // Content-description claims 42; the path id is next at 43.
            pathId = addPathData(floatArrayOf(0f, 0f, 10f, 20f, 30f, 40f))
        }
        assertEquals(43, pathId, "raw-form helper pulls region-0 plain (mirror pathCreate's allocation)")
        val ops = DocumentReader.inflate(bytes).operations
        val pathDatas = ops.filterIsInstance<PathData>()
        assertEquals(1, pathDatas.size, "exactly one DATA_PATH op — NOT split into PATH_CREATE + PATH_ADD")
        assertEquals(43, pathDatas[0].id, "wire id = bare id when winding = 0")
        assertEquals(6, pathDatas[0].data.size, "wire count = floats.size")
    }

    @Test
    fun addPathData_preservesRawFloatBitsForNaNRefs() {
        // Floats may carry NaN-encoded variable ids (PathData.kt header comment). The helper must
        // write Float.toRawBits() so NaN bit-patterns survive the FloatArray → IntArray copy.
        val nanRef = Float.fromBits(0x7FC00042) // arbitrary NaN payload
        val bytes = document(width = 100, height = 100) {
            addPathData(floatArrayOf(1f, nanRef, -1f, Float.NEGATIVE_INFINITY))
        }
        val op = DocumentReader.inflate(bytes).operations.first { it is PathData } as PathData
        assertEquals(4, op.data.size)
        assertEquals(1f.toRawBits(), op.data[0])
        assertEquals(0x7FC00042, op.data[1], "NaN bit-pattern preserved verbatim — id-ref survives")
        assertEquals((-1f).toRawBits(), op.data[2])
        assertEquals(Float.NEGATIVE_INFINITY.toRawBits(), op.data[3])
    }

    @Test
    fun addPathData_windingOverload_orsIntoHighByte_andReturnsBareId() {
        // Pins the upstream encoding `RemoteComposeBuffer.java:895`:
        //   PathData.apply(buffer, id | (winding << 24), data)
        // Caller-facing id stays bare so subsequent drawPath(id) etc. reference the same path.
        var pathId = -1
        val winding = 1 // EVEN_ODD (upstream Path.FillType.ordinal)
        val bytes = document(width = 100, height = 100) {
            pathId = addPathData(floatArrayOf(0f, 0f, 100f, 100f), winding = winding)
        }
        assertEquals(42, pathId, "returned id is the BARE allocator value (no winding bits)")
        val op = DocumentReader.inflate(bytes).operations.first { it is PathData } as PathData
        val expectedWireId = 42 or (winding shl 24)
        assertEquals(expectedWireId, op.id, "wire id = bare-id | (winding shl 24)")
        // Verify the low-24-bit part still decodes to the bare id, the high byte to the winding.
        assertEquals(42, op.id and 0x00FFFFFF, "low 24 bits = bare path id")
        assertEquals(1, (op.id ushr 24) and 0xFF, "high byte = winding rule")
    }

    @Test
    fun addPathData_zeroWinding_writesPlainId_noOrOperation() {
        // The default branch — winding=0 must NOT OR anything; wire id = bare id literally.
        val bytes = document(width = 100, height = 100) {
            addPathData(floatArrayOf(0f, 0f))
        }
        val op = DocumentReader.inflate(bytes).operations.first { it is PathData } as PathData
        assertEquals(42, op.id, "no winding → wire id is the bare allocator value")
        assertEquals(0, (op.id ushr 24) and 0xFF, "high byte stays 0")
    }

    @Test
    fun addPathData_emptyFloats_emitsZeroCountOp() {
        // Defensive — zero-length geometry is legal upstream (PathData.read accepts count=0).
        val bytes = document(width = 100, height = 100) {
            addPathData(FloatArray(0))
        }
        val op = DocumentReader.inflate(bytes).operations.first { it is PathData } as PathData
        assertEquals(0, op.data.size)
    }

    @Test
    fun addPathData_chainableWithDrawPath_byBareIdRoundTrip() {
        // The bare id returned must be drawPath-compatible — no winding-byte leakage. Verifies the
        // user-facing contract end-to-end.
        var pathId = -1
        val bytes = document(width = 100, height = 100) {
            pathId = addPathData(floatArrayOf(0f, 0f, 50f, 50f), winding = 2)
            drawPath(pathId)
        }
        val ops = DocumentReader.inflate(bytes).operations
        val drawPath = ops.first {
            it is com.tneff.kmpremotecompose.remote.core.operations.draw.DrawPath
        } as com.tneff.kmpremotecompose.remote.core.operations.draw.DrawPath
        assertEquals(pathId, drawPath.id, "drawPath consumes the bare id verbatim")
        // The PathData op carries the OR'd wire id; the DrawPath references only the bare id.
        val pathData = ops.first { it is PathData } as PathData
        assertTrue(pathData.id != drawPath.id, "PathData.id includes winding bits; drawPath.id does not")
    }

    @Test
    fun addPathData_largeArray_2141Floats_textPathEffectsOracleShape() {
        // The text-path-effects oracle bakes count=2141 floats in a single DATA_PATH op (per the
        // PO brief). Pin: the helper handles an arbitrarily large FloatArray in one op — does NOT
        // chunk into multiple ops or otherwise mutate the count.
        val floats = FloatArray(2141) { (it.toFloat() * 0.5f) }
        val bytes = document(width = 100, height = 100) {
            addPathData(floats)
        }
        val pathDatas = DocumentReader.inflate(bytes).operations.filterIsInstance<PathData>()
        assertEquals(1, pathDatas.size, "single op even at the 2141-float corpus size")
        assertEquals(2141, pathDatas[0].data.size, "wire count matches input length verbatim")
        // Spot-check round-trip values at boundaries + a middle index.
        assertEquals(0f.toRawBits(), pathDatas[0].data[0])
        assertEquals((1000 * 0.5f).toRawBits(), pathDatas[0].data[1000])
        assertEquals((2140 * 0.5f).toRawBits(), pathDatas[0].data[2140])
    }
}
