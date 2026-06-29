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

import com.tneff.kmpremotecompose.conformance.RcCorpus
import com.tneff.kmpremotecompose.remote.core.document.DocumentReader
import com.tneff.kmpremotecompose.remote.core.operations.Operations
import com.tneff.kmpremotecompose.remote.core.operations.layout.DimensionType
import com.tneff.kmpremotecompose.remote.creation.LayoutModifier
import com.tneff.kmpremotecompose.remote.creation.Profile
import com.tneff.kmpremotecompose.remote.creation.boxLeaf
import com.tneff.kmpremotecompose.remote.creation.column
import com.tneff.kmpremotecompose.remote.creation.defaultRcPlatformServices
import com.tneff.kmpremotecompose.remote.creation.document
import com.tneff.kmpremotecompose.remote.creation.root
import com.tneff.kmpremotecompose.remote.creation.row
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * REM-130 — T2 Modifier-Extension anchors (post-assist-correction Pfad A**: 4 full-doc Stage-2 + 1
 * corpus sub-span Stage-2 + 1 Stage-1-transitive (scroll-V, REM-96-anchored) + 1 deferred to T3).
 *
 * **Bug #2 lesson pre-applied:** properties-table empirically decoded from each fixture via
 * transient probe before this file was written — every fixture verified for profile (PROFILE_ANDROIDX
 * 0x200 or 0x201 = +PROFILE_EXPERIMENTAL), w/h dims, contentDescription, container shape, and
 * modifier-bytes layout.
 *
 * **Assist-correction (msg 1521041913241931928):** the original Stage-1 plan for scroll-H,
 * alignBy, visibility claimed transitive §2 via REM-96; assist NO-GO'd that because REM-96 only
 * actually anchors scroll-V byte-for-byte (the others land at decode/source/literal anchors, not
 * corpus). Replaced with direct corpus anchors where the fixtures permit; visibility full-doc
 * remains deferred to T3 (needs FloatExpression primitive composable — closed by REM-141 S2+S3).
 *
 * **Anchor strategy per T2 modifier (corrected mapping):**
 *
 * | Modifier            | Strategy                          | Why                                                                |
 * |---------------------|-----------------------------------|--------------------------------------------------------------------|
 * | `padding`           | Stage-2 full-doc                  | `c_modifier_padding.rc` = Column + 2× BoxLeaf, fully buildable.   |
 * | `clipRect`          | Stage-2 full-doc                  | `c_modifier_clip_rect.rc` = single BoxLeaf, fully buildable.      |
 * | `roundedClipRect`   | Stage-2 full-doc                  | `c_modifier_clip_rounded_rect.rc` = single BoxLeaf.               |
 * | `border` (static)   | Stage-2 full-doc                  | `c_modifier_border.rc` reproducible with Column(spacedBy(20)) +   |
 * |                     |                                   | 2× BoxLeaf (width/height/border). Dynamic-color (colorId-ref)     |
 * |                     |                                   | path is T3.                                                       |
 * | `scroll-V`          | Stage-1 transitive (REM-96 V-anchor) | REM-96 has a full-byte corpus anchor for scroll-V; transitivity   |
 * |                     |                                   | is real here. Compose-DSL == procedural-DSL byte-for-byte.        |
 * | `scroll-H`          | Stage-1 transitive (NEW REM-96 H-anchor) | This PR adds REM-96 scroll-H full-byte test against              |
 * |                     |                                   | `c_modifier_horizontal_scroll.rc` to close the previously         |
 * |                     |                                   | over-claimed-but-untested docstring.                              |
 * | `alignBy`           | Stage-2 sub-span (NaN raw-bits)   | `c_modifier_align_by_baseline.rc` MODIFIER_ALIGN_BY 9-byte op     |
 * |                     |                                   | bytes; `line.toRawBits()` = corpus NaN bits (NOT 12.5f);           |
 * |                     |                                   | experimental profile.                                              |
 * | `visibility`        | Stage-1 + int-id-ref byte check   | Full-doc Stage-2 against corpus deferred to T3 — needs            |
 * |                     |                                   | FloatExpression primitive composable (closed by REM-141).         |
 */
class ComposeCreationModifierT2AnchorTest {

    private val androidx: Profile = Profile(
        operationsProfiles = Operations.PROFILE_ANDROIDX,
        services = defaultRcPlatformServices(),
    )

    private val androidxExperimental: Profile = Profile(
        operationsProfiles = Operations.PROFILE_ANDROIDX or Operations.PROFILE_EXPERIMENTAL,
        services = defaultRcPlatformServices(),
    )

