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

import com.tneff.kmpremotecompose.remote.core.operations.MatrixRotate
import com.tneff.kmpremotecompose.remote.core.operations.MatrixScale
import com.tneff.kmpremotecompose.remote.core.operations.MatrixTranslate
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.player.core.VariableSupport
import com.tneff.kmpremotecompose.remote.wire.WireTypes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * REM-36 E3 — op-arg resolution: NaN data-variable coord fields resolve against the store in
 * `updateVariables` (Phase A), literals pass through, unresolved → `0f` (E1 contract). Pure logic
 * (no render surface); the visible-render proof is test-2's golden round (E4).
 */
class OpArgResolutionTest {

    /** A data-variable (NaN region 2) reference for variable [id]. */
    private fun dataVar(id: Int): Float = WireTypes.asNan(0x200000 or id)

    @Test
    fun matrixOpsAreVariableSupport() {
        assertTrue(MatrixScale(1f, 1f, 0f, 0f) is VariableSupport)
        assertTrue(MatrixTranslate(0f, 0f) is VariableSupport)
        assertTrue(MatrixRotate(0f, 0f, 0f) is VariableSupport)
    }

    @Test
    fun matrixScale_resolvesNanDataVarCoords_passesLiteralsThrough() {
        val ctx = RemoteContext()
        val sx = dataVar(7)
        ctx.loadFloat(WireTypes.idFromNan(sx), 2.5f)
        // scaleX = NaN data-var (→2.5), scaleY = literal 3f, centerX = NaN data-var (→0), centerY literal.
        val op = MatrixScale(sx, 3f, dataVar(99), 10f)
        op.updateVariables(ctx)
        assertEquals(2.5f, op.rScaleX, "NaN data-var resolved to stored value")
        assertEquals(3f, op.rScaleY, "literal passes through unchanged")
        assertEquals(0f, op.rCenterX, "unresolved data-var → 0f (E1 contract)")
        assertEquals(10f, op.rCenterY, "literal passes through")
    }

    @Test
    fun matrixTranslate_resolves() {
        val ctx = RemoteContext()
        val tx = dataVar(3)
        ctx.loadFloat(WireTypes.idFromNan(tx), -42f)
        val op = MatrixTranslate(tx, 5f)
        op.updateVariables(ctx)
        assertEquals(-42f, op.rTranslateX)
        assertEquals(5f, op.rTranslateY)
    }
}
