# TechSpec — Epic E: Creation-DSL (`remote-creation` Port) — Design für dev-3

> **Autor:** PO-Assistent · **Status:** Design (kein Code), Start-Vorlage für dev-3 (Epic-E-Owner) · **Datum:** 2026-06-28
> **Scope (Mensch final):** volle prozedurale Creation-DSL **+ E6 `remote-creation-compose` IN-Scope** (@Composable-DSL).
> **Gegen** Upstream `remote-creation*` (Verhalten verstehen, NICHT kopieren — §8). **Bindend:** PROJECT_CONTEXT (kein `java.*` in commonMain; okio; byte-identisch zum Upstream-Writer = §2-Invariante).

---

## 0. Leitprinzip (das Wichtigste)

**Die DSL ist ein dünner Op-Emitter, kein neuer Encoder.** Jede API-Methode ruft am Ende `writer.add(TheOp(operands))` — und `TheOp.write()` ist **bereits byte-bewiesen (L1, 173/173)**. Byte-Korrektheit der DSL reduziert sich damit auf: **„emittiert sie die richtigen Ops, in der richtigen Reihenfolge, mit den richtigen Operanden + ids".** Nichts an der Serialisierung neu bauen. Das ist der Hebel, der Epic E gegen die §2-Invariante absichert.

---

## 1. Modul-/Schicht-Struktur (Upstream-Landkarte → KMP-Port)

Upstream hat drei Schichten; wir spiegeln sie in **einem** shared-Modul mit Source-Set-Trennung:

| Upstream | Rolle | KMP-Ziel |
|---|---|---|
| `remote-creation-core` (`RemoteComposeWriter`, `RcPaint`, Shapes/Modifiers, `Rc`) | Low-level Op-Emitter + Doc-Lifecycle | **commonMain** — erweitert unseren bestehenden `RemoteComposeWriter` |
| `remote-creation` (`RemoteComposeContext(Android)`, `Painter`, `RcPlatformServices`) | Ergonomische prozedurale API + Plattform-Services | **commonMain** Context + **expect/actual** `RcPlatformServices` |
| `remote-creation-compose` | @Composable-DSL auf dem Context | **commonMain** (CMP) — Epic-E6, sitzt AUF dem Context |

**Bestehende Basis:** unser `RemoteComposeWriter` (commonMain, `remote/core/document/`) ist heute nur `add(op)` + `encodeToByteArray()` → das ist das **Scaffold-Fundament**. E1 baut die prozedurale Schicht darauf, NICHT daneben.

**Plattform-Services (expect/actual, wie upstream `RcPlatformServices`):** Font-Metriken (Text-Measure beim Schreiben), Bitmap-Encoding. **Für den Server-Target zuerst der `jvm`-actual** (upstream hat `JvmRcPlatformServices.jvm.kt` als Beleg, dass JVM-headless-Creation ein etablierter Pfad ist). android/ios-actuals folgen für In-App-Creation; nicht blockierend für „Server erstellt Docs".

---

## 1a. E1 load-bearing API-Entscheidungen (dev-3 — explizit, damit kein API-Umbau)

**D1 — Konstruktor-Surface: EINE common DSL-Funktion, nicht upstreams 8 Android-Konstruktoren.**
```kotlin
fun document(
    width: Int, height: Int,
    profile: Profile = Profile.Baseline,
    contentDescription: String? = null,
    content: RemoteComposeContext.() -> Unit,
): ByteArray
```
Intern: `RemoteComposeWriter(width, height, profile.operationsProfiles, contentDescription)` + `RemoteComposeContext(writer, profile.services)`, dann `content()`, dann `writer.encodeToByteArray()`. Upstreams viele Android-Konstruktoren sind Bitmap-/Platform-Convenience — KMP hält **einen** common-Builder + `profile`-Param. (Context-Direktkonstruktor zusätzlich exponieren für den Compose-Capture-Pfad, §D4.)

**D2 — Lifecycle-Verb: `document { }` receiver-Lambda (`RemoteComposeContext.() -> Unit`).** Begründung: idiomatisch; die Funktion **klammert** Lifecycle (Header schon im Writer-init → emit → encode) automatisch; gibt die Bytes zurück; und der **gleiche Context** ist das, was der Compose-Capture treibt. NICHT Konstruktor-dann-mutieren (leakt halb-gebauten State), NICHT separates `apply{}`. `save{}`/Container = verschachtelte receiver-Lambdas darin.

**D3 — `Profile` (commonMain-Ersatz für upstream `Profile.create()`): dünner Holder, dockt an bestehendes Gating an.**
```kotlin
class Profile(val operationsProfiles: Int, val services: RcPlatformServices) {
    companion object { val Baseline = Profile(Operations.PROFILE_BASELINE, defaultRcPlatformServices()) }
}
```
**🔴 Nicht neu erfinden:** unser `RemoteComposeWriter` hat `profiles: Int` schon und gated **jedes `add(op)`** via `Operations.isValid(opcode, apiLevel, profiles)` (fail-closed). Die Profile-Schicht reicht nur den Int durch + bündelt die Services. Kein `java.*` (Int + Interface). `RcPlatformServices` = **expect/actual**, `defaultRcPlatformServices()` ist die expect-Factory (jvm-actual zuerst).

