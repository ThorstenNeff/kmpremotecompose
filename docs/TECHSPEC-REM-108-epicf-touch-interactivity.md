# TechSpec — REM-108 Epic-F: Touch-Interaktivität (Player-Hälfte)

> **Status:** **Architektur-GO von assist (2026-06-28)** — Q1/Q3/Q4 bestätigt, Q2 korrigiert (Scope auf
> volle Korpus-Reichweite erweitert). Dieses Dokument IST die Epic-F-TechSpec (§7). Design dev-1
> (source-grounded), reviewed PO-Assistent. **Q4 verifiziert assist final am Slice-Code (write/read
> byte-identisch).**
> **Byte-Posture:** runtime-only; **die Touch-Op-`read`/`write` bleiben UNVERÄNDERT → 173/173 (§2).**
> Verifikation: commonTest (Seam + Eval + Korpus-Reach-Guard) + test-1 Maestro (Android/iOS drag) +
> test-2 (wasmJs) + test-3 (Desktop maus).

---

## 0. Leitprinzip + die eine Invariante

Touch treibt Werte in den **Float-Store** + stateful **TouchExpression**-Output — **nur im Live-Modus**
(Pointer-Events kommen nur live). Im Static-Modus gibt es kein Pointer-Handling → `ID_TOUCH_POS_*`
bleiben `0f`, jede `TouchExpression` bleibt auf ihrem default `value` → **Static-Render == Baseline.**
Das ist der Linchpin (identisch zu D5), der Goldens / REM-78-Sweep / 173-Conformance immun hält; in S1
als render-level Conformance-Pin getestet. **§2:** die Touch-Ops sind schon byte-faithful decode-ported
(Carrier); REM-108 ergänzt NUR Laufzeit-Verhalten (eval/dispatch) — die `read`/`write`-Methoden werden
**nicht angefasst**.

---

## 1. Source-Grounding (gg `./androidx` + Korpus-Decode)

- **Touch-Float-ids** (`RemoteContext.java`): `ID_TOUCH_POS_X=13`, `ID_TOUCH_POS_Y=14`,
  `ID_TOUCH_VEL_X=15`, `ID_TOUCH_VEL_Y=16`, `ID_TOUCH_EVENT_TIME=29`. **Korpus liest nur 13/14**
  (Velocity/EventTime decode-vorhanden aber korpus-ungenutzt → reserviert, nicht gespeist in S2).
- **Ops (schon decode-ported, `: Operation`-Carrier OHNE eval):** `TouchExpression`(157),
  `ScrollModifier`(226), `TouchDownModifier`(219)/`TouchUpModifier`(220)/`TouchCancelModifier`(225),
  `HapticFeedback`(177). → Touch-Korpus decodiert, rendert HEUTE statisch.
- **Upstream-Dispatch:** Pointer → `CoreDocument.touchDown/Drag/Up/Cancel(ctx,x,y[,dx,dy])` lädt
  `ID_TOUCH_POS_X/Y` + notifyt `TouchListener`(TouchExpression) / `TouchHandler`(Modifier).
  `TouchExpression.eval(exp)` → Output `id`, geclampt `[min,max]` (min=NaN ⇒ **wrap-mode** circular bis
  `max`), mit `touchMode` Stop-Verhalten + `easingSpec`-Deceleration auf touchUp.
- **`stopLogic` Encoding:** `touchMode = stopLogic ushr 16`, `stopLen = stopLogic and 0xFFFF`.
- **🔴 Korpus-Stop-Mode-Reichweite (alle 173 dekodiert — Q2-Korrektur):**

  | Mode | Name | #Docs | Beispiel-Docs | S-Plan |
  |---|---|---|---|---|
  | 0 | STOP_GENTLY | 12 | stop_gently, scroll(h/v), touch2, stock, moon_phases | **supported** |
  | 1 | STOP_INSTANTLY | 5 | stop_instantly, touch1, plot2, themed_plot1 | **supported** |
  | 2 | STOP_ENDS | 1 | stop_ends | **supported** |
  | 3 | STOP_NOTCHES_EVEN | **11** | stop_notches_even, touch_wrap, thumb_wheel1/2, demo_flick, clock_demo2, color_list/table | **supported (häufigster!)** |
  | 4 | STOP_NOTCHES_PERCENTS | 1 | stop_notches_percents | **supported** |
  | 5 | STOP_NOTCHES_ABSOLUTE | 1 | stop_notches_absolute | **supported** |
  | 6 | STOP_ABSOLUTE_POS | 5 | stop_absolute_pos, haptic_demo, impulse_demo(confetti/hearts), particle | **supported** |
  | 7 | STOP_NOTCHES_SINGLE_EVEN | **0** | — | **korpus-absent → UNSUPPORTED/cosmetic** |

  → **Modi 0–6 alle stützen** (korpus-genutzt). Nur Mode 7 legitim absent. **Korpus-Reach-Guard-Test**
  (D1-`TextParameterParity`-/D5-`sensorIdsUsed`-Stil) failt laut, falls je ein Doc Mode 7 nutzt.

## 2. Unsere Seite

`RemoteContext.floatStore`/`getFloat=0f`/`loadFloat`. Touch-ids 13-16/29 **undefiniert**. Die Touch-Ops
sind Carrier (kein VariableSupport/PaintOperation/touch-dispatch). Dispatch-Seam = neu im Player +
`RemoteComposeApp`-`pointerInput`.

