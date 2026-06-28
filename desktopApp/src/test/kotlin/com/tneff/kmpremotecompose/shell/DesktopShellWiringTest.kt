/*
 * Copyright 2026 The KmpRemoteCompose Authors
 * Licensed under the Apache License, Version 2.0.
 */
package com.tneff.kmpremotecompose.shell

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tneff.kmpremotecompose.RcRouter
import com.tneff.kmpremotecompose.RemoteComposeApp
import com.tneff.kmpremotecompose.applyWebQueryToRouter
import com.tneff.kmpremotecompose.toRouterQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * REM-81 — proves the two desktop-shell-specific bits of `desktopApp/main.kt` wiring (the rest is the
 * shared [RemoteComposeApp], whose hook contract is covered by [RemoteComposeAppDesktopTest]):
 *
 *  1. **The unified default loader actually resolves on the Desktop/JVM runtime.** The sibling
 *     [RemoteComposeAppDesktopTest] injects a *file* loader; this test calls `RemoteComposeApp()` with
 *     **no** `loadRc` — i.e. the REM-82 default `Res.readBytes("files/rc/<name>.rc")` from
 *     composeResources — and asserts the doc renders. That is exactly what `main.kt` does, so it proves
 *     the desktop distribution can read its corpus the unified way (no per-platform copy).
 *  2. **CLI args → shared RcRouter.** `toRouterQuery` + the shared W1 [applyWebQueryToRouter] select the
 *     doc / live / t the same way every other target does (resetForLaunch discipline + fail-safes).
 */
@OptIn(ExperimentalTestApi::class)
class DesktopShellWiringTest {

    @Test fun defaultUnifiedLoader_resolvesCorpusAndRendersOnDesktop() = runComposeUiTest {
        RcRouter.resetForLaunch()
        RcRouter.select(RcRouter.DEFAULT_DOC) // procedure_simple1

        // NB: no loadRc arg → the shared REM-82 default (composeResources Res.readBytes). This is the
        // exact call `desktopApp/main.kt` makes; a green here means composeResources are on the desktop
        // runtime classpath and the unified loader works on JVM, not just web/iOS.
        setContent { RemoteComposeApp() }

        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithTag("rc-rendered").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("rc-canvas").assertExists()
        onNodeWithTag("rc-error").assertDoesNotExist()
        onNodeWithTag("rc-doc").assertTextEquals(RcRouter.DEFAULT_DOC)
        assertDrawCountAtLeastOne()
    }

    @Test fun cliArgs_driveSharedRouter_likeOtherTargets() {
        RcRouter.resetForLaunch()
        // `--key=value` and `key=value` both normalise into the shared query the W1 parser consumes.
        assertEquals("rc=clock&live=1&t=3", arrayOf("--rc=clock", "live=1", "t=3").toRouterQuery())

        applyWebQueryToRouter(arrayOf("rc=demo_winding_rule_path_winding", "t=2").toRouterQuery())
        assertEquals("demo_winding_rule_path_winding", RcRouter.docName)
        assertEquals(2f, RcRouter.staticTimeSeconds)

        // No args ⇒ empty query ⇒ select(null) keeps the default doc (contract §2A), not UNKNOWN.
        RcRouter.select(RcRouter.DEFAULT_DOC)
        applyWebQueryToRouter(emptyArray<String>().toRouterQuery())
        assertEquals(RcRouter.DEFAULT_DOC, RcRouter.docName)
    }

    private fun ComposeUiTest.assertDrawCountAtLeastOne() {
        val node = onNodeWithTag("rc-draw-count").fetchSemanticsNode("rc-draw-count not found")
        val text = node.config.getOrNull(SemanticsProperties.Text)
            ?.joinToString("") { ann -> ann.text }
            .orEmpty()
        val n = text.trim().toIntOrNull()
        assertTrue("rc-draw-count expected positive Int, got: '$text'", n != null && n >= 1)
    }
}
