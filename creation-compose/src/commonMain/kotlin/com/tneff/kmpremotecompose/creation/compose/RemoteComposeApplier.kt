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

import androidx.compose.runtime.AbstractApplier

/**
 * REM-128 — Compose `Applier` that records `@RemoteComposable` calls into a [RemoteComposeNode]
 * tree rooted at [root]. Insertion order = depth-first Compose source order; the W2 ID-order
 * invariant (TechSpec §2) follows from that as long as render-walk consumes children left-to-right
 * (see [RemoteComposeNode.renderChildren]).
 *
 * Bottom-up insertion (mirror upstream `RemoteComposeApplier.insertBottomUp`): children are
 * inserted as they are built, so by the time a parent is inserted its subtree is complete. This
 * matches how Compose Runtime drives a tree applier and avoids a partial-tree window during render.
 */
internal class RemoteComposeApplier(root: RemoteComposeNode) : AbstractApplier<RemoteComposeNode>(root) {

    override fun insertTopDown(index: Int, instance: RemoteComposeNode) {
        // Intentionally a no-op — we build bottom-up. Mirror upstream behaviour.
    }

    override fun insertBottomUp(index: Int, instance: RemoteComposeNode) {
        current.children.add(index, instance)
    }

    override fun remove(index: Int, count: Int) {
        current.children.subList(index, index + count).clear()
    }

    override fun move(from: Int, to: Int, count: Int) {
        // Standard list move — mirror what Compose's UI applier does for child reordering.
        val moved = ArrayList(current.children.subList(from, from + count))
        current.children.subList(from, from + count).clear()
        val destination = if (from < to) to - count else to
        current.children.addAll(destination, moved)
    }

    override fun onClear() {
        root.children.clear()
    }
}
