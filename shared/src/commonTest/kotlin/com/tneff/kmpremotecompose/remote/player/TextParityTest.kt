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
        fun row(param: String) = TEXT_PARAMETER_PARITY.first { it.parameter == param }
        fun support(param: String): ParitySupport = row(param).support

        // CMP has 1:1 equivalents the corpus actually uses (REM-74 wrap path covers them).
        assertEquals(ParitySupport.SUPPORTED, support("maxLines"))
        assertEquals(ParitySupport.SUPPORTED, support("maxWidth (wrap)"))
        assertEquals(ParitySupport.SUPPORTED, support("overflow=ellipsis(END)"))
        // CMP-common limits (granular Android-only params).
        assertEquals(ParitySupport.UNSUPPORTED, support("hyphenationFrequency (levels)"))
        assertEquals(ParitySupport.UNSUPPORTED, support("justificationMode (INTER_WORD) + TextAlign.Justify"))
        // GAP-2 measure divergence is an explicit approximation.
        assertEquals(ParitySupport.APPROXIMATED, support("getTextBounds pixel parity"))
    }

    @Test
    fun everyUnsupportedRow_isCosmetic_unexercisedByCorpus() {
        // REM-74 / D1 thesis: rendering complex text via CMP-common (not raw Skiko Paragraph) is acceptable
        // ONLY because no [UNSUPPORTED] CMP-limit is exercised by the corpus. The decode proof lives in
        // Rem74WrapDecisionTest.corpusNeverExercisesCmpLimitedGranularParams; this pins the classification.
        val exercisedGaps = TEXT_PARAMETER_PARITY
            .filter { it.support == ParitySupport.UNSUPPORTED && it.corpusExercised }
        assertTrue(
            exercisedGaps.isEmpty(),
            "an UNSUPPORTED CMP-limit is marked corpus-exercised → no longer cosmetic, escalate: " +
                exercisedGaps.map { it.parameter },
        )
    }
}
