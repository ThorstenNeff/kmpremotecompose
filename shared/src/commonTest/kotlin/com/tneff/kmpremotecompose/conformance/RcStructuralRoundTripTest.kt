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
package com.tneff.kmpremotecompose.conformance

import com.tneff.kmpremotecompose.remote.core.operations.Header
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawCircle
import com.tneff.kmpremotecompose.remote.wire.WireBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Structural decode→re-encode proof for a **multi-operation** document through the real codec —
 * the F1-shaped path (a header plus a registered drawing op). Registration is triggered by the
 * reader's `Builtins.register()` (REM-6), so this exercises the full pipeline: decode → registered
 * op readers → faithful sequential re-encode.
 *
 * The actual `procedure_simple1.rc` F1 fixture + its byte-equality assertion are tester-owned
 * (rc-corpus + test body); this synthetic document proves the dev-2 mechanic without owning F1.
 */
class RcStructuralRoundTripTest {

    @Test
    fun headerPlusDrawCircle_decodeReEncode_isByteIdentical() {
        val buffer = WireBuffer()
        Header.fromProperties(mapOf(Header.DOC_WIDTH to 320, Header.DOC_HEIGHT to 470)).write(buffer)
        DrawCircle(150f, 150f, 150f).write(buffer)
        val golden = buffer.toByteArray()

        val r = ConformanceEngine.writerByteEquality("header+circle", golden, RcDocumentCodec)
        assertTrue(r.ok, r.report)
        assertNull(r.firstDivergence)

        // Two ops decoded (header + draw), and the op spans name them.
        val spans = RcDocumentCodec.decode(golden).opSpans()
        assertEquals(2, spans.size)
        assertEquals("HEADER", spans[0].name)
        assertEquals("DRAW_CIRCLE", spans[1].name)
    }
}
