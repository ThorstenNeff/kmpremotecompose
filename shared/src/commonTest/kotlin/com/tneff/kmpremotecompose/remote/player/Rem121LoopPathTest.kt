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
import com.tneff.kmpremotecompose.remote.core.operations.layout.ContainerEnd
import com.tneff.kmpremotecompose.remote.core.operations.layout.LoopStart
import com.tneff.kmpremotecompose.remote.core.operations.draw.PathAppend
import com.tneff.kmpremotecompose.remote.player.core.PathDataResolver
import com.tneff.kmpremotecompose.remote.player.core.RemoteComposePlayer
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.wire.WireTypes
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * REM-121 — a `PathAppend` inside a `LoopStart` must **bake** the index-variant coordinate of EACH
 * iteration into the accumulated path. The bug: `PathAppend` stored the raw coord var-refs (the same id
 * N times); at draw time `resolvePathData` resolved them ALL to the variable's FINAL value → every
 * segment collapsed to one point → a degenerate flat line (the cross-platform heart_rate red-line bug).
 * The fix bakes per iteration, so the stored path holds N distinct literal coordinates.
 *
 * Cross-platform (NOT wasm-specific): verified on jvm/desktop render + this unit. Byte-invariant: only
 * `PathAppend.paint` (render) bakes — `write()` keeps the raw NaN ids → conformance stays byte-exact.
 */
class Rem121LoopPathTest {

    // A LINE command: [marker, pad, pad, x, y]; here x = y = the loop index var (id 70).
    private fun lineFromIndexVar() = floatArrayOf(
        WireTypes.asNan(PathDataResolver.LINE), 0f, 0f, WireTypes.asNan(70), WireTypes.asNan(70),
    )

    @Test
    fun loopPathAppend_bakesDistinctPointsPerIteration() {
        val ctx = RemoteContext()
        RemoteComposePlayer(ctx).paint(
            RemoteComposeDocument(listOf(
                LoopStart(indexId = 70, from = 0f, step = 1f, until = 3f),
                PathAppend(id = 99, data = lineFromIndexVar()),
                ContainerEnd(),
            )),
            NoOpPaintContext(ctx),
        )
        // 3 LINE commands accumulated (5 floats each). The x slot of segment k is at index 5k + 3.
        val acc = ctx.getPathData(99) ?: error("no path data accumulated")
        assertEquals(15, acc.size, "3 LINE commands × 5 floats")
        val bakedXs = listOf(acc[3], acc[8], acc[13])
        assertEquals(listOf(0f, 1f, 2f), bakedXs, "each iteration must bake its own index value, not all the final one")
    }

    @Test
    fun pathDataResolver_bakesCoordsLeavesMarkersAndPadding() {
        val ctx = RemoteContext()
        ctx.loadFloat(70, 42f)
        val resolved = PathDataResolver.resolvePathData(ctx, lineFromIndexVar())
        // marker stays the raw NaN id; the two literal pads stay 0; the two coord var-refs → 42.
        assertEquals(PathDataResolver.LINE, WireTypes.idFromNan(resolved[0]), "marker untouched")
        assertEquals(0f, resolved[1]); assertEquals(0f, resolved[2])
        assertEquals(42f, resolved[3]); assertEquals(42f, resolved[4])
    }
}
