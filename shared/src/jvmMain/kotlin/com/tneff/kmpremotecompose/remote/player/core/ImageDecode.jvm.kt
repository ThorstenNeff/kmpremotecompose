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

import androidx.compose.ui.graphics.ImageBitmap

/**
 * REM-55: the JVM source set exists only for the headless conformance/unit harness — it is not a product
 * render target (Android + iOS only, PROJECT_CONTEXT §1). Inline decode is unsupported here (returns
 * null → the caller renders empty, fail-soft); real decoding lives in the android/ios actuals.
 */
actual fun decodeImageBitmap(bytes: ByteArray, type: Int): ImageBitmap? = null
