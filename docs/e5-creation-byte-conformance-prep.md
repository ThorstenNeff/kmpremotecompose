# E5 — Creation-Byte-Conformance · PREP (REM-84, test-2)

> **Owner:** test-2 (E5-Gate). **Status:** Prep gegen merged E1 (develop `a77c6ad`). Voll-Assertion gated auf E2–E4 (dev-3).
> **Roadmap:** `TECHSPEC-REM-E-creation-dsl.md` §4 (3-Stufen-Strategie). **Invariante:** §2 — DSL-erzeugtes `.rc`
> MUSS byte-identisch zum Upstream-Writer sein. **Harness:** `CreationByteConformanceTest.kt` (commonTest).
>
> **Leitprinzip (Techspec §0):** die DSL ist ein dünner Op-Emitter — die Op-`write()` ist L1-byte-bewiesen (173/173).
> Byte-Korrektheit reduziert sich auf „richtige Ops, in richtiger Reihenfolge, mit richtigen Operanden **+ ids**".

---

## 1. Strategie (3 Stufen, schwach→stark) + Harness-Stand

| Stufe | Was | Harness-Stand |
|---|---|---|
| **1 Round-trip-Selbstkonsistenz** | DSL→`encodeToByteArray`→L1-decode→`reEncode` → byte-stabil | **AKTIV/grün** (`e1_document_roundTrips_byteStable`, `e1_document_isDeterministic`) |
| **2 Decode-and-inspect** | erzeugtes Doc mit L1-Reader lesen, Op-Baum gegen Intent assert (Typ/Operand/Reihenfolge) | per DSL-Methode wenn E2–E4 landen |
| **3 Byte-Gleichheit vs Orakel** | `document{…}` == gebündeltes `procedure_*`-Fixture, byteweise | 4 Tests `@Ignore`d (Targets unten), un-ignore wenn Ops+Prolog landen |

---

## 2. 🎯 Orakel-Decode der 4 Fixtures — id-Order + Op-Sequenz = Replikations-Target

> **📌 Kanonische Quelle = `docs/TECHSPEC-E5-id-order-reference.md` (assist, gemergt).** Volle byte-akkurate op+id-Sequenzen aller 4 dort.
> **Cross-Validation:** mein unabhängiger L1-Decode (unten) **deckt sich exakt** mit assists Referenz (id=42-content-desc-first · single-pool-monoton-ab-43 · `ID_MAP=(2<<20)|42=2097194` region · `ROOT_CONTENT_BEHAVIOR(0,34,2,6)` konstant). Zwei unabhängige Decodes = solides Orakel. Mein Harness assertet gegen die **echten Fixture-Bytes** (= was die Referenz dokumentiert), nicht gegen eine Transkription.

Alle 4 sind **300×300, contentDescription `"Clock"`, profiles=0 (Baseline)**. **Shared-Pool id ab START_ID=42** (IdAllocator).
**🔑 id=42 ist IMMER die contentDescription** (`DATA_TEXT id=42 "Clock"` direkt nach HEADER + `ROOT_CONTENT_DESCRIPTION id=42`),
**vor jedem Content-Op** — die contentDescription verbraucht die erste id. Danach monoton 43,44,… Inline-Bundles
(`PAINT_VALUES`) verbrauchen **keine** id; `ANIMATED_FLOAT`/`DATA_*`/`TEXT_MEASURE`/`DATA_MAP_LOOKUP` **schon**.

### procedure_gradient1 (267 B, 14 Ops) — kleinstes mit Body-id-Allokation
`HEADER(v1.0.0 flat) · DATA_TEXT(id42 "Clock") · ROOT_CONTENT_DESCRIPTION(id42) · ROOT_CONTENT_BEHAVIOR(scroll0,align34,size2,mode6) ·
PAINT_VALUES(12) · ANIMATED_FLOAT(id43) · ANIMATED_FLOAT(id44) · ANIMATED_FLOAT(id45) · MATRIX_SAVE · MATRIX_SCALE · DRAW_OVAL · MATRIX_RESTORE ·
DATA_TEXT(id46 "gradient") · DRAW_TEXT_ANCHOR(textId46)`. **id-Order: 42(desc),43,44,45(floats),46(text).**

