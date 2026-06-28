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

import com.tneff.kmpremotecompose.remote.core.operations.layout.AlignByModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.BackgroundModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.BorderModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.BoxLayout
import com.tneff.kmpremotecompose.remote.core.operations.layout.CanvasContent
import com.tneff.kmpremotecompose.remote.core.operations.layout.CanvasLayout
import com.tneff.kmpremotecompose.remote.core.operations.layout.ClickModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.ClipRectModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.CollapsibleColumnLayout
import com.tneff.kmpremotecompose.remote.core.operations.layout.CollapsibleRowLayout
import com.tneff.kmpremotecompose.remote.core.operations.layout.ColumnLayout
import com.tneff.kmpremotecompose.remote.core.operations.layout.ContainerEnd
import com.tneff.kmpremotecompose.remote.core.operations.layout.DimensionType
import com.tneff.kmpremotecompose.remote.core.operations.layout.FitBoxLayout
import com.tneff.kmpremotecompose.remote.core.operations.layout.FlowLayout
import com.tneff.kmpremotecompose.remote.core.operations.layout.HeightInModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.HeightModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.LayoutContent
import com.tneff.kmpremotecompose.remote.core.operations.layout.PaddingModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.RootLayout
import com.tneff.kmpremotecompose.remote.core.operations.layout.RoundedClipRectModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.RowLayout
import com.tneff.kmpremotecompose.remote.core.operations.layout.ScrollModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.StateLayout
import com.tneff.kmpremotecompose.remote.core.operations.layout.TouchExpression
import com.tneff.kmpremotecompose.remote.core.operations.layout.VisibilityModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.WidthInModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.WidthModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.ZIndexModifier
import com.tneff.kmpremotecompose.remote.wire.WireTypes

/**
 * REM-96 (FC-Layout-Container) — positioning constants for container open ops.
 *
 * Mirrors upstream `BoxLayout.START/CENTER/END/TOP/BOTTOM` (verified at
 * `remote-core/.../operations/layout/managers/BoxLayout.java:43-47`). Wire values are the int
 * literals below — same slot for `horizontalPositioning` and `verticalPositioning` (the caller
 * picks horizontal-meaningful + vertical-meaningful pairs, no type-system protection).
 *
 * Typical pairings (mirrors upstream usage):
 *  - `(POS_START, POS_TOP)` — top-start (e.g. `startBox(modifier, START, TOP)` `RemoteComposeWriter.java:3563`)
 *  - `(POS_CENTER, POS_CENTER)` — centered box (`RemoteComposeWriter.java:4057`)
 */

const val POS_START: Int = 1
const val POS_CENTER: Int = 2
const val POS_END: Int = 3
const val POS_TOP: Int = 4
const val POS_BOTTOM: Int = 5

/**
 * Modifier-list builder for container helpers — mirrors upstream `RecordingModifier`
 * (`remote-creation-core/.../modifiers/RecordingModifier.java`). Each modifier method appends a
 * **deferred emit lambda** to [emitters]; the container's open helper runs each lambda in
 * insertion order between the container open op and the trailing `LayoutContent` marker,
 * matching upstream's modifier-then-content sequence (`RemoteComposeWriter.java:3275-3283`).
 *
 * **Why lambdas instead of plain `Operation`s.** Most modifiers (`width`, `padding`,
 * `background`, ...) emit a single op with no allocator interaction. `scroll()` is special:
 * upstream's `addModifierScroll` allocates **two** ids via `reserveFloatVariable()` and emits a
 * three-op group (`MODIFIER_SCROLL` + `TOUCH_EXPRESSION` + `CONTAINER_END`); the allocation has
 * to happen at emit-time, against the active [RemoteComposeContext.ids]. Storing emitters as
 * lambdas lets a multi-op, allocator-touching modifier compose alongside the single-op ones in
 * one ordered list.
 *
 * `explicitComponentId` defaults to `-1` (sentinel) — caller can pin a stable id via
 * [componentId]. [spacedBy] is read by `column`/`row`/`flow`/collapsible variants.
 */
@RemoteComposeCreationDsl
class LayoutModifier {

    @PublishedApi internal var explicitComponentId: Int = -1
    @PublishedApi internal var spacedBy: Float = 0f