**D4 — 🔴 E6-Compose-Hook: der Context ist das RECORDING-Target; die @Composable-DSL treibt ihn via Applier.** Upstream-Muster = `CanvasOperationBuffer` → `CapturedDocument` + `@RemoteComposable`-Marker + `CaptureRemoteDocument(content: @Composable @RemoteComposable () -> Unit)`. KMP-Entscheidung: E1-Context so bauen, dass ein Compose-**Applier** ihn treiben kann:
- (a) **scoped Container öffnen/schließen** als explizite Calls (Applier `insert/remove/move` Nodes → ContainerStart/Ende-Ops);
- (b) ein stabiler **„current emission cursor"** (der Context hält die Schreibposition, nicht der Aufrufer);
- (c) **extern-getriebener Lifecycle** (begin→emit→end NICHT in einen Monolith-Call gebacken — `document{}` ist nur die *prozedurale* Bequemlichkeit über demselben begin/emit/end).
→ E6 wird dann `captureRemoteDocument(w,h,profile){ /* @RemoteComposable */ }`: instanziiert denselben Context, läuft die Komposition, mappt @Composable-Nodes (`RemoteColumn`/`RemoteText`/`RemoteCanvas{ drawCircle() }`) auf dieselben Context-emit/scope-Calls. **Wenn E1 (a)+(b)+(c) erfüllt, ist E6 ein Aufsatz, kein Rework.**

---

## 2. API-Surface E1–E4 (gegen Upstream `RemoteComposeWriter` + `RemoteComposeContextAndroid`)

