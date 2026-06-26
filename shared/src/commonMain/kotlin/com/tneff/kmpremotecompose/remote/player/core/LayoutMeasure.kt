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
import com.tneff.kmpremotecompose.remote.core.operations.ComponentValue
import com.tneff.kmpremotecompose.remote.core.operations.TextData
import com.tneff.kmpremotecompose.remote.core.operations.layout.BackgroundModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.CoreText
import com.tneff.kmpremotecompose.remote.core.operations.layout.BorderModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.BoxLayout
import com.tneff.kmpremotecompose.remote.core.operations.layout.ComponentStart
import com.tneff.kmpremotecompose.remote.core.operations.layout.CanvasContent
import com.tneff.kmpremotecompose.remote.core.operations.layout.CanvasLayout
import com.tneff.kmpremotecompose.remote.core.operations.layout.ColumnLayout
import com.tneff.kmpremotecompose.remote.core.operations.layout.ContainerEnd
import com.tneff.kmpremotecompose.remote.core.operations.layout.DimensionType
import com.tneff.kmpremotecompose.remote.core.operations.layout.HeightModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.LayoutContent
import com.tneff.kmpremotecompose.remote.core.operations.layout.PaddingModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.RootLayout
import com.tneff.kmpremotecompose.remote.core.operations.layout.RowLayout
import com.tneff.kmpremotecompose.remote.core.operations.layout.WidthModifier
import com.tneff.kmpremotecompose.remote.wire.WireTypes

/**
 * REM-37 E-Layout-1/2 — a **shallow** measure + position pass over the component tree.
 *
 * - **E-L1 (measure):** sizes each component (EXACT/EXACT_DP/FILL/WRAP/inherit) so the consuming
 *   `FloatExpression → MatrixScale → draw` chains read real component dims instead of `0`.
 * - **E-L2 (position + draw bounds):** assigns each component an **absolute** position (Box align,
 *   Column/Row stacking with arrangement + spacedBy), records `(x,y,w,h)` onto its Background/Border
 *   modifier ops so they can emit, and resolves POS_X/Y component values.
 *
 * Runs as a player phase **between** seeding and the eval Phase-A apply (dev-1 wires the call):
 * `seed → RootContentBehavior scale → measure → Phase A → Paint`. **Doc-space** (root =
 * document.width/height). The flat Phase-B walk emits component backgrounds/borders in their natural
 * op order (container → modifiers → content → children) which is already correct z-order, so the
 * modifiers draw at **absolute** bounds with no per-component canvas translate.
 *
 * **Scope:** Box / Row / Column / Canvas + content holders, EXACT/EXACT_DP/FILL/WRAP, padding,
 * background/border emission, alignment + arrangement + spacedBy, bounded depth. WEIGHT distribution,
 * flow / collapsible / scroll, align-by, graphics-layer = later. Pure runtime — no wire/byte change.
 */
internal object LayoutMeasure {

    private const val MAX_DEPTH = 32 // bounded-depth guard; real docs are 2–4 deep

    // ComponentValue.type wire constants (upstream ComponentValue): which dimension a value exposes.
    private const val CV_WIDTH = 0
    private const val CV_HEIGHT = 1
    private const val CV_POS_X = 2
    private const val CV_POS_Y = 3
    private const val CV_POS_ROOT_X = 4
    private const val CV_POS_ROOT_Y = 5
    private const val CV_CONTENT_WIDTH = 6
    private const val CV_CONTENT_HEIGHT = 7

    // Positioning / arrangement wire constants (upstream Box/Column/Row).
    private const val START = 1
    private const val CENTER = 2
    private const val END = 3
    private const val TOP = 4
    private const val BOTTOM = 5
    private const val SPACE_BETWEEN = 6
    private const val SPACE_EVENLY = 7
    private const val SPACE_AROUND = 8

    private enum class Kind { ROOT, BOX, ROW, COLUMN, CONTENT, COMPONENT, TEXT }

    private class Node(val componentId: Int, val kind: Kind, val startW: Float, val startH: Float) {
        var width: WidthModifier? = null
        var height: HeightModifier? = null
        var padding: PaddingModifier? = null
        // REM-37 c_text: a CoreText component's op + its measured text-bounds offset (left/top, for baseline).
        var coreText: CoreText? = null
        var textLeft = 0f
        var textTop = 0f
        var background: BackgroundModifier? = null
        var border: BorderModifier? = null
        var horizontalPositioning = 0
        var verticalPositioning = 0
        var spacedBy = 0f
        val children = ArrayList<Node>()
        var w = 0f
        var h = 0f
        var x = 0f // absolute
        var y = 0f
        var localX = 0f // relative to parent content box
        var localY = 0f
    }

