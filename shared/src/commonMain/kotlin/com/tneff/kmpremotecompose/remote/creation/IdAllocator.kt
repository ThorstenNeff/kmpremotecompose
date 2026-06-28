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

/**
 * Monotonic id source used by every resource-emitting helper on [RemoteComposeContext].
 *
 * Byte-format invariant (§2): the order in which ids are handed out is part of the document's wire
 * shape — `addText` / `addColor` / `addPathData` etc. embed the id into the operation that consumes
 * it, and a different id => different bytes. Mirrors upstream `RemoteComposeState.createNextAvailableId`
 * (single counter, `START_ID = 42`); per-type buckets (variables, collections) live behind the
 * NaN-encoded ranges and are introduced when the helpers that need them land in E2+.
 *
 * Keeping the counter on the context — not on a global — means each `document { … }` call starts
 * from a known baseline (deterministic byte output for the same script).
 */
class IdAllocator(start: Int = START_ID) {
    private var next: Int = start

    /** Next general-purpose id (float/int/string/color/bitmap/path/text/sound share one pool). */
    fun nextId(): Int = next++

    /** Reseed the counter (mirrors upstream `RemoteComposeState.setNextId`, used by test setups). */
    fun setNextId(id: Int) { next = id }

    /** Current value of the counter without consuming it. */
    fun peek(): Int = next

    companion object {
        /** Upstream `RemoteComposeState.START_ID`. */
        const val START_ID: Int = 42
    }
}