    // -------------------------------------------------------------------------------------------
    // Stage-2 FULL-DOC anchors (3 modifiers — fixtures reproducible in pure T2 scope)
    // -------------------------------------------------------------------------------------------

    /**
     * `c_modifier_padding.rc` (318 B, PROFILE_ANDROIDX, 400×400, contentDescription="") —
     * `ColumnLayout(POS_START/POS_TOP/spacedBy=0)` + width(FILL)/height(FILL)/background(WHITE)
     * → LayoutContent → 2× childless BoxLayout(POS_CENTER/POS_CENTER) each with
     * padding/width/height/background. Padding values: `(20,20,20,20)` then `(40,5,0,10)`.
     */
    @Test
    fun stage2_padding_matchesCModifierPaddingOracle_byteForByte() = runBlocking {
        val produced = captureSingleRemoteDocument(
            width = 400, height = 400, profile = androidx, contentDescription = "",
        ) {
            RemoteRoot {
                RemoteColumn(
                    modifier = RemoteModifier
                        .width(DimensionType.FILL, Float.NaN)
                        .height(DimensionType.FILL, Float.NaN)
                        .background(color = 0xffffffff.toInt()),
                ) {
                    RemoteBoxLeaf(
                        modifier = RemoteModifier
                            .padding(20f)
                            .width(DimensionType.EXACT, 100f)
                            .height(DimensionType.EXACT, 100f)
                            .background(color = 0xffff0000.toInt()),
                    )
                    RemoteBoxLeaf(
                        modifier = RemoteModifier
                            .padding(start = 40f, top = 5f, end = 0f, bottom = 10f)
                            .width(DimensionType.EXACT, 100f)
                            .height(DimensionType.EXACT, 100f)
                            .background(color = 0xff0000ff.toInt()),
                    )
                }
            }
        }
        assertContentEquals(
            RcCorpus.readFixture("corpus/c_modifier_padding.rc"),
            produced,
            "RemoteModifier.padding must byte-match c_modifier_padding.rc (Column + 2× padded BoxLeaf).",
        )
    }

    /**
     * `c_modifier_clip_rect.rc` (129 B, PROFILE_ANDROIDX, 400×400) — single childless
     * `BoxLayout(POS_CENTER/POS_CENTER)` + width(200)/height(200)/clipRect()/background(RED).
     */
    @Test
    fun stage2_clipRect_matchesCModifierClipRectOracle_byteForByte() = runBlocking {
        val produced = captureSingleRemoteDocument(
            width = 400, height = 400, profile = androidx, contentDescription = "",
        ) {
            RemoteRoot {
                RemoteBoxLeaf(
                    modifier = RemoteModifier
                        .width(DimensionType.EXACT, 200f)
                        .height(DimensionType.EXACT, 200f)
                        .clipRect()
                        .background(color = 0xffff0000.toInt()),
                )
            }
        }
        assertContentEquals(
            RcCorpus.readFixture("corpus/c_modifier_clip_rect.rc"),
            produced,
            "RemoteModifier.clipRect must byte-match c_modifier_clip_rect.rc (BoxLeaf + clipRect).",
        )
    }

    /**
     * `c_modifier_clip_rounded_rect.rc` (145 B, PROFILE_ANDROIDX, 400×400) — single childless
     * `BoxLayout(POS_CENTER/POS_CENTER)` + width(200)/height(200)/roundedClipRect(40,40,40,40)/
     * background(BLUE).
     */
    @Test
    fun stage2_roundedClipRect_matchesCModifierClipRoundedRectOracle_byteForByte() = runBlocking {
        val produced = captureSingleRemoteDocument(
            width = 400, height = 400, profile = androidx, contentDescription = "",
        ) {
            RemoteRoot {
                RemoteBoxLeaf(
                    modifier = RemoteModifier
                        .width(DimensionType.EXACT, 200f)
                        .height(DimensionType.EXACT, 200f)
                        .roundedClipRect(40f, 40f, 40f, 40f)
                        .background(color = 0xff0000ff.toInt()),
                )
            }
        }
        assertContentEquals(
            RcCorpus.readFixture("corpus/c_modifier_clip_rounded_rect.rc"),
            produced,
            "RemoteModifier.roundedClipRect must byte-match c_modifier_clip_rounded_rect.rc.",
        )
    }

    // -------------------------------------------------------------------------------------------
    // Stage-2 FULL-DOC anchor (border — corpus has Column.spacedBy=20 + padding(20); modifier-chain
    // additions in this PR (RemoteModifier.spacedBy + .padding) make full-doc reproducible)
    // -------------------------------------------------------------------------------------------

