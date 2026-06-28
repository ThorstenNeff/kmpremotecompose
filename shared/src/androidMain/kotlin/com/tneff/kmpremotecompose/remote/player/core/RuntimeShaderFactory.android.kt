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

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.ui.graphics.Shader
import com.tneff.kmpremotecompose.remote.core.operations.ShaderData

/**
 * REM-77 (Epic D, DATA_SHADER): Android consumes AGSL natively via [RuntimeShader] — no translation.
 *
 * 🚩 RuntimeShader is API 33 (Tiramisu) only, but our `minSdk` is 24 → on API 24..32 we fail soft
 * (return null → plain fill). Flagged to the PO: shader docs (AiAgent / TimeSphere) render unshaded on
 * pre-33 Android. RuntimeShader extends `android.graphics.Shader`, which is the Compose `Shader` actual
 * on Android, so the result is returned directly.
 */
actual fun createRuntimeShader(
    source: String,
    floatUniforms: List<ShaderData.FloatUniform>,
    intUniforms: List<ShaderData.IntUniform>,
): Shader? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    return runCatching {
        val shader = RuntimeShader(source)
        for (u in floatUniforms) shader.setFloatUniform(u.name, u.values)
        for (u in intUniforms) shader.setIntUniform(u.name, u.values)
        shader
    }.getOrNull()
}
