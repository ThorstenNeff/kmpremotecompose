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
 * REM-36 E3 — resolve a possibly-NaN-encoded coordinate field to a concrete float for rendering: if the
 * raw field is a **data variable** ([WireTypes.isDataVariable], NaN region 2), look its value up in the
 * variable store ([RemoteContext.getFloat], unresolved → `0f` per the E1 contract); otherwise it's a
 * literal and is returned unchanged. Operator NaNs (region 3) are not coords and never reach here.
 *
 * Pure render-time resolution — the op's raw `val` fields (and thus `write()`/`read()`) are untouched,
 * so the byte format / conformance is unaffected.
 */
internal fun RemoteContext.resolveCoord(value: Float): Float =
    if (WireTypes.isDataVariable(value)) getFloat(WireTypes.idFromNan(value)) else value
