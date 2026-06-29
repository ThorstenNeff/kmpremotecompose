# TECHSPEC — REM-143: Particles/Impulse-Subsystem (FULL S1+S2+S3)

> **Status:** DRAFT (dev-2-authored aus dem read-only Scoping-Census; Mensch-GO für FULL erteilt).
> Epic **REM-143**. Decode-grounded (7 Korpus-Docs) + Upstream-Verhaltens-Ref (`ParticlesCreate`/
> `ParticlesLoop`/`ImpulseOperation`/`ImpulseProcess`, PROJECT_CONTEXT §5 — kein Paste).
> **§2-Byte-Invariante bleibt oberste Regel: render-only, kein Binärformat-Touch.**

---

## 1. Scope & Entscheid
**Voll-Subsystem** (Mensch-GO): ein **stateful Per-Frame-Partikel-Simulator** auf der Draw-Seite, LIVE-
animiert + interaktiv (capability-gestaffelt). 7 Korpus-Docs, aktuell blank (alle 5 Ops `Operation`-only):
`impulse_demo_confetti_demo`, `impulse_demo_hearts_demo`, `maze`/`maze1`/`maze2`, `particle` (2 Systeme),
`haptic_demo_demo_haptic1` (Impulse-only, keine Partikel).

**Kern-Entscheid:** KEIN Player-Frame-Architektur-Umbau. Der Partikel-State lebt als **Op-Feld** (wie
upstream `mParticles`), nicht im per-Frame-resetteten `RemoteContext`. Per-Partikel-Ausführung renutzt das
**runLoop-Muster (REM-58)**; Equations renutzen **RpnFloatEvaluator (REM-127, inkl. OP_RAND/RAND_SEED
REM-109)**. Berührt **NICHT** LayoutMeasure — reine Paint-Walk + Eval + Op-Feld-State + Animations-Clock.

## 2. Mechanismus (decode + upstream)
- **ParticlesCreate** (`PARTICLE_DEFINE`; upstream `PaintOperation,VariableSupport,VariableProvider`):
  Felder `id, particleCount, varIds[K], equations[K]`. Hält `mParticles: float[count][K]` = der **State**.
  **Seed (einmalig):** pro Partikel `i`, pro Var `j` → `mParticles[i][j] = eval(equations[j], context, particleIndex=i)`.
  Registriert sich unter `id` im Store (`putObject(id, this)`) als VariableProvider für die Loop.
- **ParticlesLoop** (`PARTICLE_LOOP`, Container; `PaintOperation,VariableSupport`): Felder `id, restart[], equations[K]`.
  Hält Ref auf die ParticlesCreate-Source (`context.getObject(id)`). **Pro Frame, pro Partikel `i`:**
  1. Partikel-Vars `varIds[j] = mParticles[i][j]` in den Store laden.
  2. Restart-Eq evaluieren — `>0` → Partikel `i` re-seeden (via Create-Init).
  3. Update-Eqs evaluieren → `mParticles[i][j]` neu schreiben (Evolution).
  4. **Body zeichnen** (die Ops zwischen PARTICLE_LOOP und CONTAINER_END) mit den geladenen Partikel-Vars —
     wie `runLoop`, aber N× pro Partikel (Body enthält z.B. DrawBitmap-Sprite REM-132, DrawOval, Matrix-Ops).
- **ImpulseStart** (`IMPULSE_START`, Container; duration/startAt) + **ImpulseProcess** (`IMPULSE_PROCESS`,
  Container): Timeline-Block — re-evaluiert seine Kinder pro Frame über `[startAt, startAt+duration]`. Treibt
  die zeit-basierte Animation; bindet an den continuous-repaint-Clock (REM-36 E-D1, `wakeIn`).
