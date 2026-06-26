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
package com.tneff.kmpremotecompose.remote.player

import com.tneff.kmpremotecompose.remote.player.compose.ParitySupport
import com.tneff.kmpremotecompose.remote.player.compose.TEXT_PARAMETER_PARITY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * REM-32: guards the complex-text parity classification (the GAP-1 surface) — headless, pure data.
 * The actual CMP text rendering runs on the iOS gate (Skiko), see `ComposeTextRendererIosTest`.
 */
class TextParityTest {

    @Test
    fun parityTable_isConsistentAndComplete() {
        // no duplicate parameter rows.
        val names = TEXT_PARAMETER_PARITY.map { it.parameter }
        assertEquals(names.size, names.toSet().size, "duplicate parity rows: $names")
        // every row carries a CMP mapping note.
        assertTrue(TEXT_PARAMETER_PARITY.all { it.cmpMapping.isNotBlank() })
        // every non-SUPPORTED row carries a rationale (no silent approximation).
        assertTrue(
            TEXT_PARAMETER_PARITY.filter { it.support != ParitySupport.SUPPORTED }.all { it.note.isNotBlank() },
            "approximated/unsupported rows must justify themselves",
        )
    }

    @Test
    fun parityTable_pinsTheKnownGapClassifications() {
        fun support(param: String): ParitySupport =
            TEXT_PARAMETER_PARITY.first { it.parameter == param }.support

        // Basis covers these (GAP-1 says CMP has 1:1 equivalents).
        assertEquals(ParitySupport.SUPPORTED, support("maxLines"))
        assertEquals(ParitySupport.SUPPORTED, support("letterSpacing"))
        assertEquals(ParitySupport.SUPPORTED, support("underline"))
        // The real deferred gaps (L2-D1, Skiko Paragraph direct).
        assertEquals(ParitySupport.UNSUPPORTED, support("hyphenationFrequency"))
        assertEquals(ParitySupport.UNSUPPORTED, support("justificationMode"))
        // GAP-2 measure divergence is an explicit approximation.
        assertEquals(ParitySupport.APPROXIMATED, support("getTextBounds pixel parity"))
    }
}
