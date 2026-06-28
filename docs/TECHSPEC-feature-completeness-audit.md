# TechSpec — Feature-Completeness-Audit (gegen die Mensch-Definition)

> **Autor:** PO-Assistent (Reviewer) · **Status:** Audit/Analyse (KEIN Code) · **Datum:** 2026-06-28
> **Audit-Basis:** echte Klon-Kopie `KmpRemoteCompose` @ develop-Stand (HEAD 2026-06-28). Alle Zellen am Code/git verifiziert, nicht aus dem Gedächtnis. Peer-Inputs (dev-1/dev-2) **unabhängig am Code gegengeprüft** (eine Korrektur: jvm-actuals sind Test-Stubs, nicht render-real — siehe §2).
> **Feature-complete (Mensch):** (a) ALLE Korpus-Docs rendern auf **iOS, Android, Desktop, WASM** + (b) Docs lassen sich auf einem **Server (JVM, headless)** erstellen. Aktiviert Desktop+WASM (vorher dormant) + die Server-Creation-Seite.

---

## 0. Executive Summary

- **Layer-1 (binärer .rc Reader+Writer, 173 Ops) ist byte-bewiesen fertig** und liegt **rein in commonMain** → läuft auf JEDEM Target wo es compilet. Das ist das schwerste/risikoreichste Stück (die §2-Invariante) und es ist durch.
- **Render läuft heute auf Android + iOS** (Layer-2-Adapter + Eval + Layout + Color, REM-31..70; on-device-Sweeps via test-1 laufend).
- **Desktop + WASM sind NICHT „nur App-Wiring".** Beide brauchen **3 render-reale Plattform-actuals** (`decodeImageBitmap`, `createOffscreen`, `renderOpaque`). Desktop hat sie als **Test-Stubs** (compilet, rendert leer); WASM/JS haben sie **gar nicht** (compilet nicht). Die **iOS-Skiko-actuals sind die Wiederverwendungs-Vorlage** für alle drei (Desktop/wasm/js rendern alle via Skiko).
- **Server-Creation low-level geht HEUTE** (Op-Liste + `RemoteComposeWriter.encodeToByteArray`, byte-bewiesen); die **ergonomische prozedurale Creation-API (`remote-creation`) fehlt**.
- **„ALLE Docs" verlangt zusätzlich die Deferred-Render-Features** (Komplex-Text, Shader, Variable-Fonts, GraphicsLayer-advanced, Sensoren/Touch) — target-unabhängig, das lange Ende.
- **Gemessene Velocity:** 52 REM-Tickets / 86 develop-Merges / 267 Commits über ~2,8 Kalendertage (5 Agenten parallel) ≈ **~17 Tickets/Tag**. **Rest-Backlog grob ~40–70 Ticket-Äquivalente** ≈ nochmal so viel wie bisher, dominiert von zwei L-Brocken (Deferred-Features + Creation-API). **🔴 Aber:** die bisherige Rate kam aus Politur auf fertiger Basis; der Rest ist Plattform-Bring-up + Greenfield-DSL (qualitativ anders, langsamer) → Schätzung als **Spanne ~3–7 aktive Tage**, nicht Punkt (§8).

---

## 1. Modul × Target-Matrix

Legende: ✅ fertig/bewiesen · 🟡 teilweise/ungeprüft · ❌ offen/fehlt · n/a.
Targets: **And**=Android · **iOS** · **Desk**=Desktop(jvm) · **WASM**=wasmJs · **JS**=js · **Srv**=Server-Creation(jvm headless).