    /**
     * `c_modifier_border.rc` (262 B, PROFILE_ANDROIDX, 400×400, contentDescription="") —
     * `ColumnLayout(POS_START/POS_TOP/spacedBy=20)` + padding(20,20,20,20) → LayoutContent → 2×
     * childless BoxLayout(POS_CENTER/POS_CENTER) each with width(100)/height(100)/border(4f,
     * 0.1f, color, shape=2). First border = red 0xffff0000, second = blue 0xff0000ff.
     *
     * **Switched from sub-span to full-doc** per assist correction (msg 1521041913241931928):
     * `c_modifier_border.rc` IS a dedicated single-modifier fixture and full-doc Stage-2 is the
     * stronger gate. spacedBy(20) is now part of the T2 modifier-chain API (mirrors REM-96
     * `LayoutModifier.spacedBy`) — same data-class-element pattern.
     *
     * **Dynamic-color border (colorId-ref) is T3:** the corpus `c_modifier_dynamic_border.rc`
     * carries `colorId=42` and a ColorExpression op; the procedural-DSL `border()` only accepts
     * `color: Int`, can't emit the dynamic form. Out of T2 scope.
     */
    @Test
    fun stage2_border_matchesCModifierBorderOracle_byteForByte() = runBlocking {
        val produced = captureSingleRemoteDocument(
            width = 400, height = 400, profile = androidx, contentDescription = "",
        ) {
            RemoteRoot {
                RemoteColumn(
                    modifier = RemoteModifier
                        .spacedBy(20f)
                        .padding(20f),
                ) {
                    RemoteBoxLeaf(
                        modifier = RemoteModifier
                            .width(DimensionType.EXACT, 100f)
                            .height(DimensionType.EXACT, 100f)
                            .border(borderWidth = 4f, roundedCorner = 0.1f, color = 0xffff0000.toInt(), shape = 2),
                    )
                    RemoteBoxLeaf(
                        modifier = RemoteModifier
                            .width(DimensionType.EXACT, 100f)
                            .height(DimensionType.EXACT, 100f)
                            .border(borderWidth = 4f, roundedCorner = 0.1f, color = 0xff0000ff.toInt(), shape = 2),
                    )
                }
            }
        }
        assertContentEquals(
            RcCorpus.readFixture("corpus/c_modifier_border.rc"),
            produced,
            "RemoteModifier.border + Column.spacedBy(20)/padding(20) must byte-match c_modifier_border.rc.",
        )
    }

    // -------------------------------------------------------------------------------------------
    // Stage-1 anchors (compose==procedural; transitive §2 via REM-96 corpus-confirm)
    // -------------------------------------------------------------------------------------------

    /**
     * `MODIFIER_SCROLL` direction=1 (horizontal). The corpus fixture uses NaN-encoded id-refs into
     * a FloatConstant emitted by the same call; the IDs depend on writer state (allocator), so a
     * corpus sub-span byte-anchor is NOT stable. Stage-1: Compose-DSL emission equals procedural-DSL
     * emission byte-for-byte for the same shape. Procedural side is REM-96-corpus-anchored via
     * `LayoutModifierByteTest.scroll_fullByteEquality_vsCorpusFixture_horizontalScroll`
     * (added in REM-130 revision-pass after assist flagged that the prior docstring overclaim
     * had never actually tested SCROLL_HORIZONTAL against the corpus).
     */
    @Test
    fun stage1_scrollHorizontal_composeEqualsProcedural() = runBlocking {
        val produced = captureSingleRemoteDocument(
            width = 400, height = 400, profile = androidx, contentDescription = "",
        ) {
            RemoteRoot {
                RemoteRow(
                    modifier = RemoteModifier
                        .width(DimensionType.FILL, Float.NaN)
                        .clipRect()
                        .scroll(LayoutModifier.SCROLL_HORIZONTAL),
                ) {
                    RemoteBoxLeaf(
                        modifier = RemoteModifier
                            .width(DimensionType.EXACT, 100f)
                            .height(DimensionType.EXACT, 100f),
                    )
                }
            }
        }
        val procedural = document(
            width = 400, height = 400, profile = androidx, contentDescription = "",
        ) {
            root {
                row(
                    modifier = LayoutModifier()
                        .width(DimensionType.FILL, Float.NaN)
                        .clipRect()
                        .scroll(LayoutModifier.SCROLL_HORIZONTAL),
                ) {
                    boxLeaf(
                        modifier = LayoutModifier()
                            .width(DimensionType.EXACT, 100f)
                            .height(DimensionType.EXACT, 100f),
                    )
                }
            }
        }
        assertContentEquals(
            procedural,
            produced,
            "Compose-DSL RemoteModifier.scroll(SCROLL_HORIZONTAL) full doc must byte-match the " +
                "procedural-DSL equivalent (transitive §2 via REM-96 LayoutModifierByteTest).",
        )
    }

