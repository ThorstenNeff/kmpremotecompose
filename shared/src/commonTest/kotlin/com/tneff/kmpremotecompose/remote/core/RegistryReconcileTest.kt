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
package com.tneff.kmpremotecompose.remote.core

import com.tneff.kmpremotecompose.remote.core.operations.Builtins
import com.tneff.kmpremotecompose.remote.core.operations.Operations
import com.tneff.kmpremotecompose.remote.core.operations.Operations.Layer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the registry's per-layer opcode membership against an independent transcription of upstream
 * `Operations.java`. Wrong layer membership breaks profile gating silently, so every layer set and
 * the composition rules are locked here. Independent from [Operations.MEMBERSHIP]: this file is the
 * second source — drift between the two fails the build.
 */
class RegistryReconcileTest {

    @AfterTest
    fun cleanup() {
        Operations.resetReaders()
    }

    // Independently transcribed expected overlay sets (from Operations.java create* functions).
    private val expectedAndroidx = setOf(
        Operations.MATRIX_FROM_PATH, Operations.TEXT_SUBTEXT, Operations.BITMAP_TEXT_MEASURE,
        Operations.DRAW_BITMAP_FONT_TEXT_RUN_ON_PATH, Operations.DRAW_BITMAP_TEXT_ANCHORED,
        Operations.DATA_SHADER, Operations.DATA_FONT, Operations.DRAW_TO_BITMAP, Operations.WAKE_IN,
        Operations.ID_LOOKUP, Operations.PATH_EXPRESSION, Operations.PARTICLE_COMPARE,
        Operations.DYNAMIC_FLOAT_LIST, Operations.UPDATE_DYNAMIC_FLOAT_LIST, Operations.SKIP,
        Operations.CORE_TEXT, Operations.TEXT_STYLE, Operations.TEXT_TRANSFORM, Operations.COLOR_THEME,
    )
    private val expectedAndroidxExperimental = setOf(
        Operations.MODIFIER_ALIGN_BY, Operations.LAYOUT_COMPUTE, Operations.LAYOUT_FLOW,
        Operations.MODIFIER_MULTI_CLICK, Operations.MODIFIER_DIMENSION_CONSTRAINTS,
        Operations.REFERENCED_OPERATIONS, Operations.INCLUDE_REFERENCED_OPERATIONS,
        Operations.MACRO_DEFINE, Operations.MACRO_CALL, Operations.MACRO_ARGUMENT,
        Operations.MACRO_BLOCK, Operations.MACRO_FOR_EACH, Operations.LAYOUT_CUSTOM,
        Operations.DATA_SOUND, Operations.SOUND_EXPRESSION, Operations.PLAY_SOUND,
    )
    private val expectedWidgets = setOf(
        Operations.MATRIX_FROM_PATH, Operations.TEXT_SUBTEXT, Operations.BITMAP_TEXT_MEASURE,
        Operations.DRAW_BITMAP_FONT_TEXT_RUN_ON_PATH, Operations.DRAW_BITMAP_TEXT_ANCHORED,
        Operations.DRAW_TO_BITMAP, Operations.WAKE_IN, Operations.ID_LOOKUP, Operations.PATH_EXPRESSION,
        Operations.PARTICLE_COMPARE, Operations.DYNAMIC_FLOAT_LIST, Operations.UPDATE_DYNAMIC_FLOAT_LIST,
        Operations.SKIP, Operations.CORE_TEXT, Operations.TEXT_STYLE, Operations.TEXT_TRANSFORM,
        Operations.COLOR_THEME,
    )
    private val expectedWidgetsExperimental = setOf(
        Operations.MODIFIER_ALIGN_BY, Operations.LAYOUT_COMPUTE, Operations.LAYOUT_FLOW,
        Operations.MODIFIER_MULTI_CLICK, Operations.MODIFIER_DIMENSION_CONSTRAINTS,
        Operations.REFERENCED_OPERATIONS, Operations.INCLUDE_REFERENCED_OPERATIONS,
        Operations.MACRO_DEFINE, Operations.MACRO_CALL, Operations.MACRO_ARGUMENT,
        Operations.MACRO_BLOCK, Operations.MACRO_FOR_EACH, Operations.DATA_SOUND,
        Operations.SOUND_EXPRESSION, Operations.PLAY_SOUND,
    )