    /**
     * Deferred emit pipeline. Each lambda runs against the active [RemoteComposeContext] inside
     * the container helper's modifier loop. Single-op modifiers wrap a one-line `add(...)`;
     * multi-op modifiers (`scroll`) interact with `ctx.ids` and `ctx.add` directly.
     */
    @PublishedApi internal val emitters: MutableList<(RemoteComposeContext) -> Unit> = mutableListOf()

    /** Pin a stable [id] for the container's `componentId` slot. Default is `-1` sentinel. */
    fun componentId(id: Int): LayoutModifier {
        explicitComponentId = id
        return this
    }

    /** Set `spacedBy` (column/row/flow/collapsible variants). [value] may be NaN-encoded as `asNan(id)`. */
    fun spacedBy(value: Number): LayoutModifier {
        spacedBy = value.toFloat()
        return this
    }

    // ── 14 core modifiers (REM-96) ───────────────────────────────────────────
    // Each method appends an emit-time lambda; the container's open helper runs them in
    // insertion order between Container-Open and LayoutContent. Mirror upstream RecordingModifier
    // method names + signatures (`RecordingModifier.java` + `RemoteComposeBuffer.addModifier*`).

    /** `MODIFIER_WIDTH` — dimension width. [value] is in dp for EXACT/EXACT_DP; ignored for FILL/WRAP/etc. */
    fun width(type: DimensionType, value: Number = 0f): LayoutModifier {
        val v = value.toFloat()
        emitters.add { ctx -> ctx.add(WidthModifier(type, v)) }
        return this
    }

    /** `MODIFIER_HEIGHT` — dimension height. */
    fun height(type: DimensionType, value: Number = 0f): LayoutModifier {
        val v = value.toFloat()
        emitters.add { ctx -> ctx.add(HeightModifier(type, v)) }
        return this
    }

    /** `MODIFIER_WIDTH_IN` — bracketed horizontal constraint. `-1f` = unconstrained. */
    fun widthIn(min: Number, max: Number): LayoutModifier {
        val mn = min.toFloat(); val mx = max.toFloat()
        emitters.add { ctx -> ctx.add(WidthInModifier(mn, mx)) }
        return this
    }

    /** `MODIFIER_HEIGHT_IN` — bracketed vertical constraint. */
    fun heightIn(min: Number, max: Number): LayoutModifier {
        val mn = min.toFloat(); val mx = max.toFloat()
        emitters.add { ctx -> ctx.add(HeightInModifier(mn, mx)) }
        return this
    }

    /** `MODIFIER_PADDING` — uniform padding on all four sides. */
    fun padding(all: Number): LayoutModifier {
        val v = all.toFloat()
        emitters.add { ctx -> ctx.add(PaddingModifier(v, v, v, v)) }
        return this
    }

    /** `MODIFIER_PADDING` — per-side. Mirrors upstream `padding(start, top, end, bottom)`. */
    fun padding(start: Number, top: Number, end: Number, bottom: Number): LayoutModifier {
        val s = start.toFloat(); val t = top.toFloat()
        val e = end.toFloat(); val b = bottom.toFloat()
        emitters.add { ctx -> ctx.add(PaddingModifier(s, t, e, b)) }
        return this
    }

    /**
     * `MODIFIER_BACKGROUND` — solid background from ARGB int. Mirrors upstream
     * `addModifierBackground(int color, int shape)` (`RemoteComposeBuffer.java:1740-1746`):
     * decompose ARGB into floats, emit with `flags=0, colorId=0, reserve1=0, reserve2=0`.
     */
    fun background(color: Int, shape: Int = 0): LayoutModifier {
        val a = (color ushr 24 and 0xff) / 255f
        val r = (color ushr 16 and 0xff) / 255f
        val g = (color ushr 8 and 0xff) / 255f
        val b = (color and 0xff) / 255f
        emitters.add { ctx -> ctx.add(BackgroundModifier(0, 0, 0, 0, r, g, b, a, shape)) }
        return this
    }

