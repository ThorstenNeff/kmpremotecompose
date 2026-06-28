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

/**
 * REM-82 / W1 — the Web input-source adapter (the third deep-link source, after Android `intent.data`
 * and iOS `onOpenURL`). Pure Kotlin so it lives in `commonMain` and is JVM-unit-testable; the only
 * web-specific part (reading `window.location.search`) stays in the wasmJs entry. **No new router
 * logic:** [applyWebQueryToRouter] drives the SHARED [RcRouter] exactly as `MainActivity.selectFromIntent`
 * does, reusing its fail-safes (`select` → `UNKNOWN_DOC` on a bad/traversal name; `setStaticTime` →
 * `0f` on null/blank/non-numeric/negative).
 */

/**
 * Parse a raw URL query string (`?a=1&b=two`, `a=1&b=two`, or "") into a name→value map. Values are
 * percent-decoded (`+`→space). Last value wins on a duplicate key; a bare key (no `=`) maps to "".
 */
internal fun parseQuery(raw: String?): Map<String, String> {
    val q = (raw ?: "").removePrefix("?")
    if (q.isEmpty()) return emptyMap()
    val out = LinkedHashMap<String, String>()
    for (pair in q.split("&")) {
        if (pair.isEmpty()) continue
        val eq = pair.indexOf('=')
        if (eq < 0) {
            out[decodeComponent(pair)] = ""
        } else {
            out[decodeComponent(pair.substring(0, eq))] = decodeComponent(pair.substring(eq + 1))
        }
    }
    return out
}

/** Minimal percent-decoding incl. `+`→space (form-encoding), fail-soft (a bad `%xx` is kept literal). */
private fun decodeComponent(s: String): String {
    if ('%' !in s && '+' !in s) return s
    val sb = StringBuilder(s.length)
    var i = 0
    while (i < s.length) {
        val c = s[i]
        when {
            c == '+' -> { sb.append(' '); i++ }
            c == '%' && i + 2 < s.length -> {
                val code = s.substring(i + 1, i + 3).toIntOrNull(16)
                if (code != null) { sb.append(code.toChar()); i += 3 } else { sb.append(c); i++ }
            }
            else -> { sb.append(c); i++ }
        }
    }
    return sb.toString()
}

/**
 * REM-82 / W1 — apply a page-load URL query (`?rc=<name>&live=1&t=<sec>&density=<f>`) to the shared
 * [RcRouter], symmetric to the mobile deep-link parsers: `resetForLaunch()` first (REM-62 discipline — a
 * fresh load is unconditionally static t=0), then `select(rc)` → `live` → `setStaticTime(t)` →
 * `setForcedDensity(density)`. An absent `rc` keeps the default doc (contract §2A); a bad name →
 * `UNKNOWN_DOC` → `rc-error`. The `&density=` override (REM-91) mirrors `MainActivity.selectFromIntent`
 * and `iOSApp.swift` so the browser sweep can pin a cross-target density; absent ⇒ platform density.
 */
fun applyWebQueryToRouter(search: String?) {
    val query = parseQuery(search)
    RcRouter.resetForLaunch()
    RcRouter.select(query["rc"])
    RcRouter.live = query["live"] == "1"
    RcRouter.setStaticTime(query["t"])
    RcRouter.setForcedDensity(query["density"])
}
