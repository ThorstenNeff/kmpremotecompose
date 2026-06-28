# E3 Data-Map / Region-id-Allokation — Upstream-verifiziertes Schema (für dev-3)

> **Autor:** PO-Assistent · **Status:** Referenz (E3-Vorbereitung), gegen `./androidx` verifiziert · **Datum:** 2026-06-28
> **Auslöser:** der `ID_MAP`-Region-Encoding-Watchpoint aus der E5-id-Order-Referenz (`(2<<20)|42` = 2097194, 1 Sample). Hier das EXAKTE Upstream-Schema, **am Source verifiziert** (`RemoteComposeState.java`), damit dev-3 es bei E3/`addDataMapIds`/Array-Ops nicht selbst aus 1 Sample ableiten muss. De-riskt den höchsten-Risiko-Teil von E3 vorab.

---

## 1. Das Schema (verbatim aus `RemoteComposeState.java`)

```java
public static final int START_ID = 42;            // NanMap.START_VARIABLE_ID = 42
private int mNextId = START_ID;                    // region-0 (plain) Zähler
private final int[] mIdMaps = new int[] {          // EIN Zähler PRO Region, ALLE ab Index 42:
    START_ID,            // [0] plain  = 42
    NanMap.START_VAR,    // [1] VARS   = (1<<20)+42 = 1048618
    NanMap.START_ARRAY,  // [2] ARRAY  = (2<<20)+42 = 2097194
};

public int createNextAvailableId()        { return mNextId++; }          // region 0
public int createNextAvailableId(int type){ return (type==0) ? mNextId++ : mIdMaps[type]++; }
```
`NanMap`: `START_VARIABLE_ID=42`, `START_VAR=(1<<20)+42`, `START_ARRAY=(2<<20)+42`, `ID_REGION_MASK=0x700000`, `ID_REGION_ARRAY=0x200000`.

**Drei Schlüssel-Fakten:**
1. **Drei UNABHÄNGIGE Region-Zähler, alle mit Index 42 startend:** region 0 (plain: float/int/String/color) = 42; region 1 (VARIABLES) = `(1<<20)+42`; region 2 (ARRAY/Collections, inkl. Data-Maps) = `(2<<20)+42 = 2097194`.
2. **Der Region-2-Zähler ist UNABHÄNGIG vom plain-Pool** — `mIdMaps[2]++` rührt `mNextId` NICHT an. Deshalb in look_up1: plain-Pool vergibt 42,43,44,45 (text/text/text/int) + 46,47,48 (float) während die erste Data-Map aus dem SEPARATEN region-2-Zähler ihre id `2097194` zieht; der plain-Zähler läuft daneben 45→46 weiter. (Das war der ambige Punkt aus dem 1-Sample — **jetzt aufgelöst: separater Zähler, NICHT `START_ARRAY | plainValue`**.)
3. **Erste Data-Map/Array-id = `START_ARRAY = (2<<20)+42 = 2097194`**, zweite = 2097195, usw. = exakt der decodierte look_up1-`ID_MAP id=2097194`. ✓ Source-verifiziert, nicht inferiert.

---

## 2. Wie die Creation-Seite es nutzt (verbatim Upstream `RemoteComposeWriter.java`)

Data-Maps / Id-Listen / Arrays allozieren über **`NanMap.TYPE_ARRAY` (= region 2)**:
```java
int id = mState.cacheData(ids, NanMap.TYPE_ARRAY);   // bzw. createID(NanMap.TYPE_ARRAY)
```
→ intern `createNextAvailableId(TYPE_ARRAY)` → `mIdMaps[2]++`. `DATA_MAP_LOOKUP` speichert/referenziert die VOLLE region-getaggte id (`dataMapId=2097194`), nicht den nackten Index.

**Op-Familien je Region (für dev-3s Allokator-Routing):**
- **region 0 (plain):** DATA_TEXT, DATA_INT, DATA_FLOAT, COLOR_CONSTANT, ANIMATED_FLOAT/FLOAT_EXPRESSION, TEXT_FROM_FLOAT, TEXT_MEASURE, COLOR_EXPRESSIONS, DATA_PATH, NAMED_VARIABLE, DATA_MAP_LOOKUP-Ergebnis — alle `createNextAvailableId()` / `(0)`. (In den Korpus-Docs ziehen Variablen region 0, NICHT region 1.)
- **region 2 (ARRAY):** ID_MAP / Data-Map, Float-/Id-Listen, Arrays — `createNextAvailableId(TYPE_ARRAY)`.
- **region 1 (VAR):** separate Variablen-Klasse; in den 4 E5-Fixtures NICHT genutzt → für E3-Data-Maps irrelevant, der Vollständigkeit halber dokumentiert.

---

## 3. Was dev-3 in E3 implementiert (Erweiterung des E1-`IdAllocator`)

E1s `IdAllocator` ist heute single-pool (`nextId()=next++` ab 42). E3 erweitert auf **region-bewusst**, gespiegelt an `mIdMaps`:
```
class IdAllocator {
    private var plain = START_ID            // 42
    private var array = START_ARRAY         // (2<<20)+42 = 2097194
    // (var-Region (1<<20)+42 nur falls je gebraucht)
    fun nextId(): Int = plain++                       // region 0 (bestehend)
    fun nextArrayId(): Int = array++                  // region 2 (E3, für Data-Maps/Arrays)
}
```
- **Konstanten exakt:** `START_ARRAY = (2 shl 20) + 42`. (Kotlin `2 shl 20` = 2097152, +42 = 2097194.)
- **Unabhängigkeit wahren:** `nextArrayId()` darf den plain-Zähler NICHT inkrementieren (und umgekehrt) — sonst byte-divergent bei jedem Doc mit Data-Maps.
- **`DATA_MAP_LOOKUP`** referenziert die volle region-2-id (z.B. 2097194), nicht 42.

---

## 4. Verifikations-Anker (E5)
- look_up1 `ID_MAP id=2097194` = `START_ARRAY` (erste Array-id) → der Byte-Anker für `nextArrayId()`.
- E5-Voll-byte-equality gegen look_up1 beweist sowohl den plain-Pool (42-er für text/int/float) ALS AUCH den region-2-Zähler (2097194 für die Map) — der Doc deckt beide Pools ab. (Header-Form-Story REM-73 weiterhin Voraussetzung für Voll-Doc; bis dahin Post-Header-Tail.)
- **Damit ist der eine geflaggte E3-Byte-Watchpoint vorab aufgelöst** — dev-3 implementiert gegen das verifizierte Schema statt gegen 1 Sample.
