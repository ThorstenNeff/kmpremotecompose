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

import platform.UIKit.UIScreen

/**
 * iOS density from the main screen's scale (`@2x`/`@3x`). A Compose host should instead inject
 * `LocalDensity` as a [DensityProvider] for the composition's density (see TECHSPEC §4).
 */
actual fun platformDensityProvider(): DensityProvider =
    DensityProvider { UIScreen.mainScreen.scale.toFloat() }