    /**
     * `MODIFIER_BACKGROUND` — float-channel form. Mirrors upstream
     * `addModifierBackground(r, g, b, a, shape)` (`RemoteComposeBuffer.java:1777-1779`). Any of
     * `r/g/b/a` may carry NaN-encoded id refs (raw float bits preserved).
     */
    fun background(r: Number, g: Number, b: Number, a: Number, shape: Int = 0): LayoutModifier {
        val rf = r.toFloat(); val gf = g.toFloat(); val bf = b.toFloat(); val af = a.toFloat()
        emitters.add { ctx ->
            ctx.add(BackgroundModifier(0, 0, 0, 0, rf, gf, bf, af, shape))
        }
        return this
    }

    /**
     * `MODIFIER_BORDER` — solid border (ARGB int form). Mirrors upstream
     * `addModifierBorder(borderWidth, borderRoundedCorner, color, shape)`
     * (`RemoteComposeBuffer.java:1794-1815`): decompose ARGB; default `useLegacy=true` writes
     * `reserve1=0`. Pass `useLegacy=false` to write `reserve1=1` (non-legacy border drawing).
     */
    fun border(
        borderWidth: Number,
        roundedCorner: Number,
        color: Int,
        shape: Int = 0,
        useLegacy: Boolean = true,
    ): LayoutModifier {
        val a = (color ushr 24 and 0xff) / 255f
        val r = (color ushr 16 and 0xff) / 255f
        val g = (color ushr 8 and 0xff) / 255f
        val b = (color and 0xff) / 255f
        val bw = borderWidth.toFloat(); val rc = roundedCorner.toFloat()
        val res1 = if (useLegacy) 0 else 1
        emitters.add { ctx ->
            ctx.add(BorderModifier(0, 0, res1, 0, bw, rc, r, g, b, a, shape))
        }
        return this
    }

    /** `MODIFIER_CLIP_RECT` — clip to the component's rectangular bounds (no operands). */
    fun clipRect(): LayoutModifier {
        emitters.add { ctx -> ctx.add(ClipRectModifier()) }
        return this
    }

    /**
     * `MODIFIER_ROUNDED_CLIP_RECT` — rounded-rect clip. Corner radii are per-corner floats; any may
     * be NaN-encoded id refs.
     */
    fun roundedClipRect(
        topStart: Number,
        topEnd: Number,
        bottomStart: Number,
        bottomEnd: Number,
    ): LayoutModifier {
        val ts = topStart.toFloat(); val te = topEnd.toFloat()
        val bs = bottomStart.toFloat(); val be = bottomEnd.toFloat()
        emitters.add { ctx -> ctx.add(RoundedClipRectModifier(ts, te, bs, be)) }
        return this
    }

    /**
     * `MODIFIER_VISIBILITY` — bind component visibility to a `RemoteInt` id ([valueId] = region-0
     * plain id). The component is invisible when the referenced int evaluates to zero.
     */
    fun visibility(valueId: Int): LayoutModifier {
        emitters.add { ctx -> ctx.add(VisibilityModifier(valueId)) }
        return this
    }

    /** `MODIFIER_ZINDEX` — float z-order; higher draws on top. [value] may be NaN-encoded id ref. */
    fun zIndex(value: Number): LayoutModifier {
        val v = value.toFloat()
        emitters.add { ctx -> ctx.add(ZIndexModifier(v)) }
        return this
    }

    /** `MODIFIER_CLICK` — mark component clickable (no operands; action wiring is out of scope). */
    fun click(): LayoutModifier {
        emitters.add { ctx -> ctx.add(ClickModifier()) }
        return this
    }

