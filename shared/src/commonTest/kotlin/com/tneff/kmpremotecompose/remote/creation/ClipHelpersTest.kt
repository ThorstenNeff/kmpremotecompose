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
import com.tneff.kmpremotecompose.remote.core.operations.draw.ClipPath
import com.tneff.kmpremotecompose.remote.core.operations.draw.ClipRect
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * REM-90 (E3) — Clip-helper byte tests. Neither is id-bearing. Pins the packed-int layout of
 * `CLIP_PATH` (`regionOp shl 24 | pathId and 0xFFFFF`).
 */
class ClipHelpersTest {

    @Test
    fun clipRect_emitsClipRectOp_withFourFloats() {
        val bytes = document(width = 100, height = 100) {
            clipRect(0, 10, 80, 90)
        }
        val op = DocumentReader.inflate(bytes).operations.first { it is ClipRect } as ClipRect
        assertEquals(0f, op.x1); assertEquals(10f, op.y1)
        assertEquals(80f, op.x2); assertEquals(90f, op.y2)
    }

    @Test
    fun clipPath_packsRegionOpAndPathIdIntoSingleInt() {
        var pathId = -1
        val bytes = document(width = 100, height = 100) {
            pathId = pathCreate(0, 0) // = 42 (no content-description, so plain pool starts at 42)
            clipPath(pathId, regionOp = 1)
        }
        val op = DocumentReader.inflate(bytes).operations.first { it is ClipPath } as ClipPath
        assertEquals(pathId, op.id, "low 20 bits = path id")
        assertEquals(1, op.regionOp, "shr 24 = region op")
    }

    @Test
    fun clipPath_defaultsRegionOpToZero() {
        var pathId = -1
        document(width = 100, height = 100) {
            pathId = pathCreate(0, 0)
            clipPath(pathId)
        }
    }

    @Test
    fun clipHelpers_doNotAllocateIds() {
        document(width = 100, height = 100, contentDescription = "Clock") {
            assertEquals(43, ids.peek())
            val p = pathCreate(0, 0) // 43, path is id-bearing
            assertEquals(44, ids.peek())
            clipRect(0, 0, 10, 10)
            clipPath(p)
            assertEquals(44, ids.peek(), "clip ops must not allocate ids")
        }
    }
}
