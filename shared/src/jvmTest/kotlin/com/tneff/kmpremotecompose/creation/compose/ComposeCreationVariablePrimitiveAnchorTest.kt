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
import com.tneff.kmpremotecompose.remote.core.operations.Operations
import com.tneff.kmpremotecompose.remote.core.operations.layout.DimensionType
import com.tneff.kmpremotecompose.remote.creation.Profile
import com.tneff.kmpremotecompose.remote.creation.defaultRcPlatformServices
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals

/**
 * REM-141 S2+S3 — Stage-2 full-doc byte-anchors against the **2 explicitly-deferred T2 corpus
 * fixtures** from REM-130. Closes the deferred posten end-to-end via the new Slot-based
 * variable-primitive composables.
 *
 * **Bug #2 lesson pre-applied (transient probe deleted before this file was written):**
 *   - `c_modifier_visibility.rc` empirically emits `ANIMATED_FLOAT(id=42, value[3])` where the
 *     3 floats are `[asNan(1), 2.0, asNan(0x310005)]` (raw bits `0xff800001`, `0x40000000`,
 *     `0xffb10005`). Tests use `Float.fromBits(rawBits)` so the NaN payload is preserved
 *     bit-exactly through the procedural-DSL helper (W2 watchpoint pinned).
 *   - `c_modifier_dynamic_border.rc` empirically emits `COLOR_EXPRESSIONS(id=42, mode=4 HSV,
 *     alpha=255, h=1.0, s≈0.7, v≈0.9)`. The raw bits for s/v are `0x3F333333` / `0x3F666666` —
 *     `0.7f.toRawBits() == 0x3F333333` and `0.9f.toRawBits() == 0x3F666666` hold in IEEE 754
 *     (Kotlin float literals).
 *
 * **W1 id-allocation order:** virgin allocator (no contentDescription TEXT_DATA op — map-form
 * api=7 PROFILE_ANDROIDX carries the description in the header property only) → first
 * `ids.nextId()` returns 42. The corpus primitive id=42 reproduces.
 *
 * Profile + dimensions verified empirically (400×400, PROFILE_ANDROIDX 0x200, contentDescription="").
 */
class ComposeCreationVariablePrimitiveAnchorTest {

    private val androidx: Profile = Profile(
        operationsProfiles = Operations.PROFILE_ANDROIDX,
        services = defaultRcPlatformServices(),
    )

    // -------------------------------------------------------------------------------------------
    // Stage-2 full-doc anchor — c_modifier_visibility.rc (154 B, PROFILE_ANDROIDX, 400x400)
    // -------------------------------------------------------------------------------------------

    /**
     * `c_modifier_visibility.rc` — `FloatExpression(id=42, value=[asNan(1), 2.0, asNan(0x310005)])`
     * before the BoxLayout; `MODIFIER_VISIBILITY valueId=42` is a back-ref to the FloatExpression id.
     *
     * The Compose-DSL reproduces this via:
     *  1. `rememberRemoteFloatSlot()` allocates a stable slot at call site.
     *  2. `RemoteFloatExpression(slot, rawRpn)` emits the FloatExpression at virgin allocator
     *     state → slot.id = 42.
     *  3. `RemoteBoxLeaf(modifier = ....visibility(slot))` applies the slot at Phase-B apply time;
     *     `slot.id = 42` is already set → `lm.visibility(42)` → `MODIFIER_VISIBILITY valueId=42`.
     */
    @Test
    fun stage2_visibility_matchesCModifierVisibilityOracle_byteForByte() = runBlocking {
        // Raw NaN bits lifted verbatim from the corpus FloatExpression value[3]:
        //   value[0] = 0xff800001 = asNan(1)         (system var id 1)
        //   value[1] = 0x40000000 = 2.0f             (literal)
        //   value[2] = 0xffb10005 = asNan(0x310005)  (RPN operator id 0x310005)
        // Using Float.fromBits preserves the signaling-NaN payload exactly (W2 watchpoint).
        val rpn = floatArrayOf(
            Float.fromBits(0xff800001.toInt()),
            Float.fromBits(0x40000000),
            Float.fromBits(0xffb10005.toInt()),
        )
        val produced = captureSingleRemoteDocument(
            width = 400, height = 400, profile = androidx, contentDescription = "",
        ) {
            RemoteRoot {
                val visSlot = rememberRemoteFloatSlot()
                RemoteFloatExpression(visSlot, value = rpn)
                RemoteBoxLeaf(
                    modifier = RemoteModifier
                        .width(DimensionType.EXACT, 200f)
                        .height(DimensionType.EXACT, 200f)
                        .background(color = 0xffff0000.toInt())
                        .visibility(visSlot),
                )
            }
        }
        assertContentEquals(
            RcCorpus.readFixture("corpus/c_modifier_visibility.rc"),
            produced,
            "Compose-DSL RemoteFloatExpression + RemoteBoxLeaf(.visibility(slot)) must byte-match " +
                "c_modifier_visibility.rc — closes the REM-130 T2-deferred visibility full-doc anchor.",
        )
    }

