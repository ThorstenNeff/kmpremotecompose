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
            Operations.COLOR_CONSTANT, Operations.DATA_BITMAP,
        )
        val groupB = listOf(
            Operations.DRAW_CIRCLE, Operations.DRAW_RECT, Operations.DRAW_LINE, Operations.DRAW_OVAL,
            Operations.LAYOUT_ROOT, Operations.CONTAINER_END, Operations.COMPONENT_START,
            Operations.MODIFIER_WIDTH, Operations.MODIFIER_HEIGHT, Operations.MODIFIER_CLICK,
        )
        for (op in groupA + groupB) {
            assertTrue(Operations.isValid(op, 7, 0), "${Operations.name(op)} not resolvable at api 7")
            assertTrue(Operations.isValid(op, 6, 0), "${Operations.name(op)} not resolvable at api 6")
        }
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
