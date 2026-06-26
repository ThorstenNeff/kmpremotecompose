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

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * REM-34 — RcRouter deterministic unknown-doc handling (test-2 fix): a deep-link's `rc` value maps to a
 * single deterministic outcome so `rc-doc` is a reliable sweep anchor (no silent default-fallback, no
 * echo of an unrendered name). Valid → selected; blank/invalid → [RcRouter.UNKNOWN_DOC] (→ rc-error);
 * null (no param) → unchanged (default-launch path).
 */
class RcRouterTest {

    @Test
    fun select_isDeterministic() {
        RcRouter.select("c_box")
        assertEquals("c_box", RcRouter.docName, "valid name selected")

        RcRouter.select("definitely_not_bundled_xyz")
        assertEquals("definitely_not_bundled_xyz", RcRouter.docName, "valid-but-unknown name kept (→ rc-error at load)")

        RcRouter.select("evil.rc")
        assertEquals(RcRouter.UNKNOWN_DOC, RcRouter.docName, "invalid charset → deterministic sentinel, not silent fallback")

        RcRouter.select("../secret")
        assertEquals(RcRouter.UNKNOWN_DOC, RcRouter.docName, "path-traversal → sentinel (no escape)")

        RcRouter.select("   ")
        assertEquals(RcRouter.UNKNOWN_DOC, RcRouter.docName, "blank → sentinel")

        val before = RcRouter.docName
        RcRouter.select(null)
        assertEquals(before, RcRouter.docName, "null (no rc param) → unchanged (default-launch preserved)")
    }
}
