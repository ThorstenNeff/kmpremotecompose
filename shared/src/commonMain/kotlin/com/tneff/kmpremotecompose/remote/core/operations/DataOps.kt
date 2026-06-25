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
package com.tneff.kmpremotecompose.remote.core.operations

/**
 * Registration entry point for op-group A (document + data) operations.
 *
 * Most register into the base layers (V6 **and** V7_BASE) via [Operations.registerInBase]; a few
 * are profile-overlay ops and use [Operations.registerInOverlay] / [Operations.registerInLayer] for
 * their exact layer footprint. The central builtin registrar ([Builtins]) calls this once together
 * with the other groups before decoding real documents. Per-op byte tests register/reset locally.
 */
object DataOps {
    fun register() {
        Operations.registerInBase(Operations.HEADER, Header)
        Operations.registerInBase(Operations.DATA_TEXT, TextData)
        Operations.registerInBase(Operations.DATA_FLOAT, FloatConstant)
        Operations.registerInBase(Operations.DATA_INT, IntegerConstant)
        Operations.registerInBase(Operations.COLOR_CONSTANT, ColorConstant)
        Operations.registerInBase(Operations.DATA_BITMAP, BitmapData)
        Operations.registerInBase(Operations.ROOT_CONTENT_DESCRIPTION, RootContentDescription)
        Operations.registerInBase(Operations.TEXT_FROM_FLOAT, TextFromFloat)

        // REM-19 P2 batch — base group-A ops.
        Operations.registerInBase(Operations.ANIMATED_FLOAT, FloatExpression)
        Operations.registerInBase(Operations.NAMED_VARIABLE, NamedVariable)
        Operations.registerInBase(Operations.COLOR_EXPRESSIONS, ColorExpression)
        Operations.registerInBase(Operations.FLOAT_LIST, DataListFloat)
        Operations.registerInBase(Operations.ID_MAP, DataMapIds)

        // COLOR_THEME is a profile-overlay op (androidx + widgets), not part of the base set.
        Operations.registerInOverlay(Operations.Layer.V7_ANDROIDX, Operations.COLOR_THEME, ColorTheme)
        Operations.registerInOverlay(Operations.Layer.V7_WIDGETS, Operations.COLOR_THEME, ColorTheme)

        // DATA_SHADER is V6 base AND the V7 androidx overlay (never V7_BASE / V7_WIDGETS).
        Operations.registerInLayer(Operations.Layer.V6, Operations.DATA_SHADER, ShaderData)
        Operations.registerInOverlay(Operations.Layer.V7_ANDROIDX, Operations.DATA_SHADER, ShaderData)

        // REM-19 SB2 — COMPONENT_VALUE is base group-A; DYNAMIC_FLOAT_LIST is a profile overlay op.
        Operations.registerInBase(Operations.COMPONENT_VALUE, ComponentValue)
        Operations.registerInOverlay(Operations.Layer.V7_ANDROIDX, Operations.DYNAMIC_FLOAT_LIST, DataDynamicListFloat)
        Operations.registerInOverlay(Operations.Layer.V7_WIDGETS, Operations.DYNAMIC_FLOAT_LIST, DataDynamicListFloat)

        // REM-22 SB1 — base group-A ops (matrix state, text, data list, theme).
        Operations.registerInBase(Operations.MATRIX_SAVE, MatrixSave)
        Operations.registerInBase(Operations.TEXT_MERGE, TextMerge)
        Operations.registerInBase(Operations.ID_LIST, DataListIds)
        Operations.registerInBase(Operations.THEME, Theme)
    }
}
