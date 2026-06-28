# E5 id-Allokations-Order-Referenz (Byte-Orakel für test-2s Conformance-Harness)

> **Autor:** PO-Assistent · **Status:** Referenz (das Byte-Orakel) · **Datum:** 2026-06-28
> **Quelle:** die 4 Korpus-Fixtures **mit dem gemergten L1-Reader decodiert** (DocumentReader.inflate → op.dump()), NICHT geschätzt. Ground-truth-Op+id-Sequenz unten. Gegen das gemergte E1 (`IdAllocator` START_ID=42, single-shared-pool) + Upstream-Referenz.
> **Zweck:** test-2s Byte-Gleichheits-Harness (REM-84) assertet, dass die Creation-DSL für dieselbe Eingabe dieselben Ops mit denselben ids in derselben Reihenfolge emittiert. Das ist der eine Byte-Watchpoint von Epic E.

---

## 0. Die Allokations-Regeln (aus den 4 decodierten Sequenzen abgeleitet)

1. **`id=42` ist IMMER die Content-Description `DATA_TEXT "Clock"`** — als ALLERERSTE id-Op emittiert, VOR dem Body, gefolgt von `ROOT_CONTENT_DESCRIPTION id=42`. (= upstream `RemoteComposeWriter.header()`: `addText(contentDescription)` zieht id 42 zuerst, dann `addRootContentDescription`.) → Der DSL-Lifecycle MUSS die Content-Description als `DATA_TEXT(42)`+`ROOT_CONTENT_DESCRIPTION(42)` vor jedem Body-Op emittieren.
2. **`ROOT_CONTENT_BEHAVIOR scroll=0 alignment=34 sizing=2 mode=6`** ist in allen 4 konstant (Op #3, nach der Content-Description, kein id) → der `setRootContentBehavior`-Default muss exakt diese Werte erzeugen.
3. **Plain single-shared-pool, monoton ab 42:** jede id-tragende Op zieht die nächste id. id-tragende Ops (beobachtet): `DATA_TEXT`, `DATA_INT`, `ANIMATED_FLOAT` (FLOAT_EXPRESSION), `TEXT_FROM_FLOAT`, `TEXT_MEASURE`, `COLOR_EXPRESSIONS`, `DATA_PATH`, `DATA_MAP_LOOKUP` (das Ergebnis). **NICHT id-tragend** (ziehen keine id): `PAINT_VALUES`, `MATRIX_SAVE/SCALE/TRANSLATE/RESTORE`, `DRAW_*`, `ROOT_CONTENT_*`, `HEADER`.
4. **🔴 SPEZIALFALL `ID_MAP` (region-kodierte id, NICHT plain-monoton):** in look_up1 bekommt `ID_MAP` die id **2097194 = `(2<<20)|42`** (Region 2 = Daten/Array, Index 42) — eine SEPARATE region-getaggte Nummerierung, NICHT der plain-Zähler (der stand zu dem Zeitpunkt bei 46 und läuft danach mit 46 weiter). `DATA_MAP_LOOKUP` referenziert `dataMapId=2097194`. **dev-3 muss das exakte Upstream-Data-Map-id-Encoding reproduzieren** (region-tag (2<<20) + eigener Index ab 42), nicht als plain id behandeln. (Nur 1 Sample — gegen Upstream `RemoteComposeState`-Map-id-Allokation verifizieren, bevor verallgemeinert wird.)

---

## 1. Decodierte Ground-Truth-Sequenzen (das Orakel)

### procedure_gradient1 (267 B, 14 ops) — color/gradient
```
#0  HEADER v1.0.0 w=300 h=300 profiles=0          (kein id; flat-form-Header — s. REM-73 Header-Story)
#1  DATA_TEXT id=42 text="Clock"                  ← id 42 (content-desc)
#2  ROOT_CONTENT_DESCRIPTION id=42
#3  ROOT_CONTENT_BEHAVIOR scroll=0 align=34 sizing=2 mode=6
#4  PAINT_VALUES count=12                          (kein id; das Gradient-Bundle steckt hier in den PaintData-Slots)
#5  ANIMATED_FLOAT id=43 value[3]
#6  ANIMATED_FLOAT id=44 value[3]
#7  ANIMATED_FLOAT id=45 value[5]
#8  MATRIX_SAVE
#9  MATRIX_SCALE s=[NaN,1.0] c=[NaN,NaN]
#10 DRAW_OVAL l=0 t=0 r=NaN b=NaN
#11 MATRIX_RESTORE
#12 DATA_TEXT id=46 text="gradient"
#13 DRAW_TEXT_ANCHOR textId=46 ...
```
id-Order: **42(text"Clock") · 43 · 44 · 45 (3×ANIMATED_FLOAT) · 46(text"gradient")**.

### procedure_center_text1 (449 B, 24 ops) — text/measure
```
#1  DATA_TEXT id=42 "Clock" · #2 ROOT_CONTENT_DESCRIPTION 42 · #3 ROOT_CONTENT_BEHAVIOR …
#5  ANIMATED_FLOAT id=43 · #6 id=44 · #7 id=45
#12 ANIMATED_FLOAT id=46
#13 TEXT_FROM_FLOAT id=47 value=NaN digits=3 flags=3
#14 TEXT_MEASURE id=48 textId=47 type=0
#15 TEXT_MEASURE id=49 textId=47 type=1
#16 ANIMATED_FLOAT id=50 · #17 id=51 · #18 id=52 · #19 id=53
(#20 PAINT_VALUES, #21 DRAW_RECT, #22 PAINT_VALUES, #23 DRAW_TEXT_ANCHOR textId=47)
```
id-Order: **42 · 43 · 44 · 45 · 46 · 47(TEXT_FROM_FLOAT) · 48,49(TEXT_MEASURE) · 50,51,52,53**.

### procedure_look_up1 (514 B, 28 ops) — id-map (SPEZIALFALL)
```
#1  DATA_TEXT id=42 "Clock" · #2 ROOT_CONTENT_DESCRIPTION 42 · #3 ROOT_CONTENT_BEHAVIOR …
#5  DATA_TEXT id=43 "John"
#6  DATA_TEXT id=44 "David"
#7  DATA_INT  id=45 value=32
#8  ID_MAP    id=2097194  entries=3            ← 🔴 region-kodiert (2<<20)|42, NICHT plain
#9  ANIMATED_FLOAT id=46 · #10 id=47 · #11 id=48
#16 DATA_TEXT id=49 "First"
#17 DATA_MAP_LOOKUP id=50 dataMapId=2097194 key=49
#18 TEXT_MEASURE id=51 textId=50 type=0 · #19 TEXT_MEASURE id=52 textId=50 type=1
#20 ANIMATED_FLOAT id=53 · #21 id=54 · #22 id=55 · #23 id=56
(#24 PAINT_VALUES, #25 DRAW_RECT, #26 PAINT_VALUES, #27 DRAW_TEXT_ANCHOR textId=50)
```
id-Order plain: **42 · 43 · 44 · 45 · [ID_MAP=2097194 region] · 46 · 47 · 48 · 49 · 50(LOOKUP-result) · 51,52 · 53,54,55,56**. Der plain-Zähler überspringt die region-id NICHT (läuft 45→46 weiter).

### procedure_text_path_effects (8986 B, 27 ops) — path/color-expr
```
#1  DATA_TEXT id=42 "Clock" · #2 ROOT_CONTENT_DESCRIPTION 42 · #3 ROOT_CONTENT_BEHAVIOR …
#4  ANIMATED_FLOAT id=43 · #5 id=44 · #6 id=45
#7  COLOR_EXPRESSIONS id=46 params=[…]
#8  ANIMATED_FLOAT id=47
#10 ANIMATED_FLOAT id=48
#17 DATA_PATH id=49 count=2141
#18 DRAW_PATH id=49
#19 ANIMATED_FLOAT id=50
(#21 DRAW_PATH id=49 — re-draw, KEINE neue id)
#25 DATA_TEXT id=51 "0123456789"
#26 DRAW_TEXT_ANCHOR textId=51
```
id-Order: **42 · 43 · 44 · 45 · 46(COLOR_EXPRESSIONS) · 47 · 48 · 49(DATA_PATH) · 50 · 51(text)**. Beachte: `DRAW_PATH id=49` (#18, #21) RE-referenziert die Path-id, zieht KEINE neue id.

---

## 2. Hinweise für test-2 (REM-84-Harness) + dev-3
- **Header-Form:** alle 4 nutzen den **flat-API-6-Header** → bevor Voll-Doc-byte-equality möglich ist, braucht der Writer die flat/map-Auto-Form-Auswahl (REM-73 Header-Story). Bis dahin: **Post-Header-Tail-Vergleich** (wie E1 für procedure_simple1) gegen die obigen Sequenzen.
- **Das Orakel ist die Op+id-Reihenfolge oben**, byte-verankert über den L1-Reader. Eine DSL, die diese Sequenz reproduziert (inkl. id=42="Clock"-zuerst, ROOT_CONTENT_BEHAVIOR-Default, plain-monoton-ab-43-für-den-Body, ID_MAP-region-id), ist byte-treu.
- **Coverage über alle id-Typen:** gradient1=color/float, center_text1=text/measure/float, look_up1=text/int/**id-map**, text_path_effects=**path**/color-expr/text → zusammen decken sie DATA_TEXT/DATA_INT/ANIMATED_FLOAT/TEXT_FROM_FLOAT/TEXT_MEASURE/COLOR_EXPRESSIONS/DATA_PATH/ID_MAP/DATA_MAP_LOOKUP ab.
- **🔴 Vor E5-Voll-Conformance** muss dev-3 das `ID_MAP`-region-id-Encoding gegen Upstream verifizieren (1 Sample) — sonst byte-divergent bei look_up1.
