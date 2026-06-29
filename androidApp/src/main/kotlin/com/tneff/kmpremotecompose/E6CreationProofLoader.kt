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
package com.tneff.kmpremotecompose

import com.tneff.kmpremotecompose.creation.compose.RemoteBoxLeaf
import com.tneff.kmpremotecompose.creation.compose.RemoteModifier
import com.tneff.kmpremotecompose.creation.compose.RemoteRoot
import com.tneff.kmpremotecompose.creation.compose.RemoteRow
import com.tneff.kmpremotecompose.creation.compose.captureSingleRemoteDocument
import com.tneff.kmpremotecompose.remote.core.operations.Operations
import com.tneff.kmpremotecompose.remote.core.operations.layout.DimensionType
import com.tneff.kmpremotecompose.remote.creation.Profile
import com.tneff.kmpremotecompose.remote.creation.defaultRcPlatformServices

/**
 * REM-144 S4 — §6 Maestro End-to-End Flow Proof loader. Closes the PROJECT §6 *"done means
 * Maestro-proven"* bar for the E6-Compose-Creation-DSL line.
 *
 * **The proof:** when the app's deep-link selects [DOC_NAME] (`kmprc://render?rc=e6_creation_proof`),
 * the loader at runtime calls [captureSingleRemoteDocument] with a deterministic Compose-DSL
 * recipe (a 3-cell row of pure red / green / blue 100×100 boxes per Q3 close — trivial, visually
 * distinct, easy for Maestro to assert). The returned bytes flow into the existing
 * `RemoteComposeApp` decode + render pipeline unchanged — proving the **create-side feeds the
 * render-side in a real app**.
 *
 * **Why a 3-color row:** simplest visual the Maestro flow can assert (no text, no anim, no
 * profile-experimental ops). Maestro can either screenshot-assert visually OR use the existing
 * `rc-rendered` / `rc-draw-count` Maestro hooks to confirm the render committed ≥ 1 frame with
 * a positive draw count — both signals are surfaced by the existing app infrastructure
 * (`RemoteComposeApp.kt`). Tester (test-1) picks the granularity; this loader provides a
 * stable, byte-deterministic source.
 *
 * **Byte-determinism:** every call to [loadOrFallback] with [DOC_NAME] returns byte-identical
 * output (Stage-3 determinism from REM-141 ports here: same Compose tree → same bytes; the
 * `RemoteFloatSlot` / `RemoteColorSlot` discipline never leaks state across captures).
 *
 * **Fallback for non-proof docs:** delegates to the [Res.readBytes] default — same default
 * loader the app uses without this interceptor. So this loader is a strict superset of the
 * default: proof name → DSL emit, anything else → resources read.
 */
object E6CreationProofLoader {

    /** Sentinel doc name. `kmprc://render?rc=e6_creation_proof` activates this loader's DSL path. */
    const val DOC_NAME: String = "e6_creation_proof"

    /**
     * REM-144 S4 — the 3-color-row recipe (Q3 close). Trivial Compose-Creation-DSL document:
     * `Root { Row(width=FILL) { 3× BoxLeaf(width=100, height=100, background=R/G/B) } }`.
     * 300×100 viewport; PROFILE_ANDROIDX map-form api=7 (matches the corpus profile family used
     * throughout E6).
     */
    suspend fun buildE6ProofDocument(): ByteArray = captureSingleRemoteDocument(
        width = 300, height = 100,
        profile = Profile(
            operationsProfiles = Operations.PROFILE_ANDROIDX,
            services = defaultRcPlatformServices(),
        ),
        contentDescription = "",
    ) {
        RemoteRoot {
            RemoteRow(
                modifier = RemoteModifier.width(DimensionType.FILL, Float.NaN),
            ) {
                RemoteBoxLeaf(
                    modifier = RemoteModifier
                        .width(DimensionType.EXACT, 100f)
                        .height(DimensionType.EXACT, 100f)
                        .background(color = 0xffff0000.toInt()),
                )
                RemoteBoxLeaf(
                    modifier = RemoteModifier
                        .width(DimensionType.EXACT, 100f)
                        .height(DimensionType.EXACT, 100f)
                        .background(color = 0xff00ff00.toInt()),
                )
                RemoteBoxLeaf(
                    modifier = RemoteModifier
                        .width(DimensionType.EXACT, 100f)
                        .height(DimensionType.EXACT, 100f)
                        .background(color = 0xff0000ff.toInt()),
                )
            }
        }
    }
}
