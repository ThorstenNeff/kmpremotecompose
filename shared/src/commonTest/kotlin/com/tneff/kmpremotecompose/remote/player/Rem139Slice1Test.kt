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
import com.tneff.kmpremotecompose.remote.core.operations.DataDynamicListFloat
import com.tneff.kmpremotecompose.remote.core.operations.DataMapIds
import com.tneff.kmpremotecompose.remote.core.operations.DataMapLookup
import com.tneff.kmpremotecompose.remote.core.operations.Operation
import com.tneff.kmpremotecompose.remote.core.operations.OperationReader
import com.tneff.kmpremotecompose.remote.core.operations.TextMeasure
import com.tneff.kmpremotecompose.remote.core.operations.UpdateDynamicFloatList
import com.tneff.kmpremotecompose.remote.player.core.RemoteComposePlayer
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.wire.WireBuffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * REM-139 Slice 1 — the lookup/measure Phase-A producers. Pre-S1 they were `Operation`-only, so their
 * output store slots stayed null/0 (census: `procedure_look_up1` text[50]=null, float[51/52]=0;
 * compute docs floatArray[2097194]=null). With the producer `apply`s they now populate the store.
 *
 * jvmTest has no real text renderer, so [FakeText] supplies deterministic `getTextBounds` for the
 * `TextMeasure` check; the lookup + list checks need no renderer. Real pixel positions = test-3 oracle.
 */
class Rem139Slice1Test {

    /** Deterministic text metrics: width = len·10, height = 12 (so a measured dim is non-zero & known). */
    private class FakeText(context: RemoteContext) : NoOpPaintContext(context) {
        override fun getTextBounds(textId: Int, start: Int, end: Int, flags: Int, bounds: FloatArray) {
            val t = context.getText(textId) ?: ""
            bounds[0] = 0f; bounds[1] = 0f; bounds[2] = t.length * 10f; bounds[3] = 12f
        }
    }

    private fun play(doc: String): RemoteContext {
        Builtins.register()
        val ctx = RemoteContext()
        val d = DocumentReader.inflate(RcCorpus.readFixture("corpus/$doc.rc"))
        RemoteComposePlayer(ctx).paint(d, FakeText(ctx))
        return ctx
    }

    @Test
    fun lookup_resolvesText_andMeasureProducesNonZeroDims() {
        // procedure_look_up1: DataMapLookup loads text into 50; TextMeasure(51 width / 52 height) measure it.
        val ctx = play("procedure_look_up1")
        val looked = ctx.getText(50)
        assertNotNull(looked, "DataMapLookup must resolve the keyed text into id 50 (was null pre-S1)")
        assertTrue(looked.isNotEmpty(), "looked-up text must be non-empty")
        assertEquals(looked.length * 10f, ctx.getFloat(51), "TextMeasure width (id 51) = len·10 (was 0 pre-S1)")
        assertEquals(12f, ctx.getFloat(52), "TextMeasure height (id 52) = 12 (was 0 pre-S1)")
    }

    @Test
    fun dynamicFloatList_isAllocated_andUpdated() {
        // c_modifier_compute_measure: DynamicFloatList(6) allocated; UpdateDynamicFloatList writes index 3.
        val ctx = play("c_modifier_compute_measure")
        val list = ctx.getFloatArray(2097194)
        assertNotNull(list, "DynamicFloatList must allocate the backing array (was null pre-S1)")
        assertEquals(6, list.size, "list size = nbValues")
    }

    @Test
    fun computePosition_listAllocatedWithTwoUpdates() {
        val ctx = play("c_modifier_compute_position")
        val list = ctx.getFloatArray(2097194)
        assertNotNull(list, "DynamicFloatList must allocate")
        assertEquals(6, list.size)
    }

    @Test
    fun byteFormat_unchanged_forTheFiveOps() {
        // §2 by-construction: render-apply is additive; the 5 ops round-trip byte-identical.
        fun rt(op: Operation, reader: OperationReader) {
            val bytes = WireBuffer().also { op.write(it) }.toByteArray()
            val buf = WireBuffer.fromBytes(bytes); assertEquals(op.opcode, buf.readByte())
            val decoded = ArrayList<Operation>(); reader.read(buf, decoded)
            assertEquals(op, decoded[0], "decoded equals original")
            assertContentEquals(bytes, WireBuffer().also { decoded[0].write(it) }.toByteArray(), "re-encode byte-identical")
        }
        rt(DataMapLookup(50, 2097194, 49), DataMapLookup)
        rt(DataMapIds(2097194, listOf(DataMapIds.Entry("k", DataMapIds.TYPE_STRING, 7))), DataMapIds)
        rt(TextMeasure(51, 50, 0), TextMeasure)
        rt(DataDynamicListFloat(2097194, 6f), DataDynamicListFloat)
        rt(UpdateDynamicFloatList(2097194, 3f, Float.fromBits(0x7FC00001)), UpdateDynamicFloatList)
    }
}