    /**
     * Measure + position [document] in doc-space; record draw bounds on background/border modifiers and
     * load each `ComponentValue.valueId` (width/height/position) into the [context] float store. Safe
     * no-op for documents without a layout tree.
     */
    fun measure(document: RemoteComposeDocument, surfaceW: Float, surfaceH: Float, context: RemoteContext) {
        // REM-37 c_text: pre-load DATA_TEXT so CoreText intrinsic measure (getTextBounds) sees the text —
        // mirrors upstream, which loads TextData at inflate (into mTextData) before any layout measure.
        for (op in document.operations) if (op is TextData) context.putText(op.id, op.text)
        val root = buildTree(document) ?: return
        val byId = HashMap<Int, Node>()
        index(root, byId)

        // Doc-space root; surfaceW/H reserved for FILL-to-window edge cases (not the root).
        measureSizes(root, document.width.toFloat(), document.height.toFloat(), context, depth = 0)
        assignPositions(root, absX = 0f, absY = 0f, context = context, depth = 0)

        // Hand the measured absolute bounds to the emitting modifier ops (E-L2 draw emission).
        applyBounds(root)

        for (op in document.operations) {
            if (op is ComponentValue) {
                val n = byId[op.componentId] ?: continue
                val value = when (op.type) {
                    CV_WIDTH, CV_CONTENT_WIDTH -> n.w
                    CV_HEIGHT, CV_CONTENT_HEIGHT -> n.h
                    CV_POS_X -> n.localX
                    CV_POS_Y -> n.localY
                    CV_POS_ROOT_X -> n.x
                    CV_POS_ROOT_Y -> n.y
                    else -> continue
                }
                context.loadFloat(op.valueId, value)
            }
        }
    }

    /** Walk the flat op list into a shallow tree: layout/content ops push, [ContainerEnd] pops. */
    private fun buildTree(document: RemoteComposeDocument): Node? {
        var root: Node? = null
        val stack = ArrayDeque<Node>()
        fun open(node: Node) {
            stack.lastOrNull()?.children?.add(node)
            if (root == null) root = node
            stack.addLast(node)
        }
        for (op in document.operations) {
            when (op) {
                is RootLayout -> open(Node(op.componentId, Kind.ROOT, Float.NaN, Float.NaN))
                is BoxLayout -> open(Node(op.componentId, Kind.BOX, Float.NaN, Float.NaN).also {
                    it.horizontalPositioning = op.horizontalPositioning
                    it.verticalPositioning = op.verticalPositioning
                })
                is CanvasLayout -> open(Node(op.componentId, Kind.BOX, Float.NaN, Float.NaN))
                is RowLayout -> open(Node(op.componentId, Kind.ROW, Float.NaN, Float.NaN).also {
                    it.horizontalPositioning = op.horizontalPositioning
                    it.verticalPositioning = op.verticalPositioning
                    it.spacedBy = op.spacedBy
                })
                is ColumnLayout -> open(Node(op.componentId, Kind.COLUMN, Float.NaN, Float.NaN).also {
                    it.horizontalPositioning = op.horizontalPositioning
                    it.verticalPositioning = op.verticalPositioning
                    it.spacedBy = op.spacedBy
                })
                is LayoutContent -> open(Node(op.componentId, Kind.CONTENT, Float.NaN, Float.NaN))
                is CanvasContent -> open(Node(op.componentId, Kind.CONTENT, Float.NaN, Float.NaN))
                is ComponentStart -> open(Node(op.componentId, Kind.COMPONENT, op.width, op.height))
                is CoreText -> open(Node(op.textId, Kind.TEXT, Float.NaN, Float.NaN).also { it.coreText = op })
                is WidthModifier -> stack.lastOrNull()?.let { if (it.width == null) it.width = op }
                is HeightModifier -> stack.lastOrNull()?.let { if (it.height == null) it.height = op }
                is PaddingModifier -> stack.lastOrNull()?.let { it.padding = op }
                is BackgroundModifier -> stack.lastOrNull()?.let { if (it.background == null) it.background = op }
                is BorderModifier -> stack.lastOrNull()?.let { if (it.border == null) it.border = op }
                is ContainerEnd -> if (stack.isNotEmpty()) stack.removeLast()
                else -> {}
            }
        }
        return root
    }

    private fun index(node: Node, into: HashMap<Int, Node>) {
        into[node.componentId] = node
        for (c in node.children) index(c, into)
    }

    /**
     * Layout children, flattening **transparent** content holders ([Kind.CONTENT] — LayoutContent /
     * CanvasContent): the layout manager arranges the components inside its content holder as if direct.
     */
    private fun layoutChildren(node: Node): List<Node> =
        node.children.flatMap { if (it.kind == Kind.CONTENT) it.children else listOf(it) }

