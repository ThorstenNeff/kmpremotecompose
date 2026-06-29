# TechSpec — Feature-Completeness-Audit (gegen die Mensch-Definition)

> **Autor:** PO-Assistent (Reviewer) · **Status:** Audit/Analyse (KEIN Code) · **Datum:** 2026-06-29 (Refresh; Erst-Audit 2026-06-28)
> **Audit-Basis:** echte Klon-Kopie `KmpRemoteCompose` @ develop `19bc0b5` (96 Merges, 2026-06-29). Zellen am Code/git + Jira (REM 1–144) verifiziert, reconciled gegen die gemergte git-Realität (NICHT nur Jira-Labels — s. Jira-Lag-Hinweis §9).
> **Feature-complete (Mensch):** (a) ALLE Korpus-Docs rendern auf **iOS, Android, Desktop, WASM** + (b) Docs lassen sich auf einem **Server (JVM, headless)** erstellen (volle prozedurale Creation-DSL + Compose-Creation-DSL).

---

## 0. Executive Summary (Refresh 2026-06-29)

- **🟢 FC ist SUBSTANZIELL ERREICHT.** Der am 28.06. geschätzte Rest-Backlog (~3–7 Tage) ist
  weitgehend abgearbeitet: **Desktop + WASM rendern real**, **Server-Creation komplett**, **Creation-DSL
  korpus-komplett + deklarative Compose-Creation-DSL (E6: MVP + T2 + T3 + T4)**, die **Deferred-Render-
  Features** (Komplex-Text, Shader, Texture, PathEffect, FILL_AND_STROKE) sind gebaut, und die
  **Color-/Component-Content-Welle** (REM-131…140) + die **compute/lookup-Familie** (REM-139/142) sind zu.
- **Seit 7e2f9b8 (96 Merges): REM-139 (compute/lookup S1+S2) · REM-140 · REM-142 · E6-T3/REM-141 ·
  E6-T4/REM-144 (S1+S2) alle DONE.** Damit ist die Compose-Creation-DSL inkl. Variablen-Primitive
  (visibility-full/dynamic-color-border, T3) + RPN-Expr-DSL (T4) komplett.
- **Layer-1 (.rc Reader+Writer, 173 Ops) byte-bewiesen, rein commonMain** — unverändert die durch-
  gezogene §2-Kern-Invariante.
- **Render läuft auf allen 4 Mensch-Targets** (Android · iOS · Desktop(jvm) · WASM(wasmJs)) via dem
  geteilten Skiko/CMP-Pfad. Die 3 Render-actuals (decode/offscreen/opaque) sind für Desktop+WASM
  **real** (nicht mehr Stubs) — REM-75/76/78/79/80. Render-Sweep-Harnesses für Desktop + Web aktiv.
- **Server-Creation komplett** (REM-126): JVM-headless `.rc`-Authoring auf der prozeduralen Creation-
  DSL (REM-119 korpus-komplett) + Disk-Writer + Executable + CI.
- **Live-Interaktivität** capability-gestaffelt: Touch on-device (REM-108 S2b Android+iOS), Sensor
  Sim-Scope (Mensch-akzeptiert), Web-Live = Real-Browser-bewiesen (REM-114, Headless-Capture-Artefakt).
- **Verbleibend = kurzer Schwanz** (§6): **Particles/Impulse-Subsystem (REM-143) ist jetzt FULL
  Mensch-GO** (NICHT mehr deferred — TechSpec kanonisch, S1 in Arbeit, LIVE-Multi-Frame-Gate-Contract
  gepinnt) · Render-Backlog-Reste (VarFont/AlignBy/DrawBitmap-Tail) · ein paar offene Tester-Render-
  Gates auf gerade gemergten Wellen. **Particles ist der einzige verbliebene L-Brocken — jetzt
  beauftragt + in flight, nicht mehr eine offene Scope-Frage.**

---

## 1. Modul × Target-Matrix (Refresh)

Legende: ✅ fertig/bewiesen · 🟡 teilweise/ungeprüft · ❌ offen/fehlt · n/a.
Targets: **And**=Android · **iOS** · **Desk**=Desktop(jvm) · **WASM**=wasmJs · **Srv**=Server-Creation(jvm headless).
(js-Target per §0-Scope-Update entfernt — **wasmJs-only** für Web.)

