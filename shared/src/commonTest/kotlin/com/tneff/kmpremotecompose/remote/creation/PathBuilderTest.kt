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

import com.tneff.kmpremotecompose.conformance.RcDocumentCodec
import com.tneff.kmpremotecompose.remote.core.document.DocumentReader
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawPath
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawTweenPath
import com.tneff.kmpremotecompose.remote.core.operations.draw.PathAppend
import com.tneff.kmpremotecompose.remote.core.operations.draw.PathCreate
import com.tneff.kmpremotecompose.remote.wire.WireTypes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * REM-86 (E2) — `PathBuilder` byte tests. Pins:
 *  - `pathCreate` allocates a single id from the plain-region-0 pool (W#3 id-bearing).
 *  - The exact `[NaN-marker, …operands]` payload each `pathAppend*` writes — including the upstream
 *    `[LINE_NAN, 0, 0, x, y]` two-zero padding (file header in `PathBuilder.kt`).
 *  - `drawPath`/`drawTweenPath` re-reference an existing id and allocate nothing.
 */
class PathBuilderTest {

    @Test
    fun pathCreate_emitsPathCreateOp_andReturnsAllocatedId() {
        var pathId = -1
        val bytes = document(width = 100, height = 100, contentDescription = "Clock") {
            // Allocator started at 42 (content-desc), now at 43.
            pathId = pathCreate(5f, 10f)
        }
        assertEquals(43, pathId)
        val op = DocumentReader.inflate(bytes).operations.first { it is PathCreate } as PathCreate
        assertEquals(43, op.id)
        assertEquals(5f, op.startX)
        assertEquals(10f, op.startY)
    }

    @Test
    fun pathAppendMoveTo_emitsMoveMarkerPlusXy() {
        val bytes = document(width = 100, height = 100) {
            val p = pathCreate(0f, 0f)
            pathAppendMoveTo(p, 10f, 20f)
        }
        val appends = DocumentReader.inflate(bytes).operations.filterIsInstance<PathAppend>()
        assertEquals(1, appends.size)
        val data = appends[0].data
        assertEquals(3, data.size, "MOVE payload = [marker, x, y]")
        assertEquals(10, WireTypes.idFromNan(data[0]), "marker = MOVE (10)")
        assertEquals(10f, data[1]); assertEquals(20f, data[2])
    }

    @Test
    fun pathAppendLineTo_emitsLineMarkerPlusZeroPadPlusEndXy() {
        val bytes = document(width = 100, height = 100) {
            val p = pathCreate(0f, 0f)
            pathAppendLineTo(p, 50f, 60f)
        }
        val data = DocumentReader.inflate(bytes).operations.filterIsInstance<PathAppend>()[0].data
        assertEquals(5, data.size, "LINE payload = [marker, 0, 0, x, y]")
        assertEquals(11, WireTypes.idFromNan(data[0]), "marker = LINE (11)")
        assertEquals(0f, data[1]); assertEquals(0f, data[2])
        assertEquals(50f, data[3]); assertEquals(60f, data[4])
    }

    @Test
    fun pathAppendQuadTo_emitsQuadMarkerPlusZeroPadPlusCtrlEnd() {
        val bytes = document(width = 100, height = 100) {
            val p = pathCreate(0f, 0f)
            pathAppendQuadTo(p, 25f, 25f, 50f, 0f)
        }
        val data = DocumentReader.inflate(bytes).operations.filterIsInstance<PathAppend>()[0].data
        assertEquals(7, data.size, "QUAD payload = [marker, 0, 0, cx, cy, x, y]")
        assertEquals(12, WireTypes.idFromNan(data[0]))
        assertEquals(0f, data[1]); assertEquals(0f, data[2])
        assertEquals(25f, data[3]); assertEquals(25f, data[4])
        assertEquals(50f, data[5]); assertEquals(0f, data[6])
    }

    @Test
    fun pathAppendCubicTo_emitsCubicMarkerPlusZeroPadPlusTwoCtrlEnd() {
        val bytes = document(width = 100, height = 100) {
            val p = pathCreate(0f, 0f)
            pathAppendCubicTo(p, 10f, 0f, 90f, 0f, 100f, 50f)
        }
        val data = DocumentReader.inflate(bytes).operations.filterIsInstance<PathAppend>()[0].data
        assertEquals(9, data.size, "CUBIC payload = [marker, 0, 0, c1x, c1y, c2x, c2y, x, y]")
        assertEquals(14, WireTypes.idFromNan(data[0]))
        assertEquals(0f, data[1]); assertEquals(0f, data[2])
        assertEquals(10f, data[3]); assertEquals(0f, data[4])
        assertEquals(90f, data[5]); assertEquals(0f, data[6])
        assertEquals(100f, data[7]); assertEquals(50f, data[8])
    }

    @Test
    fun pathAppendClose_emitsCloseMarkerOnly() {
        val bytes = document(width = 100, height = 100) {
            val p = pathCreate(0f, 0f)
            pathAppendClose(p)
        }
        val data = DocumentReader.inflate(bytes).operations.filterIsInstance<PathAppend>()[0].data
        assertEquals(1, data.size, "CLOSE payload = [marker]")
        assertEquals(15, WireTypes.idFromNan(data[0]))
    }

    @Test
    fun pathAppendReset_emitsResetMarkerOnly() {
        val bytes = document(width = 100, height = 100) {
            val p = pathCreate(0f, 0f)
            pathAppendReset(p)
        }
        val data = DocumentReader.inflate(bytes).operations.filterIsInstance<PathAppend>()[0].data
        assertEquals(1, data.size, "RESET payload = [marker]")
        assertEquals(17, WireTypes.idFromNan(data[0]))
    }

    @Test
    fun drawPath_referencesExistingId_doesNotAllocate() {
        var pathId = -1
        document(width = 100, height = 100, contentDescription = "Clock") {
            pathId = pathCreate(0f, 0f) // allocates 43
            assertEquals(44, ids.peek())
            drawPath(pathId)
            assertEquals(44, ids.peek(), "drawPath must not allocate")
            drawPath(pathId) // re-reference; still no allocation
            assertEquals(44, ids.peek())
        }
    }

    @Test
    fun drawTweenPath_emitsDrawTweenPathOp_withDefaultStartStop() {
        val bytes = document(width = 100, height = 100) {
            val a = pathCreate(0f, 0f) // 42
            val b = pathCreate(10f, 10f) // 43
            drawTweenPath(a, b, 0.5f)
        }
        val op = DocumentReader.inflate(bytes).operations.first { it is DrawTweenPath } as DrawTweenPath
        assertEquals(42, op.path1Id); assertEquals(43, op.path2Id)
        assertEquals(0.5f, op.tween)
        assertEquals(0f, op.start); assertEquals(1f, op.stop)
    }

    @Test
    fun pathBuilder_fullSequence_roundTripsByteForByte() {
        // Stage-1: a multi-segment path round-trips through decode/re-encode.
        val bytes = document(width = 200, height = 200) {
            val p = pathCreate(10f, 10f)
            pathAppendLineTo(p, 100f, 10f)
            pathAppendQuadTo(p, 150f, 10f, 150f, 50f)
            pathAppendCubicTo(p, 150f, 100f, 50f, 100f, 50f, 50f)
            pathAppendClose(p)
            drawPath(p)
        }
        val reEncoded = RcDocumentCodec.decode(bytes).reEncode()
        assertTrue(bytes.contentEquals(reEncoded), "path sequence must be byte-stable through round-trip")
    }
}
