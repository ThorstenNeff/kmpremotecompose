# TechSpec — REM-81 Desktop-App-Shell (Epic B, interaktiver Desktop-Render-Pfad)

> **Autor:** PO-Assistent · **Status:** Design (kein Code), dünner Slice analog REM-8 (Touchable-App) · **Datum:** 2026-06-28
> **Auslöser:** test-3-Befund — `:desktopApp` ist Stock-CMP-Template (`Window { App() }`, App() = Template), **kein RemoteComposePlayer-Pfad**. REM-78 (headless A1-Harness) deckt den 173-Desktop-Sweep; REM-81 = der **interaktive** Desktop-Pfad (Epic-B-Abschluss + Voraussetzung Epic-F-Live).
> **Byte-irrelevant:** nur `:desktopApp` (+ Desktop-Resources); kein `shared/operations`-Change → 173/173 unberührt. Verifikation = Maestro-Desktop (test-3), nicht byte.

---

## 0. Leitprinzip: NICHTS neu bauen — `RemoteComposeApp` wiederverwenden

Der gesamte Render- + Hook-Vertrag existiert bereits im **geteilten** `RemoteComposeApp(loadRc, modifier)` (commonMain): `rc-canvas` (Surface-Box-testTag), **`rc-rendered` feuert NUR nach Frame-Commit + ≥1 Paint** (`committed && drawCount>0` — der honest-render-Gate, REM-8 §1), `rc-error` (Decode/Render-Fehler bzw. „rendered empty"), `rc-doc`/`rc-draw-count`. **Die Desktop-Shell reimplementiert KEINE Hooks** — sie verdrahtet nur `RemoteComposeApp` ins Desktop-`Window` mit (a) einem Desktop-`loadRc`, (b) der Maestro-adressierbaren Semantics-Exposition, (c) der Desktop-Doc-Auswahl. Exakt das Muster von `androidApp`/`iosApp`.

---

## 1. Der Slice (drei dünne Stücke)

**(1) Window-Content = RemoteComposeApp, nicht Stock-App().**
`desktopApp/main.kt`: `Window { App() }` → `Window { RemoteComposeApp(loadRc = desktopLoadRc, modifier = <hook-exposure>) }`. Das Stock-`App()`-Template entfällt/wird ersetzt. (Vergleich androidApp: `setContent { RemoteComposeApp(loadRc = { assets.open("rc/$name.rc")… }, modifier = …) }`.)

**(2) Desktop-`loadRc: (String) -> ByteArray`.**
Liest das gebündelte `.rc` aus Desktop-Resources. androidApp = `assets/rc/`, iosApp = `Resources/rc/`. Desktop braucht seinen eigenen Resource-Pfad: **Korpus-`.rc` nach `desktopApp/resources/rc/` bündeln** (oder über `compose.components.resources` teilen) → `loadRc = { name -> javaClass.getResourceAsStream("/rc/$name.rc")!!.readBytes() }` (oder die compose-resources-API). **Fail-closed:** unbekannter Name → der bestehende `RcRouter`/`RemoteComposeApp`-Pfad surfaced `rc-error` (kein Crash, kein Silent-Fallback) — nicht neu erfinden, der geteilte Pfad macht's schon.

**(3) Doc-Auswahl ohne Intent-System.**
Desktop hat keine `kmprc://`-Deep-Links. **Auswahl via `main(args)`** → `RcRouter.select(args["rc"])` (+ optional `live`/`t` aus args, analog REM-62-Mapping) vor dem `Window`; sonst Default-Doc. **🔴 Wiederverwende `RcRouter` + die `resetForLaunch()`-Disziplin** (REM-62) — frischer Start = static t=0, args setzen Transientes; kein neuer Auswahl-Mechanismus.

---

## 2. 🔴 Der eine echte Watchpoint: Maestro-Desktop-Hook-Adressierung

Android adressiert die testTags via `semantics { testTagsAsResourceId = true }` (Android-only API); iOS mappt testTag → a11y-id. **Desktop (Compose-Desktop/JVM) braucht das Äquivalent**, damit Maestro-Desktop `rc-canvas`/`rc-rendered`/`rc-error` per id/Tag findet. → Der `modifier`-Param an `RemoteComposeApp` muss die Desktop-korrekte Semantics-Exposition tragen. **Das ist der zu klärende Punkt** (Compose-Desktop-Semantics → Maestro-Desktop-Selector-Mechanismus): testTag direkt adressierbar? a11y-Rolle nötig? test-3 (Maestro-Desktop-Owner) verifiziert den Selektor-Pfad; falls testTag allein nicht greift, ist der Fix lokal im Desktop-`modifier` (wie Androids testTagsAsResourceId), NICHT im geteilten `RemoteComposeApp`.

---

## 3. Akzeptanz + Ownership

- **Akzeptanz:** `:desktopApp` startet, lädt ein gebündeltes `.rc` → decode (L1) → L2-Player → **sichtbar auf dem Schirm**; ein Maestro-Desktop-Flow assertet `rc-rendered` + `rc-doc == <name>` (honest-render, nicht „kompiliert"). Parität (Pixel gg. Orakel) ist REM-78s Sweep, NICHT hier — hier reicht „rendert sichtbar + Hooks feuern".
- **🔴 Review-Watchpoint (aus REM-8 übernommen):** `rc-rendered` muss nach **Frame-Commit** feuern, nicht nach Composition — das erbt die Shell automatisch von `RemoteComposeApp` (committed && drawCount>0); der Reviewer prüft nur, dass die Desktop-Wiring diesen Gate NICHT umgeht (z.B. keinen eigenen rc-rendered-Tag setzt).
- **Ownership:** Wiring = erster freier Dev; Maestro-Desktop-Flow + Selektor-Verifikation = test-3.
- **Voraussetzung für:** Epic-B-Abschluss (interaktiver Desktop neben REM-78-Headless-Sweep) + Epic-F-Live (Touch/Sensor brauchen den interaktiven Shell).

---

## 4. Harte Regeln
Byte-irrelevant (nur `:desktopApp` + Desktop-Resources, kein `shared/operations` → 173/173). Kein `java.*` in commonMain (der Desktop-`loadRc`/Resource-Read liegt in `:desktopApp` = JVM, dort erlaubt). Hooks NICHT duplizieren (geteilter `RemoteComposeApp`-Vertrag). Resource-Bündelung: Korpus-`.rc` nach Desktop-Resources (symmetrisch zu android/ios) — kein Pfad-Hardcode außerhalb des Resource-Mechanismus.
