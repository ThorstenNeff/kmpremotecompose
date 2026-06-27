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

import com.tneff.kmpremotecompose.remote.core.document.RemoteComposeDocument
import com.tneff.kmpremotecompose.remote.core.operations.ConditionalOperations
import com.tneff.kmpremotecompose.remote.core.operations.FloatExpression
import com.tneff.kmpremotecompose.remote.core.operations.Operation
import com.tneff.kmpremotecompose.remote.core.operations.Operations
import com.tneff.kmpremotecompose.remote.core.operations.layout.LoopStart
import com.tneff.kmpremotecompose.remote.core.operations.layout.RootContentBehavior
import com.tneff.kmpremotecompose.remote.wire.WireTypes

/**
 * The Layer 2 player op-walk skeleton (REM-30, L2-S1): renders a decoded [RemoteComposeDocument] by
 * walking its operations in order and dispatching every [PaintOperation] into a [PaintContext].
 *
 * S1 establishes the walk + dispatch seam only. The geometry adapter (L2-S2, dev-2) and text adapter
 * (L2-S3, dev-1) provide the real [PaintContext] primitives and mark the draw ops as [PaintOperation];
 * the walk itself stays unchanged as that coverage grows. Variable evaluation / animation clocking
 * (the upstream two-phase `apply` then `paint`) are a later milestone — see STATUS `declareId` note.
 */
class RemoteComposePlayer(val context: RemoteContext = RemoteContext()) {

    /**
     * Render [document] into [paint] for the single frame at [frameTimeSeconds].
     *
     * The frame time is **injected**, never hardcoded in the walk (PO 2026-06-26 frame-render ↔
     * time-source seam): the MVP renders one static frame at the default `t = 0`; a continuous
     * animation loop attaches by calling this again with an advancing time — the walk is unchanged.
     * Time-driven variables (`ANIMATED_FLOAT`, …) evaluate against [RemoteContext.frameTimeSeconds]
     * once that evaluation slice lands.
     *
     * Binds [paint] to the [context], resets per-pass state, resets the paint defaults, then walks the
     * ops in order, calling [PaintOperation.paint] for each draw op. Returns the player's requested
     * next-frame delay in seconds ([RemoteContext.wakeInSeconds]; -1 = no repaint requested) so a host
     * render loop can schedule the next pass.
     */
    fun paint(
        document: RemoteComposeDocument,
        paint: PaintContext,
        frameTimeSeconds: Float = 0f,
        surfaceWidth: Float = -1f,
        surfaceHeight: Float = -1f,
    ): Float {
        context.paintContext = paint
        context.resetPass(frameTimeSeconds)
        paint.reset()
        // The document authors its content in DOC-space (header dims). For SIZING_SCALE the player
        // scales doc→surface; window vars therefore reference the DOC box (so drawOval(0,0,
        // FLOAT_WINDOW_WIDTH,…) fills doc-space, then the canvas is scaled to the surface). REM-36.
        val docW = document.width.toFloat()
        val docH = document.height.toFloat()
        // Seed system variables (REM-36 E-Seed) BEFORE Phase A — window = DOC dims (revised for the
        // doc→surface scale below), density, and the static time/clock vars.
        // Static-mode time-pin (REM-57): in static mode (animation off) seed the wall-clock time vars
        // from t=0 — NOT from whatever `frameTimeSeconds` the host passes — so time-driven docs
        // (clocks → 12:00:00, countdown, flow_control's `(TIME_IN_SEC%3)-1`) are deterministic and their
        // goldens freezable. Live mode (animation on) advances from the real `frameTimeSeconds` (clocks tick).
        val timeSeed = if (context.isAnimationEnabled()) frameTimeSeconds else 0f
        context.seedSystemVariables(docW, docH, timeSeed)
        // RootContentBehavior doc→surface scaling (REM-36): when a surface box is given, apply
        // translate(align) then scale(doc→surface) — upstream `CoreDocument` order — so doc-space
        // renders with correct proportions instead of 1:1 (a 600-doc stretched into a 924-surface).
        if (surfaceWidth > 0f && surfaceHeight > 0f) {
            val behavior = document.operations.firstNotNullOfOrNull { it as? RootContentBehavior }
            if (behavior != null && behavior.sizing == ContentScaling.SIZING_SCALE) {
                val (sx, sy) = ContentScaling.computeScale(
                    surfaceWidth, surfaceHeight, docW, docH, behavior.sizing, behavior.mode,
                )
                val (tx, ty) = ContentScaling.computeTranslate(
                    surfaceWidth, surfaceHeight, sx, sy, docW, docH, behavior.alignment,
                )
                paint.translate(tx, ty)
                paint.scale(sx, sy)
            }
        }
        // Layout-measure pass (REM-37 E-Layout, dev-2) — AFTER scale-setup, BEFORE the eval phase, so a
        // `ComponentValue`'s measured dimension (e.g. server_clock #43/44) is in the store when the
        // FloatExpressions that reference it evaluate. Measures in doc-space; safe no-op without a tree.
        LayoutMeasure.measure(document, surfaceWidth, surfaceHeight, context)
        // Phase A (REM-36 Eval-Engine E1): resolve + evaluate variables BEFORE painting, so draw ops
        // read already-resolved values (the long-flagged "deferred apply-phase"). MVP evaluates every
        // VariableSupport op each frame (no dirty tracking). updateVariables (resolve NaN refs) then
        // apply (evaluate + load into the store).
        // Both phases walk through [walkRange]: CONDITIONAL_OPERATIONS skips its block when false (REM-41);
        // LOOP_START re-walks its body per iteration (REM-58). Phase A first so a conditional's a/b are
        // resolved before Phase B re-checks the same (store-persisted) condition consistently.
        val ops = document.operations
        walkRange(ops, 0, ops.size, paint, paintPhase = false) // Phase A: eval (VariableSupport)
        walkRange(ops, 0, ops.size, paint, paintPhase = true) //  Phase B: paint (PaintOperation)
        // Animation (REM-36 E-D1): a time-driven doc (references CONTINUOUS_SEC/TIME_* — clocks, the
        // cube3d spin) requests a continuous repaint so the host loop advances [frameTimeSeconds] and
        // re-renders. Gated by [RemoteContext.animationEnabled] (off ⇒ a single static frame, e.g. the
        // t=0 golden). The walk/eval re-run unchanged each pass (the S1 frame-time seam).
        if (context.isAnimationEnabled() && isTimeDriven(document)) context.wakeIn(CONTINUOUS)
        return context.wakeInSeconds
    }

