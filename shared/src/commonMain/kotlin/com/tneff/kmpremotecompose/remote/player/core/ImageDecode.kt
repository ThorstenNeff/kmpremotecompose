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
 * REM-55: decode encoded inline image [bytes] (PNG, [DATA_BITMAP][com.tneff.kmpremotecompose.remote.core.operations.BitmapData]
 * `ENCODING_INLINE`) into a Compose [ImageBitmap], or `null` when the bytes can't be decoded.
 *
 * **Fail-soft / fail-closed:** the actuals never throw — corrupt or hostile bytes return `null`
 * (the caller renders empty, never crashes). Platform-backed (Android `BitmapFactory`, iOS Skia
 * `Image.makeFromEncoded`); both decoders ship with the existing CMP graphics deps (no new dependency).
 *
 * @param type the [DATA_BITMAP][com.tneff.kmpremotecompose.remote.core.operations.BitmapData] image type;
 *   PNG types decode here, raw-pixel types return `null` (a separate sub-task if a doc needs them).
 */
expect fun decodeImageBitmap(bytes: ByteArray, type: Int): ImageBitmap?
