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
package com.tneff.kmpremotecompose.remote.player

import androidx.compose.ui.graphics.Path
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Regression cover for the REM-30 path-store fix (assist S1 review): a path id carries **both** the
 * cached [Path] and its raw `float[]` at once (upstream `mPathMap` vs `mPathData`). This runs on iOS
 * (Skiko) rather than commonTest because constructing a real CMP [Path] needs the graphics backend,
 * which the bare `jvm()` test target lacks (Skiko `LibraryLoadException`). The structural split and
 * winding store are also covered headless in `PlayerFoundationTest`.
 */
class RemoteContextPathStoreIosTest {

    @Test
    fun putPath_doesNotClobberPathData_andPutPathDataInvalidatesCache() {
        val context = RemoteContext()
        val data = floatArrayOf(0f, 0f, 10f, 10f)
        val path = Path()

        context.putPathData(7, data)
        context.putPath(7, path)

        // The bug: a single map made putPath overwrite the float[]. With separate stores, both survive.
        assertSame(path, context.getPath(7), "cached Path must be retained")
        assertTrue(context.getPathData(7) === data, "raw path data must NOT be clobbered by putPath")

        // Upstream semantics: new path data invalidates the stale cached Path (mPathMap.remove).
        val data2 = floatArrayOf(1f, 1f)
        context.putPathData(7, data2)
        assertNull(context.getPath(7), "putPathData must invalidate the cached Path")
        assertTrue(context.getPathData(7) === data2)
    }
}
