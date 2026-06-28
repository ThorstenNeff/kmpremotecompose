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
import com.tneff.kmpremotecompose.remote.core.operations.FloatConstant
import com.tneff.kmpremotecompose.remote.core.operations.draw.ClipRect
import com.tneff.kmpremotecompose.remote.player.core.RemoteComposePlayer
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.wire.WireTypes
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * REM-124 — `ClipRect` (the DRAW op) must resolve NaN-encoded variable refs in its bounds, like
 * `DrawRect`. The bug: `ClipRect` was not `VariableSupport`, so a computed clip (stock's sparkline:
 * x1..y2 = ANIMATED_FLOATs) reached `clipRect` as raw NaN → a degenerate clip that hid everything drawn
 * after it (the sparkline `DrawPath`s). Byte-invariant: only `paint` resolves; `write` keeps raw bits.
 */
class Rem124ClipRectResolveTest {

    @Test
    fun clipRect_resolvesVariableBounds() {
        val ctx = RemoteContext()
        var clip: FloatArray? = null
        val rec = object : NoOpPaintContext(ctx) {
            override fun clipRect(left: Float, top: Float, right: Float, bottom: Float) {
                clip = floatArrayOf(left, top, right, bottom)
            }
        }
        // data-var bounds (region 2 → resolvable by resolveCoord), seeded via FloatConstant ops.
        val x1 = 0x200000 or 10; val y1 = 0x200000 or 11
        val x2 = 0x200000 or 12; val y2 = 0x200000 or 13
        RemoteComposePlayer(ctx).paint(
            RemoteComposeDocument(listOf(
                FloatConstant(x1, 5f), FloatConstant(y1, 6f), FloatConstant(x2, 100f), FloatConstant(y2, 200f),
                ClipRect(WireTypes.asNan(x1), WireTypes.asNan(y1), WireTypes.asNan(x2), WireTypes.asNan(y2)),
            )),
            rec,
        )
        assertEquals(listOf(5f, 6f, 100f, 200f), clip?.toList(), "ClipRect must resolve its NaN var-ref bounds, not pass raw NaN")
    }
}
