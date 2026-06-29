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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * REM-141 T3 — Phase-A→Phase-B id-handoff for variable primitives.
 *
 * The Compose-creation DSL is two-phase (TechSpec REM-128 §1): Phase-A composition builds the node
 * tree (W1: NO emission, no context reference); Phase-B render walks the tree and calls the
 * byte-proven procedural helpers (which allocate ids and emit ops). The challenge a primitive
 * solves: a **modifier that references an id** (`MODIFIER_VISIBILITY valueId`, `MODIFIER_BORDER
 * colorId`) needs to know the id of a **primitive that the same tree walk allocates later**.
 *
 * The Slot pattern (REM-141 scoping doc §2.1):
 *  1. Caller creates a stable slot at the call site via [rememberRemoteFloatSlot] /
 *     [rememberRemoteColorSlot] — Compose `remember` keeps the same instance across recompositions.
 *  2. The primitive composable (e.g. [RemoteFloatExpression]) creates a node that, at Phase-B
 *     render, calls the procedural helper, captures the returned id, and writes it into the slot.
 *  3. The modifier overload that consumes the slot (e.g. `RemoteModifier.visibility(slot)`) stores
 *     the slot in its element list (Q4 lock: data-class equality on the slot reference); at
 *     Phase-B apply time it reads `slot.id` (guaranteed to be set because the primitive emits
 *     earlier in tree order) and forwards to the procedural-DSL via `applyToLayoutModifier(lm)`.
 *
 * **W4 (slot-ref-before-emit) check:** consumers MUST require `slot.id >= 0` before forwarding —
 * a misuse where the modifier references a slot whose primitive composable is missing or
 * placed AFTER the consumer in tree order is caught with a clear runtime error rather than
 * silently emitting an invalid id-ref.
 *
 * **Why a plain `var id: Int = -1` (not `MutableState<Int>`):** the slot is set exactly once per
 * render walk and read shortly after, in a strict Phase-B sequence — no recomposition observability
 * is needed. Compose recompositions never call render() — they just rebuild the node tree, and the
 * slot identity is what `update { set(modifier) }` change-detection compares (Q4 lock — the slot
 * reference is stable across recompositions via `remember`, so the element's `equals` is stable).
 */
class RemoteFloatSlot {
    internal var id: Int = -1
}

class RemoteColorSlot {
    internal var id: Int = -1
}

/**
 * REM-148 S1 — output-id holder for [RemotePathTween]. The `PATH_TWEEN` op allocates a fresh
 * region-0 path id at render time; the consumer of that id (a future `RemotePathDraw(slot)` or
 * chained `RemotePathTween(out = ..., pathId1 = otherSlot, ...)`) reads `slot.id` after the
 * primitive node has rendered. Same single-write-during-Phase-B semantics as [RemoteFloatSlot] /
 * [RemoteColorSlot] (W4 slot-ref-before-emit rule applies — modifier consumers must require
 * `slot.id >= 0` before forwarding).
 */
class RemotePathSlot {
    internal var id: Int = -1
}

/**
 * Allocate (and remember across recompositions) a [RemoteFloatSlot] for use with
 * [RemoteFloatExpression] and the slot-form of `RemoteModifier.visibility`. One slot per call site;
 * the slot identity is stable across recompositions (Q4 lock).
 */
@Composable
fun rememberRemoteFloatSlot(): RemoteFloatSlot = remember { RemoteFloatSlot() }

/**
 * Allocate (and remember across recompositions) a [RemoteColorSlot] for use with the
 * `RemoteColorExpression*` composables and the slot-form of `RemoteModifier.border`. One slot per
 * call site; the slot identity is stable across recompositions (Q4 lock).
 */
@Composable
fun rememberRemoteColorSlot(): RemoteColorSlot = remember { RemoteColorSlot() }

/**
 * REM-148 S1 — Allocate (and remember across recompositions) a [RemotePathSlot] for use with
 * [RemotePathTween]. Stable identity across recompositions (Q4 lock).
 */
@Composable
fun rememberRemotePathSlot(): RemotePathSlot = remember { RemotePathSlot() }
