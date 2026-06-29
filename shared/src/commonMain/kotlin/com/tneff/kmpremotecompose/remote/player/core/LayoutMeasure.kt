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
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawContent
import com.tneff.kmpremotecompose.remote.core.operations.layout.AlignByModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.BackgroundModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.CanvasOperations
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
import com.tneff.kmpremotecompose.remote.core.operations.layout.ClipRectModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.RootLayout
import com.tneff.kmpremotecompose.remote.core.operations.layout.RowLayout
import com.tneff.kmpremotecompose.remote.core.operations.layout.ScrollModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.TextLayout
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

    /**
     * REM-108 S3b — a measured scroll viewport the paint walk brackets: clip the content holder
     * [contentHolderId] to the absolute viewport `[clipL,clipT,clipR,clipB]` and translate it by the
     * live [scroll] offset along [scroll]`.direction`. Measure-only (no wire/byte change). The clip is
     * applied only when the component carries a `ClipRectModifier` (upstream's viewport clip source).
     */
    internal class ScrollBracket(
        val contentHolderId: Int,
        val clipL: Float,
        val clipT: Float,
        val clipR: Float,
        val clipB: Float,
        val clip: Boolean,
        val scroll: ScrollModifier,
    )

    /**
     * REM-134 (a) — a measured TextLayout-span content holder the paint walk brackets: translate the
     * canvas to the span's absolute origin [x],[y] so the span's content (DrawContent text + decoration
     * DrawLines, both authored in span-LOCAL coords in the `.rc`) lands at the right place. Same transient
     * stack-scoped matrix pattern as [ScrollBracket]; `DrawLine`/`DrawContent` stay coordinate-agnostic
     * (the canvas moves, not the op). Keyed on the content holder ([contentHolderId]) like the scroll one.
     */
    internal class SpanBracket(val contentHolderId: Int, val x: Float, val y: Float)

    /** Result of [measure]: the scroll brackets (REM-108) + the TextLayout-span brackets (REM-134 a). */
    internal class MeasureResult(
        val scrollBrackets: Map<Int, ScrollBracket>,
        val spanBrackets: Map<Int, SpanBracket>,
    )

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

    private enum class Kind { ROOT, BOX, ROW, COLUMN, CONTENT, COMPONENT, TEXT, TEXT_LAYOUT, MODIFIER }

    private class Node(val componentId: Int, val kind: Kind, val startW: Float, val startH: Float) {
        var width: WidthModifier? = null
        var height: HeightModifier? = null
        var padding: PaddingModifier? = null
        // REM-37 c_text: a CoreText component's op + its measured text-bounds offset (left/top, for baseline).
        var coreText: CoreText? = null
        // REM-134: a TextLayout (LAYOUT_TEXT) span component + its nested DrawContent paint placeholder +
        // baseline-alignment modifier. The text id (shared by both TEXT/TEXT_LAYOUT for getTextBounds).
        var textLayout: TextLayout? = null
        var drawContent: DrawContent? = null
        var alignBy: AlignByModifier? = null
        var textId = 0
        var textLeft = 0f
        var textTop = 0f
        var background: BackgroundModifier? = null
        var border: BorderModifier? = null
        // REM-108 S3b: a component made scrollable (ScrollModifier) + whether it clips its viewport.
        var scroll: ScrollModifier? = null
        var hasClip = false
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
    fun measure(
        document: RemoteComposeDocument,
        surfaceW: Float,
        surfaceH: Float,
        context: RemoteContext,
    ): MeasureResult {
        // REM-37 c_text: pre-load DATA_TEXT so CoreText intrinsic measure (getTextBounds) sees the text —
        // mirrors upstream, which loads TextData at inflate (into mTextData) before any layout measure.
        for (op in document.operations) if (op is TextData) context.putText(op.id, op.text)
        val root = buildTree(document) ?: return MeasureResult(emptyMap(), emptyMap())
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

        // REM-108 S3b — scroll bounds publish + bracket map. For each scrollable component: contentDimension
        // = the children's aggregate extent along the scroll axis; viewport = the component's own measured
        // size along that axis; maxScroll = max(0, content − viewport). `applyScrollBounds` runs HERE (in the
        // measure phase, BEFORE the eval Phase-A) so the paired TouchExpression clamps against a fresh max
        // (TechSpec §5 sequencing — in delta-mode docs `max` is the TE's own clamp id). The returned brackets
        // are applied by the paint walk (clip viewport + translate by the live offset). Empty ⇒ no scroll doc.
        return MeasureResult(collectScrollBrackets(root, context), collectSpanBrackets(root))
    }

    /**
     * REM-134 (a) — build the TextLayout-span → content-holder bracket map. For each TextLayout span that
     * has a DrawContent placeholder, bracket its content holder (the CONTENT child) at the holder's
     * measured absolute origin, so the paint walk translates the canvas there and the span's local-coord
     * content (text + decoration) lands correctly. (DrawContent's draw origin is set LOCAL in [applyBounds].)
     */
    private fun collectSpanBrackets(root: Node): Map<Int, SpanBracket> {
        val out = HashMap<Int, SpanBracket>()
        fun visit(node: Node) {
            if (node.kind == Kind.TEXT_LAYOUT && node.drawContent != null) {
                val holder = node.children.firstOrNull { it.kind == Kind.CONTENT }
                if (holder != null) out[holder.componentId] = SpanBracket(holder.componentId, holder.x, holder.y)
            }
            for (c in node.children) visit(c)
        }
        visit(root)
        return out
    }

    /** Walk the tree, publish scroll bounds, and build the content-holder → [ScrollBracket] map. */
    private fun collectScrollBrackets(root: Node, context: RemoteContext): Map<Int, ScrollBracket> {
        val out = HashMap<Int, ScrollBracket>()
        fun visit(node: Node) {
            val scroll = node.scroll
            if (scroll != null) {
                val horizontal = scroll.direction == ScrollModifier.HORIZONTAL
                val contentDim = aggregate(node, horizontal)
                val viewport = if (horizontal) node.w else node.h
                val maxScroll = (contentDim - viewport).coerceAtLeast(0f)
                // §5: publish max/notchMax BEFORE Phase-A TE eval (we are in the measure phase).
                scroll.applyScrollBounds(context, maxScroll, contentDim)
                // Bracket the component's content holder (the transparent CONTENT child); fall back to the
                // component itself so a holder-less scroll component still clips+translates.
                val holderId = node.children.firstOrNull { it.kind == Kind.CONTENT }?.componentId
                    ?: node.componentId
                out[holderId] = ScrollBracket(
                    holderId, node.x, node.y, node.x + node.w, node.y + node.h, node.hasClip, scroll,
                )
            }
            for (c in node.children) visit(c)
        }
        visit(root)
        return out
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
                is CoreText -> open(Node(op.textId, Kind.TEXT, Float.NaN, Float.NaN).also { it.coreText = op; it.textId = op.textId })
                // REM-134: a TextLayout (LAYOUT_TEXT) span is a text-bearing CONTAINER (its own content slot
                // → CanvasOperations → DrawContent nests below). Sized intrinsically like CoreText; its
                // content slot inherits those bounds so a ComponentValue on it (underline/strike geometry)
                // resolves. componentId = the layout id; textId = the referenced DATA_TEXT.
                is TextLayout -> open(Node(op.componentId, Kind.TEXT_LAYOUT, Float.NaN, Float.NaN).also {
                    it.textLayout = op; it.textId = op.textId
                })
                // REM-134: baseline-alignment modifier on the current TextLayout span (line=NaN sentinel).
                is AlignByModifier -> stack.lastOrNull()?.let { if (it.alignBy == null) it.alignBy = op }
                // REM-134: CANVAS_OPERATIONS is a container (carries a CONTAINER_END). buildTree previously
                // ignored it → its END over-popped a real node. Push a transparent content node so the tree
                // balances (its draw-op children are ignored in measure anyway).
                is CanvasOperations -> open(Node(0, Kind.CONTENT, Float.NaN, Float.NaN))
                // REM-134: associate the DrawContent placeholder with its nearest enclosing TextLayout span
                // (the z-order-correct paint point), so the measure pass can wire the resolved text draw.
                is DrawContent -> stack.lastOrNull { it.kind == Kind.TEXT_LAYOUT }?.let { if (it.drawContent == null) it.drawContent = op }
                is WidthModifier -> stack.lastOrNull()?.let { if (it.width == null) it.width = op }
                is HeightModifier -> stack.lastOrNull()?.let { if (it.height == null) it.height = op }
                is PaddingModifier -> stack.lastOrNull()?.let { it.padding = op }
                is BackgroundModifier -> stack.lastOrNull()?.let { if (it.background == null) it.background = op }
                is BorderModifier -> stack.lastOrNull()?.let { if (it.border == null) it.border = op }
                is ClipRectModifier -> stack.lastOrNull()?.let { it.hasClip = true }
                // REM-108 S3b: ScrollModifier (upstream `ScrollModifierOperation extends ListActionsOperation`)
                // is a CONTAINER wrapping its paired TouchExpression — it carries its own CONTAINER_END. Attach
                // it to the current component, then push a stack-only sentinel (NOT added to children) so that
                // trailing CONTAINER_END pops the sentinel instead of prematurely closing the component.
                is ScrollModifier -> {
                    stack.lastOrNull()?.let { if (it.scroll == null) it.scroll = op }
                    stack.addLast(Node(0, Kind.MODIFIER, Float.NaN, Float.NaN))
                }
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
        if (node.kind == Kind.TEXT || node.kind == Kind.TEXT_LAYOUT) {
            // Intrinsic text size via the real text renderer (mirrors upstream CoreText.computeWrapSize).
            // getTextBounds fills [left, top, right, bottom]; node.textId is the DATA_TEXT id for both kinds.
            val pc = context.paintContext
            if (pc != null) {
                // Apply the TextStyle (esp. font size) before measuring so bounds match the draw.
                pc.savePaint()
                if (node.kind == Kind.TEXT) node.coreText?.applyStyle(context, pc)
                else node.textLayout?.applyStyle(context, pc)
                val b = FloatArray(4)
                pc.getTextBounds(node.textId, 0, -1, 0, b)
                pc.restorePaint()
                node.textLeft = b[0]; node.textTop = b[1]
                node.w = b[2] - b[0]; node.h = b[3] - b[1]
            }
            // REM-134: a TEXT_LAYOUT's content slot (LayoutContent child) inherits these bounds in
            // assignPositions (transparent-holder rule) → ComponentValue on it resolves. No child measure
            // needed (the slot holds only CanvasOperations → DrawContent). Both kinds are size-leaves here.
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

        // WRAP either when the modifier says so, or — REM-134 — when a flex container (Row/Column) has NO
        // modifier on that axis: Compose's default is wrap-content, not fill. (resolveDim defaulted a
        // missing modifier to parent-avail, which made attribute_string's height-less Rows each FILL the
        // column height → the 6 rows stacked off-screen, only line 1 visible.) An explicit FILL/EXACT is
        // untouched; only the no-modifier flex case flips to wrap.
        val flex = node.kind == Kind.ROW || node.kind == Kind.COLUMN
        if (node.width?.type == DimensionType.WRAP || (flex && node.width == null)) {
            node.w = aggregate(node, horizontal = true) + padW
        }
        if (node.height?.type == DimensionType.WRAP || (flex && node.height == null)) {
            node.h = aggregate(node, horizontal = false) + padH
        }
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
                // REM-134 S2: AlignBy(line=NaN) = align spans on a shared text baseline (AttributedString
                // rows). A span's ascent = −textTop (textTop = bounds.top, negative-above-baseline); the row
                // baseline = max ascent; each aligned span is pushed down so its baseline lands there:
                // localY = maxAscent − ascent = maxAscent + textTop. Non-aligned kids keep cross-axis align.
                // Only line=NaN is in the corpus; an explicit-line AlignBy falls back to cross-align (deferred).
                val maxAscent = kids.filter { isBaselineAligned(it) }.maxOfOrNull { -it.textTop } ?: 0f
                for (kid in kids) {
                    kid.localY = if (isBaselineAligned(kid)) maxAscent + kid.textTop
                        else crossAlign(node.verticalPositioning, ch, kid.h, vertical = true)
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

    /** REM-134: true if this span requests baseline alignment (AlignBy line=NaN — the corpus sentinel). */
    private fun isBaselineAligned(node: Node): Boolean = node.alignBy?.line?.isNaN() == true

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
        // REM-74 (FC-D1): also hand over the measured box (absolute top-left + size) so CoreText can wrap
        // multi-line text to the component width and position the complex layout. Single-line text ignores
        // this and keeps the baseline origin above (Bein-2: non-wrapping text stays pixel-identical).
        node.coreText?.setTextBox(node.x, node.y, node.w, node.h)
        // REM-134 (a): wire the span's DrawContent with its text draw origin in SPAN-LOCAL coords
        // (−textLeft, −textTop). The paint walk opens a [SpanBracket] that translates the canvas to the
        // content holder's absolute origin (= the span position), so local + translate = the correct
        // absolute baseline (node.y − textTop), while the span's decoration DrawLines (also local in the
        // .rc) ride the same bracket. (Pre-(a) this was absolute; the bracket now owns the offset.)
        val tl = node.textLayout
        if (tl != null) node.drawContent?.setTextContent(tl, node.textId, -node.textLeft, -node.textTop)
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
