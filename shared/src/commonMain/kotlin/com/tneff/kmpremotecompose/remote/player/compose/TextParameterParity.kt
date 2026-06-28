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
 * REM-32 **key deliverable**, **finalized by REM-74 (FC-D1)**: per-parameter parity of the upstream
 * complex-text surface (`PaintContext.layoutComplexText`/`getTextBounds`/`drawTextRun`) mapped onto CMP
 * `TextMeasurer`/`Paragraph` text. This is the **GAP-1 surface** the PO prioritizes data-based:
 *
 *  - [SUPPORTED]   — CMP has a 1:1 equivalent; the complex-text path (CoreText wrap routing) covers it.
 *  - [APPROXIMATED]— CMP has a near-equivalent that may differ at the pixel/edge level; mapped to the
 *    closest CMP feature and **flagged** (no silent approximation).
 *  - [UNSUPPORTED] — no CMP-common equivalent (a granular Android-only param).
 *
 * **REM-74 / D1 decision (assist-confirmed): render complex text through CMP-common text, NOT raw Skiko
 * `Paragraph`.** Rationale: CMP-text wraps `StaticLayout`/`android.text` on Android (= the byte-oracle's
 * own backend) and Skia `Paragraph` on Skiko (iOS/Desktop/Web) from ONE adapter — cross-platform AND
 * oracle-faithful. Raw Skiko would be Skiko-only and break Android parity. The cost: CMP-common is
 * narrower than `android.text`, so the [UNSUPPORTED] rows below stay unexpressible. That is acceptable
 * **only because the corpus does not exercise any of them** — proven by `Rem74WrapDecisionTest
 * .corpusNeverExercisesCmpLimitedGranularParams` (all 173 docs decoded). [corpusExercised] records that
 * per row: an [UNSUPPORTED] row with `corpusExercised = false` is **cosmetic**; if a future fixture uses
 * one, the guard test fails loudly and it must be escalated (raw-Skiko `Paragraph` fallback) — no silent
 * gap (PROJECT_CONTEXT §5/§6).
 *
 * Corpus reach at REM-74 (16 docs use CORE_TEXT, 678 ops): align ∈ {Center, Start} (NO Justify),
 * overflow = END-ellipsis only (NO Start/Middle), maxLines used; breakStrategy/hyphenationFrequency/
 * justificationMode/letterSpacing/lineHeight(add+mult)/underline/strikethrough = NONE.
 */
enum class ParitySupport { SUPPORTED, APPROXIMATED, UNSUPPORTED }

/**
 * One parameter of the complex-text contract and how the CMP adapter handles it.
 * [corpusExercised] = whether any of the 173 corpus docs actually uses it (REM-74 decode scan); a
 * non-[SUPPORTED] row with `corpusExercised = false` is a cosmetic limit, not a real rendering gap.
 */
data class TextParameterParity(
    val parameter: String,
    val support: ParitySupport,
    val cmpMapping: String,
    val note: String = "",
    val corpusExercised: Boolean = true,
)

/**
 * The classification. The CMP complex-text path implements every [ParitySupport.SUPPORTED] and
 * [ParitySupport.APPROXIMATED] row; [ParitySupport.UNSUPPORTED] rows are CMP-common limits — all
 * `corpusExercised = false` at REM-74, hence cosmetic (guarded by `Rem74WrapDecisionTest`).
 */
val TEXT_PARAMETER_PARITY: List<TextParameterParity> = listOf(
    TextParameterParity("alignment (Left/Right/Center/Start/End)", ParitySupport.SUPPORTED, "TextStyle.textAlign",
        "corpus uses Center+Start", corpusExercised = true),
    TextParameterParity("maxLines", ParitySupport.SUPPORTED, "TextMeasurer.measure(maxLines=)", corpusExercised = true),
    TextParameterParity("maxWidth (wrap)", ParitySupport.SUPPORTED, "Constraints(maxWidth=) via CoreText box",
        "REM-74: drives the wrap decision (width>maxWidth && maxLines!=1)", corpusExercised = true),
    TextParameterParity("overflow=ellipsis(END)", ParitySupport.SUPPORTED, "TextOverflow.Ellipsis",
        "corpus uses END only (text_refresh_bug)", corpusExercised = true),
    TextParameterParity("rtl (drawTextRun)", ParitySupport.SUPPORTED, "LayoutDirection.Rtl", corpusExercised = false),
    TextParameterParity("color/fontSize", ParitySupport.SUPPORTED, "TextStyle.color / fontSize",
        "source = shared paint-bundle (S2↔S3 seam)", corpusExercised = true),

    TextParameterParity("letterSpacing", ParitySupport.APPROXIMATED, "TextStyle.letterSpacing (sp)",
        "Android px vs CMP sp → density-converted; not in corpus", corpusExercised = false),
    TextParameterParity("lineHeightMultiplier", ParitySupport.APPROXIMATED, "TextStyle.lineHeight (em)",
        "not in corpus", corpusExercised = false),
    TextParameterParity("underline / strikethrough", ParitySupport.APPROXIMATED, "TextDecoration.Underline/LineThrough",
        "not in corpus", corpusExercised = false),
    TextParameterParity("maxHeight", ParitySupport.APPROXIMATED, "Constraints(maxHeight=) + maxLines",
        "CMP clips by height/lines; not a direct StaticLayout maxHeight; not in corpus", corpusExercised = false),
    TextParameterParity("getTextBounds pixel parity", ParitySupport.APPROXIMATED, "TextLayoutResult metrics",
        "GAP-2: Skia measure vs Android Paint.getTextBounds may sub-pixel diverge"),
    TextParameterParity("monospace-width flag", ParitySupport.APPROXIMATED, "measured width",
        "TEXT_MEASURE_MONOSPACE_WIDTH approximated via normal measure", corpusExercised = false),

    TextParameterParity("lineHeightAdd", ParitySupport.UNSUPPORTED, "(dropped — CMP lineHeight is em/multiplier only)",
        "CMP-common has no additive px line-spacing; not in corpus → cosmetic", corpusExercised = false),
    TextParameterParity("lineBreakStrategy (Balanced/HighQuality)", ParitySupport.UNSUPPORTED, "(dropped at bridge)",
        "CMP LineBreak presets ≠ Android exact strategies; not in corpus → cosmetic", corpusExercised = false),
    TextParameterParity("hyphenationFrequency (levels)", ParitySupport.UNSUPPORTED, "(dropped at bridge; CMP Hyphens.Auto/None only)",
        "frequency levels not in CMP-common; not in corpus → cosmetic", corpusExercised = false),
    TextParameterParity("justificationMode (INTER_WORD) + TextAlign.Justify", ParitySupport.UNSUPPORTED, "(mode dropped at bridge)",
        "TextAlign.Justify maps, but INTER_WORD granularity is CMP-limited; neither in corpus → cosmetic", corpusExercised = false),
    TextParameterParity("overflow=ellipsis(START/MIDDLE)", ParitySupport.UNSUPPORTED, "TextOverflow.Ellipsis (END only)",
        "Start/Middle ellipsis approximate to END; not in corpus → cosmetic", corpusExercised = false),
    TextParameterParity("complex BiDi / script shaping", ParitySupport.UNSUPPORTED, "(CMP basic BiDi only)",
        "full bidi/shaping parity beyond CMP-common; corpus reach not characterized", corpusExercised = false),
)
