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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** REM-77: AGSL→SkSL type-alias normalization (the only syntactic delta the corpus shaders exercise). */
class AgslToSkslTest {

    @Test
    fun vecAliasesBecomeFloatTypes() {
        assertEquals("float2 a; float3 b; float4 c;", AgslToSksl.translate("vec2 a; vec3 b; vec4 c;"))
    }

    @Test
    fun ivecAndBvecAliases() {
        assertEquals("int2 a; int3 b; int4 c;", AgslToSksl.translate("ivec2 a; ivec3 b; ivec4 c;"))
        assertEquals("bool4 f;", AgslToSksl.translate("bvec4 f;"))
    }

    @Test
    fun matAliasesBecomeSquareFloatMatrices() {
        assertEquals("float2x2 m; float3x3 n; float4x4 o;", AgslToSksl.translate("mat2 m; mat3 n; mat4 o;"))
    }

    @Test
    fun matrixConstructorCallIsRenamedToo() {
        // mat3(...) constructor → float3x3(...) by the same whole-word pass (column-major in both → ok).
        assertEquals(
            "float3x3(c, 0.0, -s, 0.0, 1.0, 0.0, s, 0.0, c)",
            AgslToSksl.translate("mat3(c, 0.0, -s, 0.0, 1.0, 0.0, s, 0.0, c)"),
        )
    }

    @Test
    fun ivecIsNotMisreadAsVec() {
        // `\bvec2\b` must NOT match the `vec2` inside `ivec2` (no word boundary between `i` and `vec2`).
        assertEquals("int2 a;", AgslToSksl.translate("ivec2 a;"))
    }

    @Test
    fun identifiersContainingAnAliasAreNotCorrupted() {
        // a user identifier merely containing an alias substring stays intact.
        assertEquals("float myvec2val = 1.0;", AgslToSksl.translate("float myvec2val = 1.0;"))
    }

    @Test
    fun alreadySkslTypesUnchanged() {
        val src = "half4 main(float2 fragcoord) { float3 c = float3(0.0); return half4(c, 1.0); }"
        assertEquals(src, AgslToSksl.translate(src))
    }

    @Test
    fun realisticSnippetTranslatesEntryAndTypes() {
        val agsl = "half4 main(vec2 fragcoord) { vec2 uv = fragcoord; vec4 col = vec4(uv, 0.0, 1.0); return col; }"
        val sksl = AgslToSksl.translate(agsl)
        assertTrue(sksl.contains("float2 uv"), "vec2 should normalize to float2: $sksl")
        assertTrue(sksl.contains("float4 col = float4("), "vec4 + ctor should normalize: $sksl")
        assertTrue(!sksl.contains("vec"), "no GLSL vec aliases should remain: $sksl")
    }
}
