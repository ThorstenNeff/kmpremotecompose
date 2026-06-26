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

import com.tneff.kmpremotecompose.conformance.RcCorpus
import com.tneff.kmpremotecompose.remote.core.document.DocumentReader
import com.tneff.kmpremotecompose.remote.core.operations.Builtins
import com.tneff.kmpremotecompose.remote.player.core.LayoutMeasure
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.player.core.VariableSupport
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * REM-37 cube3d — MATRIX_EXPRESSION + MATRIX_VECTOR_MATH evaluation. The 8 cube vertices (±1,±1,±1) are
 * transformed by a rotation matrix (type 0) then a perspective matrix (type 1) into projected 2D points
 * (output float ids). Before this slice both ops were byte-only no-ops → every projected coord stayed 0
 * → a flat disc. Here we assert the projected vertices are **distinct and non-degenerate** (a real cube
 * projection), proving the matrix engine runs. Visual golden (the wireframe cube) = test-2 sweep.
 */
class CubeMatrixTest {

    @Test
    fun cube3d_projectsEightDistinctVertices() {
        Builtins.register()
        val doc = DocumentReader.inflate(RcCorpus.readFixture("corpus/cube3d.rc"))
        val ctx = RemoteContext()
        ctx.seedSystemVariables(500f, 500f, 1f) // a non-zero frame so the rotation isn't trivially identity
        // Real player order: seed → measure → Phase A. The projection's screen-scale (sx,sy) derives from
        // component-dims the measure pass produces, so the matrix engine needs measured dims to be non-degenerate.
        LayoutMeasure.measure(doc, 500f, 500f, ctx)
        doc.operations.forEach { if (it is VariableSupport) { it.updateVariables(ctx); it.apply(ctx) } }

        // The 8 perspective-projected vertices: the type-1 MatrixVectorMath outputs (57/58, 65/66, …).
        val projectedX = listOf(57, 65, 73, 81, 89, 97, 105, 113).map { ctx.getFloat(it) }
        val projectedY = listOf(58, 66, 74, 82, 90, 98, 106, 114).map { ctx.getFloat(it) }

        assertTrue(projectedX.any { it != 0f } && projectedY.any { it != 0f }, "projected coords are non-zero (engine ran)")
        assertTrue(projectedX.all { it.isFinite() } && projectedY.all { it.isFinite() }, "no NaN/Inf in projection")
        val points = projectedX.zip(projectedY).toSet()
        assertTrue(points.size >= 6, "≥6 distinct projected vertices (a 3D cube, not a collapsed disc), got ${points.size}")
    }
}
