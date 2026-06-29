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

import androidx.compose.ui.graphics.Color
import com.tneff.kmpremotecompose.remote.core.operations.layout.DimensionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * REM-128 S3a — structural equality / hashCode tests for [RemoteModifier]. TechSpec §2 Q4 lock:
 * `RemoteModifier` holds a list of **data-carrying elements** so Compose
 * `ComposeNode { update { set(modifier) } }` change-detection can skip unchanged recompositions —
 * a lambda-only buffer would compare reference-only and always trigger change-detection,
 * defeating Compose's stability optimisations and painting S4-streaming into a corner.
 */
class RemoteModifierEqualsTest {

    @Test
    fun sameChain_isEqual_andSameHashCode() {
        val a = RemoteModifier.width(DimensionType.EXACT, 100f).height(DimensionType.EXACT, 50f)
        val b = RemoteModifier.width(DimensionType.EXACT, 100f).height(DimensionType.EXACT, 50f)
        assertEquals(a, b, "RemoteModifier with identical element chain must compare equal")
        assertEquals(a.hashCode(), b.hashCode(), "Equal RemoteModifiers must have equal hashCode")
    }

    @Test
    fun differentValue_isNotEqual() {
        val a = RemoteModifier.width(DimensionType.EXACT, 100f)
        val b = RemoteModifier.width(DimensionType.EXACT, 101f)
        assertNotEquals(a, b, "Different value in same element type must not compare equal")
    }

    @Test
    fun differentOrder_isNotEqual() {
        // Element order matters (= emission order matters for byte-equality).
        val widthFirst = RemoteModifier.width(DimensionType.EXACT, 100f).height(DimensionType.EXACT, 50f)
        val heightFirst = RemoteModifier.height(DimensionType.EXACT, 50f).width(DimensionType.EXACT, 100f)
        assertNotEquals(
            widthFirst,
            heightFirst,
            "Element order matters — different chain order must not compare equal (would imply " +
                "different emission order = different bytes).",
        )
    }

    @Test
    fun emptyModifier_equalsCompanion() {
        val empty = RemoteModifier
        val explicitly = RemoteModifier
        assertEquals(empty, explicitly, "Companion-as-empty is the canonical empty RemoteModifier")
    }

    @Test
    fun backgroundColorOverload_equalsBackgroundIntOverload() {
        // Q1 structural guard: Color.toArgb() → same Int → same BackgroundElement floats → equal.
        val viaInt = RemoteModifier.background(color = 0xffff0000.toInt())
        val viaColor = RemoteModifier.background(Color(0xffff0000.toInt()))
        assertEquals(
            viaInt,
            viaColor,
            "background(Color) must compare equal to background(Int) for the same ARGB value — " +
                "Q1 round-trip guard.",
        )
        assertEquals(viaInt.hashCode(), viaColor.hashCode())
    }

    @Test
    fun chainImmutability_originalUnchanged() {
        val base = RemoteModifier.width(DimensionType.EXACT, 100f)
        val extended = base.height(DimensionType.EXACT, 50f)
        // base must NOT have height appended (immutable chain) — proves the .height() call
        // returned a NEW RemoteModifier instead of mutating the shared one.
        assertNotEquals(base, extended, "Chain calls return a new RemoteModifier (immutable).")
    }
}
