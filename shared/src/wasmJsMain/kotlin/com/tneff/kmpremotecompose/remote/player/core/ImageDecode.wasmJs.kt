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
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

/**
 * REM-76 (Epic C wasmJs): Compose for Web (wasmJs) is Skiko (CanvasKit) like iOS, so this is the 1:1 iOS impl —
 * decode the encoded bytes via Skia [Image.makeFromEncoded], bound the dimensions (fail-closed vs hostile sizes)
 * and convert to a Compose [ImageBitmap]. runCatching keeps it fail-soft (corrupt bytes → null → empty render).
 * (Skia/CanvasKit must be initialized before this runs — it only executes on a real web render, never in the
 * headless byte/round-trip harness.)
 */
actual fun decodeImageBitmap(bytes: ByteArray, type: Int, maxDim: Int): ImageBitmap? = runCatching {
    val image = Image.makeFromEncoded(bytes)
    if (image.width in 1..maxDim && image.height in 1..maxDim) image.toComposeImageBitmap() else null
}.getOrNull()
