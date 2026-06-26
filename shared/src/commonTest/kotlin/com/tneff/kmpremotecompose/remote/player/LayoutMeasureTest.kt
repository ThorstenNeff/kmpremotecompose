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
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * REM-37 E-Layout-1 — the shallow measure pass loads component-dimension variables into the store so the
 * consuming FloatExpressions resolve. server_clock (500×500) is an all-FILL chain Root→Box→Canvas, so its
 * canvas-content component dims (valueIds 43/44) must measure to the doc size — the input the clock-hand
 * expressions need (they were 0 before the measure pass → degenerate → blank).
 */
class LayoutMeasureTest {

    @Test
    fun serverClock_fillChain_loadsComponentDimsToDocSize() {
        Builtins.register()
        val doc = DocumentReader.inflate(RcCorpus.readFixture("corpus/server_clock.rc"))
        val ctx = RemoteContext()
        LayoutMeasure.measure(doc, surfaceW = 500f, surfaceH = 500f, context = ctx)
        assertEquals(500f, ctx.getFloat(43), "component WIDTH (valueId 43) = doc width via FILL chain")
        assertEquals(500f, ctx.getFloat(44), "component HEIGHT (valueId 44) = doc height via FILL chain")
    }

    @Test
    fun emptyish_doc_isSafeNoop() {
        Builtins.register()
        val doc = DocumentReader.inflate(RcCorpus.readFixture("corpus/procedure_simple1.rc"))
        val ctx = RemoteContext()
        // No layout tree / ComponentValues → measure must not throw and must leave the store untouched.
        LayoutMeasure.measure(doc, surfaceW = 500f, surfaceH = 500f, context = ctx)
        assertEquals(0f, ctx.getFloat(43), "no ComponentValue → store untouched")
    }
}
