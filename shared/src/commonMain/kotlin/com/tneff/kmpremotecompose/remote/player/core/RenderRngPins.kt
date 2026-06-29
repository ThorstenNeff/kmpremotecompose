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
package com.tneff.kmpremotecompose.remote.player.core

/**
 * REM-143: the single source of truth for the deterministic-capture RNG seed pin — the RNG analogue of
 * [RenderTimePins] (time) and the density=1.0 / BASELINE-palette capture pins.
 *
 * The particle corpus docs use `OP_RAND` with no `RAND_SEED`, so their randomness is otherwise
 * non-deterministic (a shared static RNG that drifts run-to-run). For reproducible goldens/data-oracle
 * captures, every capture path pins the RNG to [PARTICLE_SEED] via [RpnFloatEvaluator.seedRngForCapture]
 * before its run: the single-frame 173-sweep (test-3, reseed-before-paint per doc) and the multi-frame
 * particle gate's two runs (dev-1, sim-capture + independent reconstruction). All sharing ONE constant is
 * what keeps the sim and its independent oracle on an identical RAND sequence — extend/centralize here,
 * never hardcode a seed at a call site.
 *
 * The value is arbitrary (any fixed seed works); it is fixed only so captures are reproducible.
 */
object RenderRngPins {
    /** The pinned RNG seed for deterministic particle captures (arbitrary fixed constant). */
    const val PARTICLE_SEED: Long = 0x5EED_5EEDL
}
