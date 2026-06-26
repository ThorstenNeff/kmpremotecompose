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
package com.tneff.kmpremotecompose.remote.player.compose

/**
 * REM-32 **key deliverable**: per-parameter parity of the upstream complex-text surface
 * (`PaintContext.layoutComplexText`/`getTextBounds`/`drawTextRun`) when mapped onto CMP
 * `TextMeasurer`/`Paragraph` text. This is the **GAP-1 surface** the PO prioritizes data-based:
 *
 *  - [SUPPORTED]   — CMP has a 1:1 equivalent; basis text (this slice) covers it.
 *  - [APPROXIMATED]— CMP has a near-equivalent that may differ at the pixel/edge level; basis maps it
 *    to the closest CMP feature and **flags** the divergence (no silent approximation).
 *  - [UNSUPPORTED] — no CMP-common equivalent; deferred to **L2-D1** (Skiko `Paragraph` direct).
 *
 * "No silent approximating" (PROJECT_CONTEXT §5): every non-[SUPPORTED] row is a flagged item the PO
 * sees here and in the slice report; D1 work is scoped off this table.
 */
enum class ParitySupport { SUPPORTED, APPROXIMATED, UNSUPPORTED }

/** One parameter of the complex-text contract and how the CMP basis adapter handles it. */
data class TextParameterParity(
    val parameter: String,
    val support: ParitySupport,
    val cmpMapping: String,
    val note: String = "",
)

/**
 * The classification. Basis (this slice) implements every [ParitySupport.SUPPORTED] and
 * [ParitySupport.APPROXIMATED] row; [ParitySupport.UNSUPPORTED] rows are L2-D1.
 */
val TEXT_PARAMETER_PARITY: List<TextParameterParity> = listOf(
    TextParameterParity("alignment", ParitySupport.SUPPORTED, "TextStyle.textAlign (Start/Center/End/Justify→see justificationMode)"),
    TextParameterParity("maxLines", ParitySupport.SUPPORTED, "TextMeasurer.measure(maxLines=)"),
    TextParameterParity("maxWidth", ParitySupport.SUPPORTED, "Constraints(maxWidth=)"),
    TextParameterParity("letterSpacing", ParitySupport.SUPPORTED, "TextStyle.letterSpacing (sp)"),
    TextParameterParity("lineHeightMultiplier", ParitySupport.SUPPORTED, "TextStyle.lineHeight (em)"),
    TextParameterParity("underline", ParitySupport.SUPPORTED, "TextDecoration.Underline"),
    TextParameterParity("strikethrough", ParitySupport.SUPPORTED, "TextDecoration.LineThrough"),
    TextParameterParity("rtl (drawTextRun)", ParitySupport.SUPPORTED, "LayoutDirection.Rtl"),
    TextParameterParity("color/fontSize", ParitySupport.SUPPORTED, "TextStyle.color / fontSize",
        "source = shared paint-bundle; see paint-state seam flag (S2↔S3)"),

    TextParameterParity("overflow=ellipsis(END)", ParitySupport.APPROXIMATED, "TextOverflow.Ellipsis",
        "END ellipsis ✓; MIDDLE/START ellipsis have no CMP-common equivalent → D1"),
    TextParameterParity("maxHeight", ParitySupport.APPROXIMATED, "Constraints(maxHeight=) + maxLines",
        "CMP clips by height/lines; not a direct StaticLayout-style maxHeight"),
    TextParameterParity("lineHeightAdd", ParitySupport.APPROXIMATED, "folded into TextStyle.lineHeight",
        "Android adds px to line height; CMP lineHeight is absolute/em → converted, sub-px drift"),
    TextParameterParity("lineBreakStrategy", ParitySupport.APPROXIMATED, "LineBreak (Simple/Heading/Paragraph)",
        "CMP presets ≠ Android's exact strategies → closest preset, flagged"),
    TextParameterParity("getTextBounds pixel parity", ParitySupport.APPROXIMATED, "TextLayoutResult metrics",
        "GAP-2: Skia measure vs Android Paint.getTextBounds may sub-pixel diverge"),
    TextParameterParity("monospace-width flag", ParitySupport.APPROXIMATED, "measured width",
        "TEXT_MEASURE_MONOSPACE_WIDTH approximated via normal measure"),

    TextParameterParity("hyphenationFrequency", ParitySupport.UNSUPPORTED, "TextStyle (Hyphens.Auto/None only)",
        "D1: frequency not exposed by CMP-common"),
    TextParameterParity("justificationMode", ParitySupport.UNSUPPORTED, "(no CMP-common justify in basis)",
        "D1: Skiko Paragraph direct"),
    TextParameterParity("complex BiDi / script shaping", ParitySupport.UNSUPPORTED, "(CMP basic BiDi only)",
        "D1: full bidi/shaping parity"),
)
