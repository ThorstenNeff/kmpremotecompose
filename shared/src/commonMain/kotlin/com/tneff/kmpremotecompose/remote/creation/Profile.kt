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

import com.tneff.kmpremotecompose.remote.core.operations.Operations

/**
 * Document profile bundle for the creation DSL: the [operationsProfiles] bitmask that drives
 * `RemoteComposeWriter`'s `Operations.isValid` fail-closed gate, plus the [services] used during
 * write for platform-typed inputs (bitmaps, paths).
 *
 * Thin holder by design — the profile *enforcement* lives in the writer (REM-3/REM-4) and is byte-
 * bewiesen; this layer only carries the values into it.
 */
class Profile(
    val operationsProfiles: Int,
    val services: RcPlatformServices,
) {
    companion object {
        /** Default profile: baseline opcodes only, host-default services. */
        val Baseline: Profile = Profile(Operations.PROFILE_BASELINE, defaultRcPlatformServices())
    }
}
