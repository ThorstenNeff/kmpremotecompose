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

import com.tneff.kmpremotecompose.remote.core.operations.FloatExpression
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.wire.WireTypes

/**
 * REM-92 (E4) — RPN float-expression DSL.
 *
 * **The whole API.** A float expression is a postfix [FloatArray] handed to [floatExpression]; each
 * element is one of:
 *  - **literal** — any non-NaN [Float] (pushed onto the eval stack);
 *  - **variable ref** — a NaN-encoded id of a previously bound float (e.g. another
 *    `floatExpression(...)`'s return value, or a system-variable constant like [TIME_IN_SEC]);
 *  - **operator** — a NaN-encoded id `>= RcExpression.OFFSET` from the constants below.
 *
 * [floatExpression] allocates a region-0 id, emits the underlying `ANIMATED_FLOAT` op, and returns
 * the **NaN-encoded id** so the result can be embedded directly into other expressions, draw helpers,
 * or paint slots — exactly the chainable pattern upstream's `RemoteComposeWriter.floatExpression`
 * uses. The encoding contract (operator ids, system-var ids, NaN-bit layout) is verified against
 * `RpnFloatEvaluator` (see `RpnFloatEvaluator.OFFSET = 0x310_000`).
 *
 * **Closes 3/4 E5 fixtures (REM-92 byte-gate).** `procedure_look_up1` / `procedure_center_text1` /
 * `procedure_text_path_effects` all embed ANIMATED_FLOAT ids 46+ as variable refs into draw + paint
 * slots — emitting the same RPN sequences through [floatExpression] reproduces those slots
 * byte-for-byte. `gradient1` adds the PaintData gradient slot on top (see [RcPaint] gradient
 * builders).
 *
 * **id-allocation (E5 byte-contract).** [floatExpression] is **region-0 id-bearing** (pulls
 * [IdAllocator.nextId]); the returned NaN-id is the same id, just rebits-encoded. Re-use is by
 * passing the returned [Float] back as an operand — no second allocation.
 */
object RcExpression {

    /**
     * Operator-id base (upstream `AnimatedFloatExpression.OFFSET`). NaN-encoded ids `> OFFSET` are
     * decoded as operators by the player's evaluator; ids `<= OFFSET` are variable refs (resolved
     * against the [com.tneff.kmpremotecompose.remote.player.core.RemoteContext] float store).
     */
    const val OFFSET: Int = 0x310_000

    // Arithmetic.
    /** Pop `a`, `b` → push `b + a`. */
    val ADD: Float = WireTypes.asNan(OFFSET + 1)
    /** Pop `a`, `b` → push `b - a`. */
    val SUB: Float = WireTypes.asNan(OFFSET + 2)
    /** Pop `a`, `b` → push `b * a`. */
    val MUL: Float = WireTypes.asNan(OFFSET + 3)
    /** Pop `a`, `b` → push `b / a`. */
    val DIV: Float = WireTypes.asNan(OFFSET + 4)
    /** Pop `a`, `b` → push `b % a`. */
    val MOD: Float = WireTypes.asNan(OFFSET + 5)
    /** Pop `a`, `b` → push `min(b, a)`. */
    val MIN: Float = WireTypes.asNan(OFFSET + 6)
    /** Pop `a`, `b` → push `max(b, a)`. */
    val MAX: Float = WireTypes.asNan(OFFSET + 7)
    /** Pop `a`, `b` → push `b.pow(a)`. */
    val POW: Float = WireTypes.asNan(OFFSET + 8)

    // Unary numeric.
    val SQRT: Float = WireTypes.asNan(OFFSET + 9)
    val ABS: Float = WireTypes.asNan(OFFSET + 10)
    val SIGN: Float = WireTypes.asNan(OFFSET + 11)
    val EXP: Float = WireTypes.asNan(OFFSET + 13)
    val FLOOR: Float = WireTypes.asNan(OFFSET + 14)
    val LOG: Float = WireTypes.asNan(OFFSET + 15)
    val LN: Float = WireTypes.asNan(OFFSET + 16)
    val ROUND: Float = WireTypes.asNan(OFFSET + 17)

