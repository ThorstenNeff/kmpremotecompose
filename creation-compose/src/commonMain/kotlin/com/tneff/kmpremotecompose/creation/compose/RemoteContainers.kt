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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import com.tneff.kmpremotecompose.remote.creation.LayoutModifier
import com.tneff.kmpremotecompose.remote.creation.POS_CENTER
import com.tneff.kmpremotecompose.remote.creation.POS_START
import com.tneff.kmpremotecompose.remote.creation.POS_TOP
import com.tneff.kmpremotecompose.remote.creation.RemoteComposeContext
import com.tneff.kmpremotecompose.remote.creation.box
import com.tneff.kmpremotecompose.remote.creation.boxLeaf
import com.tneff.kmpremotecompose.remote.creation.column
import com.tneff.kmpremotecompose.remote.creation.root
import com.tneff.kmpremotecompose.remote.creation.row

/**
 * REM-128 S2 — layout-container `@Composable`s for the Compose-creation surface. Each composable
 * produces a node whose Phase-B render delegates to the **byte-proven** REM-96 procedural helper
 * (`root()` / `box()` / `boxLeaf()` / `column()` / `row()`) — no new op emission. The W2 op-order
 * invariant follows by construction (TechSpec §0 / §2 / §6).
 *
 * **Childless-Box routing (REM-96-followup `0ed918d`):** corpus inner-Boxes use the upstream
 * lazy-LAYOUT_CONTENT pattern (single `ContainerEnd`, no `LayoutContent`). `RemoteBox` therefore
 * picks `boxLeaf()` at render-time when its node has no `@Composable` children, and the standard
 * `box()` (LAYOUT_CONTENT + dual end) otherwise. This is the routing that closes Triple-Pin
 * Stage-2 against `c_box.rc` (RemoteBox-only) and `c_column.rc` / `c_row.rc` (RemoteColumn /
 * RemoteRow with 3 childless inner RemoteBoxes).
 *
 * **Leaf-Column / leaf-Row deferred (D1/D5).** PO scope-cut for MVP: corpus has only Box-leaves;
 * if/when a corpus fixture surfaces a childless Column or Row, the same routing pattern (and a
 * `columnLeaf()` / `rowLeaf()` REM-96-followup) lands then.
 *
 * **MVP modifier API:** the [LayoutModifier] from REM-96 is passed through directly. The S3
 * slice will introduce a Compose-idiomatic modifier surface; for now the procedural type is the
 * common ground that enables the Triple-Pin without scope-creeping S3 into S2.
 */

internal class RemoteBoxNode(
    var modifier: LayoutModifier,
    var horizontal: Int,
    var vertical: Int,
) : RemoteComposeNode() {
    override fun render(context: RemoteComposeContext) {
        if (children.isEmpty()) {
            // Default leaf positioning per REM-96-followup is POS_CENTER/POS_CENTER (mirror
            // upstream's no-content overload default at RemoteComposeWriter.java:4061). The
            // RemoteBox composable's *own* defaults are also POS_CENTER/POS_CENTER for this
            // reason — so a `RemoteBox {}` byte-matches `c_box.rc` without any explicit pos
            // argument. Callers wanting POS_START/POS_TOP (the with-children default) pass
            // them explicitly.
            context.boxLeaf(modifier = modifier, horizontal = horizontal, vertical = vertical)
        } else {
            context.box(modifier = modifier, horizontal = horizontal, vertical = vertical) {
                renderChildren(this)
            }
        }
    }
}

internal class RemoteColumnNode(
    var modifier: LayoutModifier,
    var horizontal: Int,
    var vertical: Int,
) : RemoteComposeNode() {
    override fun render(context: RemoteComposeContext) {
        // Column always uses the content variant (REM-96 column()). MVP scope per PO:
        // leaf-column deferred (D1).
        context.column(modifier = modifier, horizontal = horizontal, vertical = vertical) {
            renderChildren(this)
        }
    }
}

internal class RemoteRowNode(
    var modifier: LayoutModifier,
    var horizontal: Int,
    var vertical: Int,
) : RemoteComposeNode() {
    override fun render(context: RemoteComposeContext) {
        // Row always uses the content variant (REM-96 row()). MVP scope per PO:
        // leaf-row deferred (D5).
        context.row(modifier = modifier, horizontal = horizontal, vertical = vertical) {
            renderChildren(this)
        }
    }
}

internal class RemoteRootNodeWithChildren : RemoteComposeNode() {
    override fun render(context: RemoteComposeContext) {
        context.root { renderChildren(this) }
    }
}

/**
 * `LAYOUT_ROOT` container — singleton at the top of the body, no modifiers, single
 * `ContainerEnd` on close. Mirrors REM-96 `root { ... }`. Most captured documents wrap their
 * top-level layout content in a `RemoteRoot { ... }` so the body's first body op is
 * `LAYOUT_ROOT` (matching every corpus container fixture).
 */
@Composable
fun RemoteRoot(content: @Composable @RemoteComposable () -> Unit) {
    ComposeNode<RemoteRootNodeWithChildren, RemoteComposeApplier>(
        factory = { RemoteRootNodeWithChildren() },
        update = {},
        content = content,
    )
}

/**
 * `LAYOUT_BOX` container. Defaults match the **leaf** (no-content) variant — `POS_CENTER` /
 * `POS_CENTER` (upstream `box(modifier)` no-content overload at `RemoteComposeWriter.java:4061`).
 * Childless `RemoteBox {}` therefore byte-matches `c_box.rc` out-of-the-box; callers passing
 * children should use `horizontal = POS_START, vertical = POS_TOP` if they want the with-content
 * default semantics.
 */
@Composable
fun RemoteBox(
    modifier: LayoutModifier = LayoutModifier(),
    horizontal: Int = POS_CENTER,
    vertical: Int = POS_CENTER,
    content: @Composable @RemoteComposable () -> Unit = {},
) {
    ComposeNode<RemoteBoxNode, RemoteComposeApplier>(
        factory = { RemoteBoxNode(modifier, horizontal, vertical) },
        update = {
            set(modifier) { this.modifier = it }
            set(horizontal) { this.horizontal = it }
            set(vertical) { this.vertical = it }
        },
        content = content,
    )
}

/**
 * `LAYOUT_COLUMN` container. Default positioning = `POS_START` / `POS_TOP` (REM-96 default,
 * matches the with-content shape used by every corpus column fixture).
 */
@Composable
fun RemoteColumn(
    modifier: LayoutModifier = LayoutModifier(),
    horizontal: Int = POS_START,
    vertical: Int = POS_TOP,
    content: @Composable @RemoteComposable () -> Unit,
) {
    ComposeNode<RemoteColumnNode, RemoteComposeApplier>(
        factory = { RemoteColumnNode(modifier, horizontal, vertical) },
        update = {
            set(modifier) { this.modifier = it }
            set(horizontal) { this.horizontal = it }
            set(vertical) { this.vertical = it }
        },
        content = content,
    )
}

/**
 * `LAYOUT_ROW` container. Default positioning = `POS_START` / `POS_TOP` (REM-96 default).
 */
@Composable
fun RemoteRow(
    modifier: LayoutModifier = LayoutModifier(),
    horizontal: Int = POS_START,
    vertical: Int = POS_TOP,
    content: @Composable @RemoteComposable () -> Unit,
) {
    ComposeNode<RemoteRowNode, RemoteComposeApplier>(
        factory = { RemoteRowNode(modifier, horizontal, vertical) },
        update = {
            set(modifier) { this.modifier = it }
            set(horizontal) { this.horizontal = it }
            set(vertical) { this.vertical = it }
        },
        content = content,
    )
}
