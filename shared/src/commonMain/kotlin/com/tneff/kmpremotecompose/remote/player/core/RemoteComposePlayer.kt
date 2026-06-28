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
import com.tneff.kmpremotecompose.remote.core.operations.layout.TouchExpression
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
        staticTimeSeconds: Float = 0f,
        sensorSource: SensorSource = NoOpSensorSource,
        touchState: TouchState? = null,
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
        // from a FIXED seed — NOT from whatever `frameTimeSeconds` the host passes — so time-driven docs
        // (clocks → 12:00:00, countdown, flow_control's `(TIME_IN_SEC%3)-1`) are deterministic and their
        // goldens freezable. Live mode (animation on) advances from the real `frameTimeSeconds` (clocks tick).
        // REM-62: that static seed defaults to 0f (== the original t=0 path, byte-for-byte) but a host may
        // pin it to a fixed [staticTimeSeconds] (deep-link `&t=N`) to capture a deterministic non-zero
        // frame with spread analog-clock hands. `&t` absent / 0 → 0f → identical to the pre-REM-62 render.
        // Read ONLY in the static branch → the live loop is untouched.
        val timeSeed = if (context.isAnimationEnabled()) frameTimeSeconds else staticTimeSeconds
        // REM-93: seed ID_DENSITY with the doc's GENERATION density (header.density), not the device
        // density — the doc-px canvas (REM-51) is generation-density space, so FLOAT_DENSITY-referencing
        // coords must resolve against it for cross-target parity. Fallback 1f if a doc carries no header.
        context.seedSystemVariables(docW, docH, timeSeed, document.header?.density ?: 1f)
        // REM-101 (D5): seed the sensor ids this doc reads (17–26) from the host [sensorSource] — LIVE
        // mode ONLY, so static renders stay deterministic (goldens / REM-78 sweep / 173-conformance
        // untouched). An axis the source can't provide (read==null) is left at its 0f default
        // (capability-floor: static for that axis). Runtime-only; no serialized bytes (§2 safe). The S1
        // default [NoOpSensorSource] returns null everywhere → behaviour-identical to pre-REM-101.
        if (context.isAnimationEnabled()) {
            for (id in sensorIdsUsed(document)) sensorSource.read(id)?.let { context.loadFloat(id, it) }
        }
        // REM-108 (Epic-F) S2b: consume one live pointer transition into the doc's TouchExpressions BEFORE
        // Phase A (so their re-eval this frame sees the loaded TOUCH_POS). LIVE mode only → static renders
        // never dispatch a touch → deterministic. The phase is advanced here (DOWN→DRAG, UP/CANCEL→IDLE)
        // so the persistent [TouchState] drives exactly one step per frame.
        if (context.isAnimationEnabled() && touchState != null) dispatchTouch(document, context, touchState)
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
        // Both phases walk through [walkGated]: the conditional/non-loop path is the original verbatim walk
        // (CONDITIONAL_OPERATIONS skips its block when false — REM-41); LOOP_START is intercepted by an
        // isolated branch (REM-58) that triggers ONLY on a loop, so non-loop docs walk byte-identically.
        val ops = document.operations
        walkGated(ops, 0, ops.size, paintPhase = false, paint) { op ->
            if (op is VariableSupport) {
                op.updateVariables(context)
                op.apply(context)
            }
        }
        walkGated(ops, 0, ops.size, paintPhase = true, paint) { op ->
            if (op is PaintOperation) {
                op.paint(context, paint)
            }
        }
        // Animation (REM-36 E-D1): a time-driven doc (references CONTINUOUS_SEC/TIME_* — clocks, the
        // cube3d spin) requests a continuous repaint so the host loop advances [frameTimeSeconds] and
        // re-renders. Gated by [RemoteContext.animationEnabled] (off ⇒ a single static frame, e.g. the
        // t=0 golden). The walk/eval re-run unchanged each pass (the S1 frame-time seam).
        if (context.isAnimationEnabled() && (isTimeDriven(document) || isSensorDriven(document))) {
            context.wakeIn(CONTINUOUS) // REM-101: a sensor-driven doc also needs continuous live repaint
        }
        return context.wakeInSeconds
    }

    /**
     * Walk ops `[start, end)` applying [action] for one phase. The **non-loop path is the original verbatim
     * walk** (REM-41): a `CONDITIONAL_OPERATIONS` whose [ConditionalOperations.conditionHolds] is false skips
     * its block (to past the matching `CONTAINER_END`); everything else is handed to [action] linearly.
     *
     * **`LOOP_START` is intercepted by an isolated branch (REM-58)** that triggers ONLY on a loop — so docs
     * without loops walk byte-identically to before. The loop runs its body per iteration **only in the
     * paint phase** ([runLoop], combined eval+paint, mirroring upstream `LoopOperation.paint`); the eval
     * phase skips the loop body (the loop self-evaluates each iteration so index-dependent coords are fresh).
     */
    private fun walkGated(
        ops: List<Operation>,
        start: Int,
        end: Int,
        paintPhase: Boolean,
        paint: PaintContext,
        action: (Operation) -> Unit,
    ) {
        var i = start
        while (i < end) {
            val op = ops[i]
            when {
                op is LoopStart -> { // isolated loop interception — non-loop path below is untouched
                    val afterEnd = skipConditionalBlock(ops, i) // index past the matching CONTAINER_END
                    if (paintPhase) runLoop(op, ops, i + 1, afterEnd - 1, paint) // body = [i+1, END)
                    i = afterEnd
                }
                op is ConditionalOperations && !op.conditionHolds(context) -> i = skipConditionalBlock(ops, i)
                else -> { action(op); i++ }
            }
        }
    }

    /**
     * Run a [LoopStart]'s body `[bodyStart, bodyEnd)` for `i = from; i < until; i += step` (upstream
     * `LoopOperation.paint`). Each iteration loads the index var (id 0 ⇒ none) then re-walks the body
     * **eval-then-paint** (via [walkGated], nesting-aware) so index-dependent coords resolve fresh per
     * iteration. from/step/until resolve NaN var-refs. Guarded by [MAX_LOOP_ITERATIONS] (step ≤ 0 / runaway).
     */
    private fun runLoop(loop: LoopStart, ops: List<Operation>, bodyStart: Int, bodyEnd: Int, paint: PaintContext) {
        val from = resolveFloat(loop.from)
        val step = resolveFloat(loop.step)
        val until = resolveFloat(loop.until)
        var v = from
        var count = 0
        while (v < until && count < MAX_LOOP_ITERATIONS) {
            if (loop.indexId != 0) context.loadFloat(loop.indexId, v)
            walkGated(ops, bodyStart, bodyEnd, paintPhase = false, paint) { op ->
                if (op is VariableSupport) { op.updateVariables(context); op.apply(context) }
            }
            walkGated(ops, bodyStart, bodyEnd, paintPhase = true, paint) { op ->
                if (op is PaintOperation) op.paint(context, paint)
            }
            v += step
            count++
        }
    }

    private fun resolveFloat(f: Float): Float = if (f.isNaN()) context.getFloat(WireTypes.idFromNan(f)) else f

    // ---- REM-108 (Epic-F) S1: touch dispatch seam ----
    // The pointer→document entry points (upstream `CoreDocument.touchDown/Drag/Up/Cancel`). The CMP
    // `pointerInput` in RemoteComposeApp calls these in LIVE mode with doc-space coords; they load
    // [RemoteContext.ID_TOUCH_POS_X]/Y so a touch-reading expression can see the live position. **S1 only
    // loads the position** — no op consumes it yet (TouchExpression stays a byte carrier), so this is
    // render-invariant. S2 makes TouchExpression evaluate against these + adds the down/drag/up state
    // machine (delta/easing/stop-modes). Never called in static mode → determinism preserved.

    /** Touch began at doc-space ([x], [y]) — load the position + notify the doc's TouchExpressions (S2). */
    fun touchDown(document: RemoteComposeDocument, context: RemoteContext, x: Float, y: Float) {
        loadTouchPos(context, x, y)
        for (te in document.operations) if (te is TouchExpression) te.touchDown(context)
    }

    /** Touch moved to doc-space ([x], [y]) — load the position + re-evaluate the TouchExpressions (S2). */
    fun touchDrag(document: RemoteComposeDocument, context: RemoteContext, x: Float, y: Float) {
        loadTouchPos(context, x, y)
        for (te in document.operations) if (te is TouchExpression) te.touchDrag(context)
    }

    /** Touch ended — load the final position + settle the TouchExpressions to their stop value (S2). */
    fun touchUp(document: RemoteComposeDocument, context: RemoteContext, x: Float, y: Float) {
        loadTouchPos(context, x, y)
        for (te in document.operations) if (te is TouchExpression) te.touchUp()
    }

    /** Touch cancelled — abandon the active drag on the TouchExpressions, keep their value (S2). */
    fun touchCancel(document: RemoteComposeDocument) {
        for (te in document.operations) if (te is TouchExpression) te.touchCancel()
    }

    private fun loadTouchPos(context: RemoteContext, x: Float, y: Float) {
        context.loadFloat(RemoteContext.ID_TOUCH_POS_X, x)
        context.loadFloat(RemoteContext.ID_TOUCH_POS_Y, y)
    }

    /** Consume exactly one [TouchState] transition this frame and advance the phase (REM-108 S2b). */
    private fun dispatchTouch(document: RemoteComposeDocument, context: RemoteContext, ts: TouchState) {
        when (ts.phase) {
            TouchPhase.DOWN -> { touchDown(document, context, ts.x, ts.y); ts.phase = TouchPhase.DRAG }
            TouchPhase.DRAG -> touchDrag(document, context, ts.x, ts.y)
            TouchPhase.UP -> { touchUp(document, context, ts.x, ts.y); ts.phase = TouchPhase.IDLE }
            TouchPhase.CANCEL -> { touchCancel(document); ts.phase = TouchPhase.IDLE }
            TouchPhase.IDLE -> {}
        }
    }

    /**
     * The index **just past** a container-opener's matching `CONTAINER_END` (original REM-41 verbatim).
     * Nesting-aware: every container-opening op ([opensContainer], incl. nested conditionals/loops)
     * increments depth, every `CONTAINER_END` decrements; the match returns depth to 0.
     */
    private fun skipConditionalBlock(ops: List<Operation>, openIndex: Int): Int {
        var depth = 0
        var j = openIndex + 1
        while (j < ops.size) {
            val o = ops[j]
            if (o.opcode == Operations.CONTAINER_END) {
                if (depth == 0) return j + 1
                depth--
            } else if (opensContainer(o)) {
                depth++
            }
            j++
        }
        return j // malformed (no matching END) → consume the rest
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
         * The reserved sensor ids ([RemoteContext.SENSOR_ID_RANGE], 17–26) this document reads in its
         * float expressions (REM-101). Drives both which sensors a [SensorSource] starts and which ids the
         * player seeds per live frame. Empty ⇒ not a sensor doc.
         */
        fun sensorIdsUsed(document: RemoteComposeDocument): Set<Int> =
            buildSet {
                for (op in document.operations) if (op is FloatExpression) {
                    for (v in op.value) {
                        if (v.isNaN() && WireTypes.fromNaN(v) in RemoteContext.SENSOR_ID_RANGE) add(WireTypes.fromNaN(v))
                    }
                }
            }

        /** True if the document reads any sensor variable (17–26) → drives live sensor interactivity. */
        fun isSensorDriven(document: RemoteComposeDocument): Boolean = sensorIdsUsed(document).isNotEmpty()

        /**
         * The reserved touch ids ([RemoteContext.TOUCH_ID_RANGE] 13–16 + EVENT_TIME 29) this document reads
         * — scanned in both `FloatExpression`s and `TouchExpression.exp` (REM-108). Corpus uses only 13/14.
         */
        fun touchIdsUsed(document: RemoteComposeDocument): Set<Int> =
            buildSet {
                fun scan(arr: FloatArray) {
                    for (v in arr) if (v.isNaN()) {
                        val id = WireTypes.fromNaN(v)
                        if (id in RemoteContext.TOUCH_ID_RANGE || id == RemoteContext.ID_TOUCH_EVENT_TIME) add(id)
                    }
                }
                for (op in document.operations) when (op) {
                    is FloatExpression -> scan(op.value)
                    is TouchExpression -> scan(op.exp)
                    else -> {}
                }
            }

        /**
         * True if the document is touch-interactive — has any [TouchExpression] (incl. the one a
         * `ScrollModifier` wraps). Drives whether RemoteComposeApp attaches the live pointer seam (REM-108).
         */
        fun isTouchDriven(document: RemoteComposeDocument): Boolean =
            document.operations.any { it is TouchExpression }

        /**
         * The set of `TouchExpression` stop-modes (`stopLogic ushr 16`) used by the document (REM-108).
         * Corpus-grounded scope: modes 0–6 are supported; **mode 7 (SINGLE_EVEN) is corpus-absent** and the
         * S1 reach-guard test fails loudly if a doc ever uses it (no silent gap — D1/D5 discipline).
         */
        fun touchStopModesUsed(document: RemoteComposeDocument): Set<Int> =
            document.operations.filterIsInstance<TouchExpression>().mapTo(mutableSetOf()) { it.stopLogic ushr 16 }

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
