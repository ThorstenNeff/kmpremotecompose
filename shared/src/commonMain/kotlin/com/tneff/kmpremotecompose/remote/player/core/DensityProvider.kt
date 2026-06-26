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
 * Supplies the platform display density to the player (PROJECT_CONTEXT §5: density is evaluated on
 * the player and **never hardcoded**).
 *
 * The default [platformDensityProvider] reads the live platform value via `expect`/`actual`
 * (Android `Resources.displayMetrics.density`, iOS `UIScreen.mainScreen.scale`). In a Compose host
 * the density is instead available from `LocalDensity`; inject that as a [DensityProvider] so the
 * player picks up the composition's density rather than the system default. Either way the player
 * calls [RemoteContext.setDensity] once at init — see TECHSPEC §4.
 */
fun interface DensityProvider {
    /** The current display density (logical-px → device-px scale factor). */
    fun density(): Float
}

/** The platform-default density source. */
expect fun platformDensityProvider(): DensityProvider