    /**
     * `MODIFIER_SCROLL` — scroll wrapper. Emits the **full upstream op group** (verified against
     * `c_modifier_vertical_scroll.rc` + `c_modifier_horizontal_scroll.rc` corpus fixtures —
     * NOT just MODIFIER_SCROLL).
     *
     * **Wire group, mirror `RemoteComposeWriter.addModifierScroll(direction, positionId)`
     * (`RemoteComposeWriter.java:3670-3691`):**
     * 1. Reserve 3 region-0 plain ids via `ids.nextId()` (mirror `reserveFloatVariable()` at
     *    `RemoteComposeWriter.java:1953-1956` — pure id allocation, **no op emitted**):
     *    `positionId`, `maxId`, `notchMaxId`.
     * 2. `ScrollModifier(direction, asNan(positionId), asNan(maxId), asNan(notchMaxId))` — all
     *    three slots are NaN-encoded id-refs into the reserved plain pool.
     * 3. `TouchExpression(positionId, value=0f, min=0f, max=asNan(maxId), velocityId=0f,
     *    touchEffects=3, exp=[touchDirection, -1f, MUL], stopLogic=STOP_GENTLY<<16, stops=[],
     *    easing=[])`. `touchDirection` is `asNan(ID_TOUCH_POS_X=13)` for horizontal,
     *    `asNan(ID_TOUCH_POS_Y=14)` for vertical (mirror `RemoteContext.FLOAT_TOUCH_POS_X/Y`
     *    at `RemoteContext.java:840-841,922-925`). `MUL` is the RPN multiply marker
     *    (`asNan(0x310003)` per [RcExpression.OFFSET]).
     * 4. **`ContainerEnd`** — closes the `ListActionsOperation` scope opened by `MODIFIER_SCROLL`
     *    (`ScrollModifierOperation extends ListActionsOperation` — without the trailing End
     *    the following modifier / `LayoutContent` ops would be sucked into the scroll-action
     *    list, corrupting the doc tree).
     *
     * [direction] is [SCROLL_VERTICAL] (0) or [SCROLL_HORIZONTAL] (1) — verified against
     * `ScrollModifierOperation.java:245,257,276`. **Scope:** REM-96 emits the byte-faithful op
     * group only; live touch handling at render time is Epic-F.
     */
    fun scroll(direction: Int): LayoutModifier {
        emitters.add { ctx ->
            val positionId = ctx.ids.nextId()
            val maxId = ctx.ids.nextId()
            val notchMaxId = ctx.ids.nextId()
            ctx.add(
                ScrollModifier(
                    direction,
                    WireTypes.asNan(positionId),
                    WireTypes.asNan(maxId),
                    WireTypes.asNan(notchMaxId),
                ),
            )
            val touchDirection = if (direction != SCROLL_VERTICAL) {
                WireTypes.asNan(ID_TOUCH_POS_X)
            } else {
                WireTypes.asNan(ID_TOUCH_POS_Y)
            }
            ctx.add(
                TouchExpression(
                    id = positionId,
                    value = 0f,
                    min = 0f,
                    max = WireTypes.asNan(maxId),
                    velocityId = 0f,
                    touchEffects = 3,
                    exp = floatArrayOf(touchDirection, -1f, RcExpression.MUL),
                    stopLogic = TOUCH_STOP_GENTLY shl 16,
                    stops = floatArrayOf(),
                    easing = floatArrayOf(),
                ),
            )
            ctx.add(ContainerEnd())
        }
        return this
    }

    /**
     * `MODIFIER_ALIGN_BY` — align by a baseline / arbitrary line. Mirrors upstream
     * `addModifierAlignBy(line)` (`RemoteComposeBuffer.java:1782-1784`) with `flags=0`. Pass an
     * explicit [flags] int for non-default alignment metadata.
     *
     * **Profile-gated:** `MODIFIER_ALIGN_BY` (and `LAYOUT_FLOW`, `LAYOUT_COMPUTE`,
     * `MODIFIER_DIMENSION_CONSTRAINTS`, ...) live in the AndroidX-experimental overlay
     * (`Operations.kt:413-417`) and are rejected by baseline/flat-form documents — append this
     * modifier inside a map-form (api 7) document opened with
     * `PROFILE_ANDROIDX | PROFILE_EXPERIMENTAL` (see `LayoutModifierByteTest.alignBy_*`).
     */
    fun alignBy(line: Number, flags: Int = 0): LayoutModifier {
        val l = line.toFloat()
        emitters.add { ctx -> ctx.add(AlignByModifier(l, flags)) }
        return this
    }