    /**
     * Stage-3 determinism — two independent captures of the same Compose tree produce
     * byte-identical output. Pins that the writer state (id allocator, etc.) is correctly
     * fresh per capture: no slot.id leak between captures, no allocator drift. The visibility
     * fixture's id-allocation reliance (id=42 must reproduce) makes this a sharper test than
     * the static-modifier MVP determinism would have caught.
     */
    @Test
    fun stage3_visibility_twoCaptures_byteIdentical() = runBlocking {
        val rpn = floatArrayOf(
            Float.fromBits(0xff800001.toInt()),
            Float.fromBits(0x40000000),
            Float.fromBits(0xffb10005.toInt()),
        )
        val first = captureSingleRemoteDocument(
            width = 400, height = 400, profile = androidx, contentDescription = "",
        ) {
            RemoteRoot {
                val visSlot = rememberRemoteFloatSlot()
                RemoteFloatExpression(visSlot, value = rpn)
                RemoteBoxLeaf(
                    modifier = RemoteModifier
                        .width(DimensionType.EXACT, 200f)
                        .height(DimensionType.EXACT, 200f)
                        .background(color = 0xffff0000.toInt())
                        .visibility(visSlot),
                )
            }
        }
        val second = captureSingleRemoteDocument(
            width = 400, height = 400, profile = androidx, contentDescription = "",
        ) {
            RemoteRoot {
                val visSlot = rememberRemoteFloatSlot()
                RemoteFloatExpression(visSlot, value = rpn)
                RemoteBoxLeaf(
                    modifier = RemoteModifier
                        .width(DimensionType.EXACT, 200f)
                        .height(DimensionType.EXACT, 200f)
                        .background(color = 0xffff0000.toInt())
                        .visibility(visSlot),
                )
            }
        }
        assertContentEquals(first, second, "Two captures of the same Compose tree must produce byte-identical output (determinism).")
    }

    // -------------------------------------------------------------------------------------------
    // Stage-2 full-doc anchor — c_modifier_dynamic_border.rc (157 B, PROFILE_ANDROIDX, 400x400)
    // -------------------------------------------------------------------------------------------

    /**
     * `c_modifier_dynamic_border.rc` — `ColorExpressionHsv(id=42, h=1.0, s=0.7, v=0.9, alpha=255)`
     * before the BoxLayout; `MODIFIER_BORDER flags=2, colorId=42, …` is a back-ref. Reproduced via
     * `RemoteColorExpressionHsv(slot, …)` + `RemoteModifier.border(borderWidth, roundedCorner,
     * colorIdSlot = slot, shape, useLegacy)` which routes through the REM-141 S1
     * `LayoutModifier.borderColorRef` helper.
     */
    @Test
    fun stage2_dynamicBorder_matchesCModifierDynamicBorderOracle_byteForByte() = runBlocking {
        // ColorExpressionHsv params from corpus:
        //   hue        = 0x3F800000 = 1.0f
        //   saturation = 0x3F333333 = 0.7f (matches IEEE-754 exactly)
        //   value      = 0x3F666666 = 0.9f (matches IEEE-754 exactly)
        //   alpha      = 255 (param1 high16)
        // Border op: borderWidth=5.0, roundedCorner=1.0, shape=1, useLegacy=true (reserve1=0).
        val produced = captureSingleRemoteDocument(
            width = 400, height = 400, profile = androidx, contentDescription = "",
        ) {
            RemoteRoot {
                val colorSlot = rememberRemoteColorSlot()
                RemoteColorExpressionHsv(
                    slot = colorSlot,
                    hue = 1.0f, saturation = 0.7f, value = 0.9f, alpha = 255,
                )
                RemoteBoxLeaf(
                    modifier = RemoteModifier
                        .width(DimensionType.EXACT, 200f)
                        .height(DimensionType.EXACT, 200f)
                        .border(
                            borderWidth = 5f, roundedCorner = 1f,
                            colorIdSlot = colorSlot, shape = 1,
                        ),
                )
            }
        }
        assertContentEquals(
            RcCorpus.readFixture("corpus/c_modifier_dynamic_border.rc"),
            produced,
            "Compose-DSL RemoteColorExpressionHsv + RemoteBoxLeaf(.border(colorIdSlot=…)) must " +
                "byte-match c_modifier_dynamic_border.rc — closes the REM-130 T2-deferred " +
                "dynamic-color border full-doc anchor.",
        )
    }

    @Test
    fun stage3_dynamicBorder_twoCaptures_byteIdentical() = runBlocking {
        val first = captureSingleRemoteDocument(
            width = 400, height = 400, profile = androidx, contentDescription = "",
        ) {
            RemoteRoot {
                val colorSlot = rememberRemoteColorSlot()
                RemoteColorExpressionHsv(colorSlot, 1.0f, 0.7f, 0.9f, alpha = 255)
                RemoteBoxLeaf(
                    modifier = RemoteModifier
                        .width(DimensionType.EXACT, 200f)
                        .height(DimensionType.EXACT, 200f)
                        .border(5f, 1f, colorSlot, shape = 1),
                )
            }
        }
        val second = captureSingleRemoteDocument(
            width = 400, height = 400, profile = androidx, contentDescription = "",
        ) {
            RemoteRoot {
                val colorSlot = rememberRemoteColorSlot()
                RemoteColorExpressionHsv(colorSlot, 1.0f, 0.7f, 0.9f, alpha = 255)
                RemoteBoxLeaf(
                    modifier = RemoteModifier
                        .width(DimensionType.EXACT, 200f)
                        .height(DimensionType.EXACT, 200f)
                        .border(5f, 1f, colorSlot, shape = 1),
                )
            }
        }
        assertContentEquals(first, second, "Determinism — two captures must be byte-identical.")
    }
}