| Modul / Fähigkeit | And | iOS | Desk | WASM | JS | Srv | Beleg |
|---|---|---|---|---|---|---|---|
| **L1 Reader** (`remote-core`, .rc→Ops) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | commonMain, byte/round-trip-bewiesen (173/173); kein Plattform-Layer |
| **L1 Writer** (`RemoteComposeWriter`, Ops→.rc) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | commonMain, 121 Ops `write()`, byte-identisch vs Orakel; **low-level** (`add`/`encodeToByteArray`) |
| **L2 Geometrie-Adapter** (rect/oval/path/clip/matrix/gradient, CMP) | ✅ | ✅ | 🟡 | ❌ | ❌ | n/a | commonMain CMP; rendert wo Skiko+actuals da; Desk/WASM gated auf Render-actuals |
| **Bitmap-Decode** (`decodeImageBitmap`) | ✅ | ✅ | ❌ | ❌ | ❌ | n/a | And BitmapFactory, iOS Skiko; **jvm=null (Stub)**; js/wasm fehlen |
| **Offscreen/Render-to-Bitmap** (`createOffscreen`, REM-60) | ✅ | ✅ | ❌ | ❌ | ❌ | n/a | iOS Skia-Surface+Flush; **jvm=bare Canvas (kein Flush, Stub)**; js/wasm fehlen |
| **Opaque-Surface** (`renderOpaque`, REM-56) | ✅ | ✅ | ❌ | ❌ | ❌ | n/a | iOS Raster-Surface; **jvm=passthrough (Stub)**; js/wasm fehlen |
| **Density** (`platformDensityProvider`) | ✅ | ✅ | ✅ | ✅ | ✅ | n/a | actual in allen 5 |
| **Platform-id** (`getPlatform`) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | actual in allen 5 |
| **Basis-Text** (CMP TextMeasurer/Paragraph) | ✅ | ✅ | 🟡 | ❌ | ❌ | n/a | commonMain CMP; Desk Skiko-plausibel ungeprüft; Web gated |
| **Eval-Engine** (Var/RPN/Array/Expression) | ✅ | ✅ | ✅ | ✅ | ✅ | n/a | commonMain pure (REM-36/59); läuft wo es compilet |
| **Layout** (Shallow-Measure/Arrangement/Modifier) | ✅ | ✅ | ✅ | ✅ | ✅ | n/a | commonMain pure (REM-37 E-Layout) |
| **Color/Theme** (ColorConstant/Expression) | ✅ | ✅ | ✅ | ✅ | ✅ | n/a | REM-61/67; `system_accent`-Palette = REM-68 (Design, pending) |
| **Prozedurale Creation-API** (`remote-creation` DSL) | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | existiert NICHT (nur low-level Writer) |
| **App/Harness** | ✅ | ✅ | 🟡 | 🟡 | 🟡 | 🟡 | androidApp/iosApp rendern; desktopApp `Window{App()}` verdrahtet (rendert leer bis Render-actuals); webApp `ComposeViewport{App()}` js+wasm verdrahtet (linkt nicht bis actuals) |
| **Deferred-Render-Features** (Shader/VarFont/KomplexText/GfxLayer/Sensoren) | 🟡 | 🟡 | 🟡 | 🟡 | 🟡 | n/a | target-unabhängige Korpus-Lücke, §5 |

---

## 2. Plattform-Schicht pro Target (expect/actual — am Code verifiziert)

**5 commonMain-expects** (NICHT 6 — `systemAccentPalette` ist REM-68-Design, noch nicht im Code):
`getPlatform`, `platformDensityProvider`, `decodeImageBitmap`, `renderOpaque`, `createOffscreen`.

| expect | And | iOS | jvm/Desk | wasmJs | js |
|---|---|---|---|---|---|
| getPlatform | ✅ | ✅ | ✅ | ✅ | ✅ |
| platformDensityProvider | ✅ | ✅ | ✅ | ✅ | ✅ |
| decodeImageBitmap | ✅ real | ✅ real (Skiko) | 🟡 **Stub (=null)** | ❌ | ❌ |
| createOffscreen | ✅ real | ✅ real (Skia-Surface+Flush) | 🟡 **Stub (bare Canvas)** | ❌ | ❌ |
| renderOpaque | ✅ real | ✅ real (Raster) | 🟡 **Stub (passthrough)** | ❌ | ❌ |

**🔴 Die Kern-Erkenntnis (dev-2-Flag, von mir am Code bestätigt):** jvm „hat alle 5 actuals" ist **Compile-Vollständigkeit, nicht Render-Vollständigkeit**. Die 3 Render-actuals sind headless-Test-geformt (decode=null, offscreen=Flush-los, opaque=passthrough) — korrekt für das jvmTest-Conformance-Harness, **unbrauchbar für ein echtes Desktop-Render-Target**. Desktop-Render = die 3 jvm-Stubs auf **render-real** heben (Skiko, iOS-Impl als nahe Vorlage), NICHT nur App-Wiring.

