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
import androidx.compose.ui.graphics.asComposeShader
import com.tneff.kmpremotecompose.remote.core.operations.ShaderData
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

/**
 * REM-77 (Epic D, DATA_SHADER): iOS is Skiko → 1:1 the Desktop/Web actual. Translate AGSL→SkSL
 * ([AgslToSksl]), compile via [RuntimeEffect.makeForShader], bind uniforms through [RuntimeShaderBuilder],
 * and bridge the resulting `org.jetbrains.skia.Shader` to a Compose [Shader] via [asComposeShader].
 * Fail-soft: any SkSL compile error / unbound uniform → null → plain fill.
 */
actual fun createRuntimeShader(
    source: String,
    floatUniforms: List<ShaderData.FloatUniform>,
    intUniforms: List<ShaderData.IntUniform>,
): Shader? = runCatching {
    val effect = RuntimeEffect.makeForShader(AgslToSksl.translate(source))
    val builder = RuntimeShaderBuilder(effect)
    for (u in floatUniforms) setFloatUniform(builder, u.name, u.values)
    for (u in intUniforms) setIntUniform(builder, u.name, u.values)
    builder.makeShader(null).asComposeShader()
}.getOrNull()

private fun setFloatUniform(builder: RuntimeShaderBuilder, name: String, v: FloatArray) {
    when (v.size) {
        1 -> builder.uniform(name, v[0])
        2 -> builder.uniform(name, v[0], v[1])
        3 -> builder.uniform(name, v[0], v[1], v[2])
        4 -> builder.uniform(name, v[0], v[1], v[2], v[3])
        // 🚩 scaffold: >4-float uniforms (e.g. matrices/arrays) not yet bound — golden-phase follow-up.
        else -> Unit
    }
}

private fun setIntUniform(builder: RuntimeShaderBuilder, name: String, v: IntArray) {
    when (v.size) {
        1 -> builder.uniform(name, v[0])
        2 -> builder.uniform(name, v[0], v[1])
        3 -> builder.uniform(name, v[0], v[1], v[2])
        4 -> builder.uniform(name, v[0], v[1], v[2], v[3])
        else -> Unit
    }
}
