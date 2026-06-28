package com.tneff.kmpremotecompose

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

/**
 * REM-81 — Desktop interactive-render shell (Epic-B). The 4th app-shell target: wires the **shared**
 * [RemoteComposeApp] into a Compose-Desktop `Window`, exactly like `androidApp`/`iosApp`/`webApp`. No new
 * render path and **no re-implemented hooks** — the shared composable owns the whole contract
 * (`rc-canvas` / `rc-rendered` only after frame-commit + ≥1 paint / `rc-error` / `rc-doc` /
 * `rc-draw-count`, the REM-8 honest-render gate). The acceptance gate is `RemoteComposeAppDesktopTest`
 * (compose-ui-test — test-3's resolution of the spec §2 watchpoint: native Compose-Desktop is not in
 * Maestro's device catalog, so compose-ui-test's identical testTag API is the JVM-native equivalent).
 *
 * Two desktop specifics (everything else is shared):
 *  1. **loadRc:** none passed → the shared default **unified Compose-Multiplatform-resources** loader
 *     (`Res.readBytes("files/rc/$name.rc")`, REM-82/C5), packaged into the desktop distribution by the
 *     compose-resources plugin. Same 173-doc corpus as Android/iOS/Web, no per-platform copy. Fail-closed:
 *     an unknown doc surfaces `rc-error` via the shared path (no crash, no silent fallback).
 *  2. **Doc selection:** desktop has no `kmprc://` deep-link system → CLI `args` drive the shared
 *     [RcRouter] through the **same** sequence the web entry uses (`resetForLaunch` → `select` → `live`
 *     → `setStaticTime` → `setForcedDensity`, with all the W1 fail-safes), by formatting `key=value` /
 *     `--key=value` args into the query string the shared parser already consumes. E.g.
 *     `./desktopApp rc=clock live=1 t=3`. Absent `rc` ⇒ the default doc (contract §2A).
 */
fun main(args: Array<String>) {
    // Reuse the shared, JVM-tested W1 router-drive (REM-82 [applyWebQueryToRouter]) instead of a second
    // desktop-only parser: resetForLaunch discipline (fresh start = static t=0) + select/live/t/density
    // + the unknown-doc/fail-safe handling all come for free and stay consistent across targets.
    applyWebQueryToRouter(args.toRouterQuery())
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "KmpRemoteCompose",
        ) {
            RemoteComposeApp()
        }
    }
}

/** Format CLI `key=value` / `--key=value` args into the `&`-joined query the shared W1 parser consumes. */
internal fun Array<String>.toRouterQuery(): String =
    joinToString("&") { it.removePrefix("--") }
