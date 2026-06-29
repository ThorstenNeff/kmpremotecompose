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
- **PARTICLE_COMPARE** (op 194): opcode existiert, keine Klasse — vermutl. Conditional im Loop-Body. Bei
  S2-Impl prüfen; falls in keinem der 7 Docs aktiv → loud-guard/deferred (D1-Disziplin).

## 3. Design (normativ)

### 3.1 Op-Feld-State-Lebensdauer (Design-Punkt 1)
- `mParticles` ist ein **render-only Op-Feld** auf ParticlesCreate (nicht serialisiert → §2-safe).
- **Seed-once-Semantik:** ParticlesCreate seedt beim ERSTEN apply (ein `seeded`-Flag); Folge-Frames seeden
  NICHT neu (sonst keine Evolution). Statischer Render (1 paint) = Seed-Frame; Live (N paints) = Seed + Evolution.

- **🔴 LOAD-BEARING VERIFIZIERTE VORBEDINGUNG (dev-1, read-only, VOR S2) — NICHT ANGENOMMEN:** der Op-Feld-
  State-Ansatz steht und fällt damit, dass der Player das `RemoteComposeDocument`/die Op-Instanzen über Frames
  **wiederverwendet (decode-once → paint-N)**, NICHT pro Frame re-inflated. Wenn re-decode-pro-Frame → kein
  `mParticles`-Op-Feld überlebt → Ansatz braucht Rethink (Fallback unten). dev-1 verifiziert den Decode-/Host-
  Paint-Pfad read-only + flagt VOR S2; PO relayt den Befund; diese Sektion wird auf der VERIFIZIERTEN Antwort
  finalisiert. **Doppelt load-bearing:** auch das **LIVE-Multi-Frame-Gate-Harness MUSS decode-once-paint-N**,
  sonst evolviert der State im Orakel nicht → das Gate sieht nur Seed-Frames = die §6-Render-Golden-Gate-Falle
  für akkumulierende Generierung (REM-89/121-Klasse). Das Harness teilt diese Vorbedingung mit der Impl.
- **Fallback (falls re-inflate-pro-Frame):** Partikel-State NICHT als Op-Feld, sondern in einem **persistenten
  Player-State-Map keyed nach Partikel-id**, der den per-Frame-Context-Reset überlebt (Host-gehalten oder ein
  reset-exemptes Store-Segment). Größerer Eingriff → erst nach dev-1s Verdikt entscheiden.
- **S1 ist von dieser Frage UNABHÄNGIG:** S1 = ein einziger paint() (Seed + Draw @t=0) → keine Cross-Frame-
  Persistenz nötig. S1 kann fast-parallel laufen; die Vorbedingung blockt nur S2.

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
  2. **PARTICLE_COMPARE:** defer-S2, **loud-guard falls korpus-absent** (D1) — approved.
  3. **Multi-Frame-Orakel-Harness:** **dev-1** baut das Tooling, **test-3** fährt das Gate.
