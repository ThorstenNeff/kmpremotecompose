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

import com.tneff.kmpremotecompose.remote.core.document.RemoteComposeDocument
import com.tneff.kmpremotecompose.remote.core.operations.Operation
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawCircle
import com.tneff.kmpremotecompose.remote.core.operations.layout.ContainerEnd
import com.tneff.kmpremotecompose.remote.core.operations.layout.LoopStart
import com.tneff.kmpremotecompose.remote.player.core.RemoteComposePlayer
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.wire.WireTypes
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * REM-58 — LOOP_START executes its body per iteration, loading the index var each pass (upstream
 * `LoopOperation.paint`: `for i = from; i < until; i += step`). The body's index-dependent coords
 * (graph bars) resolve fresh per iteration. Probe: a DrawCircle whose centerX = the index var.
 */
class LoopExecutionTest {

    private fun circleXs(ops: List<Operation>): List<Float> {
        val ctx = RemoteContext()
        val xs = mutableListOf<Float>()
        val rec = object : NoOpPaintContext(ctx) {
            override fun drawCircle(centerX: Float, centerY: Float, radius: Float) { xs += centerX }
        }
        RemoteComposePlayer(ctx).paint(RemoteComposeDocument(ops), rec)
        return xs
    }

    @Test
    fun loop_iteratesAndLoadsIndexPerIteration() {
        val xs = circleXs(listOf(
            LoopStart(indexId = 70, from = 0f, step = 1f, until = 3f),
            DrawCircle(WireTypes.asNan(70), 0f, 5f), // centerX = index var 70
            ContainerEnd(),
        ))
        assertEquals(listOf(0f, 1f, 2f), xs, "loop ran for i=0,1,2 with the index loaded each iteration")
    }

    @Test
    fun loop_zeroIterations_whenFromGeUntil() {
        val xs = circleXs(listOf(
            LoopStart(indexId = 70, from = 5f, step = 1f, until = 5f), // from == until → no iterations
            DrawCircle(WireTypes.asNan(70), 0f, 5f),
            ContainerEnd(),
        ))
        assertEquals(emptyList(), xs, "from >= until → body never runs")
    }

    @Test
    fun loop_stepGreaterThanOne() {
        val xs = circleXs(listOf(
            LoopStart(indexId = 70, from = 0f, step = 2f, until = 5f),
            DrawCircle(WireTypes.asNan(70), 0f, 5f),
            ContainerEnd(),
        ))
        assertEquals(listOf(0f, 2f, 4f), xs, "i = 0,2,4 (step 2, i < 5)")
    }
}
