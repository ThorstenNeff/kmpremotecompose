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
package com.tneff.kmpremotecompose.remote.core.operations.draw

import com.tneff.kmpremotecompose.remote.core.operations.Operation
import com.tneff.kmpremotecompose.remote.core.operations.OperationReader
import com.tneff.kmpremotecompose.remote.core.operations.Operations
import com.tneff.kmpremotecompose.remote.core.operations.layout.TextLayout
import com.tneff.kmpremotecompose.remote.player.core.PaintContext
import com.tneff.kmpremotecompose.remote.player.core.PaintOperation
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.wire.WireBuffer

/**
 * `DRAW_CONTENT` (opcode [Operations.DRAW_CONTENT]) — draws the component's content placeholder.
 *
 * Wire layout: opcode byte only, no fields (mirrors upstream `DrawContent`).
 *
 * **REM-134 (Option C):** upstream `DrawContent` is a conditional `PaintOperation` that delegates to its
 * enclosing component's content (`mComponent.drawContent`). We resolve that delegation at **measure
 * time** on the existing LayoutMeasure tree: for the TextLayout-span case (the only corpus user,
 * `attribute_string`) the measure pass wires the enclosing [TextLayout] + the resolved single-line draw
 * origin onto this op via [setTextContent], and [paint] emits the span text through the same CoreText
 * paint primitives — at the DrawContent stream position, which is the **z-order-correct** point (after
 * the span's modifiers, e.g. a background). An unmeasured / non-TextLayout DrawContent is a fail-soft
 * no-op (generic component-content delegation is deferred — 0 corpus docs).
 *
 * **§2:** render-only — [write]/[read]/[equals]/[hashCode] are the original zero-payload form; the
 * delegation fields are not serialized → 173-byte conformance intact.
 */
class DrawContent : Operation, PaintOperation {

    // REM-134 render-only delegation target, set by LayoutMeasure. Not serialized → byte-safe.
    private var textLayout: TextLayout? = null
    private var textId = 0
    private var drawX = 0f
    private var baselineY = 0f
    private var positioned = false

    /** Called by LayoutMeasure with the enclosing TextLayout span + its resolved single-line draw origin. */
    fun setTextContent(layout: TextLayout, textId: Int, x: Float, baseline: Float) {
        textLayout = layout
        this.textId = textId
        drawX = x
        baselineY = baseline
        positioned = true
    }

    override val opcode: Int get() = Operations.DRAW_CONTENT

    /**
     * Paint the enclosing TextLayout span's text at its measured baseline, styled via the span's
     * [TextLayout.applyStyle] (same paint path as `CoreText`). Fail-soft no-op when unwired (non-text
     * DrawContent / unmeasured) or the text isn't loaded.
     */
    override fun paint(context: RemoteContext, paint: PaintContext) {
        val layout = textLayout ?: return
        if (!positioned) return
        val text = context.getText(textId) ?: return
        paint.savePaint()
        layout.applyStyle(context, paint)
        paint.drawTextRun(textId, 0, -1, 0, 1, drawX, baselineY, false)
        paint.restorePaint()
    }

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
    }

    override fun dump(): String = "DRAW_CONTENT"

    override fun equals(other: Any?): Boolean = this === other || other is DrawContent

    override fun hashCode(): Int = opcode

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += DrawContent()
        }
    }
}
