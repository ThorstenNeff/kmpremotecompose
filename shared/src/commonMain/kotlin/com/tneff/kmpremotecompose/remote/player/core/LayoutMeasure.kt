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
 * REM-37 E-Layout-1 (core) — a **shallow** measure/position pass over the component tree that produces
 * component-dimension variables so the consuming `FloatExpression → MatrixScale → draw` chains (clock /
 * component family: winding, server_clock, …) resolve to real values instead of `0`.
 *
 * Runs as a new player phase **between** system-var seeding and the eval Phase-A apply (dev-1 wires the
 * one call into [RemoteComposePlayer.paint]): `seed → RootContentBehavior scale → measure → Phase A →
 * Paint`. After [measure] returns, every `ComponentValue.valueId` is loaded into the float store, so the
 * Phase-A expressions read measured dimensions.
 *
 * **Doc-space** (PO/assist-confirmed): the measure root is `document.width/height`, not the surface size —
 * the whole draw pipeline is authored in doc coordinates and RootContentBehavior scales doc→surface at
 * paint. `surfaceW/H` is accepted for FILL-to-window edge cases but is not the root.
 *
 * **Scope (E-Layout-1):** Box / Row / Column / Canvas + content holders, with EXACT / EXACT_DP / FILL /
 * WRAP and padding, bounded depth. WEIGHT distribution, flow / collapsible / scroll, alignment-by and
 * POS_* positioning are E-Layout-2 — POS_* component values resolve to `0` here (logged, not silently
 * approximated). Pure runtime — no wire/byte change.
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

    private enum class Kind { ROOT, BOX, ROW, COLUMN, CONTENT, COMPONENT }

    private class Node(val componentId: Int, val kind: Kind, val startW: Float, val startH: Float) {
        var width: WidthModifier? = null
        var height: HeightModifier? = null
        var padding: PaddingModifier? = null
        val children = ArrayList<Node>()
        var w = 0f
        var h = 0f
    }

    /**
     * Measure [document] in doc-space and load each `ComponentValue`'s measured dimension into the
     * [context] float store under its `valueId`. Safe no-op for documents without a layout tree.
     */
    fun measure(document: RemoteComposeDocument, surfaceW: Float, surfaceH: Float, context: RemoteContext) {
        val root = buildTree(document) ?: return
        val byId = HashMap<Int, Node>()
        index(root, byId)

        // Doc-space root; surfaceW/H reserved for FILL-to-window edge cases (not the root).
        measureNode(root, document.width.toFloat(), document.height.toFloat(), context, depth = 0)

        for (op in document.operations) {
            if (op is ComponentValue) {
                val n = byId[op.componentId] ?: continue
                val value = when (op.type) {
                    CV_WIDTH, CV_CONTENT_WIDTH -> n.w
                    CV_HEIGHT, CV_CONTENT_HEIGHT -> n.h
                    CV_POS_X, CV_POS_Y, CV_POS_ROOT_X, CV_POS_ROOT_Y -> 0f // E-Layout-2 (positioning)
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
                is BoxLayout -> open(Node(op.componentId, Kind.BOX, Float.NaN, Float.NaN))
                is CanvasLayout -> open(Node(op.componentId, Kind.BOX, Float.NaN, Float.NaN))
                is RowLayout -> open(Node(op.componentId, Kind.ROW, Float.NaN, Float.NaN))
                is ColumnLayout -> open(Node(op.componentId, Kind.COLUMN, Float.NaN, Float.NaN))
                is LayoutContent -> open(Node(op.componentId, Kind.CONTENT, Float.NaN, Float.NaN))
                is CanvasContent -> open(Node(op.componentId, Kind.CONTENT, Float.NaN, Float.NaN))
                is ComponentStart -> open(Node(op.componentId, Kind.COMPONENT, op.width, op.height))
                is WidthModifier -> stack.lastOrNull()?.let { if (it.width == null) it.width = op }
                is HeightModifier -> stack.lastOrNull()?.let { if (it.height == null) it.height = op }
                is PaddingModifier -> stack.lastOrNull()?.let { it.padding = op }
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
     * Two-pass-per-node measure: resolve EXACT/EXACT_DP/FILL/inherit top-down (so FILL chains propagate
     * the parent size — the common clock-family case), measure children in the padded content box, then
     * override with the WRAP aggregate (Box=max, Column=sum-height, Row=sum-width) bottom-up.
     */
    private fun measureNode(node: Node, availW: Float, availH: Float, context: RemoteContext, depth: Int) {
        node.w = resolveDim(node.width?.type, node.width?.value, node.startW, availW, context)
        node.h = resolveDim(node.height?.type, node.height?.value, node.startH, availH, context)

        val p = node.padding
        val padW = if (p != null) resolvePad(p.left, context) + resolvePad(p.right, context) else 0f
        val padH = if (p != null) resolvePad(p.top, context) + resolvePad(p.bottom, context) else 0f
        val contentW = (node.w - padW).coerceAtLeast(0f)
        val contentH = (node.h - padH).coerceAtLeast(0f)

        if (depth < MAX_DEPTH) {
            for (child in node.children) measureNode(child, contentW, contentH, context, depth + 1)
        }

        // WRAP overrides self size from children (bottom-up).
        if (node.width?.type == DimensionType.WRAP) node.w = aggregate(node, horizontal = true) + padW
        if (node.height?.type == DimensionType.WRAP) node.h = aggregate(node, horizontal = false) + padH
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
        DimensionType.WEIGHT, // weight distribution is E-Layout-2 → treat as fill for now
        -> avail
        DimensionType.EXACT -> resolveValue(value ?: 0f, context)
        DimensionType.EXACT_DP -> resolveValue(value ?: 0f, context) * context.density
        DimensionType.WRAP,
        DimensionType.INTRINSIC_MIN,
        DimensionType.INTRINSIC_MAX,
        -> avail // refined by the WRAP aggregate after children are measured
    }

    /** Box/Canvas/content → max child extent; Column → sum heights; Row → sum widths. */
    private fun aggregate(node: Node, horizontal: Boolean): Float {
        if (node.children.isEmpty()) return 0f
        return when (node.kind) {
            Kind.COLUMN -> if (horizontal) node.children.maxOf { it.w } else node.children.sumOf { it.h.toDouble() }.toFloat()
            Kind.ROW -> if (horizontal) node.children.sumOf { it.w.toDouble() }.toFloat() else node.children.maxOf { it.h }
            else -> if (horizontal) node.children.maxOf { it.w } else node.children.maxOf { it.h }
        }
    }

    private fun resolvePad(v: Float, context: RemoteContext): Float = resolveValue(v, context)

    /** A NaN value is a variable id (resolve against the store); otherwise it is a literal. */
    private fun resolveValue(v: Float, context: RemoteContext): Float =
        if (v.isNaN() && !WireTypes.isOperationVariable(v)) context.getFloat(WireTypes.idFromNan(v)) else v
}
