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
 * Registration entry point for the REM-4 op-group A (document + data) operations.
 *
 * These are all in the upstream default set, so they register into V6 **and** V7_BASE via
 * [Operations.registerInBase] (which validates layer membership). The central builtin registrar
 * ([Builtins]) calls this once together with the other groups before decoding real documents.
 * Per-op byte tests register/reset locally and do not depend on it.
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
    }
}