### E1 — Core-Scaffold (der Contract, blockierend)
- **Doc-Lifecycle:** `header(width, height, …)`, `setRootContentBehavior(scroll, alignment, sizing, mode)`, `setTheme(theme)`, Doc-Ende/Flush → `encodeToByteArray()`. Emittiert die exakten Prolog-Ops (Header + RootContentBehavior) **byte-identisch** — das ist der erste Byte-Gleichheits-Prüfstein.
- **id-Allokation:** zentral im Writer — `addFloatConstant(v)→id`, `addColor(argb)→id`, `addText(str)→id`, `addBitmap(...)→id`, `pathCreate(...)→id`. **Die id-Vergabe-Reihenfolge ist Teil der Byte-Ausgabe → muss dem Upstream-Schema folgen** (sonst byte-divergent trotz „gleicher" Doc).
- **`RemoteComposeContext` (common):** der ergonomische Wrapper — hält den Writer + `RcPaint`-State, bietet `save{ }`/scoping (lambda-receiver), Number-Overloads. **Von Anfang an Compose-drivable auslegen** (§3).

### E2 — Draw + Paint (dev-3)
- `drawCircle/drawRect/drawOval/drawLine/drawArc/drawSector` (+ Number-Overloads wie upstream Context).
- **Path-Builder:** `pathCreate` + `pathAppendMoveTo/LineTo/QuadTo/Close/Reset` → `drawPath` (+ `drawTweenPath`/`pathTween`).
- **`RcPaint`:** color/colorId/strokeWidth/style/cap/join/alpha/blend/shader → emittiert das `PAINT_VALUES`-Bundle (L1 PaintData byte-bewiesen; Slot-Reihenfolge = die in REM-37 verifizierte).

### E3 — Text / Bitmap / Matrix / Clip (dev-3)
- Text: `addText`, `drawTextRun`, `drawTextAnchored` (panX/panY/flags), `drawTextOnPath`, `createTextFromFloat`, `textSubtext/textTransform`.
- Bitmap: `addBitmap` (→ via Platform-Service encoden) + `drawBitmap`.
- Matrix: `translate/scale/rotate/skew/matrixFromPath` + `save{}/restore`.
- Clip: `clipRect`/`clipPath`.

### E4 — High-Level (dev-3)
- **Zeit/Clock:** Time-Variablen-Refs (CONTINUOUS/TIME_IN_SEC/…), `wakeIn`.
- **Layout:** Column/Row/Box + Modifier (width/height/padding/background/border/clip/align) — die Creation-Seite der L2-Layout-Ops.
- **State/Variablen:** NamedVariable, `beginGlobal/endGlobal`, Float-Expression-Builder (RPN — die Schreib-Seite der Eval-Engine), `loop(from,step,until){ }`.

> **Verifikation pro Schritt = decode-and-inspect** (unser L1-Reader liest das erzeugte Doc → Op-Baum gegen Intent prüfen) **+ Byte-Gleichheit wo ein Orakel existiert** (§4).

---

## 3. E6-Einordnung: `remote-creation-compose` (@Composable-DSL) — von Anfang an mitdenken

**Die Compose-DSL reimplementiert KEINE Op-Emission — sie treibt den prozeduralen Context.** @Composable-Funktionen (`RemoteBox`/`RemoteColumn`/`RemoteText`/`RemoteCanvas { drawCircle(...) }` …) mappen über einen **Composer/Applier** auf `RemoteComposeContext`-Aufrufe. Konsequenz für E1 (jetzt zu beachten, damit E6 nicht später Umbau erzwingt):
- Der Context muss **scoped Emission** sauber können (`save{}`/Layout-Container öffnen→Kinder→schließen) — genau das Muster, das ein Compose-Applier (`insert/remove/move` Nodes → Container-Start/Ende-Ops) braucht.
- Lifecycle (begin → emit → end) als **explizite, von außen treibbare Sequenz**, nicht in einen monolithischen Aufruf eingebacken.
- State/Recomposition: die Compose-DSL erzeugt ein **statisches Doc pro „Komposition"** (kein Live-Recompose im Writer) — der Applier läuft einmal, emittiert die Op-Liste, encoded. (Live-Verhalten steckt im PLAYER, nicht im Writer.)

→ dev-3 legt die E1-Context-Struktur **applier-freundlich** aus; E6 wird dann ein Aufsatz, kein Rebuild.

---

## 4. E5 — Byte-Conformance-Strategie (gegen die Korpus-Fixtures)

Drei Stufen, von schwach→stark:
1. **Round-trip-Selbstkonsistenz:** DSL→`encodeToByteArray`→**L1-Reader decode**→re-encode → **byte-stabil**. Nutzt den byte-bewiesenen L1-Codec; fängt id/Reihenfolge-Fehler.
2. **Decode-and-inspect:** erzeugtes Doc mit L1-Reader lesen, **Op-Baum gegen die beabsichtigte Sequenz** assert (Typ/Operanden/Reihenfolge) — der Haupt-Unit-Test je DSL-Methode.
3. **🎯 Byte-Gleichheit gegen echte Orakel-`.rc` (das stärkste — und wir HABEN Orakel):** die Korpus-`procedure_*`-Fixtures (`procedure_simple1`, `procedure_gradient1-4`, `procedure_center_text1`, `procedure_look_up1`, `procedure_version`, `procedure_text_path_effects`) sind **selbst Upstream-prozedural-Creation-Outputs** → das ideale Byte-Orakel. dev-3 repliziert deren Op-Sequenz via DSL und assertet **byte-identisch zum gebündelten Fixture**. **Start mit den kleinsten** (`procedure_version` 289B, `procedure_gradient1` 267B, `procedure_simple1`) → früher, scharfer Byte-Beweis, dass Header+id-Schema+Op-Emission upstream-treu sind.

> Wo kein procedure_*-Orakel existiert (Clock/Layout-Docs aus anderen Generatoren), bleibt Stufe 1+2 das Gate. Byte-Gleichheit immer dort einziehen, wo ein Orakel-Doc existiert.

---

## 5. KMP-Leitplanken (Review-durchgesetzt)
- **commonMain:** prozeduraler Writer + Context + Compose-DSL rein common; **kein `java.*`** (Ops emittieren über WireBuffer — schon byte-safe).
- **expect/actual `RcPlatformServices`:** Font-Metriken/Bitmap-Encode; **jvm zuerst** (Server), dann android/ios.
- **Byte-identisch zum Upstream-Writer** ist die Abnahme (§2) — id-Schema + Op-Reihenfolge + Header-Prolog exakt. Eine DSL-Methode ohne decode-inspect-Test (min. Stufe 2) ist nicht fertig.
- **Kein Verbatim-Copy** von Upstream — Verhalten nachbauen (Apache-2.0-Provenienz sauber halten).

---

## 6. Empfohlene Start-Sequenz für dev-3
1. **E1** Writer-Lifecycle (`header`/`setRootContentBehavior`/id-Allokation) + Context-Scaffold (applier-freundlich) → sofort gegen `procedure_version`/`procedure_gradient1` byte-prüfen (kleinster End-to-End-Beweis, dass Prolog+ids stimmen).
2. **E2** Draw+RcPaint → byte gegen `procedure_simple1`/`procedure_gradient1-4`.
3. **E3** Text/Bitmap/Matrix/Clip → `procedure_center_text1`/`procedure_text_path_effects`.
4. **E4** High-Level (Clock/Layout/State/Expr/loop) → Stufe-1/2 + Orakel wo vorhanden.
5. **E5** als durchgehendes Gate (nicht am Ende).
6. **E6** Compose-DSL als Aufsatz auf dem fertigen Context.

**Lane:** dev-3 (Owner, durchgehend). Review: PO-Assistent (Story-Reviews E1→E6, jeweils byte-safe-additiv + Orakel-Beleg). Test-Byte-Conformance (E5): test-2.