- **PARTICLE_COMPARE** (op 194; **decode-aufgelöst — NICHT loud-guard, korpus-AKTIV**): Klasse
  `ParticlesCompare(id, flags, min, max, compare[], equations1[], equations2[])` = ein **per-Partikel-
  Conditional**: wertet `compare` (+ min/max-Range) pro Partikel aus → wendet `equations1` ODER `equations2`
  auf den Partikel-State an (Branch). **Aktiv in `maze`/`maze1`/`maze2` (4× je, zusammen mit
  `ConditionalOperations`)** = die Maze-Per-Partikel-Branch-Logik; confetti/hearts/particle: 0. → **S2-Scope**
  (gehört zur Evolution; S1-Seed-Frame rendert maze ohne es). NICHT defer-loud-guard.

## 3. Design (normativ)

### 3.1 Op-Feld-State-Lebensdauer (Design-Punkt 1)
- `mParticles` ist ein **render-only Op-Feld** auf ParticlesCreate (nicht serialisiert → §2-safe).
- **Seed-once-Semantik:** ParticlesCreate seedt beim ERSTEN apply (ein `seeded`-Flag); Folge-Frames seeden
  NICHT neu (sonst keine Evolution). Statischer Render (1 paint) = Seed-Frame; Live (N paints) = Seed + Evolution.

- **✅ VERIFIZIERTE VORBEDINGUNG (dev-1, first-hand): decode-once → paint-N HÄLT.** doc-by-`remember` +
  LaunchedEffect-inflate (`RemoteComposeApp:91/103-111`, 1× pro `docName`); die Frame-Loop reused die Op-
  Instanzen; TouchExpression-Render-Felder überleben heute schon Frames. → **Op-Feld-State (`mParticles`) trägt,
  KEIN persistenter-State-Map-Fallback nötig** (kein Player-State-Arch-Touch). Auch das Multi-Frame-Gate-Harness
  MUSS decode-once-paint-N (dasselbe inflated Doc N× painten, NICHT pro Frame re-inflaten) — sonst evolviert der
  State im Orakel nicht → §6-Akkumulierungs-Falle (REM-89/121). Harness-Contract: §5b.
  **⚠️ Scope der Vorbedingung (assist):** decode-once→paint-N beweist nur die Instanz-**PERSISTENZ** (mParticles
  überlebt Frames), NICHT die Akkumulations-**KORREKTHEIT** (evolviert der State richtig) — letztere ist genau
  das S2-Daten-Orakel-Gate (§5b), nicht durch die Persistenz-Verifikation abgedeckt.
- **(Verworfen) Fallback** (reset-exempter Player-State-Map) = Player-State-Arch-Touch mit Risk-Posture-Δ —
  **nicht nötig** (Vorbedingung hält); falls je gebraucht = eigene PO-/Mensch-Entscheidung, nicht still.
- **S1 ist von dieser Frage ohnehin UNABHÄNGIG:** S1 = ein einziger paint() (Seed + Draw @t=0).

- **Determinismus für Goldens:** Init-Eqs mit `OP_RAND` brauchen einen reproduzierbaren Seed. Wenn das Doc
  selbst `RAND_SEED` setzt → reproduzierbar. Sonst: **Partikel-RNG-Seed-Pin** (analog RenderTimePins/REM-57)
  für die Capture-Determinismus — Tester-Config, §2-irrelevant. (S1-Open-Question, s. §7.)

### 3.2 Per-Partikel-Loop-Execution (Design-Punkt 2)
- Spiegelt `runLoop` (REM-58): der Paint-Walk interceptet PARTICLE_LOOP (wie LOOP_START), runt den Body
  `[i+1, matchEnd)` **pro Partikel** mit eval-then-paint, lädt vor jedem Body-Durchlauf die Partikel-Vars.
- PARTICLE_LOOP ist bereits in CONTAINER_OPENING_OPCODES (skip-depth) — der Walk-Intercept ist additiv.

### 3.3 Impulse-Timeline + Animations-Clock (Design-Punkt 3)
- ImpulseStart/Process treiben die Zeit über `[startAt, startAt+duration]`. Integration mit dem bestehenden
  Animations-Clock: time-driven → `context.wakeIn(CONTINUOUS)` (REM-36 E-D1) → Host re-paintet → Evolution.