    /** Pass 1 — sizes: EXACT/EXACT_DP/FILL/inherit top-down; WRAP aggregate (over layout children) bottom-up. */
    private fun measureSizes(node: Node, availW: Float, availH: Float, context: RemoteContext, depth: Int) {
        if (node.kind == Kind.TEXT) {
            // Intrinsic text size via the real text renderer (mirrors upstream CoreText.computeWrapSize).
            // node.componentId == the CoreText.textId; getTextBounds fills [left, top, right, bottom].
            val pc = context.paintContext
            if (pc != null) {
                val b = FloatArray(4)
                pc.getTextBounds(node.componentId, 0, -1, 0, b)
                node.textLeft = b[0]; node.textTop = b[1]
                node.w = b[2] - b[0]; node.h = b[3] - b[1]
            }
            return // text size is intrinsic — ignore children / WRAP
        }
        node.w = resolveDim(node.width?.type, node.width?.value, node.startW, availW, context)
        node.h = resolveDim(node.height?.type, node.height?.value, node.startH, availH, context)

        val (padW, padH) = paddingWH(node, context)
        val contentW = (node.w - padW).coerceAtLeast(0f)
        val contentH = (node.h - padH).coerceAtLeast(0f)

        if (depth < MAX_DEPTH) {
            for (child in node.children) measureSizes(child, contentW, contentH, context, depth + 1)
        }

        if (node.width?.type == DimensionType.WRAP) node.w = aggregate(node, horizontal = true) + padW
        if (node.height?.type == DimensionType.WRAP) node.h = aggregate(node, horizontal = false) + padH
    }

    /** Pass 2 — positions: assign each node an absolute (x,y); arrange layout children; recurse. */
    private fun assignPositions(node: Node, absX: Float, absY: Float, context: RemoteContext, depth: Int) {
        node.x = absX
        node.y = absY
        val p = node.padding
        val padLeft = if (p != null) resolveValue(p.left, context) else 0f
        val padTop = if (p != null) resolveValue(p.top, context) else 0f
        val (padW, padH) = paddingWH(node, context)
        val contentX = absX + padLeft
        val contentY = absY + padTop
        val contentW = (node.w - padW).coerceAtLeast(0f)
        val contentH = (node.h - padH).coerceAtLeast(0f)

        // Transparent content holders inherit this content box (so a ComponentValue on them resolves).
        for (child in node.children) {
            if (child.kind == Kind.CONTENT) {
                child.x = contentX; child.y = contentY; child.w = contentW; child.h = contentH
            }
        }

        val kids = layoutChildren(node)
        if (kids.isNotEmpty()) arrange(node, kids, contentX, contentY, contentW, contentH, context)
        if (depth < MAX_DEPTH) {
            for (kid in kids) assignPositions(kid, kid.x, kid.y, context, depth + 1)
        }
    }

    /** Place [kids] inside the content box per the node's kind / positioning / arrangement / spacedBy. */
    private fun arrange(
        node: Node, kids: List<Node>, cx: Float, cy: Float, cw: Float, ch: Float, context: RemoteContext,
    ) {
        when (node.kind) {
            Kind.COLUMN -> {
                val spaced = resolveValue(node.spacedBy, context)
                val total = kids.sumOf { it.h.toDouble() }.toFloat() + spaced * (kids.size - 1)
                var y = cy + mainStart(node.verticalPositioning, ch, total)
                val gap = mainGap(node.verticalPositioning, ch, kids.sumOf { it.h.toDouble() }.toFloat(), kids.size)
                if (node.verticalPositioning == SPACE_EVENLY || node.verticalPositioning == SPACE_AROUND) y = cy + gap.first
                for (kid in kids) {
                    kid.localX = crossAlign(node.horizontalPositioning, cw, kid.w)
                    kid.localY = y - cy
                    kid.x = cx + kid.localX
                    kid.y = y
                    y += kid.h + spaced + gap.second
                }
            }
            Kind.ROW -> {
                val spaced = resolveValue(node.spacedBy, context)
                val total = kids.sumOf { it.w.toDouble() }.toFloat() + spaced * (kids.size - 1)
                var x = cx + mainStart(node.horizontalPositioning, cw, total)
                val gap = mainGap(node.horizontalPositioning, cw, kids.sumOf { it.w.toDouble() }.toFloat(), kids.size)
                if (node.horizontalPositioning == SPACE_EVENLY || node.horizontalPositioning == SPACE_AROUND) x = cx + gap.first
                for (kid in kids) {
                    kid.localY = crossAlign(node.verticalPositioning, ch, kid.h, vertical = true)
                    kid.localX = x - cx
                    kid.x = x
                    kid.y = cy + kid.localY
                    x += kid.w + spaced + gap.second
                }
            }
            else -> for (kid in kids) { // Box / Root / Component overlay: align each independently
                kid.localX = crossAlign(node.horizontalPositioning, cw, kid.w)
                kid.localY = crossAlign(node.verticalPositioning, ch, kid.h, vertical = true)
                kid.x = cx + kid.localX
                kid.y = cy + kid.localY
            }
        }
    }