| Modul / Fähigkeit | And | iOS | Desk | WASM | Srv | Beleg |
|---|---|---|---|---|---|---|
| **L1 Reader** (`remote-core`, .rc→Ops) | ✅ | ✅ | ✅ | ✅ | ✅ | commonMain, 173/173 byte/round-trip |
| **L1 Writer** (`RemoteComposeWriter`, Ops→.rc) | ✅ | ✅ | ✅ | ✅ | ✅ | commonMain, byte-identisch vs Orakel |
| **L2 Geometrie-Adapter** (rect/oval/path/clip/matrix/gradient) | ✅ | ✅ | ✅ | ✅ | n/a | Skiko/CMP; Desk+WASM Render-actuals real (REM-75/76) |
| **Bitmap-Decode** (`decodeImageBitmap`) | ✅ | ✅ | ✅ | ✅ | n/a | jvm/wasm render-real (REM-75/76), nicht mehr Stub |
| **Offscreen/Render-to-Bitmap** (`createOffscreen`) | ✅ | ✅ | ✅ | ✅ | n/a | Skia-Surface+Flush auf allen 4 |
| **Opaque-Surface** (`renderOpaque`) | ✅ | ✅ | ✅ | ✅ | n/a | REM-56/75/76 |
| **Density / Platform-id** | ✅ | ✅ | ✅ | ✅ | ✅ | actual in allen aktiven Targets |
| **Basis- + Komplex-Text** (CMP/Skiko-Paragraph) | ✅ | ✅ | ✅ | ✅ | n/a | REM-32/74 (Komplex-Text-Layout) |
| **Eval-Engine** (Var/RPN/Array/Expression) | ✅ | ✅ | ✅ | ✅ | n/a | commonMain pure (REM-36/59/92/109/127) |
| **Layout** (Shallow-Measure/Arrangement/Modifier) | ✅ | ✅ | ✅ | ✅ | n/a | commonMain pure (REM-37 + REM-134-Span-Layout) |
| **Color/Theme/Component-Content** | ✅ | ✅ | ✅ | ✅ | n/a | REM-61/67/68/131/133/134/135 (Host-Palette-Seed, AttributedString) |
| **Prozedurale Creation-API** (`remote-creation` DSL) | ✅ | ✅ | ✅ | ✅ | ✅ | **korpus-komplett (REM-119, E1–E5)** |
| **Compose-Creation-DSL** (deklarativ, E6) | ✅ | ✅ | ✅ | ✅ | ✅ | E6 MVP + T2-Modifier (REM-128/130); T3 in flight (REM-141) |
| **App/Harness** | ✅ | ✅ | ✅ | ✅ | ✅ | androidApp/iosApp/desktopApp/webApp rendern; Server-Executable+CI (REM-126) |
| **Deferred-Render-Features** (Shader/Texture/PathEffect/KomplexText/FILL_AND_STROKE) | ✅ | ✅ | ✅ | ✅ | n/a | REM-74/77/94/98/99 — s. §5 für den Rest-Schwanz |

---

## 2. Plattform-Schicht pro Target (expect/actual — am Code verifiziert)

Die 3 Render-actuals (`decodeImageBitmap`, `createOffscreen`, `renderOpaque`) sind für **alle 4 aktiven
Targets render-real** — der 28.06.-Befund „jvm/wasm = Stub/fehlt" ist mit **REM-75 (Desktop jvm-actuals
render-real)** + **REM-76 (Web wasmJs actuals greenfield + js-Target raus + Link)** behoben.

| expect | And | iOS | jvm/Desk | wasmJs |
|---|---|---|---|---|
| getPlatform | ✅ | ✅ | ✅ | ✅ |
| platformDensityProvider | ✅ | ✅ | ✅ | ✅ |
| decodeImageBitmap | ✅ real | ✅ real (Skiko) | ✅ real (REM-75) | ✅ real (REM-76) |
| createOffscreen | ✅ real | ✅ real | ✅ real (REM-75) | ✅ real (REM-76) |
| renderOpaque | ✅ real | ✅ real | ✅ real (REM-75) | ✅ real (REM-76) |

**Apps:** androidApp/iosApp/desktopApp rendern; webApp (wasmJs, `ComposeViewport{App()}`, Browser-`.rc`-
async-Fetch REM-82) rendert + linkt. Server-Creation als Executable + CI (REM-126).

---

## 3. Render-Korrektheit: Status

**Geteilter Skiko-Vorteil eingelöst:** iOS, Desktop(jvm) UND WASM rendern via Skiko=Skia — die gesamte
commonMain-CMP-Render-Logik (~21,9k LOC) wirkt target-agnostisch. **Voll-173-Render-Sweeps laufen auf
Desktop (REM-78) und Web (REM-79/80).** Render-Korrektheit ist über das **REM-123-Daten-Orakel-Gate**
(in PROJECT_CONTEXT §6 ratifiziert) abgesichert: Docs mit dynamisch/akkumulierender Kurvengenerierung
brauchen am Golden-Promote einen unabhängigen Daten-Orakel-/Upstream-Player-Check, NICHT nur Cross-
Target-Self-Compare.