    @Test
    fun overlaySets_matchOperationsJava() {
        assertEquals(expectedAndroidx, Operations.MEMBERSHIP.getValue(Layer.V7_ANDROIDX))
        assertEquals(expectedAndroidxExperimental, Operations.MEMBERSHIP.getValue(Layer.V7_ANDROIDX_EXPERIMENTAL))
        assertEquals(expectedWidgets, Operations.MEMBERSHIP.getValue(Layer.V7_WIDGETS))
        assertEquals(expectedWidgetsExperimental, Operations.MEMBERSHIP.getValue(Layer.V7_WIDGETS_EXPERIMENTAL))
        assertEquals(setOf(Operations.ROOT_CONTENT_BEHAVIOR), Operations.MEMBERSHIP.getValue(Layer.V7_ANDROIDX_DEPRECATED))
        assertEquals(setOf(Operations.ROOT_CONTENT_BEHAVIOR), Operations.MEMBERSHIP.getValue(Layer.V7_WIDGETS_DEPRECATED))
    }

    @Test
    fun baseLayerSizes_areDefaultSetPlusExtras() {
        // Default set = 123. V6 adds 2 (shader, behavior); V7_BASE adds 4 always-on.
        val v6 = Operations.MEMBERSHIP.getValue(Layer.V6)
        val v7 = Operations.MEMBERSHIP.getValue(Layer.V7_BASE)
        assertEquals(125, v6.size)
        assertEquals(127, v7.size)
        // The shared default set is exactly the intersection of V6 and V7_BASE (123).
        assertEquals(123, (v6 intersect v7).size)
    }

    @Test
    fun v6Extras_andV7BaseExtras_areCorrect() {
        val v6 = Operations.MEMBERSHIP.getValue(Layer.V6)
        val v7 = Operations.MEMBERSHIP.getValue(Layer.V7_BASE)
        // V6-only extras.
        assertTrue(Operations.DATA_SHADER in v6)
        assertTrue(Operations.ROOT_CONTENT_BEHAVIOR in v6)
        // V7_BASE always-on extras, absent from V6.
        for (op in setOf(Operations.REM, Operations.MATRIX_CONSTANT, Operations.MATRIX_EXPRESSION, Operations.MATRIX_VECTOR_MATH)) {
            assertTrue(op in v7, "expected $op in V7_BASE")
            assertFalse(op in v6, "did not expect $op in V6")
        }
    }

    @Test
    fun shaderAndBehavior_areV6base_butV7overlay_notV7base() {
        // The assist-flagged trap: DATA_SHADER / ROOT_CONTENT_BEHAVIOR are V6-base, but in V7 they
        // live in the overlays, NEVER in V7_BASE.
        val v7base = Operations.MEMBERSHIP.getValue(Layer.V7_BASE)
        assertFalse(Operations.DATA_SHADER in v7base)
        assertFalse(Operations.ROOT_CONTENT_BEHAVIOR in v7base)
        assertTrue(Operations.DATA_SHADER in Operations.MEMBERSHIP.getValue(Layer.V7_ANDROIDX))
        assertFalse(Operations.DATA_SHADER in Operations.MEMBERSHIP.getValue(Layer.V7_WIDGETS)) // androidx-only
        assertTrue(Operations.ROOT_CONTENT_BEHAVIOR in Operations.MEMBERSHIP.getValue(Layer.V7_ANDROIDX_DEPRECATED))
    }