---

## 3. Design (capability-gestaffelt) + Q-Antworten

- **(A)** Touch-ids 13-16/29 in `RemoteContext` (runtime, §2-safe).
- **(B) [Q1 ✓ commonMain]** Input = **EIN CMP `pointerInput`-Modifier in commonMain `RemoteComposeApp`**
  (auf der `rc-canvas`-Box). CMP unifiziert touch/maus/pointer cross-target → **kein per-Platform-Input-
  Provider** (einfacher als D5). Übersetzt down/move/up/cancel → `player.touch*(ctx, docX, docY)` (Pointer-
  px → Doc-Space via der bestehenden REM-36-Skalierung invers). **Nur aktiv wenn `live`** → static
  unberührt.
- **(C) Player-Dispatch-Seam:** `RemoteComposePlayer.touchDown/Drag/Up/Cancel(ctx,x,y)` lädt
  `ID_TOUCH_POS_X/Y` + notifyt eine `TouchListener`-Registry (TouchExpression registriert sich in einem
  apply/collect-Pass, analog VariableSupport).
- **(D) `TouchExpression` → VariableSupport + stateful [Q2 korrigiert]:** `updateVariables`+`apply`
  (resolve exp-NaN-refs, Output-id in Store), `touchDown/Drag/Up` (eval `exp` gg POS-vars → Output,
  clamp `[min,max]`, wrap wenn min=NaN, **Stop-Modi 0–6** + `easingSpec`-Deceleration). **Mode 7
  UNSUPPORTED** (korpus-absent). Korpus-grounded Supported/Unsupported in einer `TouchParity`-Tabelle +
  Guard-Test.
- **(E)** Touch-Modifier (down/up/cancel) = `TouchHandler` → führen ihre Action-Blocks bei Event;
  `ScrollModifier` wrappt eine `TouchExpression` → Scroll-Offset.
- **(F) [Q3 ✓ app-shell]** `HapticFeedback` → capability-gated Output via **app-shell-injected
  `HapticPerformer`** (symmetrisch `SensorSource`/`loadRc`): android=Vibrator/`performHapticFeedback`,
  ios=`UIImpactFeedbackGenerator`, **desktop+web=No-Op** (Capability-Floor). Default `NoOpHapticPerformer`.
- **(G) [Q4 ✓ §2]** `TouchExpression` wird `VariableSupport`, aber **`read`/`write`/`equals`/`hashCode`
  bleiben byte-identisch** — nur Laufzeit-Methoden kommen dazu. assist verifiziert final am Code.
- **(H) live-only→static-deterministisch** (s. §0); Capability-Floor: Touch-Input überall via CMP
  (maus/pointer auf Desktop/Web), Haptic mobile-only; never-hard-fail.

---

## 4. Slicing

| Slice | Inhalt | Verifikation | Status |
|---|---|---|---|
| **S1** | Touch-ids 13-16/29 + Player-Dispatch-Seam (`touch*`, TouchListener-Registry) + `pointerInput` (live-only) + `TouchExpression` als VariableSupport mit **NoOp-eval** (Output bleibt default) | `Rem108TouchSeamTest` (jvm+iOS) + static==baseline-Pin + Stop-Mode-Reach-Guard | **verhalten-identisch, Conformance grün** |
| **S2** | `TouchExpression` real-eval: drag→eval→clamp/wrap + **Stop-Modi 0,1,2,6** (non-notch) + easing | test-1 drag (touch1/2), test-2/3 maus | nach S1 |
| **S3** | **Notch-Modi 3,4,5** (even/percents/absolute) → thumb_wheel/touch_wrap/demo_flick + `ScrollModifier` | test-1/2/3 | nach S2 |
| **S4** | Touch-Modifier (down/up/cancel Action-Blocks) | test-1 c_modifier_on_touch_* | nach S3 |
| **S5** | `HapticFeedback` + `HapticPerformer` (android/ios real, desktop/web NoOp) | test-1 haptic_demo (on-device) | nach S4 |

---

## 5. Entschiedene Mechanik
- **Input (Q1):** ein CMP `pointerInput` in commonMain `RemoteComposeApp`, live-only.
- **Haptic (Q3):** app-shell-injizierter `HapticPerformer` (Default NoOp), wie `SensorSource`/`loadRc`.
- **Stop-Modi (Q2):** 0–6 supported (korpus), 7 cosmetic/unsupported (korpus-absent, Guard-getestet).
- **§2 (Q4):** Touch-Op-Wire-Methoden unverändert; assist-final-Verify am Slice-Code.

## 6. Harte Regeln
- **§2:** Touch-Op `read`/`write` byte-identisch → 173/173. Abweichung = Blocker.
- **Kein `java.*` in commonMain** — `HapticPerformer`-Impls in Plattform-SourceSets/App-Shells; Input via CMP (commonMain-safe).
- **live-only:** kein Touch-Dispatch/Haptic im Static-Modus → Determinismus-Pin darf nie brechen.
- **Capability-Floor:** never-hard-fail; kein Haptic-Target → NoOp.
- **Korpus-Reach-Guard:** Stop-Mode 7 oder unerwartete Touch-id → lauter Test-Fail (kein Silent-Gap).
