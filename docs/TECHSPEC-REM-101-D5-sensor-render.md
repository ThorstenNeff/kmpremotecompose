# TechSpec — REM-101 D5: Live-Interaktivität / Sensor-Render (Epic-F)

> **Status:** **GO von assist (2026-06-28)** — dieses Dokument IST die D5-TechSpec (§7). Design von
> dev-1 (source-grounded), reviewed + approved von PO-Assistent, capability-floor §0-sanktioniert vom PO.
> **Byte-Posture:** runtime-only, **kein `shared/operations`-write/read-Change → 173/173 unberührt (§2)**.
> Verifikation: commonTest (Seam) + test-1 Maestro (Android/iOS, S2/S3) + test-2 wasmJs (S4).

---

## 0. Leitprinzip + die eine Invariante

Sensoren speisen Werte in den **Float-Store** des laufenden Dokuments — **nur im Live-Modus**. Im
Static-Modus bleiben die Sensor-ids auf ihrem `0f`-Default (`getFloat(id) = floatStore[id] ?: 0f`), d.h.
**Static-Render == Baseline, unabhängig von jeder Sensor-Quelle.** Das ist der Linchpin, der Goldens,
den REM-78-Desktop-Sweep und die 173-Doc-Conformance immun gegen Sensorik hält. Diese Eigenschaft ist
als Conformance-Pin getestet (`Rem101SensorSeamTest.staticRender_identicalRegardlessOfSource_conformancePin`).

---

## 1. Source-Grounding (gg `./androidx`)

- **Reservierte Sensor-Float-ids 17–26** (`RemoteContext.java:846-970`), region 0, NaN-encoded:
  `ID_ACCELERATION_X/Y/Z = 17/18/19` (m/s²), `ID_GYRO_ROT_X/Y/Z = 20/21/22` (rad/s),
  `ID_MAGNETIC_X/Y/Z = 23/24/25` (µT), `ID_LIGHT = 26` (lux).
- **Kein dedizierter Sensor-Op.** Ein Doc liest die ids direkt in Float-Expressions; Ops registrieren
  `context.listensTo(id, this)`. Host injiziert pro Frame via `setExternalFloat(id,v) → loadFloat →
  updateFloat → markDirty` (Re-Eval der Listener).
- **Capability upstream:** `copySensorListeners` scannt 17–26 auf `hasListener`, registriert nur die real
  vorhandenen Sensoren (`SensorManager.getDefaultSensor` → null wenn fehlend); fehlend → kein Update →
  Doc rendert mit Default/Last-Value. Graceful, nie hard-fail.
- **Korpus (decodiert):** `sensor_demo_acc`→17,18,19 · `gyro`→20,21,22 · `mag`→23,24,25 ·
  `compass`→23,24 (+`CONTINUOUS_SEC` 1; Heading aus MAG-X/Y) · `light`→26.

## 2. Unsere Seite (Status quo + Seam)

`RemoteContext.floatStore` / `getFloat(id)=0f-default` / `loadFloat(id,v)`. `seedSystemVariables(...)`
seedet time/window/density VOR Phase-A. **Sensor-ids 17–26 waren bisher nicht definiert** → Sensor-Docs
rendern heute statisch (alle Werte 0). Der Sensor-Injektions-Seam sitzt **exakt analog** direkt nach
`seedSystemVariables` in `RemoteComposePlayer.paint`.

---

## 3. Design (capability-gestaffelt)

- **(A)** Reservierte ids 17–26 + `SENSOR_ID_RANGE` in `RemoteContext` (runtime-only).
- **(B)** `SensorSource` (commonMain): `read(id): Float?` (**null = Achse auf diesem Target/Gerät nicht
  verfügbar**), `start(neededIds: Set<Int>)`, `stop()`. + `NoOpSensorSource` (read→null überall).
- **(C)** Per-Target-Provider (expect/actual oder Injektion — s. §6 offene Frage):
  | Target | Provider | Abdeckung |
  |---|---|---|
  | Android | `SensorManager` | voll (17–26) |
  | iOS | CoreMotion (`CMMotionManager`) | 17–25; **Light 26 = kein public API → null → statisch** |
  | Desktop (JVM) | keiner | alle null → **statischer Frame** |
  | wasmJs | `DeviceMotion`/`DeviceOrientation` (+`AmbientLightSensor` wo verfügbar, hinter Permission/HTTPS) | verfügbar→live, sonst null |
- **(D) Seam (`RemoteComposePlayer.paint`):** **nur wenn `isAnimationEnabled()`** die vom Doc genutzten
  ids (`sensorIdsUsed(doc)` = Scan FloatExpression-NaN-ids ∈ 17–26) aus der `SensorSource` lesen;
  non-null → `loadFloat(id, v)`. Sensor-driven Doc (`isSensorDriven`) → continuous live-repaint.
