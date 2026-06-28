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

import androidx.compose.ui.graphics.Shader
import com.tneff.kmpremotecompose.remote.core.operations.ShaderData

/**
 * REM-77 (Epic D, DATA_SHADER): build a platform runtime shader from AGSL [source] + its uniforms,
 * returned as a Compose [Shader] ready to assign to `Paint.shader`.
 *
 * Platform split (the AGSL→SkSL seam):
 * - **Android** ([android.graphics.RuntimeShader]) consumes AGSL natively → source passes through; the
 *   actual must guard the API-33 minimum (RuntimeShader is Tiramisu+) and return null below it.
 * - **Skiko** (iOS / Desktop / Web — [org.jetbrains.skia.RuntimeEffect]) consumes SkSL → the actual runs
 *   [AgslToSksl.translate] first.
 *
 * **Fail-soft contract** (same posture as gradients in `PaintBundleApplier`): a malformed/unsupported
 * shader (compile error, unbound uniform, pre-33 Android) returns `null` — the caller then leaves the
 * paint shader unset (plain fill) instead of throwing the render path. Bitmap (`shader`-typed) uniforms
 * are not yet bound (scaffold) — they are ignored, which a shader sampling a texture will render dark;
 * tracked for the golden phase.
 */
expect fun createRuntimeShader(
    source: String,
    floatUniforms: List<ShaderData.FloatUniform>,
    intUniforms: List<ShaderData.IntUniform>,
): Shader?
