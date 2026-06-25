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
package com.tneff.kmpremotecompose.remote.core.operations.layout

/**
 * Sizing mode of a dimension modifier (width/height).
 *
 * The **ordinal is the wire value** (written as an int) and must match the upstream
 * `DimensionModifierOperation.Type` order exactly — do not reorder. See [WidthModifier]/[HeightModifier].
 */
enum class DimensionType {
    EXACT,
    FILL,
    WRAP,
    WEIGHT,
    INTRINSIC_MIN,
    INTRINSIC_MAX,
    EXACT_DP,
    FILL_PARENT_MAX_WIDTH,
    FILL_PARENT_MAX_HEIGHT,
    ;

    companion object {
        /** Decode an int wire value to its [DimensionType]; fail closed on an out-of-range value. */
        fun fromInt(value: Int): DimensionType {
            require(value in entries.indices) { "invalid DimensionType ordinal $value" }
            return entries[value]
        }
    }
}
