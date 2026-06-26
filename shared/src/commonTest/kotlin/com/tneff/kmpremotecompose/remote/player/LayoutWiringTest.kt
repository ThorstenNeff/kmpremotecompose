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
import com.tneff.kmpremotecompose.remote.player.core.RemoteComposePlayer
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * REM-36/37 — the player wires dev-2's `LayoutMeasure` pass into the render order **between the
 * RootContentBehavior scale-setup and the eval (Phase A)**, so a `ComponentValue`'s measured dimension
 * is in the store when the FloatExpressions that reference it evaluate. server_clock's canvas-content
 * dims (valueIds 43/44 = 500) were 0 before this (→ degenerate → blank).
 */
class LayoutWiringTest {

    @Test
    fun player_runsMeasurePass_soComponentDimsAreInStoreForEval() {
        Builtins.register()
        val doc = DocumentReader.inflate(RcCorpus.readFixture("corpus/server_clock.rc"))
        val ctx = RemoteContext()

        RemoteComposePlayer(ctx).paint(doc, NoOpPaintContext(ctx), surfaceWidth = 500f, surfaceHeight = 500f)

        // The player's measure pass (ordered before Phase A) loaded the component dims into the store.
        assertEquals(500f, ctx.getFloat(43), "player measure pass loaded component WIDTH (valueId 43)")
        assertEquals(500f, ctx.getFloat(44), "player measure pass loaded component HEIGHT (valueId 44)")
    }
}