- **(E) Live-Loop (`RemoteComposeApp`, S2):** sensor-driven+live → `source.start(usedIds)`, pro Frame
  lesen+seeden, `!live`/dispose → `stop()`.
- **(F) Static-Mode-Invariante:** Sensoren feeden NIE im Static-Modus → Determinismus (s. §0).
- **(G) §2:** null write/read-Change.
- **(H) Capability-Floor (§0-sanktioniert):** *verfügbar→live, sonst statischer Frame, never-hard-fail,
  per-Achse.* Mobile voll; Desktop/Web verfügbare-Sensoren sonst statisch.

### assist-TechSpec-Zusätze (2026-06-28)
- **(a) Static==Baseline als Conformance-Pin + S1-Test** — erledigt (`staticRender_identicalRegardless…`).
- **(b) Live-Re-Eval-Wiring (S2):** der Live-Loop muss bei `loadFloat(sensor)` eine Re-Evaluation der
  sensor-lesenden Ops auslösen (analog time). Da unser Player jeden Frame Phase-A komplett neu evaluiert
  (kein Dirty-Tracking, MVP), reicht **„pro Frame seeden vor Phase-A"** — eine explizite
  `listensTo`/markDirty-Registrierung ist erst nötig, wenn dirty-tracking eingeführt wird. **In S2
  verifizieren:** ändert sich der Sensorwert zwischen zwei Live-Frames, ändert sich der Render.
- **(c) Sensor-Werte = rohe Physik-Einheiten (m/s², rad/s, µT, lux), KEINE Density-Interaktion.** Der Seam
  ruft `loadFloat` mit dem Rohwert — nie mit Density skaliert.

---

## 4. Slicing

| Slice | Inhalt | Verifikation | Status |
|---|---|---|---|
| **S1** | commonMain ids 17–26 + `SensorSource` + `NoOpSensorSource` + `sensorIdsUsed`/`isSensorDriven` + Player-Seam (live-only, NoOp-Default) | `Rem101SensorSeamTest` (jvm+iOS) + static==baseline-Pin | **✅ gebaut+gepusht (verhalten-identisch, jvm 484/0, iOS 7/0)** |
| **S2** | Android-Provider + `RemoteComposeApp`-Live-Loop-Wiring + Re-Eval-Verify | test-1 Maestro Android (Sensor-Tilt → Render ändert sich) | nach Injektions-Entscheid (§6) |
| **S3** | iOS-Provider (CoreMotion; Light statisch) | test-1 Maestro iOS | — |
| **S4** | wasmJs-Provider (DeviceMotion/Orientation) | test-2 wasmJs | — |
| **S5** | Desktop = statisch (Capability-Floor; optional Sim-Driver) | — | — |

---

## 5. 🔴 Offene Frage für S2 (BLOCKER vor S2-Impl): Provider-Injektions-Mechanik

Der Android-`SensorManager` braucht einen `Context` → ein Provider kann **nicht** context-los via
`expect fun createSensorSource()` entstehen. Drei Optionen, die die TechSpec/assist festklopfen muss,
**bevor** dev-1 S2 zieht:

1. **`expect fun createSensorSource(platform): SensorSource`** mit einem Plattform-Handle-Param (Android:
   `Context`/`Application`; andere: ignoriert). Sauber typisiert, aber der Param ist target-asymmetrisch.
2. **App-Shell-Injektion:** jede App-Shell (androidApp/iosApp/webApp/desktopApp) baut ihren Provider und
   reicht ihn an `RemoteComposeApp(sensorSource = …)`. Symmetrisch zum `loadRc`-Muster (REM-82), hält
   commonMain platform-frei, aber 4 Call-Sites.
3. **In-Composable via `LocalContext`/Platform-Locals:** `RemoteComposeApp` baut den Provider intern aus
   Compose-Locals (Android: `LocalContext.current`). Eine Call-Site, aber koppelt commonMain an
   Compose-Local-Verfügbarkeit pro Target.

**dev-1-Empfehlung:** Option 2 (App-Shell-Injektion) — konsistent mit dem etablierten `loadRc`-Default-
Param-Muster, hält `commonMain` sauber (kein `java.*`/Context-Leak), und der Provider-Lifecycle
(`start/stop`) hängt ohnehin am App-Shell-/Composable-Lifecycle. **Entscheid abwarten.**

---

## 6. Harte Regeln

- **§2:** runtime-only, kein write/read-Change → 173/173. Jede Abweichung = Blocker.
- **Kein `java.*` in commonMain** — Provider liegen in den Plattform-SourceSets/App-Shells.
- **Capability-Floor:** never-hard-fail; fehlende Achse → `read`=null → 0f-Default (statisch).
- **Rohe Einheiten, keine Density-Skalierung** der Sensorwerte.
- **Static bleibt Baseline** — der Conformance-Pin darf nie brechen.
