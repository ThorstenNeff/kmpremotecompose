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

import com.tneff.kmpremotecompose.remote.core.operations.Operation
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
import com.tneff.kmpremotecompose.remote.core.operations.layout.VisibilityModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.WidthInModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.WidthModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.ZIndexModifier

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
 * (`remote-creation-core/.../modifiers/RecordingModifier.java`). Each modifier method appends an
 * `Operation` to [ops]; container helpers emit those ops in insertion order between the container
 * open op and the trailing `LayoutContent` marker, matching upstream's modifier-then-content
 * sequence (`RemoteComposeWriter.java:3275-3283`).
 *
 * `explicitComponentId` defaults to `-1` (sentinel) — caller can pin a stable id via
 * [componentId]. [spacedBy] is read by `column`/`row`/`flow`/collapsible variants.
 *
 * The full set of modifier methods (width/height/padding/background/border/clipRect/
 * roundedClipRect/visibility/zIndex/scroll/alignBy/click + widthIn/heightIn) lands with the
 * companion modifier-helpers file in the next REM-96 sub-commit.
 */
@RemoteComposeCreationDsl
class LayoutModifier {

    @PublishedApi internal var explicitComponentId: Int = -1
    @PublishedApi internal var spacedBy: Float = 0f
    @PublishedApi internal val ops: MutableList<Operation> = mutableListOf()

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
    // Each method appends an `Operation` to [ops]; the container's open helper writes them in
    // insertion order between Container-Open and LayoutContent. Mirror upstream RecordingModifier
    // method names + signatures (`RecordingModifier.java` + `RemoteComposeBuffer.addModifier*`).

    /** `MODIFIER_WIDTH` — dimension width. [value] is in dp for EXACT/EXACT_DP; ignored for FILL/WRAP/etc. */
    fun width(type: DimensionType, value: Number = 0f): LayoutModifier {
        ops.add(WidthModifier(type, value.toFloat()))
        return this
    }

    /** `MODIFIER_HEIGHT` — dimension height. */
    fun height(type: DimensionType, value: Number = 0f): LayoutModifier {
        ops.add(HeightModifier(type, value.toFloat()))
        return this
    }

    /** `MODIFIER_WIDTH_IN` — bracketed horizontal constraint. `-1f` = unconstrained. */
    fun widthIn(min: Number, max: Number): LayoutModifier {
        ops.add(WidthInModifier(min.toFloat(), max.toFloat()))
        return this
    }

    /** `MODIFIER_HEIGHT_IN` — bracketed vertical constraint. */
    fun heightIn(min: Number, max: Number): LayoutModifier {
        ops.add(HeightInModifier(min.toFloat(), max.toFloat()))
        return this
    }

    /** `MODIFIER_PADDING` — uniform padding on all four sides. */
    fun padding(all: Number): LayoutModifier {
        val v = all.toFloat()
        ops.add(PaddingModifier(v, v, v, v))
        return this
    }

    /** `MODIFIER_PADDING` — per-side. Mirrors upstream `padding(start, top, end, bottom)`. */
    fun padding(start: Number, top: Number, end: Number, bottom: Number): LayoutModifier {
        ops.add(PaddingModifier(start.toFloat(), top.toFloat(), end.toFloat(), bottom.toFloat()))
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
        ops.add(BackgroundModifier(0, 0, 0, 0, r, g, b, a, shape))
        return this
    }

    /**
     * `MODIFIER_BACKGROUND` — float-channel form. Mirrors upstream
     * `addModifierBackground(r, g, b, a, shape)` (`RemoteComposeBuffer.java:1777-1779`). Any of
     * `r/g/b/a` may carry NaN-encoded id refs (raw float bits preserved).
     */
    fun background(r: Number, g: Number, b: Number, a: Number, shape: Int = 0): LayoutModifier {
        ops.add(
            BackgroundModifier(
                0, 0, 0, 0,
                r.toFloat(), g.toFloat(), b.toFloat(), a.toFloat(), shape,
            ),
        )
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
        ops.add(
            BorderModifier(
                0, 0,
                if (useLegacy) 0 else 1,
                0,
                borderWidth.toFloat(), roundedCorner.toFloat(),
                r, g, b, a,
                shape,
            ),
        )
        return this
    }

    /** `MODIFIER_CLIP_RECT` — clip to the component's rectangular bounds (no operands). */
    fun clipRect(): LayoutModifier {
        ops.add(ClipRectModifier())
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
        ops.add(
            RoundedClipRectModifier(
                topStart.toFloat(), topEnd.toFloat(),
                bottomStart.toFloat(), bottomEnd.toFloat(),
            ),
        )
        return this
    }

    /**
     * `MODIFIER_VISIBILITY` — bind component visibility to a `RemoteInt` id ([valueId] = region-0
     * plain id). The component is invisible when the referenced int evaluates to zero.
     */
    fun visibility(valueId: Int): LayoutModifier {
        ops.add(VisibilityModifier(valueId))
        return this
    }

    /** `MODIFIER_ZINDEX` — float z-order; higher draws on top. [value] may be NaN-encoded id ref. */
    fun zIndex(value: Number): LayoutModifier {
        ops.add(ZIndexModifier(value.toFloat()))
        return this
    }

    /** `MODIFIER_CLICK` — mark component clickable (no operands; action wiring is out of scope). */
    fun click(): LayoutModifier {
        ops.add(ClickModifier())
        return this
    }

    /**
     * `MODIFIER_SCROLL` — scroll wrapper. [direction] is `ScrollModifier.HORIZONTAL`/`VERTICAL`
     * upstream-int. Defaults match upstream `addModifierScroll(direction, 0f)` (zero position,
     * zero max, zero notchMax). Pass [position]/[max]/[notchMax] for richer states.
     *
     * **Wire note:** upstream emits a trailing `ContainerEnd` after the `ScrollModifier` op
     * (`RemoteComposeBuffer.java:addModifierScroll`). REM-96 does NOT replicate that trailing
     * `ContainerEnd` here — corpus c_*.rc fixtures don't exercise scroll, so the wire shape is
     * pinned to the single-op form. The wrapped-container semantics ship with a dedicated scroll
     * story when needed.
     */
    fun scroll(
        direction: Int,
        position: Number = 0f,
        max: Number = 0f,
        notchMax: Number = 0f,
    ): LayoutModifier {
        ops.add(
            ScrollModifier(direction, position.toFloat(), max.toFloat(), notchMax.toFloat()),
        )
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
        ops.add(AlignByModifier(line.toFloat(), flags))
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
    for (op in modifier.ops) add(op)
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
    for (op in modifier.ops) add(op)
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
