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
package com.tneff.kmpremotecompose.remote.creation.server

import okio.FileSystem
import okio.Path

/**
 * REM-126 §0 — Server-Creation disk surface (JVM-headless `.rc` authoring).
 *
 * The Creation-DSL (`commonMain` `document{}`) already encodes byte-true on JVM —
 * `CreationByteConformanceTest` pins five `procedure_*` oracles in-memory. The one new surface
 * REM-126 introduces is the okio disk write→read path, and this helper *is* that surface.
 *
 * **Pure okio. No `java.io.File`, no `java.nio`, no `DataOutputStream`** (TechSpec §7). Defaulting
 * to [FileSystem.SYSTEM] keeps callers ergonomic; the parameter remains for test injection. Living
 * in `jvmMain` (not `commonMain`) because the only server target today is JVM — promotable to a
 * `commonMain` `expect`/`actual` if a Native CLI ever materialises.
 *
 * Byte-faithfulness: `BufferedSink.write(ByteArray)` / `BufferedSource.readByteArray()` are
 * binary-exact — no newline translation, no UTF re-encoding. The `.rc` round-trips byte-for-byte
 * through the filesystem. The REM-126 §3 anchor (`ServerCreationDiskConformanceTest`) exercises
 * this end-to-end (write → read-back → diff vs corpus oracle).
 */
object RcDiskWriter {

    /** Write [bytes] to [path]. Truncates / creates as needed (`FileSystem.write` default). */
    fun write(path: Path, bytes: ByteArray, fileSystem: FileSystem = FileSystem.SYSTEM) {
        fileSystem.write(path) { write(bytes) }
    }

    /** Read all bytes from [path]. Throws `okio.IOException` if the file is missing. */
    fun read(path: Path, fileSystem: FileSystem = FileSystem.SYSTEM): ByteArray =
        fileSystem.read(path) { readByteArray() }
}
