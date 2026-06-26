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
package com.tneff.kmpremotecompose.remote.player

import com.tneff.kmpremotecompose.remote.core.operations.MatrixRestore
import com.tneff.kmpremotecompose.remote.core.operations.MatrixRotate
import com.tneff.kmpremotecompose.remote.core.operations.MatrixSave
import com.tneff.kmpremotecompose.remote.core.operations.MatrixScale
import com.tneff.kmpremotecompose.remote.core.operations.MatrixSkew
import com.tneff.kmpremotecompose.remote.core.operations.MatrixTranslate
import com.tneff.kmpremotecompose.remote.core.operations.Operation
import com.tneff.kmpremotecompose.remote.core.operations.draw.ClipPath
import com.tneff.kmpremotecompose.remote.core.operations.draw.ClipRect
import com.tneff.kmpremotecompose.remote.core.operations.draw.PathData
import com.tneff.kmpremotecompose.remote.player.core.PaintOperation
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * REM-33 — the op→paint render binding: the matrix/clip/path ops must implement [PaintOperation] so the
 * [RemoteComposePlayer] walk dispatches them (matrix was the biggest lever — SAVE/RESTORE ~69 docs).
 * Pure structural check (no render surface); behavioral render proof is test-2's Maestro sweep. The
 * binding is **additive** — `write()`/`read()` untouched, so the conformance suite stays 173/173.
 */
class OpRenderBindingTest {

    @Test
    fun matrixClipPathOpsAreRenderBound() {
        val ops: List<Operation> = listOf(
            MatrixSave(),
            MatrixRestore(),
            MatrixTranslate(1f, 2f),
            MatrixScale(1f, 1f, 0f, 0f),
            MatrixRotate(0f, 0f, 0f),
            MatrixSkew(0f, 0f),
            ClipRect(0f, 0f, 1f, 1f),
            ClipPath(packed = 0),
            PathData(1, intArrayOf()),
        )
        for (op in ops) {
            assertTrue(op is PaintOperation, "${op::class.simpleName} must implement PaintOperation (REM-33)")
        }
    }
}
