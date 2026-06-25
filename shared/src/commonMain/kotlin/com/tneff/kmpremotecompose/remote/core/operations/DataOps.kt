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

        // REM-22 SB2 — matrix transforms + conditional/debug/color-attribute (base group-A).
        Operations.registerInBase(Operations.MATRIX_RESTORE, MatrixRestore)
        Operations.registerInBase(Operations.MATRIX_TRANSLATE, MatrixTranslate)
        Operations.registerInBase(Operations.MATRIX_SCALE, MatrixScale)
        Operations.registerInBase(Operations.MATRIX_ROTATE, MatrixRotate)
        Operations.registerInBase(Operations.MATRIX_SKEW, MatrixSkew)
        Operations.registerInBase(Operations.CONDITIONAL_OPERATIONS, ConditionalOperations)
        Operations.registerInBase(Operations.DEBUG_MESSAGE, DebugMessage)
        Operations.registerInBase(Operations.ATTRIBUTE_COLOR, ColorAttribute)
        // UPDATE_DYNAMIC_FLOAT_LIST + WAKE_IN are profile-overlay ops (androidx + widgets).
        Operations.registerInOverlay(Operations.Layer.V7_ANDROIDX, Operations.UPDATE_DYNAMIC_FLOAT_LIST, UpdateDynamicFloatList)
        Operations.registerInOverlay(Operations.Layer.V7_WIDGETS, Operations.UPDATE_DYNAMIC_FLOAT_LIST, UpdateDynamicFloatList)
        Operations.registerInOverlay(Operations.Layer.V7_ANDROIDX, Operations.WAKE_IN, WakeIn)
        Operations.registerInOverlay(Operations.Layer.V7_WIDGETS, Operations.WAKE_IN, WakeIn)

        // REM-22 SB3 — TEXT_LOOKUP base; MATRIX_EXPRESSION is a V7_BASE always-on (NOT V6);
        // ID_LOOKUP + TEXT_TRANSFORM are profile-overlay ops (androidx + widgets).
        Operations.registerInBase(Operations.TEXT_LOOKUP, TextLookup)
        Operations.registerInLayer(Operations.Layer.V7_BASE, Operations.MATRIX_EXPRESSION, MatrixExpression)
        Operations.registerInOverlay(Operations.Layer.V7_ANDROIDX, Operations.ID_LOOKUP, IdLookup)
        Operations.registerInOverlay(Operations.Layer.V7_WIDGETS, Operations.ID_LOOKUP, IdLookup)
        Operations.registerInOverlay(Operations.Layer.V7_ANDROIDX, Operations.TEXT_TRANSFORM, TextTransform)
        Operations.registerInOverlay(Operations.Layer.V7_WIDGETS, Operations.TEXT_TRANSFORM, TextTransform)

        // REM-27 P2 group-A tail — base ops; MATRIX_VECTOR_MATH is a V7_BASE always-on (NOT V6).
        Operations.registerInBase(Operations.DATA_BITMAP_FONT, BitmapFontData)
        Operations.registerInBase(Operations.INTEGER_EXPRESSION, IntegerExpression)
        Operations.registerInBase(Operations.TEXT_MEASURE, TextMeasure)
        Operations.registerInBase(Operations.DATA_MAP_LOOKUP, DataMapLookup)
        Operations.registerInBase(Operations.ATTRIBUTE_TEXT, TextAttribute)
        Operations.registerInBase(Operations.ATTRIBUTE_TIME, TimeAttribute)
        Operations.registerInLayer(Operations.Layer.V7_BASE, Operations.MATRIX_VECTOR_MATH, MatrixVectorMath)
    }
}