- Static-Mode (animation off): Impulse @ `staticTimeSeconds` (REM-57/62) = deterministischer Seed-Frame.

### 3.4 Capability-Staffing (Design-Punkt 4; PROJECT_CONTEXT §0)
- **Mobile:** voll — Touch treibt den Impulse (TouchExpression-Seam REM-108), Partikel reagieren live.
- **Desktop/Web:** verfügbare-Sensoren sonst statischer Render-Frame (Seed/zeit-getrieben ohne Touch).
- Interaktivität ist die S3-Achse; S1/S2 sind touch-unabhängig (Seed + zeit-Evolution).

## 4. §2 / Byte-Invariante (HARD GATE)
- **`write`/`read`/`companion read`/`equals`/`hashCode` aller 5 Ops UNVERÄNDERT** (bereits byte-faithful
  gemergt) → 173-Byte-Conformance by-construction. Sim = additive apply/paint + render-only Op-Felder
  (`mParticles`, `seeded`), nicht serialisiert. Kein Binärformat-Touch.

## 5. 🔴 Render-Gate-Strategie (LIVE — der harte Teil)
- **§2:** Conformance-173 grün + 4-Target-Compile.
- **VOLL-173-RENDER-Sweep PFLICHT** (Shared-Draw-Path — REM-140-Lehre): kein Draw-Fingerprint-Proxy, echter
  Render; nur die Ziel-Docs dürfen Δ, 0 Collateral auf den anderen.
- **S1 (Seed-Frame):** statischer t=0-Golden (Partikel @ Init-Position) — deterministisch (RNG-Seed-Pin §3.1),
  golden-bar. Daten-Orakel: N Partikel an Soll-Seed-Positionen.
- **S2/S3 (LIVE):** **NICHT headless-Screenshot** (headless captured nur first-paint — die wasm-headless-Lehre).
  → **Multi-Frame-Daten-Orakel** (seed-time → N Frames → Partikel-State evolviert korrekt, REM-123-Klasse:
  Daten-Orakel nicht nur Pixel) + **Maestro-Live-Flow** (Touch→Impulse→Partikel animieren) auf ≥1 Target
  (real-browser/CDP-per-Frame ODER Maestro, kein headless-First-Paint).

## 5b. LIVE-Multi-Frame-Gate — Harness↔Sim-Contract (gepinnt für dev-1)
Das Multi-Frame-Harness existiert noch nicht (DesktopRenderSweep ist single-frame: 1 paint @ nanoTime=0). dev-1
baut es, test-3 fährt es. Gepinnter Contract:

- **#1 Snapshot-Schedule:** Frame 0 = Seed (`frameTimeSeconds = startAt`), dann fixe Frames @ konstantem dt über
  `[startAt, startAt+duration]` + ein Decay-Tail. `duration`/`startAt` aus `ImpulseStart` (resolved; NaN/0 →
  Default-Fenster 2.0s). **Pflicht-Snapshots:** Seed (t=startAt), Mid (t=startAt+duration/2), End (t=startAt+
  duration) + jeder dt-Frame dazwischen. (Keyframe-Trio + dichte dt-Abtastung = Evolution sichtbar, nicht nur Endpunkte.)
- **#2 Clock-Advance:** **deterministisch, NICHT wall-clock.** `frameTimeSeconds = startAt + k·dt` für k=0..N,
  `dt = 1/30 s` (fix), `N = ceil(duration/dt) + 4` (Decay-Tail). `animationEnabled=true` (LIVE → Impulse
  advanciert; `staticTimeSeconds` ungenutzt). Harness ruft `player.paint(doc, recCtx, frameTimeSeconds=…)` für
  jedes k auf **demselben inflated Doc** (decode-once→paint-N, §3.1-Vorbedingung).
