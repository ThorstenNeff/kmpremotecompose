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
package com.tneff.kmpremotecompose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * REM-34 — the one-route doc selector. The default launch renders [DEFAULT_DOC]; a deep-link
 * `kmprc://render?rc=<name>` selects another bundled fixture by setting [docName]. Each platform parses
 * its own deep-link (Android `intent.data`, iOS `onOpenURL`) and calls [select]; `RemoteComposeApp`
 * observes [docName] (Compose state) and reloads. An unknown name still flows through — the asset
 * lookup fails → `rc-error` (contract §2B).
 */
object RcRouter {

    /** The default bundled fixture rendered without any deep-link (contract §2A). */
    const val DEFAULT_DOC: String = "procedure_simple1"

    /** The currently selected bundled doc name (no extension). Observed by `RemoteComposeApp`. */
    var docName: String by mutableStateOf(DEFAULT_DOC)
        private set

    /**
     * Select a bundled doc by name from a deep-link. **Sanitized to the bundled-asset charset**
     * (`[A-Za-z0-9_-]`) so a hostile/typo URI can't escape the `rc/` asset dir (path traversal); a
     * rejected/blank name leaves the current selection unchanged. A *valid but unknown* name is
     * accepted and surfaces as `rc-error` at load (the honest "unknown doc" signal).
     */
    fun select(name: String?) {
        val n = name?.trim().orEmpty()
        if (n.isNotEmpty() && n.all { it.isLetterOrDigit() || it == '_' || it == '-' }) {
            docName = n
        }
    }
}