    @Test
    fun stage1_scrollVertical_composeEqualsProcedural() = runBlocking {
        val produced = captureSingleRemoteDocument(
            width = 400, height = 400, profile = androidx, contentDescription = "",
        ) {
            RemoteRoot {
                RemoteColumn(
                    modifier = RemoteModifier
                        .height(DimensionType.FILL, Float.NaN)
                        .clipRect()
                        .scroll(LayoutModifier.SCROLL_VERTICAL),
                ) {
                    RemoteBoxLeaf(
                        modifier = RemoteModifier
                            .width(DimensionType.EXACT, 100f)
                            .height(DimensionType.EXACT, 100f),
                    )
                }
            }
        }
        val procedural = document(
            width = 400, height = 400, profile = androidx, contentDescription = "",
        ) {
            root {
                column(
                    modifier = LayoutModifier()
                        .height(DimensionType.FILL, Float.NaN)
                        .clipRect()
                        .scroll(LayoutModifier.SCROLL_VERTICAL),
                ) {
                    boxLeaf(
                        modifier = LayoutModifier()
                            .width(DimensionType.EXACT, 100f)
                            .height(DimensionType.EXACT, 100f),
                    )
                }
            }
        }
        assertContentEquals(
            procedural,
            produced,
            "Compose-DSL RemoteModifier.scroll(SCROLL_VERTICAL) full doc must byte-match the " +
                "procedural-DSL equivalent (transitive §2 via REM-96).",
        )
    }

    /**
     * `MODIFIER_ALIGN_BY` Stage-2 corpus sub-span anchor vs `c_modifier_align_by_baseline.rc`.
     * **Replaces the prior Stage-1 anchor that pinned line=12.5f** (fictional value — no corpus
     * doc carries that). Reads the 4 line-bytes (big-endian float) from the corpus MODIFIER_ALIGN_BY
     * sub-span, passes `Float.fromBits(corpusBits)` to `RemoteModifier.alignBy(line, flags=0)`,
     * and asserts the emitted sub-span byte-equals the corpus sub-span (raw NaN bits intact —
     * critical because `Float.NaN != Float.NaN` and any signaling-NaN repack would diverge).
     *
     * **Profile:** PROFILE_ANDROIDX | PROFILE_EXPERIMENTAL, map-form api=7 — `MODIFIER_ALIGN_BY`
     * lives in the AndroidX-experimental overlay (REM-96 [LayoutModifier.alignBy] docstring).
     * `c_modifier_align_by_baseline.rc` header property `9=DemoModifierAlignByBaseline` is part
     * of the header (no separate TEXT_DATA op) — `contentDescription=""` keeps the body ops
     * untouched.
     */
    @Test
    fun stage2_subSpan_alignBy_matchesCModifierAlignByBaselineOpBytes() = runBlocking {
        val corpus = RcCorpus.readFixture("corpus/c_modifier_align_by_baseline.rc")
        val corpusAlignBySpan = extractFirstOpSpan(corpus, Operations.MODIFIER_ALIGN_BY)
        // Corpus MODIFIER_ALIGN_BY wire = 1B opcode + 4B float(line) + 4B int(flags) = 9 bytes.
        assertEquals(9, corpusAlignBySpan.size, "MODIFIER_ALIGN_BY wire-size = 9 B (opcode + line + flags)")
        // Extract the corpus `line` raw float bits (big-endian, bytes 1..5). Float.fromBits
        // preserves the exact NaN payload — including any signaling-NaN id-ref encoding.
        val corpusLineBits = ((corpusAlignBySpan[1].toInt() and 0xff) shl 24) or
            ((corpusAlignBySpan[2].toInt() and 0xff) shl 16) or
            ((corpusAlignBySpan[3].toInt() and 0xff) shl 8) or
            (corpusAlignBySpan[4].toInt() and 0xff)
        val corpusLine = Float.fromBits(corpusLineBits)

        val produced = captureSingleRemoteDocument(
            width = 400, height = 400, profile = androidxExperimental, contentDescription = "",
        ) {
            RemoteRoot {
                RemoteBoxLeaf(
                    modifier = RemoteModifier.alignBy(line = corpusLine, flags = 0),
                )
            }
        }
        val producedAlignBySpan = extractFirstOpSpan(produced, Operations.MODIFIER_ALIGN_BY)
        assertContentEquals(
            corpusAlignBySpan,
            producedAlignBySpan,
            "MODIFIER_ALIGN_BY 9-byte op sub-span must byte-match c_modifier_align_by_baseline.rc — " +
                "Float.fromBits/toBits must preserve the raw NaN payload (signaling-NaN-id-ref " +
                "preservation is the classic §2 trap; this pins it).",
        )
    }

