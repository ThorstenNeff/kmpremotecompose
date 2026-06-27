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
import com.tneff.kmpremotecompose.remote.core.document.RemoteComposeDocument
import com.tneff.kmpremotecompose.remote.core.operations.Builtins
import com.tneff.kmpremotecompose.remote.core.operations.ConditionalOperations
import com.tneff.kmpremotecompose.remote.core.operations.FloatExpression
import com.tneff.kmpremotecompose.remote.core.operations.Operation
import com.tneff.kmpremotecompose.remote.core.operations.layout.ContainerEnd
import com.tneff.kmpremotecompose.remote.player.core.RemoteComposePlayer
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * REM-41 conditional gate — the ops up to the matching `CONTAINER_END` run only if the comparison
 * holds. A `FloatExpression` inside the block is the headless probe: it loads its id iff the block ran.
 */
class ConditionalGateTest {

    private fun run(ops: List<Operation>): RemoteContext {
        val ctx = RemoteContext()
        RemoteComposePlayer(ctx).paint(RemoteComposeDocument(ops), NoOpPaintContext(ctx))
        return ctx
    }

    @Test
    fun blockRuns_whenConditionHolds() {
        // a(5) > b(0) → GT holds → the gated FloatExpression applies.
        val ctx = run(listOf(
            ConditionalOperations(ConditionalOperations.TYPE_GT, 5f, 0f),
            FloatExpression(id = 90, value = floatArrayOf(7f)),
            ContainerEnd(),
        ))
        assertEquals(7f, ctx.getFloat(90), "condition holds → block executed")
    }

    @Test
    fun blockSkipped_whenConditionFails() {
        // a(-5) > b(0) → GT fails → the gated block is skipped, its FloatExpression never applies.
        val ctx = run(listOf(
            ConditionalOperations(ConditionalOperations.TYPE_GT, -5f, 0f),
            FloatExpression(id = 91, value = floatArrayOf(7f)),
            ContainerEnd(),
        ))
        assertEquals(0f, ctx.getFloat(91), "condition fails → block skipped (not applied)")
    }

    @Test
    fun skipIsNestingAware_resumesAfterMatchingEnd() {
        // Outer GT fails → skip its whole block, incl. a NESTED conditional (depth +1/-1), and resume
        // only after the OUTER CONTAINER_END. The op after the outer end must still run.
        val ctx = run(listOf(
            ConditionalOperations(ConditionalOperations.TYPE_GT, -5f, 0f), // outer: false
            ConditionalOperations(ConditionalOperations.TYPE_GT, 5f, 0f), // nested (opens a container)
            FloatExpression(id = 92, value = floatArrayOf(7f)), // inside nested → skipped
            ContainerEnd(), // closes nested
            ContainerEnd(), // closes outer → skip ends here
            FloatExpression(id = 93, value = floatArrayOf(9f)), // after outer end → must run
        ))
        assertEquals(0f, ctx.getFloat(92), "nested block inside the skipped outer is skipped")
        assertEquals(9f, ctx.getFloat(93), "depth-counting resumed after the OUTER end, not the nested one")
    }

    @Test
    fun realFixture_gateSelectsExactlyOneBranch() {
        // flow_control_checks_test_conditional has two branches: type=GT(a>0) and type=LT(a<0), each
        // gating a DRAW_CIRCLE. Ungated, BOTH circles drew (wrong); the gate must select **at most one**.
        Builtins.register()
        val doc = DocumentReader.inflate(RcCorpus.readFixture("corpus/flow_control_checks_test_conditional.rc"))
        val ctx = RemoteContext()
        var circles = 0
        val recorder = object : NoOpPaintContext(ctx) {
            override fun drawCircle(centerX: Float, centerY: Float, radius: Float) { circles++ }
        }
        RemoteComposePlayer(ctx).paint(doc, recorder, surfaceWidth = 500f, surfaceHeight = 500f)
        assertTrue(circles <= 1, "the gate selects at most one branch (was 2 when ungated); drew=$circles")
    }

    @Test
    fun comparisonTypes_matchUpstream() {
        val c = RemoteContext()
        assertTrue(ConditionalOperations(ConditionalOperations.TYPE_EQ, 3f, 3f).conditionHolds(c))
        assertTrue(ConditionalOperations(ConditionalOperations.TYPE_NEQ, 3f, 4f).conditionHolds(c))
        assertTrue(ConditionalOperations(ConditionalOperations.TYPE_LT, 2f, 4f).conditionHolds(c))
        assertTrue(ConditionalOperations(ConditionalOperations.TYPE_LTE, 4f, 4f).conditionHolds(c))
        assertTrue(ConditionalOperations(ConditionalOperations.TYPE_GT, 5f, 4f).conditionHolds(c))
        assertTrue(ConditionalOperations(ConditionalOperations.TYPE_GTE, 4f, 4f).conditionHolds(c))
        assertTrue(!ConditionalOperations(ConditionalOperations.TYPE_LT, 5f, 4f).conditionHolds(c))
    }
}
