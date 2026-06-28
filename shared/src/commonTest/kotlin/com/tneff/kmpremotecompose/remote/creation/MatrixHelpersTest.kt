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
import com.tneff.kmpremotecompose.remote.core.operations.MatrixRestore
import com.tneff.kmpremotecompose.remote.core.operations.MatrixRotate
import com.tneff.kmpremotecompose.remote.core.operations.MatrixSave
import com.tneff.kmpremotecompose.remote.core.operations.MatrixScale
import com.tneff.kmpremotecompose.remote.core.operations.MatrixSkew
import com.tneff.kmpremotecompose.remote.core.operations.MatrixTranslate
import com.tneff.kmpremotecompose.remote.core.operations.Operation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * REM-90 (E3) — Matrix-helper byte tests. None of these are id-bearing. Also pins `matrixSaved { }`
 * emits the right enclosing save/restore pair (also on exception).
 */
class MatrixHelpersTest {

    @Test
    fun matrixSave_andRestore_emitMarkerOps() {
        val bytes = document(width = 100, height = 100) {
            matrixSave()
            matrixRestore()
        }
        val ops = DocumentReader.inflate(bytes).operations
        assertTrue(ops.any { it is MatrixSave })
        assertTrue(ops.any { it is MatrixRestore })
    }

    @Test
    fun matrixTranslate_emitsTranslateOp_withOperands() {
        val bytes = document(width = 100, height = 100) {
            matrixTranslate(10, 20)
        }
        val op = DocumentReader.inflate(bytes).operations.first { it is MatrixTranslate } as MatrixTranslate
        assertEquals(10f, op.translateX); assertEquals(20f, op.translateY)
    }

    @Test
    fun matrixScale_defaultsPivotToZeroZero() {
        val bytes = document(width = 100, height = 100) {
            matrixScale(2, 3)
        }
        val op = DocumentReader.inflate(bytes).operations.first { it is MatrixScale } as MatrixScale
        assertEquals(2f, op.scaleX); assertEquals(3f, op.scaleY)
        assertEquals(0f, op.centerX); assertEquals(0f, op.centerY)
    }

    @Test
    fun matrixRotate_emitsRotateOp_withPivot() {
        val bytes = document(width = 100, height = 100) {
            matrixRotate(rotate = 45, pivotX = 50, pivotY = 60)
        }
        val op = DocumentReader.inflate(bytes).operations.first { it is MatrixRotate } as MatrixRotate
        assertEquals(45f, op.rotate)
        assertEquals(50f, op.pivotX); assertEquals(60f, op.pivotY)
    }

    @Test
    fun matrixSkew_emitsSkewOp_withOperands() {
        val bytes = document(width = 100, height = 100) {
            matrixSkew(0.1f, 0.2f)
        }
        val op = DocumentReader.inflate(bytes).operations.first { it is MatrixSkew } as MatrixSkew
        assertEquals(0.1f, op.skewX); assertEquals(0.2f, op.skewY)
    }

    @Test
    fun matrixSavedBlock_emitsSaveBeforeAndRestoreAfter() {
        val bytes = document(width = 100, height = 100) {
            matrixSaved {
                matrixTranslate(10, 10)
            }
        }
        // Op stream (post-prolog) should be: MatrixSave, MatrixTranslate, MatrixRestore.
        val ops = DocumentReader.inflate(bytes).operations
        val firstSave = ops.indexOfFirst { it is MatrixSave }
        val firstRestore = ops.indexOfFirst { it is MatrixRestore }
        val translateIdx = ops.indexOfFirst { it is MatrixTranslate }
        assertTrue(firstSave >= 0); assertTrue(firstRestore > firstSave)
        assertTrue(translateIdx in (firstSave + 1) until firstRestore, "translate between save and restore")
    }

    @Test
    fun matrixSavedBlock_restoresEvenOnException() {
        var threw = false
        val ctx = RemoteComposeContext(
            writer = com.tneff.kmpremotecompose.remote.core.document.RemoteComposeWriter(
                width = 100, height = 100, apiLevel = 6,
            ),
            profile = Profile.Baseline,
        )
        try {
            ctx.matrixSaved {
                matrixTranslate(5, 5)
                throw IllegalStateException("scripted failure")
            }
        } catch (_: IllegalStateException) {
            threw = true
        }
        assertTrue(threw, "exception should propagate")
        // Even on throw, the restore must have been emitted (finally semantics).
        val tail: List<Operation> = DocumentReader.inflate(ctx.encodeToByteArray()).operations
        val restoreCount = tail.count { it is MatrixRestore }
        val saveCount = tail.count { it is MatrixSave }
        assertEquals(saveCount, restoreCount, "save/restore balanced after exception")
        if (saveCount == 0) fail("matrixSave was not emitted")
    }

    @Test
    fun matrixHelpers_doNotAllocateIds() {
        document(width = 100, height = 100, contentDescription = "Clock") {
            assertEquals(43, ids.peek())
            matrixSave(); matrixTranslate(1, 1); matrixScale(2, 2)
            matrixRotate(30); matrixSkew(0.1f, 0.0f); matrixRestore()
            assertEquals(43, ids.peek())
        }
    }
}