**Apps:** androidApp ✅, iosApp ✅ (rendern). desktopApp ✅ verdrahtet (`Window{App()}`, compose.desktop.currentOs) — rendert leer bis Render-actuals real. webApp ✅ verdrahtet (`ComposeViewport{App()}`, js+wasmJs `binaries.executable()`, hängt an `:shared`) — **linkt nicht** bis js/wasm-actuals existieren.

---

## 3. Render-Korrektheit: was läuft by-construction vs. was blockt

**Geteilter Skiko-Vorteil:** iOS, Desktop(jvm/Compose-Desktop) UND WASM/JS rendern alle via **Skiko=Skia** (gleiche Engine). Die gesamte commonMain-CMP-Render-Logik (Geometrie/Text/Paint/Eval/Layout, ~15,5k LOC) ist **target-agnostisch** und läuft, sobald (i) die 3 Render-actuals real sind und (ii) das Target compilet/verlinkt. → **Der iOS-Render-Pfad ist die Blaupause; Desktop/WASM erben den Großteil.**

**Konkret blockierend:**
- **Desktop erster Render:** `ImageDecode.jvm` (Skia `Image.makeFromEncoded` statt null), `Offscreen.jvm` (Skia-Surface+`makeImageSnapshot`-Flush wie iOS REM-60 statt bare Canvas), `OpaqueSurface.jvm` (Raster-Surface wie iOS REM-56 statt passthrough). Alle drei = iOS-Impl quasi 1:1 (beide Skiko). + Desktop-Render-Sweep-Harness.
- **WASM erster Render:** dieselben 3 actuals **greenfield** in wasmJsMain (Skiko-wasm hat die APIs; wasm-Bitmap-Decode + async Browser-`.rc`-Fetch statt okio-FileSystem sind die echten Knackpunkte) + compile-Target verifizieren + web-Render-Harness. JS analog (niedrigere Prio falls wasmJs der Web-Haupt-Target ist).

---

## 4. Server-Creation (JVM headless)

- **Low-level: FERTIG.** `RemoteComposeWriter(add(op) … encodeToByteArray())` in commonMain, 121 Ops mit `write()`, byte-identisch zum Orakel (Round-trip-bewiesen). Ein Server kann **heute** eine Op-Liste assemblieren und ein byte-korrektes `.rc` schreiben — auf jvm-headless (keine Render-actuals nötig).
- **Fehlt: die prozedurale Creation-API.** Upstream `remote-creation` (ergonomische `rect()/circle()/text()/clock`-Prozedur-API) + optional `remote-creation-compose` (Compose-DSL für Creation) sind **nicht portiert**. Ohne sie ist „Doc von Grund auf erstellen" nur manuelle Op-Assemblierung (funktioniert, aber roh).
- **Entscheidung Mensch/PO:** reicht Op-Listen-Creation (→ S, nur Wrapper+Doku) oder braucht „Server erstellt Docs" die volle DSL (→ L, eigenes Modul)?

---

## 5. Deferred-Features (Korpus-Reichweite, target-unabhängig)

Aus der L2-Korpus-Investigation (Generator-Grep) — nötig für „**ALLE** Docs rendern" auf JEDEM Target:
- **Komplex-Text-Layout** (Hyphenation/Justification/LineBreak/BiDi): **mehrere Docs** (CanvasComponents, DslTextDemo, Text, Type) → größte Deferred-Lücke. **L.**
- **DATA_SHADER (AGSL→SkSL)**: **~2 Docs** (AiAgent, TimeSphere) → klein aber spiky (Sprach-Übersetzung). **M.**
- **Variable-Fonts (FONT_AXIS)**: wenige (DslFontAxis, VariableFont, Text) → Skiko-Interop. **M.**
- **GraphicsLayer-advanced** (cameraDistance/3D/Shadow/Blur-tileMode): wenige. **M.**
- **Sensoren/Touch/Interaktiv** (touch1/2, sensor_demo, haptic, wake): für **statischen Render** reicht ein Frame; **volle Interaktivität** ist jenseits „rendern". Klären: zählt „rendert einen Frame" als complete? **M (Render-Frame) / L (Interaktivität).**

> Anmerkung: Deferred-Features sind **commonMain/Skiko-Lücken** → einmal gebaut wirken sie auf allen Skiko-Targets gleich. Sie sind orthogonal zu den Plattform-actuals (§2).

