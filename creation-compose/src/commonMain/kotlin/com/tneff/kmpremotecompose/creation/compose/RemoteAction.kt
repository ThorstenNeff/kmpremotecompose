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
package com.tneff.kmpremotecompose.creation.compose

import com.tneff.kmpremotecompose.remote.creation.RemoteComposeContext
import com.tneff.kmpremotecompose.remote.creation.valueIntegerChange

/**
 * REM-145 S3 — structural action element for the Compose-DSL touch modifier surface
 * ([RemoteModifier.onTouchDown] / `onTouchUp` / `onTouchCancel`).
 *
 * Each action runs against the active [RemoteComposeContext] inside the touch modifier's
 * `ListActionsOperation` scope (between the `MODIFIER_TOUCH_*` op and its trailing
 * `CONTAINER_END`). Data-class equality + apply, mirroring the [RemoteModifierElement] pattern
 * (TechSpec §2 Q4 lock — necessary for `update { set(modifier) }` change-detection to skip
 * unchanged compositions; opaque lambdas would compare reference-only and always trigger
 * recomposition).
 *
 * The seal currently has one variant — [ValueIntegerChangeActionElement] — which is what all 3
 * REM-145 corpus touch fixtures emit inside their ListActions scope. Future actions
 * (HOST_ACTION, RUN_ACTION, etc.) are added by extending the sealed hierarchy and routing through
 * the appropriate procedural helper.
 */
sealed interface ActionElement {
    fun emit(context: RemoteComposeContext)
}

/**
 * REM-145 S3 — `VALUE_INTEGER_CHANGE_ACTION`. Mutates the integer at [valueId] (a previously
 * allocated region-0 int) to [value] when the surrounding touch event fires. Routes through the
 * REM-145 S1 procedural-DSL helper `RemoteComposeContext.valueIntegerChange(valueId, value)`,
 * which itself is corpus-byte-anchored against `c_modifier_on_touch_down.rc` (REM-145 S1 sub-span).
 */
data class ValueIntegerChangeActionElement(
    val valueId: Int,
    val value: Int,
) : ActionElement {
    override fun emit(context: RemoteComposeContext) {
        context.valueIntegerChange(valueId, value)
    }
}

/**
 * REM-145 S3 — factory for [ValueIntegerChangeActionElement]. Matches the conventional naming
 * of the procedural helper (`valueIntegerChange(valueId, value)`) so the Compose-DSL call site
 * reads identically to the procedural one.
 */
fun valueIntegerChange(valueId: Int, value: Int): ActionElement =
    ValueIntegerChangeActionElement(valueId, value)
