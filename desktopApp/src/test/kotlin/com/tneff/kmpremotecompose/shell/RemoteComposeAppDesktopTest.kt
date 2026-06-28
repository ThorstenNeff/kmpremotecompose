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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * REM-81 — Desktop interactive-render hook-contract test (compose-ui-test in-lane analog to
 * Maestro). Verifies the geteilte [RemoteComposeApp] composable on the Compose-Desktop/JVM stack
 * end-to-end: load .rc bytes → L1 decode → L2 player paint → the testTag hook contract
 * (rc-canvas / rc-rendered post-frame-commit / rc-error / rc-doc / rc-draw-count) fires.
 *
 * Why this instead of Maestro: Maestro supports Android/iOS/RN/Flutter-mobile/Web-Browser; native
 * Compose-Desktop (Skiko-direct, no a11y-bridge for selectors) is NOT in Maestro's catalog
 * (mobile-dev-inc/maestro-docs, 2026-06-28). The REM-81 spec §2 watchpoint asked test-3 to
 * resolve the Desktop selector mechanism — answer: compose-ui-test's identical testTag-API,
 * which IS the JVM-native test stack for Compose-Desktop.
 *
 * Independent of the REM-81 desktop-Window-Wiring (deferred to a free dev): this test exercises
 * the shared composable directly, so it can land + run NOW + become the REM-81 acceptance gate.
 */
@OptIn(ExperimentalTestApi::class)
class RemoteComposeAppDesktopTest {

    /** Cwd: `:desktopApp` build dir / project dir per gradle test default. Walk up to corpus. */
    private val rcDir: File by lazy {
        // Resolve from the desktopApp project dir up to the bundled android corpus (the canonical
        // 173-doc set, symmetric across android+ios+conformance per REM-64). The desktop test does
        // not own its own resource bundle yet (REM-81 wiring adds that); for the harness phase we
        // read directly from the android assets which are checked in to the repo root.
        sequenceOf(
            File("../androidApp/src/main/assets/rc"),
            File("androidApp/src/main/assets/rc"),
        ).firstOrNull { it.isDirectory }
            ?: error("could not locate androidApp rc corpus from cwd=${File(".").absolutePath}")
    }

    private fun loadRc(name: String): ByteArray =
        File(rcDir, "$name.rc").also {
            require(it.isFile) { "rc fixture not bundled: ${it.absolutePath}" }
        }.readBytes()

    @Test fun defaultDoc_rendersWithFullHookContract() = runComposeUiTest {
        RcRouter.select(RcRouter.DEFAULT_DOC) // procedure_simple1

        setContent { RemoteComposeApp(loadRc = ::loadRc) }

        // rc-rendered fires only after the first frame-commit AND drawCount > 0 (REM-8 honest-
        // render gate). Wait on it instead of asserting blind so the compose dispatcher actually
        // settles the LaunchedEffect(load) + the Canvas first paint.
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithTag("rc-rendered").fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("rc-canvas").assertExists()
        onNodeWithTag("rc-error").assertDoesNotExist()
        // rc-doc = the name of the doc that produced the committed frame (REM-34 robust anchor).
        onNodeWithTag("rc-doc").assertTextEquals(RcRouter.DEFAULT_DOC)
        // rc-draw-count carries the executed paint primitive count; honest-render requires ≥1.
        assertDrawCountAtLeastOne()
    }

    @Test fun selectedNonDefaultDoc_loadsAndRenders() = runComposeUiTest {
        val target = "demo_winding_rule_path_winding" // bundled, distinct from procedure_simple1
        RcRouter.select(target)

        setContent { RemoteComposeApp(loadRc = ::loadRc) }

        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithTag("rc-rendered").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("rc-error").assertDoesNotExist()
        // PO-preferred robust identity check (test-2 REM-34): rc-doc text == the selected name
        // proves the SELECTED doc loaded, not the default (false-green guard).
        onNodeWithTag("rc-doc").assertTextEquals(target)
        assertDrawCountAtLeastOne()
    }

    @Test fun unknownDoc_surfacesRcError() = runComposeUiTest {
        RcRouter.select(RcRouter.UNKNOWN_DOC) // sentinel — matches no bundled fixture

        setContent { RemoteComposeApp(loadRc = ::loadRc) }

        // Either decode-throw OR "rendered empty" both surface on rc-error (RemoteComposeApp §1).
        // The contract is: rc-rendered does NOT appear, rc-error DOES.
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithTag("rc-error").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("rc-rendered").assertDoesNotExist()
    }

    // ---- helpers ----

    /**
     * Reads the `rc-draw-count` node's text and asserts the count parses to an Int ≥ 1, matching
     * the Android Maestro flow's `^[1-9][0-9]*$` regex check (smoke-defense vs blank-but-committed
     * frames where rc-rendered would otherwise false-green).
     */
    private fun ComposeUiTest.assertDrawCountAtLeastOne() {
        val node = onNodeWithTag("rc-draw-count").fetchSemanticsNode("rc-draw-count not found")
        val text = node.config.getOrNull(SemanticsProperties.Text)
            ?.joinToString("") { ann -> ann.text }
            .orEmpty()
        val n = text.trim().toIntOrNull()
        assertTrue(
            "rc-draw-count expected positive Int (regex ^[1-9][0-9]*$), got: '$text'",
            n != null && n >= 1,
        )
    }
}