---

## 6. Geordneter Rest-Backlog + Aufwand (S/M/L) — priorisiert

| # | Brocken | Aufwand | Ticket-Äquiv (grob) | Abhängig |
|---|---|---|---|---|
| 1 | **Desktop render-real**: zuerst Desktop-Render-Sweep (Schiedsrichter) → die meisten Docs (Shapes/Text/Pfade/Farbe) rendern by-construction (Skiko); dann die jvm-actual-Real-Upgrades, die der Sweep aufdeckt — v.a. **Bitmap-Docs** (`ImageDecode.jvm=null`→leer) + **offscreen/opaque-sensitive** (bit_draw2/cube3d, brauchten auf iOS REM-55/56/60). iOS-Vorlage 1:1 | **S (Verify) + M (Upgrades)** | ~5–9 | iOS-actuals (Vorlage da) |
| 2 | **WASM/JS render**: 3 actuals greenfield (Skiko-wasm/js) + compile/Link aktiv + webApp `.rc`-async-Loading + Web-Render-Sweep | **M–L** | ~7–12 | #1-Muster |
| 3 | **Deferred-Render-Features** (Komplex-Text L, Shader M, VarFont M, GfxLayer M, Sensoren/Touch M) für Voll-Korpus-Parität | **L** | ~12–20 | — |
| 4 | **Prozedurale Creation-API** (`remote-creation` Port) für ergonomische Server-Creation | **L** (S falls Op-Liste reicht) | ~10–18 (od. ~2) | L1-Writer (da) |
| 5 | **Cross-Target-Test-Infra** (Desktop+Web-Render-Sweep-Harness; Conformance ist schon cross-platform) | **M** | ~4–7 | #1/#2 |
| 6 | **REM-68 Theme-Palette** (system_accent, Design fertig) + lfd. Render-Parity-Restwelle (REM-70+ on-device) | **S–M** | ~3–6 | REM-68-Freigabe |

**Summe grob: ~40–70 Ticket-Äquivalente** (Mitte ~50), dominiert von #3 (Deferred) + #4 (Creation-DSL).

---

## 7. Gemessene Velocity (empirisch aus git)

| Metrik | Wert |
|---|---|
| Commits gesamt | 267 |
| distinkte REM-Tickets (Subjects) | 52 (REM-2 … REM-69, mit Lücken) |
| develop-Merges (`-> develop`) | 86 |
| commonMain Kotlin LOC | ~15 494 |
| Op-Dateien | 129 |
| Zeitspanne (Author-Dates) | 2026-06-25 15:47 → 2026-06-28 10:46 ≈ **2,8 Kalendertage** |
| Commits/Tag | 94 / 124 / 33 / 16 (25./26./27./28.) |
| develop-Merges/Tag | 15 / 56 / 13 / 2 |

**Abgeleitete Rate:** ~**17 REM-Tickets/Tag** bzw. ~**29 Merges/Tag** im beobachteten Tempo (Spitze Tag 2: 124 Commits, 56 Merges).

**🟡 Caveat (keine Scheinpräzision):** die Zeitspanne ist **stark komprimiert** (3 aktive Tage, 5 Agenten parallel, front-loaded). Das ist **agent-parallel-intensive** Zeit, nicht Team-Kalenderzeit. Die Merge-Kadenz fällt (56→13→2), aber das spiegelt **Annäherung an die in-scope-Feature-Grenze** (Android+iOS-Render fast fertig → Rest = Fixes), nicht sinkenden Durchsatz. Velocity daher robust nur als **Tickets-pro-aktivem-Tag / pro-Merge-Welle** ausgedrückt; jede Wall-Clock-Umrechnung hängt am Durchhalten dieser Intensität.

---

## 8. Zeit-Schätzung, kalibriert gegen die gemessene Velocity

**Basis:** ~50 abgeschlossene Ticket-Äquiv. in ~3 aktiven, intensiven Tagen → ~17/Tag.
**Rest:** ~40–70 Ticket-Äquiv. (§6, Mitte ~50).

