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
package com.tneff.kmpremotecompose.remote.creation

import com.tneff.kmpremotecompose.remote.core.operations.FloatConstant
import com.tneff.kmpremotecompose.remote.core.operations.TextFromFloat
import com.tneff.kmpremotecompose.remote.core.operations.layout.ValueFloatExpressionChangeAction
import com.tneff.kmpremotecompose.remote.wire.WireTypes

/**
 * REM-175 — Counter building blocks (Path A: float-counter, end-to-end byte-proven).
 *
 * The app-Dogfooding gap: the Creation-DSL had no straightforward way to build a hochzählenden
 * Counter (tap-increment + visible value). The two underlying needs were (a) **mutate a value via
 * an expression** (not just a fixed value) and (b) **display an integer-look number** end-to-end.
 * §2 investigation (REM-175 Befund 2026-06-30) showed the wire-format **already supports both** —
 * the gap was render-side dispatch + DSL ergonomics. This file delivers the ergonomic surface;
 * `RemoteComposePlayer.runAction` was extended to honor [ValueFloatExpressionChangeAction] (the
 * one render-side branch that was missing) so the chain actually accumulates across frames.
 *
 * **Why float, not int (Path-A vs Path-B):** the user-visible result for a hochzählenden counter
 * (display 1, 2, 3, … via `TextFromFloat(digitsAfter=0)`) is identical for both float and int
 * paths. Float reuses fully byte-proven and render-eval'd ops (DATA_FLOAT, FloatExpression,
 * ValueFloatExpressionChangeAction, TextFromFloat) — no new wire op, no IntegerExpressionEvaluator
 * port. Path B (true-int via INTEGER_EXPRESSION + VALUE_INTEGER_EXPRESSION_CHANGE_ACTION) is
 * tracked separately under REM-176 for the day exact-int semantics are required.
 */

/**
 * REM-175 — handle returned by [floatCounter]. Bundles the two ids the caller needs to wire into
 * the touch-modifier action: the **counter id** (the float store slot whose value advances on
 * each tap) and the **increment expression id** (the FloatExpression that evaluates to
 * `counter + 1` each frame). The pair flows into [valueFloatExpressionChange] inside the touch
 * block.
 *
 * Both ids are **raw region-0 ints** (not NaN-encoded) — the wire-side action op takes raw int
 * field values verbatim. Use [WireTypes.asNan] if you also want to embed the counter in a
 * downstream RPN expression as a variable ref (e.g. `floatExpression(counterId.asNan(), 2f,
 * RcExpression.MUL)` for a doubled-display).
 */
data class FloatCounterHandle(
    /** The float store id holding the counter's current value (DATA_FLOAT-bound initial, then
     *  overwritten via the action's `floatOverrides` cross-frame persistence). */
    val counterId: Int,
    /** The float store id of the increment expression `counterId + 1`. The action reads this id
     *  on tap and writes the result onto [counterId]. */
    val incrementExprId: Int,
)

/**
 * REM-175 — emit a hochzählenden Counter primitive: a `DATA_FLOAT(counterId, initial)` + a
 * `FloatExpression(incrementExprId, [counterRef, 1f, ADD])`. Returns a [FloatCounterHandle] the
 * caller wires into a touch-modifier action via [valueFloatExpressionChange].
 *
 * **End-to-end behavior:**
 *  - Phase-A every frame: DATA_FLOAT re-loads `initial` into `counterId`; cross-frame
 *    [TapState.floatOverrides] **overrides** that with the accumulated value (re-applied at the
 *    end of [dispatchClick] last frame). FloatExpression then evaluates `counterId + 1` against
 *    the live counter value → result in `incrementExprId`.
 *  - On tap: [ValueFloatExpressionChangeAction] runs at dispatch time, reads
 *    `context.getFloat(incrementExprId)` (the just-computed `counter + 1`), and persists it to
 *    `floatOverrides[counterId]`. The next frame's Phase-A sees the new counter value.
 *
 * **Determinism:** static (non-live) render → tap queue stays empty → no override fires →
 * counter stays at `initial`. REM-108-S2's determinism contract is preserved.
 */
fun RemoteComposeContext.floatCounter(initial: Float = 0f): FloatCounterHandle {
    val counterId = ids.nextId()
    add(FloatConstant(counterId, initial))
    val counterRef = WireTypes.asNan(counterId)
    val incrementRefAsNan = floatExpression(counterRef, 1f, RcExpression.ADD)
    val incrementExprId = WireTypes.idFromNan(incrementRefAsNan)
    return FloatCounterHandle(counterId, incrementExprId)
}

/**
 * REM-175 — `VALUE_FLOAT_EXPRESSION_CHANGE_ACTION` body op. Designed for use inside a touch-event
 * action block (`onTouchDown { incrementCounter(handle) }`-style) to mutate the float at
 * [targetValueId] to the current value of the expression at [valueExpressionId]. With a
 * [FloatCounterHandle] this is `counter += 1`.
 *
 * Wire shape: opcode + int targetValueId + int valueExpressionId = 9 bytes (mirrors upstream
 * `ValueFloatExpressionChangeActionOperation.apply`).
 */
fun RemoteComposeContext.valueFloatExpressionChange(targetValueId: Int, valueExpressionId: Int) {
    add(ValueFloatExpressionChangeAction(valueId = targetValueId, value = valueExpressionId))
}

/**
 * REM-175 — convenience wrapper: emit a [ValueFloatExpressionChangeAction] that increments the
 * counter behind [handle]. Equivalent to `valueFloatExpressionChange(handle.counterId,
 * handle.incrementExprId)`; the helper exists so the touch block reads `incrementCounter(handle)`
 * — closer to user intent.
 */
fun RemoteComposeContext.incrementCounter(handle: FloatCounterHandle) {
    valueFloatExpressionChange(handle.counterId, handle.incrementExprId)
}

/**
 * REM-175 — display an integer-look number from a float-store id via `TEXT_FROM_FLOAT`. Allocates
 * a region-0 text id, binds it to a TextFromFloat formatter with `digitsAfter = 0` so the value
 * renders without a decimal part (e.g. `1`, `2`, `42`); returns the text id (suitable for
 * `drawTextAnchored` / `drawTextRun` / etc.).
 *
 * [digits] is the upstream `digitsBefore` parameter — minimum integer-digit width. With the
 * default flag (`PAD_PRE_NONE`), a shorter integer is NOT padded; with `flags =
 * TextFromFloat.PAD_PRE_ZERO` (12) a 1-digit value at digits=3 renders as `001`. The default is
 * unflagged — `1`, `42`, `1234` all render without padding (which is the natural counter look).
 *
 * Use with a counter handle:
 *   `val handle = floatCounter()`
 *   `val labelId = displayInt(handle.counterId, digits = 3, flags = TextFromFloat.PAD_PRE_NONE)`
 *   `drawTextAnchored(labelId, cx, cy, 0f, 0f)`
 */
fun RemoteComposeContext.displayInt(
    valueId: Int,
    digits: Int = 1,
    flags: Int = TextFromFloat.PAD_PRE_NONE,
): Int = createTextFromFloat(
    value = WireTypes.asNan(valueId),
    digitsBefore = digits,
    digitsAfter = 0,
    flags = flags,
)
