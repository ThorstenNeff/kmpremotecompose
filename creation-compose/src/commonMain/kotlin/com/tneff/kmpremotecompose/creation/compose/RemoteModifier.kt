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
import androidx.compose.ui.graphics.toArgb
import com.tneff.kmpremotecompose.remote.core.operations.layout.DimensionType
import com.tneff.kmpremotecompose.remote.creation.LayoutModifier

/**
 * REM-128 S3 — Compose-creation modifier surface (Path-A wrapper, mirror upstream `RemoteModifier`).
 * **Holds a structural list of data-carrying [RemoteModifierElement]s** (NOT opaque lambdas) so that
 * Compose `ComposeNode { update { set(modifier) } }` change-detection can skip unchanged
 * recompositions — and so the door to S4 streaming (where recomposition stability is load-bearing)
 * stays open. TechSpec §2 Q4 lock.
 *
 * **No second encoder (TechSpec §0 / §4):** each element knows how to inject itself into a
 * [LayoutModifier] via its public chain API ([RemoteModifierElement.applyToLayoutModifier]); the
 * Phase-B render walk converts the RemoteModifier to a LayoutModifier via [toLayoutModifier] and
 * passes it to the byte-proven REM-96 container helpers (`box()` / `boxLeaf()` / `column()` /
 * `row()`). Bytes come from the byte-proven path, exactly as S2.
 *
 * **Why immutable chain (returns new RemoteModifier per `.foo()`):** mirror Compose's `Modifier` —
 * a `Modifier.fillMaxWidth().background(Color.Red)` style chain composes by building a new value.
 * Avoids the shared-mutable-builder trap when the same `RemoteModifier` is captured into multiple
 * Compose call-sites in the same composition.
 *
 * **Structural equality:** lists of [RemoteModifierElement] (all data classes) — two
 * `RemoteModifier`s are equal iff their element lists are equal. Two `Modifier.background(red)`
 * calls compare equal (same element); two semantically-equivalent paths like
 * `.background(Color.Red)` and `.background(0xFFFF0000.toInt())` compare equal **iff the resulting
 * BackgroundElement floats are equal** (covered by the Color round-trip guard at the §3 anchor).
 */
open class RemoteModifier private constructor(internal val elements: List<RemoteModifierElement>) {

    /**
     * `MODIFIER_WIDTH` — dimension width (T1 in the census). [value] is in dp for `EXACT`/`EXACT_DP`,
     * ignored for `FILL`/`WRAP`/etc. Returns a new RemoteModifier with the element appended.
     */
    fun width(type: DimensionType, value: Number = 0f): RemoteModifier =
        RemoteModifier(elements + WidthElement(type, value.toFloat()))

    /** `MODIFIER_HEIGHT` — dimension height. */
    fun height(type: DimensionType, value: Number = 0f): RemoteModifier =
        RemoteModifier(elements + HeightElement(type, value.toFloat()))

    /**
     * `MODIFIER_BACKGROUND` — ARGB-int form (REM-96-canonical). Decomposes [color] into normalised
     * `r/g/b/a` floats and stores the element in canonical float form so `background(0xFFFF0000.toInt())`
     * compares equal to `background(Color.Red).toArgb()`-equivalent invocations.
     */
    fun background(color: Int, shape: Int = 0): RemoteModifier {
        val a = (color ushr 24 and 0xff) / 255f
        val r = (color ushr 16 and 0xff) / 255f
        val g = (color ushr 8 and 0xff) / 255f
        val b = (color and 0xff) / 255f
        return RemoteModifier(elements + BackgroundElement(r, g, b, a, shape))
    }

    /**
     * `MODIFIER_BACKGROUND` — CMP `Color` convenience overload. Routes through [toArgb] →
     * the canonical Int path → the same [BackgroundElement] floats. **The `Int` overload is the
     * byte-anchor**; this convenience must produce byte-identical output (sRGB round-trip guard
     * pinned at `RemoteModifierEqualsTest.colorOverload_roundTripsThroughInt`).
     */
    fun background(color: Color, shape: Int = 0): RemoteModifier =
        background(color.toArgb(), shape)

    /**
     * `MODIFIER_BACKGROUND` — float-channel form (mirrors REM-96 `LayoutModifier.background(r, g, b,
     * a, shape)`). Any of `r/g/b/a` may carry NaN-encoded id refs (raw float bits preserved).
     */
    fun background(r: Number, g: Number, b: Number, a: Number, shape: Int = 0): RemoteModifier =
        RemoteModifier(elements + BackgroundElement(r.toFloat(), g.toFloat(), b.toFloat(), a.toFloat(), shape))

    /**
     * Convert to a REM-96 [LayoutModifier] for emission. Each element registers itself via the
     * public chain API of [LayoutModifier] — no `@PublishedApi internal` access, no separate
     * code path: the bytes the procedural helpers produce here are byte-identical to bytes a
     * pure procedural-DSL caller would produce with the same `LayoutModifier()` chain.
     */
    internal fun toLayoutModifier(): LayoutModifier {
        val lm = LayoutModifier()
        for (e in elements) e.applyToLayoutModifier(lm)
        return lm
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is RemoteModifier && elements == other.elements)

    override fun hashCode(): Int = elements.hashCode()

    override fun toString(): String = "RemoteModifier($elements)"

    /**
     * Empty `RemoteModifier` — the entry point for the chain (`RemoteModifier.width(...)`...).
     * Mirrors `androidx.compose.ui.Modifier`'s companion-as-empty pattern so the call site reads
     * naturally without an explicit constructor call.
     */
    companion object : RemoteModifier(emptyList()) {
        // Companion *is* the empty RemoteModifier; users chain off it directly.
    }
}

/**
 * Structural modifier element — data-carrying (equals/hashCode) AND knows how to inject itself
 * into a REM-96 [LayoutModifier]. The dual role (equality + apply) collapses the lambda-vs-element
 * tension flagged in TechSpec §2 Q4 — one representation does both.
 */
internal sealed interface RemoteModifierElement {
    fun applyToLayoutModifier(lm: LayoutModifier)
}

internal data class WidthElement(val type: DimensionType, val value: Float) : RemoteModifierElement {
    override fun applyToLayoutModifier(lm: LayoutModifier) {
        lm.width(type, value)
    }
}

internal data class HeightElement(val type: DimensionType, val value: Float) : RemoteModifierElement {
    override fun applyToLayoutModifier(lm: LayoutModifier) {
        lm.height(type, value)
    }
}

internal data class BackgroundElement(
    val r: Float,
    val g: Float,
    val b: Float,
    val a: Float,
    val shape: Int,
) : RemoteModifierElement {
    override fun applyToLayoutModifier(lm: LayoutModifier) {
        lm.background(r, g, b, a, shape)
    }
}