**🔴 Arbeits-Typ-Caveat (entscheidend — die Rate überträgt sich NICHT 1:1):** die gemessenen ~17/Tag entstanden aus **Render-Debugging / Byte-Format / Paritäts-Politur auf einer FERTIGEN Plattform-Basis** (Android+iOS-actuals lagen, der Korpus war gebündelt, das Conformance-Harness stand). Die Restarbeit ist **qualitativ anders**: (i) Plattform-**Bring-up** (WASM 3 actuals greenfield + Browser-async-Loading, Desktop actual-Upgrades) und (ii) eine **Creation-DSL von Grund auf** (neues Modul, kein Render-Debugging). Solche Greenfield-/Bring-up-Arbeit läuft typischerweise **langsamer** als das bisherige Politur-Tempo. Deshalb: **Order-of-Magnitude-SPANNE, keine Punkt-Schätzung.**

→ **Differenzierte Spanne (statt einer Zahl):**
- **Desktop** (#1, iOS-Vorlage 1:1, der Sweep zeigt wie viel by-construction läuft) → am NÄCHSTEN am bisherigen Tempo → ~1 aktiver Tag.
- **WASM/JS** (#2, greenfield-Skiko + Browser-Loading) → LANGSAMER als die Rate → ~1–2 aktive Tage.
- **Deferred-Features** (#3, neue Risiko-Klassen Komplex-Text/Shader) → Render-nah aber neu → ~1–2 aktive Tage.
- **Creation-DSL** (#4) → eigene Achse, am wenigsten vom bisherigen Tempo gedeckt → S (~0,3 Tg, Op-Liste) bis L (~1,5–2,5 Tg, volle DSL) je nach Mensch-Scope.
- **Summe: grob ~3–7 weitere aktive Tage** bei gehaltener 5-Agenten-Intensität — Mitte „**etwa nochmal so viel wie bisher**", aber die obere Hälfte ist realistischer als die untere, weil Bring-up/Greenfield langsamer ist als Politur. Untere Grenze (~3 Tg) nur, wenn der Mensch #4→Op-Liste und Sensoren→Render-Frame scopt.

**Annahmen + Unsicherheiten (transparent):**
- **Dominante Unsicherheit = die zwei L-Brocken:** #3 Deferred-Features (besonders Komplex-Text + Shader-Übersetzung — neue Risiko-Klassen, kein Template) und #4 Creation-DSL (eigenes Modul). Wenn der Mensch #4 auf „Op-Liste reicht" (S) reduziert und Sensoren/Touch auf „Render-Frame statt Interaktivität" scopt, **fällt der Rest auf ~25–40 Ticket-Äquiv. ≈ ~1,5–2,5 Tage**.
- **Desktop (#1) ist günstiger als es aussieht** (iOS-Skiko-Vorlage 1:1) trotz der Stub-Korrektur — der teure Teil (Skia-Render-Logik) ist schon da.
- **WASM (#2)** trägt das größte Plattform-spezifische Risiko (wasm-Bitmap-Decode, async Resource-Loading) — könnte am oberen Rand landen.
- **Die ~17/Tag-Rate ist intensiv** (Spitzentempo Tag 1-2); hält sie nicht durch, skaliert die Wall-Clock linear hoch. Velocity in Tickets gemessen ist robuster als in Tagen.
- Render-Korrektheit verlangt weiterhin die on-device/cross-target-Sweeps als Gate (dispatch≠render) — in der Schätzung als Test-Infra (#5) enthalten.

---

## 9. Empfehlung an PO/Mensch

1. **Scope-Hebel zuerst klären** (halbiert potenziell den Rest): (a) Creation = Op-Liste (S) vs volle DSL (L)? (b) Sensoren/Touch = Render-Frame (M) vs Interaktivität (L)? (c) JS zwingend oder reicht wasmJs als Web-Target?
2. **Sequenz:** #1 Desktop (billigster echter neuer Target, iOS-Vorlage) → #2 WASM → #3 Deferred (Komplex-Text zuerst, meiste Docs) ‖ #4 Creation (parallel, unabhängige Lane). #5 Test-Infra zieht mit #1/#2.
3. **Lanes:** Render-actuals (Desk/WASM) = dev-2/shared; Creation-API = dev-1; Deferred-Features split dev-1/dev-2; Cross-Target-Sweep = test-1/test-2.
4. Größte Brocken (#3, #4) nach Mensch-Scope in Stories/Epics splitten.
