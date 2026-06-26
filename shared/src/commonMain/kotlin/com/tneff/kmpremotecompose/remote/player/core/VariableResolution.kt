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

import com.tneff.kmpremotecompose.remote.wire.WireTypes

/**
 * REM-36 E3 — resolve a possibly-NaN-encoded coordinate field to a concrete float for rendering. Any
 * **variable** NaN (system region 0, normal region 1, data region 2 — i.e. every NaN that is not an
 * RPN **operator**) is looked up in the variable store ([RemoteContext.getFloat], unresolved → `0f` per
 * the E1 contract); literals and operator NaNs (region 3, for the RPN evaluator) pass through unchanged.
 *
 * Broadened from data-only (region 2) to all variable classes (REM-36 fix): system variables like
 * `WINDOW_WIDTH`/`TIME` are **region 0** — gating on `isDataVariable` alone left them as raw NaN →
 * `drawOval(…, NaN, NaN)` → blank. This is for **op fields** only (no path-command markers); path-data
 * resolution guards markers separately (see `PathGeometry.resolvePathData`).
 *
 * Pure render-time resolution — the op's raw `val` fields (and thus `write()`/`read()`) are untouched,
 * so the byte format / conformance is unaffected.
 */
internal fun RemoteContext.resolveCoord(value: Float): Float =
    if (value.isNaN() && !WireTypes.isOperationVariable(value)) getFloat(WireTypes.idFromNan(value)) else value
