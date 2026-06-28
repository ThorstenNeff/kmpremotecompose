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

## 2. 🎯 Orakel-Decode der 4 Fixtures (headless, evidenzbasiert) — id-Order + Op-Sequenz = Replikations-Target

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

## 3. 🔴 VERIFIZIERTER E1-Prolog-Befund (pre-E2/E4, gegen develop `a77c6ad`) — E1 ist NOCH NICHT byte-treu

Ich habe `document(300,300,contentDescription="Clock"){}` ausgeführt + decoded vs `procedure_gradient1`. **`HEADER-BYTE-MATCH = false`.** Drei Divergenzen:

| Aspekt | E1 (jetzt) | Orakel (Ziel) |
|---|---|---|
| **Version** | `v1.1.0` | `v1.0.0` |
| **Header-Form** | **map-API**: Property-Header `props=[5=300,6=300,9="Clock"]`, **46 B**, 1 Op total | **flat-API**: fixed-Header **29 B**, keine Property-Map |
| **contentDescription** | als **Header-Property 9** (DOC_CONTENT_DESCRIPTION) → KEIN Body-Op, KEINE id verbraucht | als **Body-Op**: `DATA_TEXT(id42 "Clock")` + `ROOT_CONTENT_DESCRIPTION(id42)` → **verbraucht id=42** |

**Ursache = genau die „Header-Form-Auto-Auswahl", die der PO/assist benannt hat:** *flat-API-6 ohne extra-properties / map-API-7 mit.* Die `procedure_*`-Orakel sind **flat-API-6** (keine extra-props → contentDescription als Body-Op, fixed-Header v1.0.0). **E1 wählt aktuell IMMER map-API-7** (`RemoteComposeWriter.init` schreibt contentDescription in `properties[DOC_CONTENT_DESCRIPTION]` + `Header.fromProperties`). → Für procedure_*-Byte-Match muss der Writer **flat-API-6 wählen, wenn keine extra-properties vorliegen**, und contentDescription als Body-`DATA_TEXT`+`ROOT_CONTENT_DESCRIPTION` emittieren (das ist auch, was id=42 vergibt).

**Severity/Framing:** **kein „Bug", sondern E1-noch-nicht-implementiert** (E1 ist Scaffold; Auto-Auswahl ist das benannte Design, nur noch nicht im Merge). **Aber es ist der GATING-Prolog:** solange er map-API ist, kann KEIN procedure_*-Fixture byte-matchen (Prolog steht vor allem) — und die id-Order verschiebt sich (ohne id=42-desc-Body-Op fangen Content-ids bei 42 statt 43 an). **→ dev-3 sollte die flat/map-Auto-Auswahl als ersten E1-Byte-Checkpoint landen, VOR E2-Draw-Aufbau** (Techspec §6.1: „sofort gegen procedure_gradient1 byte-prüfen"). Harness-Test `e1Prolog_byteMatchesOracleHeaderBlock` ist als KNOWN-RED `@Ignore`d + un-ignorebar, sobald das landet.

---

## 4. Gate-Stand + nächste Schritte
- **AKTIV jetzt:** Stufe-1-Round-trip (grün) — beweist Harness-Mechanik + E1-Encode-Selbstkonsistenz.
- **dev-3 (via PO):** (a) flat/map-API-Auto-Auswahl → E1-Prolog byte-treu (§3); (b) E2–E4-Ops → Body-Replikation der 4 Targets (§2).
- **test-2 (ich), wenn das landet:** un-ignore die Stufe-3-Tests inkrementell, byte-gegen-Orakel; Stufe-2-decode-inspect je DSL-Methode ergänzen.
- **Byte-Watchpoints für dev-3:** (1) contentDescription=Body-Op-id42-zuerst; (2) flat-API-6 wenn keine extra-props; (3) Collection-ids in NaN-Range 0x200000+42 (ID_MAP/DATA_MAP_LOOKUP), getrennt vom 42-Pool; (4) `PAINT_VALUES` verbraucht keine id.