**🔴 Render-Gate-Lehre (REM-140, 29.06.):** Shared-Render-Path-Changes (z. B. LayoutMeasure) brauchen
einen **Voll-173-Render-Sweep** — ein DrawLine-Fingerprint übersieht Text-Positions-Shifts. REM-134
regressierte 2 Docs (player_info/text_baseline), gefangen erst vom Voll-Sweep, gefixt durch Scoping der
Measure-Changes auf den TextLayout-Span-Fall. Verankert als Merge-Gate-Pflicht.

---

## 4. Server-Creation (JVM headless) — ✅ KOMPLETT (REM-126)

- **Prozedurale Creation-DSL korpus-komplett (REM-119):** `rect()/circle()/text()/path()/…` + Layout-
  Container-API (REM-96) + Expressions (REM-92) + Matrix/Clip (REM-90) — alle Korpus-Ops, byte-
  gleichheits-conform (erzeugte Docs == Orakel, REM-84).
- **Server-Creation-Epic (REM-126) ZU:** JVM-headless Authoring + Disk-Writer + Executable + CI. Ein
  Server erstellt heute byte-korrekte `.rc`-Docs from scratch — ohne Render-actuals.
- **Compose-Creation-DSL (E6, deklarativ):** MVP (Capture+Container+Modifier) + T2 (+7 Modifier, ~95%
  Parität, REM-130) gemergt; T3 (Variablen-Primitive, REM-141) in flight.

---

## 5. Deferred-Features (Korpus-Reichweite, target-unabhängig) — Status

