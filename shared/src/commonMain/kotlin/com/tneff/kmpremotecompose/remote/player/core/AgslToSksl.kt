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
 * REM-77 (Epic D, DATA_SHADER): translate Android's **AGSL** shader source into Skia **SkSL** for the
 * Skiko targets (iOS / Desktop / Web). Android's [android.graphics.RuntimeShader] consumes AGSL natively,
 * so the Android actual passes the source through unchanged; the Skiko `org.jetbrains.skia.RuntimeEffect`
 * consumes SkSL, which differs from AGSL mainly in the **type spelling**: AGSL accepts the GLSL-style
 * aliases (`vec2`, `mat3`, `ivec4`), whereas SkSL requires the explicit forms (`float2`, `float3x3`,
 * `int4`). This object normalizes those aliases. Both languages share the rest of the grammar
 * (`half4 main(float2)`, `uniform shader`, `.eval(...)`, constructors, swizzles), so no semantic rewrite.
 *
 * Scope (the corpus shaders — AiAgent, TimeSphere — exercise exactly these): `vec[234]`→`float[234]`,
 * `ivec[234]`→`int[234]`, `bvec[234]`→`bool[234]`, `mat[234]`→`float[234]x[234]`. Identical-token
 * constructor calls (`mat3(...)`) are renamed by the same pass (column-major in both → semantics match).
 *
 * 🚩 KNOWN RESIDUALS (out of scope here, verified/handled in the golden phase, not by string rewrite):
 * - non-square matrices (`mat2x3`) — none in the corpus;
 * - precision/color-space: AGSL works in the destination color space, SkSL runtime effects in the
 *   input color space — may shift colors without failing to compile (a golden-pixel concern);
 * - `half`/`float` narrowing in return/constructors — accepted implicitly by both, left as-is.
 * Replacements are whole-word (`\b`-anchored) so identifiers that merely contain an alias
 * (e.g. `myvec2`, `ivec2` vs `vec2`) are never corrupted.
 */
internal object AgslToSksl {

    // Order is irrelevant: each rule is \b-anchored, so `vec2` inside `ivec2` is not matched
    // (no word boundary between `i` and `vec2`). Listed longest-prefix-first for readability only.
    private val rules: List<Pair<Regex, String>> = listOf(
        "ivec2" to "int2", "ivec3" to "int3", "ivec4" to "int4",
        "bvec2" to "bool2", "bvec3" to "bool3", "bvec4" to "bool4",
        "vec2" to "float2", "vec3" to "float3", "vec4" to "float4",
        "mat2" to "float2x2", "mat3" to "float3x3", "mat4" to "float4x4",
    ).map { (from, to) -> Regex("\\b" + from + "\\b") to to }

    /** Return the SkSL equivalent of [agsl]. Pure string transform — never throws. */
    fun translate(agsl: String): String {
        var s = agsl
        for ((pattern, replacement) in rules) {
            s = pattern.replace(s, replacement)
        }
        return s
    }
}
