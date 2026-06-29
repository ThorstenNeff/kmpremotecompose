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
 * REM-130 — T2 Modifier-Extension anchors (Pfad A: 3 full-doc Stage-2 + 1 corpus sub-span Stage-2 +
 * 3 Stage-1 compose==procedural, gated on assist's REM-96 transitive-corpus-confirm).
 *
 * **Bug #2 lesson pre-applied:** properties-table empirically decoded from each fixture via
 * transient probe before this file was written — every fixture verified for profile (PROFILE_ANDROIDX
 * 0x200 or 0x201 = +PROFILE_EXPERIMENTAL), w/h dims, contentDescription, container shape, and
 * modifier-bytes layout.
 *
 * **Anchor strategy per T2 modifier:**
 *
 * | Modifier            | Strategy           | Why                                                              |
 * |---------------------|--------------------|------------------------------------------------------------------|
 * | `padding`           | Stage-2 full-doc   | `c_modifier_padding.rc` = Column + 2× BoxLeaf, fully buildable. |
 * | `clipRect`          | Stage-2 full-doc   | `c_modifier_clip_rect.rc` = single BoxLeaf, fully buildable.    |
 * | `roundedClipRect`   | Stage-2 full-doc   | `c_modifier_clip_rounded_rect.rc` = single BoxLeaf.             |
 * | `border`            | Stage-2 sub-span   | Corpus uses Column.spacedBy=20 (out of T2 scope). Sub-span:     |
 * |                     |                    | MODIFIER_BORDER op bytes have no ID coupling → byte-stable.     |
 * | `scroll` (h/v)      | Stage-1            | Scroll group uses NaN-id-ref-coupling (FloatConstant id) →      |
 * |                     |                    | sub-span not stable across docs. Transitive via REM-96.         |
 * | `alignBy`           | Stage-1 (+ probe)  | Corpus shows `line=NaN` (likely id-ref) + `flags=0`. Stage-1    |
 * |                     |                    | compose==procedural; gated on assist REM-96 confirm.            |
 * | `visibility`        | Stage-1 (+ id-ref) | Corpus needs ANIMATED_FLOAT primitive (out of T2 scope) →       |
 * |                     |                    | full-doc Stage-2 deferred to T3. Stage-1 compose==procedural    |
 * |                     |                    | + explicit raw-int-id-ref byte check (5-byte op).               |
 *
 * The Stage-1 tests do not on their own constitute a §2 gate (two of our own writers agreeing on
 * bytes is a circular guarantee). They become §2 only when transitively chained to the corpus via
 * REM-96 `LayoutModifierByteTest.{scroll,alignBy,visibility}_*` — that completeness claim is
 * routed to assist for parallel confirmation per the PO dispatch.
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
    // Stage-2 SUB-SPAN anchor (border — ID-decoupled op bytes from `c_modifier_border.rc`)
    // -------------------------------------------------------------------------------------------

    /**
     * `c_modifier_border.rc` (262 B) full-doc reproduction is blocked by Column.spacedBy=20
     * (container-API extension, out of T2 scope). The MODIFIER_BORDER op itself is ID-decoupled
     * (`flags=0, colorId=0, reserve1=0, reserve2=0, borderWidth, roundedCorner, r/g/b/a, shape`)
     * → byte-identical regardless of surrounding container shape. Extract the corpus MODIFIER_BORDER
     * op bytes (the inner 4×4=45-byte run), build a small Compose-DSL doc carrying the same border
     * modifier on a BoxLeaf, extract the same op span, assert byte-equal.
     *
     * **Direct corpus anchor**: this is the strongest form for ID-decoupled ops — Compose-emit
     * bytes = corpus bytes, no transitivity needed. Bypasses the "two-of-our-writers-agreeing"
     * concern flagged in the PO dispatch.
     */
    @Test
    fun stage2_subSpan_border_matchesCModifierBorderOpBytes() = runBlocking {
        val corpus = RcCorpus.readFixture("corpus/c_modifier_border.rc")
        val corpusBorderBytes = extractFirstOpBytes(corpus, Operations.MODIFIER_BORDER)
        assertEquals(45, corpusBorderBytes.size, "MODIFIER_BORDER wire-size on c_modifier_border.rc = 45 B")

        val produced = captureSingleRemoteDocument(
            width = 400, height = 400, profile = androidx, contentDescription = "",
        ) {
            RemoteRoot {
                RemoteBoxLeaf(
                    modifier = RemoteModifier
                        .width(DimensionType.EXACT, 100f)
                        .height(DimensionType.EXACT, 100f)
                        // First border in the corpus: 4 px, roundedCorner 0.1, red, shape=2 (CIRCLE).
                        .border(borderWidth = 4f, roundedCorner = 0.1f, color = 0xffff0000.toInt(), shape = 2),
                )
            }
        }
        val producedBorderBytes = extractFirstOpBytes(produced, Operations.MODIFIER_BORDER)
        assertContentEquals(
            corpusBorderBytes,
            producedBorderBytes,
            "MODIFIER_BORDER op sub-span (45 B, no ID coupling) emitted by RemoteModifier.border " +
                "must byte-match the upstream-corpus bytes — direct §2 anchor.",
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
     * `LayoutModifierByteTest.scroll_horizontal_emitsFullGroup`.
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
     * `MODIFIER_ALIGN_BY` is profile-experimental (PROFILE_ANDROIDX | PROFILE_EXPERIMENTAL, map-form
     * api=7). Stage-1 compose==procedural. The corpus `line=NaN` is empirically the procedural
     * helper's NaN-id-ref encoding for the auto-resolved baseline reference — Stage-2 sub-span is
     * therefore deferred to REM-96 transitive confirm (assist).
     */
    @Test
    fun stage1_alignBy_composeEqualsProcedural() = runBlocking {
        val produced = captureSingleRemoteDocument(
            width = 400, height = 400, profile = androidxExperimental, contentDescription = "",
        ) {
            RemoteRoot {
                RemoteRow(
                    modifier = RemoteModifier.width(DimensionType.FILL, Float.NaN),
                ) {
                    RemoteBoxLeaf(
                        modifier = RemoteModifier
                            .width(DimensionType.EXACT, 50f)
                            .height(DimensionType.EXACT, 50f)
                            .alignBy(line = 12.5f, flags = 0),
                    )
                }
            }
        }
        val procedural = document(
            width = 400, height = 400, profile = androidxExperimental, contentDescription = "",
        ) {
            root {
                row(modifier = LayoutModifier().width(DimensionType.FILL, Float.NaN)) {
                    boxLeaf(
                        modifier = LayoutModifier()
                            .width(DimensionType.EXACT, 50f)
                            .height(DimensionType.EXACT, 50f)
                            .alignBy(line = 12.5f, flags = 0),
                    )
                }
            }
        }
        assertContentEquals(
            procedural,
            produced,
            "Compose-DSL RemoteModifier.alignBy(12.5, 0) full doc must byte-match procedural-DSL.",
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
                "(full-doc Stage-2 against corpus deferred to T3 — needs ANIMATED_FLOAT primitive).",
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
        val visBytes = extractFirstOpBytes(produced, Operations.MODIFIER_VISIBILITY)
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
    private fun extractFirstOpBytes(docBytes: ByteArray, opcode: Int): ByteArray {
        val (_, spans) = DocumentReader.inflateWithTrace(docBytes)
        val span = spans.firstOrNull { it.opcode == opcode }
            ?: error("opcode $opcode (${Operations.name(opcode)}) not found in document")
        return docBytes.copyOfRange(span.byteStart, span.byteEnd)
    }
}
