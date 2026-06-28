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

/**
 * REM-128 DslMarker restricting what may compose inside a `captureSingleRemoteDocument { ... }`
 * scope. Mirrors upstream `@RemoteComposable` (`remote-creation-compose/.../RemoteComposable.kt`)
 * in intent: composables annotated with this marker may only emit `RemoteComposeNode`s into the
 * Remote-compose applier — they don't render onto a regular Android/CMP screen.
 *
 * The structural intent: composables with this marker take **no `RemoteComposeContext` reference**
 * — they record draw intent into nodes, which the render-walk (Phase B) replays onto the
 * procedural context. This is the W1 structural guard from TechSpec §2.
 */
@DslMarker
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
annotation class RemoteComposable
