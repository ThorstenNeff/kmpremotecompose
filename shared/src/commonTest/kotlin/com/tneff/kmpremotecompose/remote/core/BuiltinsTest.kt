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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The central builtin registrar resolves every op group (REM-4 data + REM-5 draws/layout) for real
 * document decoding — the REM-6 acceptance for the single entry point.
 */
class BuiltinsTest {

    @AfterTest
    fun cleanup() {
        Operations.resetReaders()
    }

    @Test
    fun register_resolvesAllGroupsInBothBaseLayers() {
        Operations.resetReaders()
        Builtins.register()

        val groupA = listOf(
            Operations.HEADER, Operations.DATA_TEXT, Operations.DATA_FLOAT, Operations.DATA_INT,
            Operations.COLOR_CONSTANT, Operations.DATA_BITMAP, Operations.TEXT_FROM_FLOAT,
            // REM-19 P2 base ops.
            Operations.ANIMATED_FLOAT, Operations.NAMED_VARIABLE, Operations.COLOR_EXPRESSIONS,
            Operations.FLOAT_LIST, Operations.ID_MAP,
            Operations.COMPONENT_VALUE, // SB2
            // REM-22 SB1.
            Operations.MATRIX_SAVE, Operations.TEXT_MERGE, Operations.ID_LIST, Operations.THEME,
        )
        val groupB = listOf(
            // Checkpoint base ops.
            Operations.DRAW_CIRCLE, Operations.DRAW_RECT, Operations.DRAW_LINE, Operations.DRAW_OVAL,
            Operations.LAYOUT_ROOT, Operations.CONTAINER_END, Operations.COMPONENT_START,
            Operations.MODIFIER_WIDTH, Operations.MODIFIER_HEIGHT, Operations.MODIFIER_CLICK,
            // REM-5-full base ops (REM-13 coverage gap): a deleted registerInBase fails here at the
            // unit level instead of only at the REM-7 corpus gate.
            Operations.DRAW_ROUND_RECT, Operations.DRAW_ARC, Operations.DRAW_SECTOR,
            Operations.PAINT_VALUES, Operations.MODIFIER_BACKGROUND,
            Operations.LAYOUT_BOX, Operations.LAYOUT_CONTENT,
            Operations.DRAW_TEXT_RUN, Operations.DATA_PATH, Operations.DRAW_PATH,
            Operations.LAYOUT_COLUMN, // REM-15
            Operations.DRAW_TEXT_ANCHOR, // REM-16 (F3)
            Operations.MODIFIER_PADDING, // REM-17 (F6)
            // REM-20 P2 group-B sub-batch 1 (base ops).
            Operations.LAYOUT_CANVAS, Operations.LAYOUT_CANVAS_CONTENT, Operations.LAYOUT_ROW,
            Operations.MODIFIER_CLIP_RECT, Operations.LAYOUT_COLLAPSIBLE_ROW,
            Operations.VALUE_STRING_CHANGE_ACTION, Operations.ACCESSIBILITY_SEMANTICS,
        )
        for (op in groupA + groupB) {
            assertTrue(Operations.isValid(op, 7, 0), "${Operations.name(op)} not resolvable at api 7")
            assertTrue(Operations.isValid(op, 6, 0), "${Operations.name(op)} not resolvable at api 6")
        }
    }

    /** `CORE_TEXT` (REM-17, F6) is an overlay op: resolves under androidx/widgets, not the v7 baseline. */
    @Test
    fun register_coreText_isAndroidxAndWidgetsOverlay() {
        Operations.resetReaders()
        Builtins.register()
        val op = Operations.CORE_TEXT
        assertFalse(Operations.isValid(op, 7, 0), "must not resolve at v7 baseline")
        assertTrue(Operations.isValid(op, 7, Operations.PROFILE_ANDROIDX), "androidx overlay")
        assertTrue(Operations.isValid(op, 7, Operations.PROFILE_WIDGETS), "widgets overlay")
    }

    /**
     * `ROOT_CONTENT_BEHAVIOR` is the one REM-5 op that is NOT a v7 base member: it resolves in the
     * API-6 base and (API ≥ 7) only under the deprecated overlays — mirroring its layer placement.
     * Catches a missing/mis-layered registration of this special-case op.
     */
    @Test
    fun register_rootContentBehavior_isV6AndDeprecatedOverlayOnly() {
        Operations.resetReaders()
        Builtins.register()
        val op = Operations.ROOT_CONTENT_BEHAVIOR
        assertTrue(Operations.isValid(op, 6, 0), "ROOT_CONTENT_BEHAVIOR not resolvable at api 6")
        assertFalse(Operations.isValid(op, 7, 0), "must not resolve at api 7 baseline")
        assertFalse(
            Operations.isValid(op, 7, Operations.PROFILE_ANDROIDX),
            "must not resolve under plain androidx (non-deprecated)",
        )
        assertTrue(
            Operations.isValid(op, 7, Operations.PROFILE_ANDROIDX or Operations.PROFILE_DEPRECATED),
            "must resolve under androidx + deprecated overlay",
        )
        assertTrue(
            Operations.isValid(op, 7, Operations.PROFILE_WIDGETS or Operations.PROFILE_DEPRECATED),
            "must resolve under widgets + deprecated overlay",
        )
    }

    @Test
    fun register_isIdempotent() {
        Operations.resetReaders()
        Builtins.register()
        val afterFirst = Operations.registeredOpcodes(Layer.V7_BASE)
        Builtins.register() // second call must be a no-op
        assertEquals(afterFirst, Operations.registeredOpcodes(Layer.V7_BASE))
    }

    @Test
    fun resetReaders_reArmsRegistration() {
        Builtins.register()
        Operations.resetReaders()
        assertTrue(Operations.registeredOpcodes(Layer.V7_BASE).isEmpty())
        Builtins.register() // re-arms after reset
        assertTrue(Operations.isValid(Operations.DRAW_CIRCLE, 7, 0))
    }
}