    @Test
    fun membershipComposition_followsProfileRules() {
        assertEquals(Operations.MEMBERSHIP.getValue(Layer.V6), Operations.membershipFor(6, 0))
        assertEquals(Operations.MEMBERSHIP.getValue(Layer.V7_BASE), Operations.membershipFor(7, 0))

        val androidx = Operations.membershipFor(7, Operations.PROFILE_ANDROIDX)
        assertEquals(Operations.MEMBERSHIP.getValue(Layer.V7_BASE) + expectedAndroidx, androidx)

        val androidxExp = Operations.membershipFor(7, Operations.PROFILE_ANDROIDX or Operations.PROFILE_EXPERIMENTAL)
        assertTrue(androidxExp.containsAll(expectedAndroidxExperimental))

        // Multiple profiles intersect the overlays: only ops valid in BOTH resolve.
        val both = Operations.membershipFor(7, Operations.PROFILE_ANDROIDX or Operations.PROFILE_WIDGETS)
        assertTrue(Operations.CORE_TEXT in both) // in androidx AND widgets
        assertFalse(Operations.DATA_SHADER in both) // androidx-only → excluded by intersection
    }

    @Test
    fun androidNativeProfile_isRejectedInMembership() {
        assertFailsWith<UnsupportedOperationException> {
            Operations.membershipFor(7, Operations.PROFILE_ANDROID_NATIVE)
        }
    }

    @Test
    fun registeredReaders_stayWithinTheirLayerMembership() {
        Operations.resetReaders()
        Builtins.register()
        for (layer in Layer.entries) {
            val registered = Operations.registeredOpcodes(layer)
            val members = Operations.MEMBERSHIP.getValue(layer)
            assertTrue(members.containsAll(registered), "layer $layer registered non-members: ${registered - members}")
        }
        // The group-A data ops + header are registered in both base layers.
        val expectedBase = setOf(
            Operations.HEADER, Operations.DATA_TEXT, Operations.DATA_FLOAT, Operations.DATA_INT,
            Operations.COLOR_CONSTANT, Operations.DATA_BITMAP, Operations.ROOT_CONTENT_DESCRIPTION,
            Operations.TEXT_FROM_FLOAT,
        )
        assertTrue(Operations.registeredOpcodes(Layer.V6).containsAll(expectedBase))
        assertTrue(Operations.registeredOpcodes(Layer.V7_BASE).containsAll(expectedBase))
    }

    @Test
    fun baseDrawOps_areInBothBaseLayers_notOverlays() {
        // Coordination pin (REM-4 ⇄ REM-5): dev-2's base draw ops belong to V6 AND V7_BASE, never
        // an overlay. Pinned here so a misclassification on either side fails this shared test.
        val baseDrawOps = setOf(
            Operations.DRAW_RECT, Operations.DRAW_CIRCLE, Operations.DRAW_LINE, Operations.DRAW_OVAL,
            Operations.DRAW_PATH, Operations.DRAW_ROUND_RECT, Operations.DRAW_ARC,
            Operations.DRAW_TEXT_RUN,
        )
        val v6 = Operations.MEMBERSHIP.getValue(Layer.V6)
        val v7base = Operations.MEMBERSHIP.getValue(Layer.V7_BASE)
        val overlayLayers = setOf(
            Layer.V7_ANDROIDX, Layer.V7_ANDROIDX_EXPERIMENTAL, Layer.V7_ANDROIDX_DEPRECATED,
            Layer.V7_WIDGETS, Layer.V7_WIDGETS_EXPERIMENTAL, Layer.V7_WIDGETS_DEPRECATED,
        )
        for (op in baseDrawOps) {
            assertTrue(op in v6, "expected ${Operations.name(op)} in V6")
            assertTrue(op in v7base, "expected ${Operations.name(op)} in V7_BASE")
            for (layer in overlayLayers) {
                assertFalse(op in Operations.MEMBERSHIP.getValue(layer), "${Operations.name(op)} must not be in $layer")
            }
        }
    }

    @Test
    fun screenshottestProfile_resolvesCoreText() {
        // The screenshottest.rc fixture declares profiles=ANDROIDX (512) and uses CORE_TEXT (239),
        // which is an androidx-overlay op — it must be a member under that profile.
        assertTrue(Operations.CORE_TEXT in Operations.membershipFor(7, Operations.PROFILE_ANDROIDX))
        assertFalse(Operations.CORE_TEXT in Operations.membershipFor(7, Operations.PROFILE_BASELINE))
    }
}