### procedure_center_text1 (449 B, 24 Ops)
Prolog identisch (id42 desc). Body u.a. `TEXT_FROM_FLOAT`, `TEXT_MEASURE×2`, `DRAW_RECT`, 8×`ANIMATED_FLOAT`, `PAINT_VALUES×3`. **Nur 1 id-bearing DATA_TEXT (id42)** — der Rest sind ANIMATED_FLOAT/TEXT_MEASURE-ids.

### procedure_look_up1 (514 B, 28 Ops) — 🔴 zeigt die NaN-Range-Collection-id
`… DATA_TEXT(id42 "Clock") · ROOT_CONTENT_DESCRIPTION(42) · ROOT_CONTENT_BEHAVIOR · PAINT_VALUES(4) ·
DATA_TEXT(id43 "John") · DATA_TEXT(id44 "David") · DATA_INT(id45 =32) · **ID_MAP(id=2097194, entries=3)** ·
ANIMATED_FLOAT(46,47,48) · MATRIX_… · DATA_TEXT(id49 "First") · DATA_MAP_LOOKUP(id50, dataMapId=2097194, key=49) ·
TEXT_MEASURE(id51,textId50) · TEXT_MEASURE(id52) · ANIMATED_FLOAT(53,54,55,56) · PAINT_VALUES(5) · DRAW_RECT · PAINT_VALUES(3) · DRAW_TEXT_ANCHOR(textId50)`.
**🔑 Watchpoint:** `ID_MAP` liegt **NICHT im 42-Pool** — `2097194 = 0x200000 + 42` = NaN-encoded **Collection-Range** (IdAllocator-Docstring: „per-type buckets behind NaN-encoded ranges, introduced in E2+"). `DATA_MAP_LOOKUP.dataMapId` referenziert diese High-id, `key` referenziert die normale-Pool-id (49). **dev-3 (E4): Map/Collection-Helfer brauchen eine separate Range-Allokation ab 0x200000+START_ID — sonst byte-divergent.**

### procedure_text_path_effects (8986 B, 27 Ops) — groß via Payload, nicht Op-Zahl
Prolog identisch (id42). Schwer: **`DATA_PATH(id49, count=2141)`** (großer Path-Payload = Großteil der 8986 B) + `COLOR_EXPRESSIONS` + 2×`DRAW_PATH` + `DATA_TEXT(id51 "0123456789")`. **id-Order: 42(desc), …, 49(path), …, 51(text).**

---

## 3. ✅ E1-Prolog — GELÖST durch REM-85 (develop `fad13e2`), mein Checkpoint grün

**Update (REM-85 gemergt):** `document(300,300,contentDescription="Clock"){}` ist jetzt **byte-treu** — emittiert exakt den **48-B flat-API-Prolog** (HEADER v1.0.0 29B + `DATA_TEXT(id42 "Clock")` 14B + `ROOT_CONTENT_DESCRIPTION(42)` 5B), byte-identisch zu den ersten 48 B jedes `procedure_*`-Orakels. Mein Stage-3-Checkpoint `e1Prolog_byteMatchesOracleHeaderBlock` ist **un-ignored + grün** (selbst verifiziert, nicht nur PO-Claim). `ROOT_CONTENT_BEHAVIOR` ist korrekt NICHT im Prolog — kommt in E2 via `setRootContentBehavior`. → **Erster echter §2-WRITE-Byte-Beweis steht.** Der ursprüngliche Befund (unten, historisch) war der GATING-Posten, den REM-85 vollendet hat.

---

### (Historisch) 🔴 VERIFIZIERTER E1-Prolog-Befund (pre-REM-85, gegen develop `a77c6ad`) — E1 war NOCH NICHT byte-treu

Ich habe `document(300,300,contentDescription="Clock"){}` ausgeführt + decoded vs `procedure_gradient1`. **`HEADER-BYTE-MATCH = false`.** Drei Divergenzen:

| Aspekt | E1 (jetzt) | Orakel (Ziel) |
|---|---|---|
| **Version** | `v1.1.0` | `v1.0.0` |
| **Header-Form** | **map-API**: Property-Header `props=[5=300,6=300,9="Clock"]`, **46 B**, 1 Op total | **flat-API**: fixed-Header **29 B**, keine Property-Map |
| **contentDescription** | als **Header-Property 9** (DOC_CONTENT_DESCRIPTION) → KEIN Body-Op, KEINE id verbraucht | als **Body-Op**: `DATA_TEXT(id42 "Clock")` + `ROOT_CONTENT_DESCRIPTION(id42)` → **verbraucht id=42** |

**Ursache = genau die „Header-Form-Auto-Auswahl", die der PO/assist benannt hat:** *flat-API-6 ohne extra-properties / map-API-7 mit.* Die `procedure_*`-Orakel sind **flat-API-6** (keine extra-props → contentDescription als Body-Op, fixed-Header v1.0.0). **E1 wählt aktuell IMMER map-API-7** (`RemoteComposeWriter.init` schreibt contentDescription in `properties[DOC_CONTENT_DESCRIPTION]` + `Header.fromProperties`). → Für procedure_*-Byte-Match muss der Writer **flat-API-6 wählen, wenn keine extra-properties vorliegen**, und contentDescription als Body-`DATA_TEXT`+`ROOT_CONTENT_DESCRIPTION` emittieren (das ist auch, was id=42 vergibt).

**Severity/Framing:** **kein „Bug", sondern E1-noch-nicht-implementiert** (E1 ist Scaffold; Auto-Auswahl ist das benannte Design, nur noch nicht im Merge). **Von assist unabhängig bestätigt** (`TECHSPEC-E5-id-order-reference.md` §Schluss: „alle 4 nutzen den flat-API-6-Header → braucht die flat/map-Auto-Form-Auswahl (REM-73 Header-Story)"). **Aber es ist der GATING-Prolog:** solange er map-API ist, kann KEIN procedure_*-Fixture byte-matchen (Prolog steht vor allem) — und die id-Order verschiebt sich (ohne id=42-desc-Body-Op fangen Content-ids bei 42 statt 43 an). **→ dev-3 sollte die flat/map-Auto-Auswahl als ersten E1-Byte-Checkpoint landen, VOR E2-Draw-Aufbau** (Techspec §6.1: „sofort gegen procedure_gradient1 byte-prüfen"). Harness-Test `e1Prolog_byteMatchesOracleHeaderBlock` ist als KNOWN-RED `@Ignore`d + un-ignorebar, sobald das landet.

> **Interim-Assertion (assist-Vorschlag, vor der flat/map-Auswahl):** **Post-Header-Tail-Vergleich** — die Body-Op-Bytes NACH dem Header gegen die Orakel-Sequenz prüfen, auch wenn der Header noch divergiert. Gibt ein früheres Grün-Signal auf die Body-Replikation (sobald E2–E4-Ops da sind), entkoppelt vom Header-Fix. Faltet sich in die Stage-3-Tests als Zwischenschritt ein.

---

## 3a. 🔑 Watchpoint — NaN-geboxte System-Variablen-Refs in Draw-Floats (E2, verifiziert an procedure_simple2)

procedure_simple2s `DRAW_OVAL(0,0,r,b)` hat **r/b NICHT als Literal-Floats**, sondern **NaN-geboxte System-Var-ids**: roh decodiert `r=0xff800005`, `b=0xff800006` = Region-0-System-Variablen **id 5 (`ID_WINDOW_WIDTH`)** + **id 6 (`ID_WINDOW_HEIGHT`)**, NaN-encoded in die Float-Slots. **DSL-Idiom (E2):** `drawOval(0f, 0f, WireTypes.asNan(RemoteContext.ID_WINDOW_WIDTH), WireTypes.asNan(RemoteContext.ID_WINDOW_HEIGHT))`. **Byte-Risiko:** der NaN muss **bit-exakt** (`0xff80000N`, raw-bits, NICHT kanonisch `0x7fc00000`) durch den Writer → mein Harness-Test `simple2_bytesMatchOracle` ist grün, also reicht der E2-Pfad die rohen Bits durch. **Relevant für die E3/E4-Fixtures:** gleiche NaN-Box für jede koord/wert-Bindung an eine Variable (nicht nur window-dims) — beim Un-ignore der 4 darauf achten, dass koord-Bindungen als asNan(id) und nicht als Literal repliziert werden.

## 4. Gate-Stand + nächste Schritte

### ✅ STAND nach E3/E4 (REM-90/REM-92) — 2026-06-28, post-compact Voll-Byte-Closure
**E5-Gate: 7 grün / 1 dokumentiert-skipped / 0 rot** (jvmTest; iOS-Parität folgt aus plattform-unabhängigem commonMain-Writer + byte-bewiesenem L1-Codec).
- **grün:** round-trip · determinism · prolog-48B · **simple2 voll-byte** · **gradient1 voll-byte (267B)** · **center_text1 voll-byte (449B)** · **look_up1 voll-byte (514B)**.
- **3 Watchpoint-Fixtures voll-byte geschlossen** (gegen echte Orakel-Bytes self-verifiziert, Decode-Probe → DSL-Replikation → `assertContentEquals`):
  - **gradient1:** PAINT linearGradient((0,0)→(0,WIN_H),[0xff00ff00,0xff0022ff],tile=REPEAT)+textSize(64); id43=WIN_W*0.5, id44=WIN_H*0.5, id45=(CONTINUOUS_SEC%2)−1; MATRIX_SCALE(45,1,43,44)+OVAL+ANCHOR(46,43,44).
  - **center_text1:** + id46=99−(TIME_IN_SEC%100), TEXT_FROM_FLOAT(46,before=3,after=0,flags=3), TEXT_MEASURE w/h, zentrierte Box (RECT stroke / TEXT fill).
  - **look_up1:** ID_MAP@region-2 `2097194`, DATA_MAP_LOOKUP(key=text "First"), DATA_INT, gleiche Box-Mechanik.

### 🔴 2 verifizierte Byte-Befunde (an PO für dev-3-Follow-up)
1. **ID_MAP-Entry-`type`-Konstante falsch für Byte-Match:** look_up1s String-Entries (First/Last) tragen Wire-`type=0`, NICHT `DATA_MAP_TYPE_STRING(=2)`; das Int-Entry (DOB) ist `type=1` (= `DATA_MAP_TYPE_INT`, passt). → `dataMapEntry`-Default (STRING=2) + die Konstante `DATA_MAP_TYPE_STRING=2` würden byte-divergieren (sollte für Strings 0 sein). Im Test mit Literal-Types (0/0/1) repliziert. **dev-3: Konstanten/Defaults gegen echte Wire-Ordinals prüfen.**
2. **`procedure_text_path_effects` NICHT voll-byte-replizierbar (kein Defekt, fehlende Write-Surface):** Orakel backt die Geometrie als **ein einziges DATA_PATH-Op (id=49, count=2141 Floats = 8573 B)**; die DSL hat **keinen DATA_PATH-Roh-Emitter** — `PathBuilder` baut Pfade inkrementell als `PATH_CREATE`+N×`PATH_ADD` (andere Op-Form) → Op-Shape-Mismatch, selbst mit den 2141 Floats. Zusätzlich COLOR_EXPRESSIONS + Path-Effect-PAINT-Slots unverifiziert. **Bleibt @Ignore bis DATA_PATH-Roh-Float-Helfer landet.**

### Nächste Schritte
- **test-2:** Stufe-2-decode-inspect optional je DSL-Methode; text_path_effects un-ignoren sobald DATA_PATH-Helfer da.
- **Byte-Watchpoints (bestätigt):** (1) contentDescription=Body-Op-id42-zuerst; (2) flat-API-6 wenn keine extra-props; (3) Collection-ids in NaN-Range 0x200000+42 (ID_MAP/DATA_MAP_LOOKUP), getrennt vom 42-Pool; (4) `PAINT_VALUES` verbraucht keine id; (5) RCB-`mode` variiert je Fixture (simple2=SCALE_FIT(4), die 3 reicheren=SCALE_FILL_BOUNDS(6)) — nicht annehmen, decoden; (6) ID_MAP-Entry-type-Ordinals (Befund 1).