    companion object {
        /** Default empty modifier — equivalent to upstream `Modifier`. */
        operator fun invoke(): LayoutModifier = LayoutModifier()

        /**
         * `MODIFIER_SCROLL.direction` constants — verified against upstream
         * `ScrollModifierOperation.java:245,257,276`: **0=VERTICAL, 1=HORIZONTAL** (NOT the
         * intuitive ordering — pin it explicitly here so callers can't mis-read).
         */
        const val SCROLL_VERTICAL: Int = 0
        const val SCROLL_HORIZONTAL: Int = 1

        /**
         * System-variable ids for touch position. Mirror upstream
         * `RemoteContext.ID_TOUCH_POS_X/Y` (`RemoteContext.java:840-841`). Used in the
         * `scroll()` group as `asNan(ID_TOUCH_POS_*)` operands of the trailing TouchExpression.
         */
        const val ID_TOUCH_POS_X: Int = 13
        const val ID_TOUCH_POS_Y: Int = 14

        /**
         * `TouchExpression.STOP_GENTLY` — `TouchExpression.java:92` (`public static final int
         * STOP_GENTLY = 0;`). Packed into the high 16 bits of TouchExpression's `stopLogic`
         * wire int.
         */
        const val TOUCH_STOP_GENTLY: Int = 0
    }
}

/**
 * Internal helper: emit a container's open op (via [emitOpen]), then the modifier ops, then the
 * `LayoutContent` marker, run [block], and emit two `ContainerEnd` ops in `finally`. Mirrors
 * upstream `startX(...)` + `endX()` semantics (`RemoteComposeWriter.java` lines per container).
 *
 * The negative-counter is advanced via [RemoteComposeContext.resolveComponentId] — once for the
 * container's own componentId (only if user didn't pin one), once for the `LayoutContent` marker
 * (always, since upstream `addContentStart()` hardcodes `-1` input).
 */
@PublishedApi
internal inline fun RemoteComposeContext.standardContainer(
    modifier: LayoutModifier,
    emitOpen: (componentId: Int) -> Unit,
    block: RemoteComposeContext.() -> Unit,
) {
    val componentId = resolveComponentId(modifier.explicitComponentId)
    emitOpen(componentId)
    for (e in modifier.emitters) e(this)
    add(LayoutContent(resolveComponentId(-1)))
    try {
        block()
    } finally {
        add(ContainerEnd())
        add(ContainerEnd())
    }
}

/** `LAYOUT_BOX` container (`startBox`/`endBox`). Default positioning = `POS_START`/`POS_TOP`. */
inline fun RemoteComposeContext.box(
    modifier: LayoutModifier = LayoutModifier(),
    horizontal: Int = POS_START,
    vertical: Int = POS_TOP,
    block: RemoteComposeContext.() -> Unit,
) {
    standardContainer(modifier, { id -> add(BoxLayout(id, -1, horizontal, vertical)) }, block)
}

/** `LAYOUT_FIT_BOX` container (`startFitBox`/`endFitBox`). */
inline fun RemoteComposeContext.fitBox(
    modifier: LayoutModifier = LayoutModifier(),
    horizontal: Int = POS_START,
    vertical: Int = POS_TOP,
    block: RemoteComposeContext.() -> Unit,
) {
    standardContainer(modifier, { id -> add(FitBoxLayout(id, -1, horizontal, vertical)) }, block)
}

/** `LAYOUT_COLUMN` container (`startColumn`/`endColumn`). Carries `modifier.spacedBy`. */
inline fun RemoteComposeContext.column(
    modifier: LayoutModifier = LayoutModifier(),
    horizontal: Int = POS_START,
    vertical: Int = POS_TOP,
    block: RemoteComposeContext.() -> Unit,
) {
    standardContainer(
        modifier,
        { id -> add(ColumnLayout(id, -1, horizontal, vertical, modifier.spacedBy)) },
        block,
    )
}

/** `LAYOUT_ROW` container (`startRow`/`endRow`). Carries `modifier.spacedBy`. */
inline fun RemoteComposeContext.row(
    modifier: LayoutModifier = LayoutModifier(),
    horizontal: Int = POS_START,
    vertical: Int = POS_TOP,
    block: RemoteComposeContext.() -> Unit,
) {
    standardContainer(
        modifier,
        { id -> add(RowLayout(id, -1, horizontal, vertical, modifier.spacedBy)) },
        block,
    )
}

/** `LAYOUT_COLLAPSIBLE_COLUMN` container (`startCollapsibleColumn`/`endCollapsibleColumn`). */
inline fun RemoteComposeContext.collapsibleColumn(
    modifier: LayoutModifier = LayoutModifier(),
    horizontal: Int = POS_START,
    vertical: Int = POS_TOP,
    block: RemoteComposeContext.() -> Unit,
) {
    standardContainer(
        modifier,
        { id -> add(CollapsibleColumnLayout(id, -1, horizontal, vertical, modifier.spacedBy)) },
        block,
    )
}

