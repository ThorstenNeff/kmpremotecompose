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

import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawTweenPath
import com.tneff.kmpremotecompose.remote.core.operations.draw.PathTween
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.wire.WireTypes
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * REM-125 — the path-tween ops (`DrawTweenPath`, `PathTween`) must resolve NaN-encoded variable refs in
 * their `tween` / `start` / `stop` floats, like `ClipRect`/`DrawRect`. The bug (assist's independent
 * class-confirm caught it): they were not `VariableSupport`, so a computed tween/sweep (e.g. sweep-clock
 * `stop = 1/60·TIME`, `path_tween_demo` tween 0.75 via an ANIMATED_FLOAT) reached the geometry as raw NaN
 * → degenerate interpolation (broken hand / frozen tween). Byte-invariant: only the resolved `r*` fields
 * are used by `paint`; `write` keeps the raw bits → conformance byte-exact.
 */
class Rem125TweenResolveTest {

    private val ctx = RemoteContext().apply {
        loadFloat(0x200000 or 30, 0.75f) // tween
        loadFloat(0x200000 or 31, 0.10f) // start
        loadFloat(0x200000 or 32, 0.90f) // stop
    }
    private fun ref(id: Int) = WireTypes.asNan(0x200000 or id)

    @Test
    fun drawTweenPath_resolvesTweenStartStop() {
        val op = DrawTweenPath(path1Id = 1, path2Id = 2, tween = ref(30), start = ref(31), stop = ref(32))
        op.updateVariables(ctx)
        assertEquals(0.75f, op.rTween); assertEquals(0.10f, op.rStart); assertEquals(0.90f, op.rStop)
    }

    @Test
    fun pathTween_resolvesTween() {
        val op = PathTween(outId = 9, pathId1 = 1, pathId2 = 2, tween = ref(30))
        op.updateVariables(ctx)
        assertEquals(0.75f, op.rTween, "PathTween must resolve its NaN var-ref tween, not pass raw NaN")
    }

    @Test
    fun literalTween_passesThroughUnchanged() {
        val op = DrawTweenPath(1, 2, tween = 0.5f, start = 0f, stop = 1f)
        op.updateVariables(ctx)
        assertEquals(0.5f, op.rTween); assertEquals(0f, op.rStart); assertEquals(1f, op.rStop)
    }
}