    /**
     * Walk ops `[start, end)` for one phase. `paintPhase=false` evaluates [VariableSupport]; `true` paints
     * [PaintOperation]. Container-aware:
     *  - **CONDITIONAL_OPERATIONS** (REM-41): when [ConditionalOperations.conditionHolds] is false, skip its
     *    block (to past the matching `CONTAINER_END`); else walk into it.
     *  - **LOOP_START** (REM-58): re-walk its body per iteration. The loop is run **only in the paint phase**
     *    (combined eval+paint per iteration, mirroring upstream `LoopOperation.paint`); the eval phase skips
     *    the body (the loop self-evaluates each iteration so index-dependent coords are fresh per pass).
     */
    private fun walkRange(ops: List<Operation>, start: Int, end: Int, paint: PaintContext, paintPhase: Boolean) {
        var i = start
        while (i < end) {
            val op = ops[i]
            when {
                op is ConditionalOperations ->
                    if (op.conditionHolds(context)) i++ else i = matchingContainerEnd(ops, i) + 1
                op is LoopStart -> {
                    val endIdx = matchingContainerEnd(ops, i)
                    if (paintPhase) runLoop(op, ops, i + 1, endIdx, paint)
                    i = endIdx + 1
                }
                paintPhase -> { if (op is PaintOperation) op.paint(context, paint); i++ }
                else -> { if (op is VariableSupport) { op.updateVariables(context); op.apply(context) }; i++ }
            }
        }
    }

    /**
     * Run a [LoopStart]'s body `[bodyStart, bodyEnd)` for `i = from; i < until; i += step` (upstream
     * `LoopOperation.paint`). Each iteration loads the index var (id 0 ⇒ none) then re-walks the body
     * **eval-then-paint** so index-dependent coords resolve fresh per iteration. from/step/until resolve
     * NaN var-refs. Guarded by [MAX_LOOP_ITERATIONS] (step ≤ 0 / runaway safety).
     */
    private fun runLoop(loop: LoopStart, ops: List<Operation>, bodyStart: Int, bodyEnd: Int, paint: PaintContext) {
        val from = resolveFloat(loop.from)
        val step = resolveFloat(loop.step)
        val until = resolveFloat(loop.until)
        var v = from
        var count = 0
        while (v < until && count < MAX_LOOP_ITERATIONS) {
            if (loop.indexId != 0) context.loadFloat(loop.indexId, v)
            walkRange(ops, bodyStart, bodyEnd, paint, paintPhase = false)
            walkRange(ops, bodyStart, bodyEnd, paint, paintPhase = true)
            v += step
            count++
        }
    }

    private fun resolveFloat(f: Float): Float = if (f.isNaN()) context.getFloat(WireTypes.idFromNan(f)) else f

