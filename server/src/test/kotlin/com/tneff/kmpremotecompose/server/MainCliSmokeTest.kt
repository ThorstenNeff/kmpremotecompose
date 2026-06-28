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
package com.tneff.kmpremotecompose.server

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * REM-126 S2 — CLI smoke for the [main] entry point (TechSpec §8 S2). The byte-truth of the
 * server-creation path is owned by the S1 anchor (`ServerCreationDiskConformanceTest` in
 * `:shared` `jvmTest`, where the corpus oracle lives) — this smoke just proves the **executable
 * wires up**: `main(args[0]=outPath)` runs, produces a file, and the file is non-empty.
 */
class MainCliSmokeTest {

    private val fs: FileSystem = FileSystem.SYSTEM
    private val tempDir: Path = System.getProperty("java.io.tmpdir").toPath()
    private val tempPaths = mutableListOf<Path>()

    private fun newTempPath(): Path {
        val path = tempDir / "rem126-server-${UUID.randomUUID()}.rc"
        tempPaths += path
        return path
    }

    @AfterTest
    fun cleanup() {
        for (p in tempPaths) if (fs.exists(p)) fs.delete(p)
    }

    @Test
    fun main_writesNonEmptyFile_atGivenOutPath() {
        val out = newTempPath()
        main(arrayOf(out.toString()))
        assertTrue(fs.exists(out), "main(args[0]=outPath) must create the file at outPath")
        val size = fs.metadata(out).size ?: -1L
        assertTrue(size > 0L, "Produced file must be non-empty (got $size bytes at $out)")
    }

    @Test
    fun main_failsClosed_whenNoOutPathProvided() {
        // Fence the IndexOutOfBounds / silent-default trap: an empty args list must fail loudly,
        // not silently default to a path the operator did not choose.
        assertFailsWith<IllegalArgumentException> { main(emptyArray()) }
    }
}
