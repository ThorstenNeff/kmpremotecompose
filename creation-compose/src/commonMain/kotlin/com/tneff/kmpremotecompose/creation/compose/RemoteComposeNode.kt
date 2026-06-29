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
package com.tneff.kmpremotecompose.creation.compose

import com.tneff.kmpremotecompose.remote.creation.RemoteComposeContext
import com.tneff.kmpremotecompose.remote.creation.drawOval

/**
 * REM-128 node-tree base for the Compose-creation applier. Each `@Composable` in the
 * `captureSingleRemoteDocument { ... }` scope contributes a [RemoteComposeNode] subtype to a tree
 * managed by [RemoteComposeApplier]. After composition (Phase A) the tree is render-walked
 * (Phase B) — each node's [render] calls the **already-byte-proven procedural helpers** on the
 * [RemoteComposeContext]. There is no separate op-emission here (TechSpec §0, W2 ID-order pin).
 *
 * **W1 structural guard (TechSpec §2):** nodes hold *no reference* to a [RemoteComposeContext]
 * during composition — the context is passed *only* into [render] at Phase B. Emission during
 * Phase A is therefore impossible by construction; the [currentBufferSize] runtime check in
 * `CaptureRemoteDocument` is belt-and-suspenders.
 */
internal abstract class RemoteComposeNode {
    val children: MutableList<RemoteComposeNode> = mutableListOf()

    /** Phase B: emit this node's contribution onto [context] using the procedural helpers. */
    abstract fun render(context: RemoteComposeContext)

    /** Depth-first child render in insertion (= Compose source) order — pins W2 op order. */
    protected fun renderChildren(context: RemoteComposeContext) {
        for (child in children) child.render(context)
    }
}

/**
 * Root of the node tree — the [RemoteComposeApplier]'s starting `current`. Owns nothing of its
 * own; [render] is a pass-through that walks children. The root never modifies the context
 * directly; container nodes (S2 slice) will open/close their own scoped emission.
 */
internal class RemoteRootNode : RemoteComposeNode() {
    override fun render(context: RemoteComposeContext) {
        renderChildren(context)
    }
}

/**
 * Records a `RemoteCanvas { drawOval(...) }` lambda at composition time; replays it onto a
 * [ContextBackedDrawScope] at render time. The scope's methods are **thin shims** to the
 * byte-proven procedural helpers — no independent emission (TechSpec §0 / W3).
 *
 * `drawIntent` is `var` so the applier's `update` block can swap it on recomposition without
 * rebuilding the node — Compose Runtime's standard `ComposeNode { factory; update }` shape.
 */
internal class RemoteCanvasNode(var drawIntent: RemoteDrawScope.() -> Unit) : RemoteComposeNode() {
    override fun render(context: RemoteComposeContext) {
        val scope = ContextBackedDrawScope(context)
        scope.drawIntent()
        // RemoteCanvas is a leaf in MVP — no children to walk. Container nodes land in S2.
    }

    private class ContextBackedDrawScope(private val context: RemoteComposeContext) : RemoteDrawScope {
        override fun drawOval(left: Number, top: Number, right: Number, bottom: Number) {
            context.drawOval(left, top, right, bottom)
        }
    }
}
