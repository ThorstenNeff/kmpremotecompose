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
 * REM-128 S3 — layout-container `@Composable`s for the Compose-creation surface. Each composable
 * produces a node whose Phase-B render delegates to the byte-proven REM-96 procedural helper.
 * No new op emission (TechSpec §0 / §6).
 *
 * **§2 Q2 Resolution-A split (TechSpec §2):** [RemoteBox] (with content, START/TOP default) maps
 * 1:1 to REM-96 `box()`; [RemoteBoxLeaf] (no content, CENTER default) maps 1:1 to REM-96
 * `boxLeaf()`. Each composable's default is the byte-correct one for its variant — no
 * children-detection magic, no runtime POS_AUTO sentinel. Supersedes S2's render-time routing.
 *
 * **Modifier API (TechSpec §2 Q1/Q4):** [RemoteModifier] (structural element list) — converted to
 * a REM-96 [com.tneff.kmpremotecompose.remote.creation.LayoutModifier] at the procedural-helper
 * call-site via [RemoteModifier.toLayoutModifier], so the bytes are byte-identical to the
 * procedural-DSL caller's output for the same modifier chain.
 */

internal class RemoteRootContainerNode : RemoteComposeNode() {
    override fun render(context: RemoteComposeContext) {
        context.root { renderChildren(this) }
    }
}

internal class RemoteBoxContainerNode(
    var modifier: RemoteModifier,
    var horizontal: Int,
    var vertical: Int,
) : RemoteComposeNode() {
    override fun render(context: RemoteComposeContext) {
        context.box(
            modifier = modifier.toLayoutModifier(),
            horizontal = horizontal,
            vertical = vertical,
        ) {
            renderChildren(this)
        }
    }
}

internal class RemoteBoxLeafNode(
    var modifier: RemoteModifier,
    var horizontal: Int,
    var vertical: Int,
) : RemoteComposeNode() {
    override fun render(context: RemoteComposeContext) {
        // Leaf has no children-render step — boxLeaf() emits BoxLayout + modifiers + single
        // ContainerEnd directly. Compose Runtime never inserts children into this node (no
        // `content` ComposeNode overload below).
        context.boxLeaf(
            modifier = modifier.toLayoutModifier(),
            horizontal = horizontal,
            vertical = vertical,
        )
    }
}

internal class RemoteColumnContainerNode(
    var modifier: RemoteModifier,
    var horizontal: Int,
    var vertical: Int,
) : RemoteComposeNode() {
    override fun render(context: RemoteComposeContext) {
        context.column(
            modifier = modifier.toLayoutModifier(),
            horizontal = horizontal,
            vertical = vertical,
        ) {
            renderChildren(this)
        }
    }
}

internal class RemoteRowContainerNode(
    var modifier: RemoteModifier,
    var horizontal: Int,
    var vertical: Int,
) : RemoteComposeNode() {
    override fun render(context: RemoteComposeContext) {
        context.row(
            modifier = modifier.toLayoutModifier(),
            horizontal = horizontal,
            vertical = vertical,
        ) {
            renderChildren(this)
        }
    }
}

/**
 * `LAYOUT_ROOT` container — singleton at the top of the body, no modifiers, single
 * `ContainerEnd` on close. Mirrors REM-96 `root { ... }`.
 */
@Composable
fun RemoteRoot(content: @Composable @RemoteComposable () -> Unit) {
    ComposeNode<RemoteRootContainerNode, RemoteComposeApplier>(
        factory = { RemoteRootContainerNode() },
        update = {},
        content = content,
    )
}

/**
 * `LAYOUT_BOX` container — **content variant** (`box()` → `LayoutContent` + dual `ContainerEnd`).
 * Default positioning = `POS_START` / `POS_TOP` (mirror upstream `box(modifier, h, v, content)`
 * @`RemoteComposeWriter.java:3537` / REM-96 `box()`). Always routes to the content overload — for
 * an empty `content {}` block use [RemoteBoxLeaf] instead (which is byte-different and matches
 * the upstream leaf overload).
 */
@Composable
fun RemoteBox(
    modifier: RemoteModifier = RemoteModifier,
    horizontal: Int = POS_START,
    vertical: Int = POS_TOP,
    content: @Composable @RemoteComposable () -> Unit,
) {
    ComposeNode<RemoteBoxContainerNode, RemoteComposeApplier>(
        factory = { RemoteBoxContainerNode(modifier, horizontal, vertical) },
        update = {
            set(modifier) { this.modifier = it }
            set(horizontal) { this.horizontal = it }
            set(vertical) { this.vertical = it }
        },
        content = content,
    )
}

/**
 * `LAYOUT_BOX` container — **leaf variant** (`boxLeaf()` → single `ContainerEnd`, no
 * `LayoutContent`). No content lambda. Default positioning = `POS_CENTER` / `POS_CENTER` (mirror
 * upstream `box(modifier)` no-content overload default @`RemoteComposeWriter.java:4043` & `:4061`,
 * REM-96 `boxLeaf()`). Used for childless decorative boxes — the corpus pattern in `c_box.rc` and
 * every inner-Box in `c_column.rc` / `c_row.rc` / `c_modifier_*`.
 */
@Composable
fun RemoteBoxLeaf(
    modifier: RemoteModifier = RemoteModifier,
    horizontal: Int = POS_CENTER,
    vertical: Int = POS_CENTER,
) {
    ComposeNode<RemoteBoxLeafNode, RemoteComposeApplier>(
        factory = { RemoteBoxLeafNode(modifier, horizontal, vertical) },
        update = {
            set(modifier) { this.modifier = it }
            set(horizontal) { this.horizontal = it }
            set(vertical) { this.vertical = it }
        },
    )
}

/**
 * `LAYOUT_COLUMN` container. Default positioning = `POS_START` / `POS_TOP` (REM-96 default).
 */
@Composable
fun RemoteColumn(
    modifier: RemoteModifier = RemoteModifier,
    horizontal: Int = POS_START,
    vertical: Int = POS_TOP,
    content: @Composable @RemoteComposable () -> Unit,
) {
    ComposeNode<RemoteColumnContainerNode, RemoteComposeApplier>(
        factory = { RemoteColumnContainerNode(modifier, horizontal, vertical) },
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
    modifier: RemoteModifier = RemoteModifier,
    horizontal: Int = POS_START,
    vertical: Int = POS_TOP,
    content: @Composable @RemoteComposable () -> Unit,
) {
    ComposeNode<RemoteRowContainerNode, RemoteComposeApplier>(
        factory = { RemoteRowContainerNode(modifier, horizontal, vertical) },
        update = {
            set(modifier) { this.modifier = it }
            set(horizontal) { this.horizontal = it }
            set(vertical) { this.vertical = it }
        },
        content = content,
    )
}