- **Komplex-Text-Layout** (Hyphenation/Justification/LineBreak/BiDi): ✅ **REM-74**.
- **DATA_SHADER (AGSL→SkSL)**: ✅ **REM-77**.
- **Texture-Bitmap-Shader** ✅ REM-98 · **PATH_EFFECT** ✅ REM-99 · **FILL_AND_STROKE** ✅ REM-94.
- **AttributedString / Component-Content** ✅ **REM-134** (24 Spans voll-styled, DrawContent-Delegation).
- **Variable-Fonts (FONT_AXIS)**: 🟡 Skiko-Interop, wenige Docs — Rest-Schwanz (FALLBACK_TYPEFACE REM-106 offen).
- **Sensoren/Touch/Interaktiv**: ✅ Touch on-device (REM-108) + Sensor Sim-Scope; **live-animiertes
  Impulse/Particles-Subsystem** (confetti/hearts/maze) = jetzt FULL Mensch-GO, in flight (REM-143, §6 #1).

---

## 6. Geordneter Rest-Backlog (Refresh — der kurze Schwanz)

| # | Posten | Status | Aufwand | Anm. |
|---|---|---|---|---|
| 1 | **Particles/Impulse-Subsystem (REM-143)** stateful Per-Frame-Sim, LIVE | **IN FLIGHT** (FULL Mensch-GO; TechSpec kanonisch, S1 in Arbeit) | L | dev-2/dev-1; LIVE-Multi-Frame-Gate · assist-§5b-Harden (RAND-Order spec-grounded) vor S2 |
| 2 | **E6-T3** Variablen-Primitive (visibility-full + dynamic-color-border) | ✅ **DONE** (REM-141) | — | gemergt |
| 3 | **E6-T4** RPN-Expr-DSL (RemoteFloatExpression) | ✅ **DONE** (REM-144 S1+S2) | — | gemergt |
| 4 | **compute/lookup** Render-Apply-Gap | ✅ **DONE** (REM-139 S1+S2 + REM-142 Golden) | — | gemergt |
| 5 | **Offene Tester-Render-Gates** auf gemergten Wellen (REM-131/132/134-Voll-Render) | OFFEN | S | test-3; REM-134 4-TextLayout-Docs Voll-Render-Confirm |
| 6 | **Render-Backlog-Tail** (AlignBy/DrawBitmap-Folge, paths_demos-Seed-Gap, VarFont/FALLBACK_TYPEFACE) | DEFERRED low/med | M | datenbasiert |
| 7 | **Creation-DSL-Long-Tail** (G3/G6–9, 0-Korpus-Konsument) | DEPRIORISIERT | — | optional, kein Korpus-Bedarf |
| 8 | **Cleanup/Hardening** (redundante rc-Kopien REM-100, Registry-Concurrency REM-11, CI-wasm-Guard, OFL.txt) | LOW | S | non-blocking |

**Der einzige große L-Brocken (#1 Particles) ist jetzt beauftragt + in flight** (nicht mehr eine offene
Scope-Frage). Der 28.06.-Rest (~40–70 Ticket-Äquiv.) ist auf Particles + einen kurzen Schwanz geschrumpft.

---

## 7. Gemessene Velocity (empirisch aus git, Refresh)

| Metrik | 28.06. | **29.06. @ `19bc0b5`** |
|---|---|---|
| Commits gesamt | 267 | **565** |
| distinkte REM-Tickets (git-Subjects) | 52 | **125** (REM-2 … REM-144) |
| develop-Merges | 86 | **96** |
| commonMain Kotlin LOC | ~15 494 | **~22 070** |
| Zeitspanne (Author-Dates) | ~2,8 Tage | **2026-06-25 → 2026-06-29 ≈ 4 Kalendertage** |

**Rate gehalten:** ~277 Commits + ~69 REM-Tickets in ~1,2 weiteren Kalendertagen (5–7 Agenten parallel)
= das Tempo aus §0 ist NICHT eingebrochen; die FC-Welle (Desktop/WASM-Render + Server-Creation + E6 +
Color/Component-Content) lief im selben intensiven Takt durch.

**🟡 Caveat (unverändert gültig):** komprimierte, agent-parallel-intensive Zeit, nicht Team-Kalenderzeit.
Velocity robust nur als Tickets-pro-aktivem-Tag / pro-Merge-Welle; Wall-Clock hängt am Durchhalten der Intensität.

---

## 8. Zeit-Schätzung (Refresh — der Rest ist klein)

Die 28.06.-Spanne (~3–7 aktive Tage Rest) ist eingelöst. **Verbleibend, konservativ:**
- **Particles/Impulse (#1)**: der einzige große L-Brocken, **beauftragt + in flight** (S1 läuft); LIVE-
  Multi-Frame-Gate + assist-§5b-Härtung vor S2. ~1–2 aktive Tage.
- **Offene Render-Gates (#5)** + **Render-Backlog-Tail/VarFont (#6)** + Cleanup (#8): **~0,5–1 aktiver
  Tag**, datenbasiert/non-blocking. (E6-T3/T4 + compute/lookup sind DONE.)

→ **Kern-FC (a)+(b) der Mensch-Definition ist erreicht.** Was bleibt, ist Particles (in flight) + ein
kurzer Schwanz. **Grob ~1–2 aktive Tage** für den non-Particles-Rest bei gehaltener Intensität.

**Dominante Unsicherheit jetzt:** (i) die offenen Tester-Render-Gates könnten echte Render-Bugs
aufdecken (REM-140-Klasse — ein Voll-Sweep fand schon 2 Regresse); (ii) Particles ist der größte
verbleibende Brocken (LIVE-Sim) — jetzt in flight, mit dem §5b-Gate als Schlüsselrisiko (False-Green-
Vektor, s. assist-Review).

---

## 9. Empfehlung an PO/Mensch

1. **FC-Kern als erreicht ratifizieren** (a: 4-Target-Render via Sweeps · b: Server-Creation + DSLs) —
   nach Abschluss der offenen Tester-Render-Gates (#3) als formaler Abnahme-Schritt.
2. **Particles (REM-143) zu Ende führen** — der einzige verbleibende L-Brocken, jetzt beauftragt + in
   flight (S1). **Schlüssel-Gate: die §5b-LIVE-Multi-Frame-Härtung (assist-NO-GO auf #4 bis spec-grounded)
   vor S2** — sonst zirkuläres False-Green-Gate (Gate-Referenz = Sim statt Spec).
3. **Render-Gate-Disziplin halten** (REM-140-Lehre): Shared-Render-Path-Changes → Voll-173-Render-Sweep,
   nicht DrawLine-Fingerprint; REM-123-Daten-Orakel am Golden-Promote.
5. **Jira-Hygiene:** mehrere gemergte Tickets stehen wegen des dev-2/dev-3-Jira-Auth-Lochs noch auf
   „Zu erledigen" (z. B. REM-133/137/138) — bei gelöstem Auth einen Transition-Sync-Pass; bis dahin ist
   git-`develop` die Quelle der Wahrheit, nicht die Jira-Labels.

**Offene Mensch-Flags:** dev-2-Jira-Auth (Access, out-of-band). **Ratifiziert (29.06.):** Particles-FC-Scope
(= FULL GO, REM-143) · iOS-Sensor-Sim-Scope · REM-114/S4 (Headless-Artefakt, kein Bug) · §6-Daten-Orakel-Gate
+ §8 · E6-T2/T3/T4.
