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
package com.tneff.kmpremotecompose.remote.creation

import com.tneff.kmpremotecompose.remote.core.document.DocumentReader
import com.tneff.kmpremotecompose.remote.core.operations.IntegerConstant
import com.tneff.kmpremotecompose.remote.core.operations.Operations
import com.tneff.kmpremotecompose.remote.core.operations.RootContentDescription
import com.tneff.kmpremotecompose.remote.core.operations.TextData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * REM-146 — DocumentDsl content-description id-reservation regression pin.
 *
 * Three behaviours must hold simultaneously (all empirically corpus-grounded):
 *
 *   1. **flat-form (PROFILE_BASELINE / api=6) + non-empty description**: reserve id=42, emit
 *      `DATA_TEXT(42, desc) + ROOT_CONTENT_DESCRIPTION(42)` as the first body ops. Anchor:
 *      `procedure_simple2.rc` (REM-128). Body's next user `ids.nextId()` = 43.
 *
 *   2. **map-form (PROFILE_ANDROIDX / api=7) + empty description ("")**: header property 9 = ""
 *      is encoded; NO id reservation; first user `ids.nextId()` = 42. Anchor:
 *      `c_modifier_visibility.rc` (REM-141 — FloatExpression at id=42).
 *
 *   3. **map-form (PROFILE_ANDROIDX|EXPERIMENTAL / api=7) + non-empty description**: header
 *      property 9 = `"DemoModifierOnTouchDown"` etc.; NO id reservation; first user
 *      `ids.nextId()` = 42. Anchor: `c_modifier_on_touch_down.rc` (REM-145 / REM-146 W12 verify).
 *
 * REM-141's original fix gated reservation on `isNotEmpty()` (covered case 2). REM-146 adds the
 * `flatForm` gate (covers case 3) so map-form documents with non-empty descriptions match
 * upstream's allocation order — id=42 becomes the first user allocation, not 43.
 */
class DocumentDslMapFormIdReservationTest {

    private val baseline = Profile.Baseline
    private val androidx = Profile(
        operationsProfiles = Operations.PROFILE_ANDROIDX,
        services = defaultRcPlatformServices(),
    )
    private val androidxExperimental = Profile(
        operationsProfiles = Operations.PROFILE_ANDROIDX or Operations.PROFILE_EXPERIMENTAL,
        services = defaultRcPlatformServices(),
    )

    /**
     * Case 1 — flat-form + non-empty description: reservation MUST happen; body's first user id = 43.
     * Mirrors `procedure_simple2.rc` (REM-128 oracle) — without the reservation the body refs
     * `DATA_TEXT(42) + ROOT_CONTENT_DESCRIPTION(42)` would collide with user ids.
     */
    @Test
    fun flatForm_nonEmptyDescription_reserves_id42_andEmitsBodyOps() {
        val bytes = document(
            width = 200, height = 200,
            profile = baseline,
            contentDescription = "Clock",
        ) {
            val firstUserId = ids.nextId()
            add(IntegerConstant(firstUserId, 1))
            assertEquals(43, firstUserId, "flat-form reserves id=42 for desc → first user id = 43")
        }
        val ops = DocumentReader.inflate(bytes).operations
        // The body emits TextData(42, "Clock") + RootContentDescription(42) before user ops.
        val textOp = ops.first { it is TextData } as TextData
        val descOp = ops.first { it is RootContentDescription } as RootContentDescription
        assertEquals(42, textOp.id)
        assertEquals("Clock", textOp.text)
        assertEquals(42, descOp.contentDescriptionId)
        // The user IntegerConstant has id=43 (post-reservation).
        val intOp = ops.first { it is IntegerConstant } as IntegerConstant
        assertEquals(43, intOp.id)
    }

    /**
     * Case 2 — map-form + empty description: no reservation; first user id = 42. Already covered
     * by REM-141's `isNotEmpty()` gate; pinned here for completeness so a future regression of
     * either gate is caught at this single suite.
     */
    @Test
    fun mapForm_emptyDescription_doesNotReserve_firstUserIdIs42() {
        val bytes = document(
            width = 200, height = 200,
            profile = androidx,
            contentDescription = "",
        ) {
            val firstUserId = ids.nextId()
            add(IntegerConstant(firstUserId, 1))
            assertEquals(42, firstUserId, "map-form + empty desc → no reservation → first user id = 42")
        }
        val ops = DocumentReader.inflate(bytes).operations
        // No TextData or RootContentDescription body op — description is in the header property.
        assertTrue(ops.none { it is TextData }, "map-form: empty desc → no DATA_TEXT body op")
        assertTrue(ops.none { it is RootContentDescription }, "map-form: empty desc → no ROOT_CONTENT_DESCRIPTION body op")
        val intOp = ops.first { it is IntegerConstant } as IntegerConstant
        assertEquals(42, intOp.id, "first user IntegerConstant takes id=42 directly")
    }

    /**
     * Case 3 — REM-146 NEW: map-form + non-empty description must NOT reserve. Corresponds to
     * `c_modifier_on_touch_down.rc` (and the other 2 touch fixtures); without this fix REM-145's
     * Stage-2 anchors would diverge at byte 42→43 (corpus DATA_INT id=42 vs DSL emit id=43).
     */
    @Test
    fun mapForm_nonEmptyDescription_doesNotReserve_firstUserIdIs42() {
        val bytes = document(
            width = 500, height = 500,
            profile = androidxExperimental,
            contentDescription = "DemoModifierOnTouchDown",
        ) {
            val firstUserId = ids.nextId()
            add(IntegerConstant(firstUserId, 0))
            assertEquals(
                42, firstUserId,
                "map-form + non-empty desc → description lives in header → no reservation → " +
                    "first user id = 42 (matches c_modifier_on_touch_down.rc empirical decode)",
            )
        }
        val ops = DocumentReader.inflate(bytes).operations
        // The description should NOT be emitted as a body op — it's a header property.
        assertTrue(ops.none { it is TextData }, "map-form non-empty desc: no body DATA_TEXT (header carries it)")
        assertTrue(ops.none { it is RootContentDescription }, "map-form non-empty desc: no body ROOT_CONTENT_DESCRIPTION")
        val intOp = ops.first { it is IntegerConstant } as IntegerConstant
        assertEquals(42, intOp.id, "REM-146 fix: id=42 is the first user allocation, matches corpus")
    }
}