- **#3 Daten-Orakel-Rekonstruktions-Form (unabhängig, §6/REM-123):** der Orakel rekonstruiert pro Frame k die
  erwartete Partikel-Position **außerhalb des Players** (separater Recompute, NICHT die Sim aufrufen): Seed =
  init-Eqs @ k=0 (mit gepinntem RNG, #4) → dann k× die ParticlesLoop-Update-Eqs (+ Restart) anwenden → die
  Positions-Vars → erwartete Draw-(cx,cy) (Body-Placement-Transform angewandt). **Prüfbare Felder pro Frame:**
  die Menge der N Partikel-Draw-Positionen (cx,cy) je Frame (Float-Toleranz).
  **🔒 Orchestrierungs-Grenze (assist):** die Rekonstruktion DARF `RpnFloatEvaluator` (REM-109/127) + die
  byte-dekodierten Equations reusen (geteilte Eval-Primitive, kein Sim-Logik-Teilen), MUSS aber
  **Seed/Loop/Restart/VAR1-Index-Injektion/RAND-Sequencing UNABHÄNGIG aus dem Upstream-SPEC reimplementieren**
  (NICHT die Sim-Orchestrierung aufrufen/spiegeln) → bug-unabhängig.
- **#5 Vergleichs-Mechanismus = DRAW-CAPTURE (black-box), NICHT op-State-Readback:** ein Recording-PaintContext
  fängt die Per-Partikel-Draw-Primitive (`drawCircle` cx/cy bzw. `drawBitmap`-dst, je nach Body) pro Frame →
  verglichen gg. die #3-Rekonstruktion. **KEIN `op.mParticles`-Readback** — der teilt den Sim-Pfad (falsche
  mParticles + Readback liest dieselben falschen mParticles = falsch-grün, REM-89/121-Klasse). Draw-Capture
  testet was tatsächlich GEMALT wird (Output, sim-bug-unabhängig). **dev-1+PO-Lean bestätigt, dev-2 stimmt zu.**
- **#4 RNG-Determinismus (= OQ1; assist-v2-Fix: SPEC-Order, nicht Sim-Order):** Docs tragen kein `RAND_SEED` →
  **Partikel-RNG-Seed-Pin** (fixe documented Konstante, Capture-Config). **🔴 Die RAND-Konsum-Order ist gegen den
  UPSTREAM-SPEC definiert, NICHT „= Sim-Eval-Order" (das wäre zirkulär → falsch-grün bei künftigem Sim-Order-
  Edit, dieselbe Klasse wie der vermiedene op-Readback):**
  - `ParticlesCreate.initializeParticle` (upstream Z.251/257-264): **partikel-major × var-major × within-eval**,
    VAR1-Index-Injektion **VOR jedem eval**.
  - `ParticlesLoop` (upstream Z.127-134): **partikel-major**, `restart>0` → re-seed via Create-Init.
  - **Der SIM MUSS diesen Spec matchen** (assist verifiziert: aktuelle Order stimmt zufällig mit Upstream →
    Sim wahrsch. korrekt); die Rekonstruktion leitet ihre Order ebenfalls aus dem Spec ab → beide gg. Spec, nicht
    gegeneinander.
  - **RNG reseed-before-BOTH:** der Harness reseedet den RNG auf den Pin VOR BEIDEN Pfaden (Sim-Capture UND
    #3-Rekonstruktion); `OP_RAND` muss **per-Run-reseedbar** sein (kein geteilter persistenter globaler RNG über
    Runs) — **verify + pin bei Impl** (heute: `RpnFloatEvaluator.rng` ist ein geteiltes `private var`, via
    `OP_RAND_SEED` reseedbar; ein Capture-externer Reseed-Hook ist für den Harness nötig → Impl-Item).
  Seed-Pin-Owner: Tester-Config (§2-irrelevant).

