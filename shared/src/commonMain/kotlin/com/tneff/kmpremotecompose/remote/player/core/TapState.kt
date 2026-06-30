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
package com.tneff.kmpremotecompose.remote.player.core

/**
 * REM-108 (Epic-F, REM-154) **S2** — persistent **tap/click gesture state**, the click-path analog of
 * [TouchState] (which carries the continuous drag for `TouchExpression`). It survives the per-frame-fresh
 * [RemoteContext]: the app's `detectTapGestures` enqueues discrete down/up/cancel events; the player drains
 * them each LIVE frame in its click dispatch.
 *
 * **Active-span (the PO/assist mandatory condition):** [activeSpanId] is the component id that received the
 * DOWN. `up`/`cancel` route to **that same span** — never a re-hit-test at up-time — so a finger that
 * presses on element X, drags off, and releases still delivers `up`/`cancel` to X (upstream
 * `mAppliedTouchOperations` semantics).
 *
 * **Cross-frame persistence:** a click action mutates an in-doc value (e.g. `VALUE_INTEGER_CHANGE_ACTION`
 * → an integer id). Because the context is rebuilt each frame and the doc re-applies its `DATA_INT`
 * defaults in Phase A, the mutation must be re-applied each frame to persist — [intOverrides] holds those
 * mutated `valueId → value` pairs, re-loaded over the defaults every live frame by the dispatcher.
 *
 * Render-only — never serialized (§2). A static (non-live) render never drains this → determinism holds.
 */
class TapState {
    enum class Phase { DOWN, UP, CANCEL }

    class Event(val phase: Phase, val x: Float, val y: Float)

    private val queue = ArrayDeque<Event>()

    /** The component id that received the current DOWN (the down-span), or [NO_SPAN] when no press is active. */
    var activeSpanId: Int = NO_SPAN

    /** Click-mutated integer values (`valueId → value`), re-applied each frame so a tap's effect persists. */
    val intOverrides: MutableMap<Int, Int> = mutableMapOf()

    /**
     * REM-175 — click-mutated float values (`valueId → value`), re-applied each frame so a tap's
     * effect persists. Symmetric to [intOverrides] for the float-store side: the float-counter
     * Path-A pattern (DATA_FLOAT + FloatExpression `c+1` + `VALUE_FLOAT_EXPRESSION_CHANGE_ACTION(c,
     * c+1)`) writes its accumulating result here, so subsequent frames keep counting from the new
     * value instead of resetting to the DATA_FLOAT initial.
     */
    val floatOverrides: MutableMap<Int, Float> = mutableMapOf()

    /** Enqueue a press at doc-space ([x], [y]). */
    fun down(x: Float, y: Float) { queue.addLast(Event(Phase.DOWN, x, y)) }

    /** Enqueue a release at doc-space ([x], [y]) (the dispatcher routes it to [activeSpanId]). */
    fun up(x: Float, y: Float) { queue.addLast(Event(Phase.UP, x, y)) }

    /** Enqueue a gesture cancel (routed to [activeSpanId]). */
    fun cancel() { queue.addLast(Event(Phase.CANCEL, 0f, 0f)) }

    /** Drain all queued events in order (handles a fast down+up that lands within one frame). */
    fun drain(): List<Event> {
        if (queue.isEmpty()) return emptyList()
        val out = queue.toList()
        queue.clear()
        return out
    }

    companion object {
        /** [activeSpanId] sentinel: no press is currently active. */
        const val NO_SPAN: Int = Int.MIN_VALUE
    }
}
