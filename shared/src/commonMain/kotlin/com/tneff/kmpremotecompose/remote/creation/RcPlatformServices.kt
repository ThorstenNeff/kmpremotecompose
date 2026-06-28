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
 * Platform-side services needed while *writing* `.rc` documents — bitmap inspection, path
 * conversion, logging — kept behind an `expect class` so the creation DSL stays in `commonMain` while
 * each target binds its own platform types (BufferedImage on JVM, UIImage on iOS, etc.).
 *
 * E1 scaffold leaves the body empty; members land alongside the bitmap (E3) and path (E2/E3) helpers
 * that need them. The seam — not the surface — is what E1 freezes.
 */
expect class RcPlatformServices()

/**
 * Default services for the host platform. Used as the implicit services on [Profile.Baseline] so
 * server-side creation works without ceremony.
 */
expect fun defaultRcPlatformServices(): RcPlatformServices