## 6. Slicing
- **S1 — Seed-Frame** (klein-mittel; etablierte Muster): ParticlesCreate-Seed (Init-Eqs, `mParticles` füllen) +
  ParticlesLoop statischer Per-Partikel-Body-Draw @t=0 (runLoop-Pattern, Partikel-Vars laden, kein Zeit-Update).
  **Akzeptanz:** confetti/hearts/particle rendern N Seed-Partikel statt blank; statischer Golden + Daten-Orakel
  (N Draws an Soll-Seed-Positionen); §2 + Voll-173-Sweep 0-Collateral. **Fast-parallel zur TechSpec-Finalisierung.**
- **S2 — Evolution + Impulse-Timeline** (mittel): Update/Restart-Eqs + Op-Feld-State-Persistenz über Frames +
  ImpulseStart/Process → Animations-Clock. LIVE. **Akzeptanz:** Multi-Frame-Daten-Orakel (State evolviert);
  Impulse-Dauer/startAt korrekt. (dev-1-Parallelisierung: Render-Gate-Tooling / Multi-Frame-Harness.)
- **S3 — Interaktivität + Haptik** (mittel-groß): Touch-getriebener Impulse capability-gestaffelt (Mobile voll;
  Desktop/Web sensor/statisch) + `haptic_demo` (Impulse-only, Haptik-Feedback-Timing). **Akzeptanz:** Maestro-
  Live-Flow (Touch→Animation) auf ≥1 Target.

## 7. Locks & aufgelöste Fragen (PO-approved)
- **Locks:** §2-render-only (5 Ops Wire unangetastet); Op-Feld-State (kein Context-Persist); runLoop-Reuse
  (kein paralleler Loop-Mechanismus); RpnFloatEvaluator-Reuse; PARTICLE_COMPARE loud-guard falls korpus-absent;
  Voll-173-Render-Sweep (kein Fingerprint-Proxy); LIVE-Gate = Multi-Frame-Orakel+Maestro (kein headless-Screenshot).
- **🔒 Precondition-Lock (dev-1, VOR S2):** Op-Feld-State UND das Multi-Frame-Harness brauchen BEIDE
  **decode-once→paint-N** (Op-Instanzen über Frames wiederverwendet). dev-1 verifiziert read-only; bis dahin steht
  §3.1 auf der unverifizierten Annahme. **Falls re-inflate-pro-Frame → der Fallback (persistenter reset-exempter
  Player-State-Map) ist ein Player-State-Architektur-Touch mit Risk-Posture-Δ → eigene PO-/ggf-Mensch-Design-
  Entscheidung, NICHT still einbauen.**
- **Aufgelöste Fragen (PO):**
  1. **Seed-Determinismus (S1-Decode-Befund):** die Partikel-Docs nutzen `OP_RAND` in den Init-Eqs, setzen aber
     **KEIN `RAND_SEED`** (decode-verifiziert: confetti 100 / hearts 50 / particle 10×2 / maze 1 Partikel; Init-Eqs
     durchgängig `rand=true seed=false`; Init referenziert auch `VAR1`=op70 = Partikel-Index). → Doc-self-seed-Pfad
     **entfällt** → **Partikel-RNG-Seed-Pin** (RenderTimePins/REM-57-analog, reine Capture-Config, §2-irrelevant)
     für deterministische Goldens. Korrektheits-Gate in BEIDEN Fällen = **Daten-Orakel** (N Partikel an Soll-Seed-
     Positionen, REM-123-Klasse), NICHT Pixel-Match. (Seed-Pin-Owner: Tester-Config.)
  2. **PARTICLE_COMPARE (decode-aufgelöst):** korpus-AKTIV in maze/maze1/maze2 (4× je) — **NICHT loud-guard.**
     Klasse `ParticlesCompare` (per-Partikel-Conditional, compare→equations1/2-Branch) → **S2-Scope** (Maze-
     Evolution); confetti/hearts/particle nutzen es nicht. S1-Seed-Frame braucht es nicht (s. §2).
  3. **Multi-Frame-Orakel-Harness:** **dev-1** baut das Tooling, **test-3** fährt das Gate.
