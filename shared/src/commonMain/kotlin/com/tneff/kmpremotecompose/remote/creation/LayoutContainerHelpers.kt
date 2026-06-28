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
import com.tneff.kmpremotecompose.remote.core.operations.layout.BoxLayout
import com.tneff.kmpremotecompose.remote.core.operations.layout.CanvasContent
import com.tneff.kmpremotecompose.remote.core.operations.layout.CanvasLayout
import com.tneff.kmpremotecompose.remote.core.operations.layout.CollapsibleColumnLayout
import com.tneff.kmpremotecompose.remote.core.operations.layout.CollapsibleRowLayout
import com.tneff.kmpremotecompose.remote.core.operations.layout.ColumnLayout
import com.tneff.kmpremotecompose.remote.core.operations.layout.ContainerEnd
import com.tneff.kmpremotecompose.remote.core.operations.layout.FitBoxLayout
import com.tneff.kmpremotecompose.remote.core.operations.layout.FlowLayout
import com.tneff.kmpremotecompose.remote.core.operations.layout.LayoutContent
import com.tneff.kmpremotecompose.remote.core.operations.layout.RootLayout
import com.tneff.kmpremotecompose.remote.core.operations.layout.RowLayout
import com.tneff.kmpremotecompose.remote.core.operations.layout.StateLayout

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

    companion object {
        /** Default empty modifier — equivalent to upstream `Modifier`. */
        operator fun invoke(): LayoutModifier = LayoutModifier()
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