    /**
     * `MODIFIER_VISIBILITY` Stage-1 compose==procedural. The `valueId` is a **raw int**
     * (5-byte op: opcode + 4-byte INT) — NOT a NaN-encoded float. The empirical id-ref
     * byte check below asserts that wire shape against the actual emission.
     *
     * **Full-doc Stage-2 deferred to T3:** `c_modifier_visibility.rc` carries an ANIMATED_FLOAT
     * primitive emit (defining the visibility's referenced id) — primitive composables are not
     * part of T2 scope. This is the explicit deferral PO requested.
     */
    @Test
    fun stage1_visibility_composeEqualsProcedural() = runBlocking {
        val produced = captureSingleRemoteDocument(
            width = 400, height = 400, profile = androidx, contentDescription = "",
        ) {
            RemoteRoot {
                RemoteBoxLeaf(
                    modifier = RemoteModifier
                        .width(DimensionType.EXACT, 200f)
                        .height(DimensionType.EXACT, 200f)
                        .background(color = 0xffff0000.toInt())
                        .visibility(valueId = 42),
                )
            }
        }
        val procedural = document(
            width = 400, height = 400, profile = androidx, contentDescription = "",
        ) {
            root {
                boxLeaf(
                    modifier = LayoutModifier()
                        .width(DimensionType.EXACT, 200f)
                        .height(DimensionType.EXACT, 200f)
                        .background(color = 0xffff0000.toInt())
                        .visibility(valueId = 42),
                )
            }
        }
        assertContentEquals(
            procedural,
            produced,
            "Compose-DSL RemoteModifier.visibility(42) full doc must byte-match procedural-DSL " +
                "(full-doc Stage-2 against corpus deferred to T3 — needs FloatExpression primitive; closed by REM-141).",
        )
    }

    /**
     * Empirical id-ref byte check for `MODIFIER_VISIBILITY`. PO's pre-impl watchpoint asked to
     * confirm whether `valueId` is NaN-encoded float or raw int. Probe found: **raw int** (4-byte
     * big-endian, no NaN sign-bit pattern). This pins that finding into a regression test.
     *
     * Wire shape: `0xD3 (MODIFIER_VISIBILITY opcode) + 4 bytes big-endian int valueId`. The exact
     * 5-byte sequence for valueId=42 = `0xD3 0x00 0x00 0x00 0x2A`.
     */
    @Test
    fun visibility_idRefIsRawBigEndianInt_notNaNEncodedFloat() = runBlocking {
        val produced = captureSingleRemoteDocument(
            width = 400, height = 400, profile = androidx, contentDescription = "",
        ) {
            RemoteRoot {
                RemoteBoxLeaf(modifier = RemoteModifier.visibility(valueId = 42))
            }
        }
        val visBytes = extractFirstOpSpan(produced, Operations.MODIFIER_VISIBILITY)
        assertContentEquals(
            byteArrayOf(0xD3.toByte(), 0, 0, 0, 42),
            visBytes,
            "MODIFIER_VISIBILITY wire = opcode + 4-byte big-endian int valueId (raw int, NOT a " +
                "NaN-encoded float). Watchpoint pinned empirically — Bug-#2 properties-check on " +
                "the id-ref encoding closed.",
        )
    }

    // -------------------------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------------------------

    /**
     * Extract the bytes of the **first** operation in [docBytes] whose opcode matches [opcode]
     * (inclusive opcode byte, end-exclusive). Used for ID-decoupled sub-span comparisons against
     * corpus oracles when full-doc reproduction is blocked by out-of-scope machinery.
     */
    private fun extractFirstOpSpan(docBytes: ByteArray, opcode: Int): ByteArray {
        val (_, spans) = DocumentReader.inflateWithTrace(docBytes)
        val span = spans.firstOrNull { it.opcode == opcode }
            ?: error("opcode $opcode (${Operations.name(opcode)}) not found in document")
        return docBytes.copyOfRange(span.byteStart, span.byteEnd)
    }
}