    // Trig (input in radians; combine with [DEG]/[RAD] for degree conversions).
    val SIN: Float = WireTypes.asNan(OFFSET + 18)
    val COS: Float = WireTypes.asNan(OFFSET + 19)

    /** Pop `hi`, `lo`, `x` → push `clamp(x, lo, hi)` (3-arg, upstream verbatim). */
    val CLAMP: Float = WireTypes.asNan(OFFSET + 27)

    /** Pop `x` (radians) → push `x * 180/π`. */
    val DEG: Float = WireTypes.asNan(OFFSET + 29)
    /** Pop `x` (degrees) → push `x * π/180`. */
    val RAD: Float = WireTypes.asNan(OFFSET + 30)

    // Array ops (operate on a FLOAT_LIST referenced by its array-id on the stack).
    /** Pop `index`, `arrayId` → push `array[index]`. */
    val A_DEREF: Float = WireTypes.asNan(OFFSET + 32)
    val A_MAX: Float = WireTypes.asNan(OFFSET + 33)
    val A_MIN: Float = WireTypes.asNan(OFFSET + 34)
    val A_SUM: Float = WireTypes.asNan(OFFSET + 35)
    val A_AVG: Float = WireTypes.asNan(OFFSET + 36)
    val A_LEN: Float = WireTypes.asNan(OFFSET + 37)

    // System-variable refs (region-0 system ids, NaN-encoded for embedding in float slots).
    /** Continuous animation seconds (player's frame clock). */
    val CONTINUOUS_SEC: Float = WireTypes.asNan(RemoteContext.ID_CONTINUOUS_SEC)
    /** Wall-clock seconds within the current minute. */
    val TIME_IN_SEC: Float = WireTypes.asNan(RemoteContext.ID_TIME_IN_SEC)
    /** Wall-clock minutes within the current hour. */
    val TIME_IN_MIN: Float = WireTypes.asNan(RemoteContext.ID_TIME_IN_MIN)
    /** Wall-clock hour of the day. */
    val TIME_IN_HR: Float = WireTypes.asNan(RemoteContext.ID_TIME_IN_HR)
    /** Render-viewport width in document units. */
    val WINDOW_WIDTH: Float = WireTypes.asNan(RemoteContext.ID_WINDOW_WIDTH)
    /** Render-viewport height in document units. */
    val WINDOW_HEIGHT: Float = WireTypes.asNan(RemoteContext.ID_WINDOW_HEIGHT)
    /** Player density. */
    val DENSITY: Float = WireTypes.asNan(RemoteContext.ID_DENSITY)
    /** Default font size. */
    val FONT_SIZE: Float = WireTypes.asNan(RemoteContext.ID_FONT_SIZE)
}

/**
 * `ANIMATED_FLOAT` — register an RPN [value] expression under a freshly allocated region-0 id and
 * return its **NaN-encoded id** so the result can be chained directly into other expressions or
 * draw helpers (mirrors upstream `RemoteComposeWriter.floatExpression`).
 *
 * Operands may be literal floats, NaN-encoded variable refs (`WireTypes.asNan(id)`, or one of the
 * [RcExpression] system-var constants like `RcExpression.TIME_IN_SEC`), or NaN-encoded operator ids
 * (the [RcExpression] operator constants).
 *
 * Example: `val centerX = floatExpression(RcExpression.WINDOW_WIDTH, 2f, RcExpression.DIV)` —
 * binds a new id to `windowWidth / 2`, then `drawCircle(centerX, 50f, 25f)` embeds it.
 */
fun RemoteComposeContext.floatExpression(vararg value: Float): Float {
    val id = ids.nextId()
    add(FloatExpression(id, value.copyOf()))
    return WireTypes.asNan(id)
}

/**
 * `ANIMATED_FLOAT` with an optional [animation] spec — the array-form overload. [value] is the RPN
 * expression; [animation] is the upstream `FloatAnimation` packing (deferred wiring on the player,
 * but the wire bytes round-trip). Returns the NaN-encoded id of the result, like [floatExpression].
 */
fun RemoteComposeContext.floatExpression(
    value: FloatArray,
    animation: FloatArray? = null,
): Float {
    val id = ids.nextId()
    add(FloatExpression(id, value, animation))
    return WireTypes.asNan(id)
}
