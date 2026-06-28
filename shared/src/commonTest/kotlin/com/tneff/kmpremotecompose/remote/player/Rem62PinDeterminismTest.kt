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

import com.tneff.kmpremotecompose.RcRouter
import com.tneff.kmpremotecompose.conformance.RcCorpus
import com.tneff.kmpremotecompose.remote.core.document.DocumentReader
import com.tneff.kmpremotecompose.remote.core.operations.Builtins
import com.tneff.kmpremotecompose.remote.core.operations.ConditionalOperations
import com.tneff.kmpremotecompose.remote.player.core.RemoteComposePlayer
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * REM-62/REM-65 — the deep-link `&t=N` pin must select a **deterministic** static frame through the full
 * shared path (RcRouter.setStaticTime → staticTimeSeconds → player static seed), identically on every
 * platform. Proves `&t=0` is a real pin (NOT treated as falsy/unpinned): t=0 seeds TIME_IN_SEC=0 → the
 * `(TIME_IN_SEC%3)-1` predicate = -1 → LT branch (the green circle), exactly like the explicit t=3.
 */
class Rem62PinDeterminismTest {

    private fun timeInSecForDeepLinkT(value: String): Float {
        RcRouter.setStaticTime(value) // exactly what MainActivity/iOSApp call with getQueryParameter("t")
        val doc = DocumentReader.inflate(RcCorpus.readFixture("corpus/flow_control_checks_test_conditional.rc"))
        // App static render: live=false ⇒ animationEnabled=false; staticTimeSeconds from RcRouter.
        val ctx = RemoteContext().apply { animationEnabled = false }
        RemoteComposePlayer(ctx).paint(
            doc, NoOpPaintContext(ctx),
            surfaceWidth = 500f, surfaceHeight = 500f,
            staticTimeSeconds = RcRouter.staticTimeSeconds,
        )
        return ctx.getFloat(RemoteContext.ID_TIME_IN_SEC)
    }

    private fun circleBranchHolds(value: String): Boolean {
        RcRouter.setStaticTime(value)
        val doc = DocumentReader.inflate(RcCorpus.readFixture("corpus/flow_control_checks_test_conditional.rc"))
        val ctx = RemoteContext().apply { animationEnabled = false }
        RemoteComposePlayer(ctx).paint(
            doc, NoOpPaintContext(ctx),
            surfaceWidth = 500f, surfaceHeight = 500f,
            staticTimeSeconds = RcRouter.staticTimeSeconds,
        )
        // cond[31] = LT(a,0); the green circle. a = (TIME_IN_SEC%3)-1.
        return doc.operations.filterIsInstance<ConditionalOperations>()
            .any { it.type == ConditionalOperations.TYPE_LT && it.conditionHolds(ctx) }
    }

    @Test
    fun deepLinkT0_isARealPin_notFalsy() {
        Builtins.register()
        assertEquals(0f, RcRouter.staticTimeSeconds.let { RcRouter.setStaticTime("0"); RcRouter.staticTimeSeconds },
            "setStaticTime(\"0\") → 0f (a real pin, not falsy/ignored)")
        assertEquals(0f, timeInSecForDeepLinkT("0"), "&t=0 → static TIME_IN_SEC=0 (deterministic)")
        assertEquals(1f, timeInSecForDeepLinkT("1"), "&t=1 → static TIME_IN_SEC=1")
        assertEquals(2f, timeInSecForDeepLinkT("2"), "&t=2 → static TIME_IN_SEC=2")
        RcRouter.setStaticTime(null) // reset shared state
    }

    @Test
    fun deepLinkT0_selectsCircleBranch_likeT3() {
        Builtins.register()
        assertEquals(true, circleBranchHolds("0"), "&t=0 → a=-1 → LT → green circle (NOT the text-only t=1 frame)")
        assertEquals(false, circleBranchHolds("1"), "&t=1 → a=0 → neither branch (text only)")
        assertEquals(true, circleBranchHolds("3"), "&t=3 → a=-1 → circle (same frame as t=0)")
        RcRouter.setStaticTime(null)
    }
}
