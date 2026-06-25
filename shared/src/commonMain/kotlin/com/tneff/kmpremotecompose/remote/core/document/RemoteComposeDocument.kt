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
package com.tneff.kmpremotecompose.remote.core.document

import com.tneff.kmpremotecompose.remote.core.operations.Header
import com.tneff.kmpremotecompose.remote.core.operations.Operation

/**
 * The in-memory model of a decoded `.rc` document: the ordered list of operations plus the metadata
 * the [Header] exposes. This is the container the reader fills and the player (later) walks.
 */
class RemoteComposeDocument(val operations: List<Operation>) {

    /** The header operation, if the document starts with one. */
    val header: Header? get() = operations.firstOrNull() as? Header

    /** Document width in pixels (from the header), or 0 if there is no header. */
    val width: Int get() = header?.width ?: 0

    /** Document height in pixels (from the header), or 0 if there is no header. */
    val height: Int get() = header?.height ?: 0

    /** API level implied by the header version, or -1 if there is no header. */
    val apiLevel: Int get() = header?.apiLevel ?: -1

    /** The DOC_PROFILES bitmask declared by the header, or 0. */
    val profiles: Int get() = header?.profiles ?: 0

    override fun toString(): String =
        "RemoteComposeDocument(${operations.size} ops, ${width}x$height, api=$apiLevel, profiles=$profiles)"
}