/** `LAYOUT_COLLAPSIBLE_ROW` container (`startCollapsibleRow`/`endCollapsibleRow`). */
inline fun RemoteComposeContext.collapsibleRow(
    modifier: LayoutModifier = LayoutModifier(),
    horizontal: Int = POS_START,
    vertical: Int = POS_TOP,
    block: RemoteComposeContext.() -> Unit,
) {
    standardContainer(
        modifier,
        { id -> add(CollapsibleRowLayout(id, -1, horizontal, vertical, modifier.spacedBy)) },
        block,
    )
}

/**
 * `LAYOUT_FLOW` container (`startFlow`/`endFlow`). Defaults [maxItemsInEachRow] + [maxLines] to
 * `Int.MAX_VALUE`, mirroring upstream's 3-arg overload (`RemoteComposeWriter.java:3443-3445`).
 */
inline fun RemoteComposeContext.flow(
    modifier: LayoutModifier = LayoutModifier(),
    horizontal: Int = POS_START,
    vertical: Int = POS_TOP,
    maxItemsInEachRow: Int = Int.MAX_VALUE,
    maxLines: Int = Int.MAX_VALUE,
    block: RemoteComposeContext.() -> Unit,
) {
    standardContainer(
        modifier,
        { id ->
            add(
                FlowLayout(
                    id, -1, horizontal, vertical,
                    modifier.spacedBy, maxItemsInEachRow, maxLines,
                ),
            )
        },
        block,
    )
}

/** `LAYOUT_STATE` container (`startStateLayout`/`endStateLayout`). [indexId] is a region-0 id ref. */
inline fun RemoteComposeContext.state(
    indexId: Int,
    modifier: LayoutModifier = LayoutModifier(),
    horizontal: Int = POS_START,
    vertical: Int = POS_TOP,
    block: RemoteComposeContext.() -> Unit,
) {
    standardContainer(
        modifier,
        { id -> add(StateLayout(id, -1, horizontal, vertical, indexId)) },
        block,
    )
}

/**
 * `LAYOUT_CANVAS` container (`startCanvas`/`endCanvas`).
 *
 * **API-level Sonderfall (`RemoteComposeWriter.java:3495-3506`):** when the writer's `apiLevel <= 7`,
 * an extra `LAYOUT_CANVAS_CONTENT` op is emitted between the `LayoutContent` marker and the
 * children, and a third `ContainerEnd` is emitted at close — yielding the sequence
 *  `CanvasLayout + modifiers + LayoutContent + CanvasContent + <children> + 3 × ContainerEnd`.
 * At `apiLevel > 7` the canvas degenerates to the Box-shape (`+ 2 × ContainerEnd`).
 */
inline fun RemoteComposeContext.canvas(
    modifier: LayoutModifier = LayoutModifier(),
    block: RemoteComposeContext.() -> Unit,
) {
    val componentId = resolveComponentId(modifier.explicitComponentId)
    add(CanvasLayout(componentId, -1))
    for (e in modifier.emitters) e(this)
    add(LayoutContent(resolveComponentId(-1)))
    val emitCanvasContent = writer.apiLevel <= 7
    if (emitCanvasContent) add(CanvasContent(resolveComponentId(-1)))
    try {
        block()
    } finally {
        if (emitCanvasContent) add(ContainerEnd())
        add(ContainerEnd())
        add(ContainerEnd())
    }
}

/**
 * `LAYOUT_ROOT` container (`startRoot`/`endRoot`). Singleton at the top of the body — emits only
 * the `RootLayout` open op (`addRootStart()` in upstream takes no args, but our op does — it
 * resolves via the negative counter just like other auto-generated ids) and a single
 * `ContainerEnd` on close (`RemoteComposeWriter.java:3350-3359`). No modifiers, no `LayoutContent`.
 */
inline fun RemoteComposeContext.root(block: RemoteComposeContext.() -> Unit) {
    add(RootLayout(resolveComponentId(-1)))
    try {
        block()
    } finally {
        add(ContainerEnd())
    }
}