    /** Start offset of the stacked block along the main axis (CENTER/END/SPACE_BETWEEN/TOP…). */
    private fun mainStart(pos: Int, avail: Float, total: Float): Float = when (pos) {
        CENTER -> (avail - total) / 2f
        END, BOTTOM -> avail - total
        else -> 0f
    }

    /** (leadingGap, betweenGap) for the SPACE_* arrangements; (0,0) otherwise. */
    private fun mainGap(pos: Int, avail: Float, contentTotal: Float, n: Int): Pair<Float, Float> = when (pos) {
        SPACE_BETWEEN -> if (n > 1) 0f to (avail - contentTotal) / (n - 1) else 0f to 0f
        SPACE_EVENLY -> ((avail - contentTotal) / (n + 1)).let { it to it }
        SPACE_AROUND -> ((avail - contentTotal) / n).let { (it / 2f) to it }
        else -> 0f to 0f
    }

    /** Cross-axis alignment of one child (START/CENTER/END or TOP/CENTER/BOTTOM). */
    private fun crossAlign(pos: Int, avail: Float, size: Float, vertical: Boolean = false): Float = when (pos) {
        CENTER -> (avail - size) / 2f
        END, BOTTOM -> avail - size
        else -> 0f
    }

    /** Record measured absolute bounds onto each node's background/border modifier ops (for emission). */
    private fun applyBounds(node: Node) {
        node.background?.setBounds(node.x, node.y, node.w, node.h)
        node.border?.setBounds(node.x, node.y, node.w, node.h)
        // REM-37 c_text: hand the CoreText its draw origin. Baseline = top − bounds.top (bounds.top is the
        // negative ascent), x = left − bounds.left — mirrors upstream mTextX=-bounds[0], mTextY=-bounds[1].
        node.coreText?.setTextDraw(node.x - node.textLeft, node.y - node.textTop)
        for (c in node.children) applyBounds(c)
    }

    private fun paddingWH(node: Node, context: RemoteContext): Pair<Float, Float> {
        val p = node.padding ?: return 0f to 0f
        return (resolveValue(p.left, context) + resolveValue(p.right, context)) to
            (resolveValue(p.top, context) + resolveValue(p.bottom, context))
    }

    /** Resolve one dimension: FILL/null → parent available; EXACT(_DP) → (resolved) value; WRAP → avail now. */
    private fun resolveDim(
        type: DimensionType?,
        value: Float?,
        startValue: Float,
        avail: Float,
        context: RemoteContext,
    ): Float = when (type) {
        null -> if (!startValue.isNaN()) resolveValue(startValue, context) else avail // inherit / ComponentStart
        DimensionType.FILL,
        DimensionType.FILL_PARENT_MAX_WIDTH,
        DimensionType.FILL_PARENT_MAX_HEIGHT,
        DimensionType.WEIGHT, // weight distribution is later → treat as fill for now
        -> avail
        DimensionType.EXACT -> resolveValue(value ?: 0f, context)
        DimensionType.EXACT_DP -> resolveValue(value ?: 0f, context) * context.density
        DimensionType.WRAP,
        DimensionType.INTRINSIC_MIN,
        DimensionType.INTRINSIC_MAX,
        -> avail // refined by the WRAP aggregate after children are measured
    }

    /** Column → sum heights / max width; Row → sum widths / max height; else → max child extent. */
    private fun aggregate(node: Node, horizontal: Boolean): Float {
        val kids = layoutChildren(node)
        if (kids.isEmpty()) return 0f
        return when (node.kind) {
            Kind.COLUMN -> if (horizontal) kids.maxOf { it.w } else kids.sumOf { it.h.toDouble() }.toFloat()
            Kind.ROW -> if (horizontal) kids.sumOf { it.w.toDouble() }.toFloat() else kids.maxOf { it.h }
            else -> if (horizontal) kids.maxOf { it.w } else kids.maxOf { it.h }
        }
    }

    /** A NaN value is a variable id (resolve against the store); otherwise it is a literal. */
    private fun resolveValue(v: Float, context: RemoteContext): Float =
        if (v.isNaN() && !WireTypes.isOperationVariable(v)) context.getFloat(WireTypes.idFromNan(v)) else v
}
