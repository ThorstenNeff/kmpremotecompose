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
package com.tneff.kmpremotecompose

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily

/**
 * REM-110 — the bundled symbol-glyph fallback family (♥/❤/⚡/⬩/▲/↑/↓), provided **only on wasm**.
 *
 * The missing-glyph bug is **web-only**: Android/iOS/Desktop already render these Misc-Symbols/Dingbats/
 * Geometric/Arrows glyphs via the platform system font, so those targets return `null` here — the text
 * renderer then never swaps the font family, and their goldens do not shift (PO decision: gate the
 * fallback exactly where the glyphs are missing). Only wasmJs, whose CMP default font lacks the glyphs,
 * supplies the bundled `rc_symbol_fallback` family. The family is injected into the text half by
 * `RemoteComposeApp` and applied per-run by [com.tneff.kmpremotecompose.remote.player.compose.ComposeTextRenderer].
 */
@Composable
expect fun symbolFallbackFamily(): FontFamily?
