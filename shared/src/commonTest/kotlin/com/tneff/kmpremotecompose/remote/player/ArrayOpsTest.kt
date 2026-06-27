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

import com.tneff.kmpremotecompose.remote.core.operations.DataListFloat
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.player.core.RpnFloatEvaluator
import com.tneff.kmpremotecompose.remote.wire.WireTypes
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * REM-59 — RPN array operators over a FLOAT_LIST collection. The array-id is pushed raw (data var,
 * region 2) so each op resolves it via [RemoteContext.getFloatArray]. Source-grounded against upstream
 * `AnimatedFloatExpression` (A_DEREF/A_LEN/A_MIN/A_MAX/A_SUM/A_AVG = OFFSET+32..37). Loop `until=A_LEN`
 * and chart body coords `data[i]=A_DEREF` are the real consumers (pie_chart2/demo_graphs1).
 */
class ArrayOpsTest {
    private val offset = 0x310000
    private fun op(n: Int) = WireTypes.asNan(offset + n)
    private fun ev(ctx: RemoteContext, vararg e: Float) = RpnFloatEvaluator.eval(e, e.size, ctx)

    @Test
    fun arrayOperators_matchUpstream() {
        val ctx = RemoteContext()
        ctx.loadFloatArray(2097194, floatArrayOf(10f, 20f, 30f, 40f, 50f))
        val arr = WireTypes.asNan(2097194)
        assertEquals(5f, ev(ctx, arr, op(37)), "A_LEN")
        assertEquals(30f, ev(ctx, arr, 2f, op(32)), "A_DEREF[2]")
        assertEquals(50f, ev(ctx, arr, op(33)), "A_MAX")
        assertEquals(10f, ev(ctx, arr, op(34)), "A_MIN")
        assertEquals(150f, ev(ctx, arr, op(35)), "A_SUM")
        assertEquals(30f, ev(ctx, arr, op(36)), "A_AVG")
    }

    @Test
    fun arrayOps_failSoftWhenArrayMissing() {
        val ctx = RemoteContext()
        assertEquals(0f, ev(ctx, WireTypes.asNan(2097194), op(37)), "missing array → 0 (no throw)")
        assertEquals(0f, ev(ctx, WireTypes.asNan(2097194), 99f, op(32)), "out-of-range deref → 0")
    }

    @Test
    fun realFixture_pieChart2_loopBoundResolves() {
        com.tneff.kmpremotecompose.remote.core.operations.Builtins.register()
        val doc = com.tneff.kmpremotecompose.remote.core.document.DocumentReader.inflate(
            com.tneff.kmpremotecompose.conformance.RcCorpus.readFixture("corpus/pie_chart2.rc"),
        )
        val ctx = RemoteContext()
        com.tneff.kmpremotecompose.remote.player.core.RemoteComposePlayer(ctx)
            .paint(doc, NoOpPaintContext(ctx), surfaceWidth = 400f, surfaceHeight = 400f)
        // id49 = A_LEN(FLOAT_LIST 2097194 of 5 values) → the loop's `until`; was 0 (unsupported) → now 5.
        assertEquals(5f, ctx.getFloat(49), "loop until = A_LEN(list) resolves to 5 (was 0 → loop ran 0×)")
    }

    @Test
    fun dataListFloat_storesArrayInContext() {
        val ctx = RemoteContext()
        DataListFloat(id = 2097194, values = floatArrayOf(1f, 2f, 3f)).apply(ctx)
        assertEquals(3, ctx.getFloatArray(2097194)?.size, "FLOAT_LIST apply stores the array")
    }
}
