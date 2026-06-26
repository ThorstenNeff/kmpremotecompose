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
import com.tneff.kmpremotecompose.remote.core.operations.Operation
import com.tneff.kmpremotecompose.remote.player.core.PaintContext
import com.tneff.kmpremotecompose.remote.player.core.PaintOperation
import com.tneff.kmpremotecompose.remote.player.core.RemoteComposePlayer
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.player.core.VariableSupport
import com.tneff.kmpremotecompose.remote.wire.WireBuffer
import com.tneff.kmpremotecompose.remote.wire.WireTypes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * REM-36 Eval-Engine E1 foundation: NaN-class resolution, the float store, and the player's variable
 * (apply) phase. Headless — the machinery + a getFloat-resolution unit test; the **visual** breakthrough
 * comes with dev-2's E3 (the ops that consume `updateVariables`) in the combined E1+E3 sweep.
 */
class EvalE1Test {

    // region 2 = data variable; region 3 = math operator (upstream NanMap).
    private val dataVarId = (2 shl 20) or 5      // 0x200005
    private val operatorId = (3 shl 20) or 1     // 0x300001

    @Test
    fun nanClassification_distinguishesDataVarFromOperatorAndLiteral() {
        val dataNan = WireTypes.asNan(dataVarId)
        val opNan = WireTypes.asNan(operatorId)

        assertTrue(WireTypes.isDataVariable(dataNan))
        assertFalse(WireTypes.isOperationVariable(dataNan))
        assertEquals(dataVarId, WireTypes.idFromNan(dataNan), "id payload round-trips")

        assertTrue(WireTypes.isOperationVariable(opNan))
        assertFalse(WireTypes.isDataVariable(opNan))

        // a plain literal is neither.
        assertFalse(WireTypes.isDataVariable(42f))
        assertFalse(WireTypes.isOperationVariable(42f))
    }

    @Test
    fun floatStore_defaultsToZero_andRoundTrips() {
        val ctx = RemoteContext()
        assertEquals(0f, ctx.getFloat(dataVarId), "unresolved ⇒ 0f (upstream IntFloatMap.get default)")
        ctx.loadFloat(dataVarId, 3.5f)
        assertEquals(3.5f, ctx.getFloat(dataVarId))
        // int/color stores default to 0 too.
        assertEquals(0, ctx.getInt(7))
        assertEquals(0, ctx.getColor(7))
    }

    @Test
    fun floatConstant_apply_loadsValueIntoStore() {
        val ctx = RemoteContext()
        FloatConstant(dataVarId, 9.25f).apply(ctx)
        assertEquals(9.25f, ctx.getFloat(dataVarId))
    }

    /** A stand-in for a dev-2 E3 consumer: resolves a NaN data-var ref into a render-only field. */
    private class FakeResolver(private val ref: Float) : PaintOperation, VariableSupport {
        var resolved: Float = Float.NaN
        var painted: Float = Float.NaN
        override val opcode: Int get() = 0
        override fun write(buffer: WireBuffer) {}
        override fun dump(): String = "FAKE_RESOLVER"
        override fun updateVariables(context: RemoteContext) {
            resolved = if (WireTypes.isDataVariable(ref)) context.getFloat(WireTypes.idFromNan(ref)) else ref
        }
        override fun paint(context: RemoteContext, paint: PaintContext) { painted = resolved }
    }

    @Test
    fun applyPhase_runsBeforePaint_soConsumerSeesResolvedValue() {
        val ctx = RemoteContext()
        val consumer = FakeResolver(WireTypes.asNan(dataVarId))
        // DATA_FLOAT precedes the consumer (document order): its apply loads the value in Phase A.
        val doc = RemoteComposeDocument(listOf<Operation>(
            FloatConstant(dataVarId, 7f),
            consumer,
        ))

        RemoteComposePlayer(ctx).paint(doc, NoOpPaintContext(ctx))

        assertEquals(7f, ctx.getFloat(dataVarId), "DATA_FLOAT.apply loaded the store in Phase A")
        assertEquals(7f, consumer.resolved, "consumer resolved the data-var in Phase A")
        assertEquals(7f, consumer.painted, "paint (Phase B) saw the resolved value — apply ran first")
    }
}