    /**
     * The index **of** a container-opener's matching `CONTAINER_END`. Nesting-aware: every container-opening
     * op ([opensContainer], incl. nested conditionals/loops) increments depth, every `CONTAINER_END`
     * decrements; the match returns depth to 0. `ops.size` if malformed (no matching END).
     */
    private fun matchingContainerEnd(ops: List<Operation>, openIndex: Int): Int {
        var depth = 0
        var j = openIndex + 1
        while (j < ops.size) {
            val o = ops[j]
            if (o.opcode == Operations.CONTAINER_END) {
                if (depth == 0) return j
                depth--
            } else if (opensContainer(o)) {
                depth++
            }
            j++
        }
        return ops.size
    }

    companion object {
        /** Safety cap on LOOP_START iterations (step ≤ 0 / runaway guard; real graph docs loop ≤ ~100). */
        private const val MAX_LOOP_ITERATIONS = 10_000

        /** Render-loop contract for [paint]'s return ([RemoteContext.wakeInSeconds]): */
        /** no repaint requested — render a single static frame. */
        const val STATIC = -1f
        /** repaint as soon as possible (next frame) — continuous animation. */
        const val CONTINUOUS = 0f

        /** True if the document references a time/clock system variable (ids 1–4) → animated. */
        fun isTimeDriven(document: RemoteComposeDocument): Boolean =
            document.operations.any { op ->
                op is FloatExpression && op.value.any { it.isNaN() && WireTypes.fromNaN(it) in 1..4 }
            }

        /**
         * Opcodes of **container-opening** ops — those whose block is closed by a `CONTAINER_END`
         * (REM-41 depth counting). This is the authoritative set: upstream `CoreDocument` inflation
         * pushes on **every** `instanceof Container` and pops on `ContainerEnd`, so this **must** equal
         * the full set of `implements Container` opcodes — verified one-for-one against upstream (each
         * Container class's `OP_CODE`). A missed type would desync the depth counter → skip the wrong
         * span → break layout docs (the 173-render-sweep is the second safety net).
         *
         * NOTE (drift): the Container set includes **inheritance subclasses** (e.g. `StateLayout extends
         * LayoutManager` → Component → Container — a container without an explicit `implements Container`),
         * not only the explicitly-declaring classes. Guarded by `ConditionalGateTest`'s completeness test
         * against the authoritative list; the fully drift-proof fix is a `Container` marker interface on
         * the op classes (cross-lane, deferred). `ListActionsOperation` has no distinct KMP opcode → N/A.
         */
        internal val CONTAINER_OPENING_OPCODES: Set<Int> = setOf(
            // Flow / structural containers
            Operations.COMPONENT_START,
            Operations.CANVAS_OPERATIONS,
            Operations.CONDITIONAL_OPERATIONS,
            Operations.LOOP_START,
            Operations.IMPULSE_START,
            Operations.IMPULSE_PROCESS,
            Operations.PARTICLE_LOOP,
            Operations.PARTICLE_COMPARE,
            Operations.RUN_ACTION,
            Operations.CORE_TEXT,
            // Action / click / function / reference containers (REM-41 NO-GO fix — were missing)
            Operations.MODIFIER_CLICK,
            Operations.MODIFIER_MULTI_CLICK,
            Operations.FUNCTION_DEFINE,
            Operations.REFERENCED_OPERATIONS,
            // Pattern/macro containers (PatternBlock/Define/ForEach/Inflation) — were missing
            Operations.MACRO_BLOCK,
            Operations.MACRO_DEFINE,
            Operations.MACRO_FOR_EACH,
            Operations.MACRO_CALL,
            // Layout managers (upstream `Component` subclasses)
            Operations.LAYOUT_ROOT,
            Operations.LAYOUT_CONTENT,
            Operations.LAYOUT_BOX,
            Operations.LAYOUT_ROW,
            Operations.LAYOUT_COLUMN,
            Operations.LAYOUT_CANVAS,
            Operations.LAYOUT_CANVAS_CONTENT,
            Operations.LAYOUT_TEXT,
            Operations.LAYOUT_IMAGE,
            Operations.LAYOUT_FIT_BOX,
            Operations.LAYOUT_FLOW,
            Operations.LAYOUT_CUSTOM,
            Operations.LAYOUT_COMPUTE,
            Operations.LAYOUT_COLLAPSIBLE_ROW,
            Operations.LAYOUT_COLLAPSIBLE_COLUMN,
            Operations.LAYOUT_STATE, // StateLayout extends LayoutManager → Container via inheritance (c_state_layout)
        )

        /** True if [op] opens a CONTAINER_END-terminated block (REM-41 depth counting). */
        fun opensContainer(op: Operation): Boolean = op.opcode in CONTAINER_OPENING_OPCODES
    }
}
