# TECHSPEC — REM-128 E6 Compose-Creation-DSL (`@Composable` Authoring Surface) (FINAL)

> **Status:** FINAL (assist-finalized from dev-3 draft `TECHSPEC-REM-128-compose-creation-DRAFT.md`,
> commit `82409b4`). Supersedes the DRAFT. Grounded against the live tree (`develop`), upstream read
> as behavior-reference only (PROJECT_CONTEXT §5 — no verbatim copy).
>
> **De-risk premise (verified against the tree, not taken on faith):** the procedural path is
> byte-proven (`CreationByteConformanceTest` pins 5 `procedure_*` oracles; REM-126 pins
> `procedure_simple2` over disk). The Compose surface is an **applier on the finished context — not a
> second encoder**: it composes a node tree (Phase A, doc-buffer untouched), then render-walks the
> tree calling the **existing public procedural helpers** (Phase B). Bytes come from the proven path,
> so divergence is structurally impossible. (= the architecture `TECHSPEC-REM-E §3` pre-committed.)

---

## 0. Leitprinzip: Compose-DSL = Applier auf den fertigen Context (kein Parallel-Encoder)

A `RemoteComposeApplier: AbstractApplier<RemoteComposeNode>` records `@Composable` calls into a node
tree; after composition a depth-first render-walk calls the **already-public** procedural helpers
(`drawOval`, `setRootContentBehavior`, …) on a `RemoteComposeContext`. REM-128 builds **no new
op-emission** — every byte is emitted by the byte-proven procedural surface. New code is purely the
authoring layer *above* that surface (nodes, applier, capture orchestrator, the §2 triple-pin).

**🔑 The byte-faithfulness shortcut (assist-added realization):** the capture orchestrator reuses
`document(w, h, profile, contentDescription){ … }` (DocumentDsl.kt:45) **as the shell**, running the
render-walk *inside* its content lambda:
```
fun captureSingleRemoteDocument(w, h, profile, contentDescription, content): ByteArray =
    document(w, h, profile, contentDescription) {           // proven prolog / id-42 / writer setup
        val tree = composeToNodeTree(content)               // Phase A — builds nodes, touches NOTHING here
        check(currentBufferSize() == bufferSizeAtLambdaEntry) // W1 hard guard
        tree.renderWalk(this)                               // Phase B — calls this.drawOval(...) etc.
    }                                                        // document{} runs encodeToByteArray()
```
This reuses REM-85's prolog, the id-42 content-description reservation, profile/auto-form selection,
and `encodeToByteArray()` **verbatim** — the Compose path can only differ from the procedural path in
*render-walk order*, which is exactly what the §3 Stage-1 pin checks.

---

## 1. Architektur (upstream behavior, KMP realization)

Upstream behavior-reference (read, not copied): `CaptureRemoteDocument.kt`, `RemoteComposeApplier.kt`,
`RemoteComposeNode.kt` under `./androidx/.../remote-creation-compose/`.

1. **`RemoteComposeApplier: AbstractApplier<RemoteComposeNode>`** — `insertBottomUp`/`remove`/`move`/
   `onClear`, builds the node tree.
2. **`@RemoteComposable`** DslMarker — constrains what may compose inside `content`.
3. **Two phases:** Phase A composition (`setContent { content() }` → node tree; **doc-buffer not
   written**); Phase B render-walk (depth-first → calls the procedural helpers).
4. **`RemoteCanvas`** draw node — records draw-intent into the node tree; on the render-walk its
   recorded calls flush to the context via the procedural draw helpers.

**KMP realization — verified facts:**
- `androidx.compose.runtime.AbstractApplier` / `Recomposer` / `Composition` / `Snapshot` are
  commonMain in Compose Runtime, and `compose.runtime` + `compose.foundation` + `compose.ui` are
  already `:shared` commonMain deps (`shared/build.gradle.kts:75-78`). **No existing code uses the
  composition/applier machinery yet** (the player uses only Compose *graphics*) → REM-128 is the first
  composition consumer → the iOS+wasmJs compile-probe (§5.9) is a **hard S1 gate**, not a formality.
- **Android `Context` is dropped:** display info passed as plain `Float` params (width/height/density/
  fontScale), mirroring `document(width, height, …)`. No `android.content.Context` /
  `android.graphics.Bitmap` in commonMain (PROJECT_CONTEXT §5).
- The procedural helpers the applier drives (`RemoteComposeContext.drawOval`, `.setRootContentBehavior`,
  `document()`, `ids`, `add`, `encodeToByteArray`, `IdAllocator.nextId()`) are **all already public**
  — confirmed in-tree. This is load-bearing for §4 below.

---

## 2. 🔴 Watchpoints

**(W1) Clean composition (doc-buffer invariant) — HARD, no flag.** Phase A must not write the
doc-buffer; otherwise render-walk order (= op order = bytes) is corrupted. Enforced as
`check(currentBufferSize() == bufferSizeAtLambdaEntry)` between composition and render-walk.
*Implementability verified:* `WireBuffer.size` exists but there is **no public non-finalizing size
accessor** on `RemoteComposeContext`/`RemoteComposeWriter` today → REM-128 adds an additive read-only
`currentBufferSize(): Int` (byte-neutral; the single permitted commonMain touch — see §4 gate).
**Stronger structural guard (required):** Phase A composables must have **no reference to the
context** — they record into nodes only — so emission-during-composition is impossible *by
construction*; the runtime `check()` is belt-and-suspenders.

**(W2) ID-allocation order — the byte-critical invariant.** `ids.nextId()` is incremental; bytes are
equal iff the render-walk emits in the **same order** as the procedural DSL. Locked order: depth-first
in insert (source) order; a node's modifiers emit **before** its children; container open/close use the
existing REM-96 negative-componentId path (`resolveComponentId`). **§3 Stage-1 (compose==procedural)
is the proof of this** — it is not optional. The ID allocator is **the procedural context's**, never a
parallel pool.

**(W3) Canvas adapter form → own `RemoteDrawScope` (decision §5.2).** Do **not** subclass
`androidx.compose.ui.graphics.Canvas` (upstream's 1×1-bitmap-backed RecordingCanvas → painful
expect/actual on iOS, zero byte benefit). REM-128 exposes a minimal `RemoteDrawScope` whose methods
are **thin shims to the proven procedural helpers** (`RemoteCanvas { drawOval(...) }` → the same
`drawOval` helper). No independent emission in the shim. We build a Compose *authoring path to `.rc`*,
not a Compose renderer — so DrawScope-drop-in parity is explicitly not a goal.

**(W4) Recomposition/streaming — DEFERRED (§7).** MVP = single capture
(`captureSingleRemoteDocument`, suspend, once). The `Flow<ByteArray>` recompose variant + its state
caches (`animCache`, …) are a follow-up epic.

**(W5) Density.** Float density/fontScale params from author code; no Android `Density` type leak in
commonMain (PROJECT_CONTEXT §5). `LocalDensity` may be read but authored values come from the doc
author, never a platform default.

**Not watchpoints:** the encoder, op layouts, header/profile selection, allocator mechanics — all
commonMain, byte-proven, untouched.

---

## 3. §2-Anker (Triple-Pin — the acceptance proof)

Same form as REM-119/126, three sources for the same bytes:

1. **Stage 1 — Compose-DSL == procedural-DSL (cross-surface, isolates applier divergence).** The same
   input built via `document(300,300){ setRootContentBehavior(...); drawOval(...) }` and via
   `captureSingleRemoteDocument(300,300){ RemoteCanvas { drawOval(...) } }` → byte-identical. This is
   the W2 render-order proof.
2. **Stage 2 — Compose-DSL == corpus oracle (THE gate).** Output == `procedure_simple2.rc`
   byte-for-byte. Source-of-truth pin (the upstream-produced fixture) — the analogue of REM-126 §3,
   with the Compose-applier as the new path instead of disk-IO. Stages 1 & 3 are supporting
   (self/cross-referential); **Stage 2 is the gate.**
3. **Stage 3 — capture determinism.** Two captures of the same content → byte-identical (W2 insurance:
   render-walk order stable between runs).

**MVP fixture:** `procedure_simple2` (82 B) — same choice/rationale as REM-126 §5.1 (the known-good
simple2 body, no new repro). Body = `setRootContentBehavior(NONE, CENTER, SCALE, SCALE_FIT)` as a
top-level setup call before `RemoteCanvas { drawOval(0,0,asNan(WIN_W),asNan(WIN_H)) }`.

**Acceptance for REM-128 MVP:** Stage 1 + 2 + 3 green vs `procedure_simple2`. Extending to gradient1/
center_text1/look_up1/text_path_effects is a cheap follow-up once containers/text components exist.

---

## 4. Modul-Grenze (§4 — assist decision: **Option B `:creation-compose`**)

**Decision: Option B — new `:creation-compose` KMP module depending on `:shared`.** This *aligns* with
dev-3's lean, but my verification **removes the caveat that worried the draft** and that drove the
"maybe Option A" hedge:

- **The draft's `@PublishedApi internal` visibility-churn concern is essentially unfounded.** I
  verified in-tree that the entire driving API is **already public**: `RemoteComposeContext` (+ ctor,
  `ids`, `add`, `encodeToByteArray`, `resolveComponentId`), the `document()` shell, and the procedural
  helpers (`drawOval`, `setRootContentBehavior`, …) are public extension funcs; `IdAllocator.nextId()`
  is public. → `:creation-compose` can drive the byte-proven path **with zero visibility widening**.
- Therefore Option B's **commonMain footprint is at most ONE additive, byte-neutral line**: the W1
  `currentBufferSize(): Int` accessor (§2). Not the wire-touching risk the draft implied.
- Option B **heals the REM-126 §4.1 Compose-version-diamond** I flagged: the Compose-runtime/composition
  machinery lives in `:creation-compose`, so `:server` (and any non-Compose consumer of `:shared`) no
  longer needs it. That is the "separate, larger refactor" REM-126 §4.1 deferred — REM-128 is its
  natural home.

**Why not Option A (in `:shared` commonMain):** it leaves the diamond in place and keeps Compose a hard
`:shared` dep for `:server`. Since B is now low-cost (public API already sufficient), B's only real cost
is module boilerplate — worth it for the diamond-heal. *(If the iOS/wasmJs S1 compile-probe (§5.9)
surfaces a Compose-runtime-in-a-second-module problem, fall back to Option A for the MVP and do the
extraction as a follow-up — but the probe is expected to pass, this is the contingency, not the plan.)*

**§2 gate for the module work:** `git diff develop..BR -- 'shared/src/commonMain/**'` must be **empty
OR contain only the additive `currentBufferSize` accessor** — zero changes to any existing op's
`write`/`read`/`equals`/`hashCode` or any DSL helper. The applier/nodes/orchestrator live entirely in
`:creation-compose` (new module, additive by definition).

---

## 5. Open-question decisions

1. **Module →** Option B `:creation-compose` (§4).
2. **Canvas adapter →** own `RemoteDrawScope` (shims to procedural helpers), not CMP-`Canvas`
   expect/actual (W3).
3. **Density →** Float params + `LocalDensity`; no Android `Density` leak (W5).
4. **Streaming `Flow<ByteArray>` →** deferred to a follow-up epic; MVP = `captureSingleRemoteDocument`
   (suspend, once) (W4).
5. **Layout containers →** none in MVP; `RemoteCanvas`-only (enough for the simple2 triple-pin).
   Containers = S2 slice.
6. **Modifiers →** none in MVP (no container ⇒ no modifier value). S3 slice.
7. **CustomComponentFactory / user extension points →** out (hard cut).
8. **Server takes Compose-DSL? →** No; `:server` stays procedural-only for REM-128 (and under Option B
   `:server` no longer carries Compose at all — the diamond-heal).
9. **iOS + wasmJs compile-probe → HARD S1 GATE (assist-elevated).** PROJECT_CONTEXT §0 makes **wasmJs**
   an active target, which the draft omitted. S1 must prove the applier/`captureSingleRemoteDocument`
   **compiles on iosSimulatorArm64 AND wasmJs** (plus jvm/androidHostTest) *before* further build.
   If headless Compose-composition is not viable on wasmJs, the surface ships on android/ios/jvm and
   **wasmJs-capture is deferred with the probe result as the recorded evidence** — not silently
   skipped.

---

## 6. Harte Regeln (review-enforced)

- **commonMain stays java-free** (PROJECT_CONTEXT §5). Compose runtime/foundation/ui-graphics are CMP,
  not java.* — OK in commonMain; the applier itself lives in `:creation-compose`.
- **No wire/equals/hashCode touch** on existing ops or DSL helpers → 173/173 corpus + 5
  `CreationByteConformanceTest` stages green by construction (additive-only, S3b pattern).
- **No second encoder lane.** Emitting an op directly from the applier instead of via a procedural
  helper = blocked. This is the entire point of the adapter design.
- **No upstream verbatim copy** — behavior reference only.
- **ID allocator = the procedural context's `ids.nextId()`** — no parallel pool in the applier.
- **Clean-composition guard is HARD** (W1): structural (no context ref in Phase A) **and** the runtime
  `check()`; no flag, no off-switch.
- **No `android.content.Context` in commonMain.**

---

## 7. Explicitly NOT in scope

- Streaming `Flow<ByteArray>` recompose variant + its state caches — follow-up epic.
- Layout containers (`RemoteBox`/`RemoteColumn`/`RemoteRow`) + the REM-96 modifier set — S2/S3, not MVP.
- CustomComponentFactory / user extension points.
- Screenshot-test equivalent (upstream's instrumented test) — we prove byte-equality (§3), not pixels.
- Compose-recompose in `:server` (stays procedural).
- 0-corpus long-tail ops (G3/G6–9) — parked.

---

## 8. Verification (the "done" bar)

- Triple-pin §3 green vs `procedure_simple2` (Stage 1 + 2 + 3); **Stage 2 is the gate.**
- 173/173 corpus + 5 `CreationByteConformanceTest` stages unchanged (additive-only; commonMain diff
  empty-or-only-the-`currentBufferSize`-accessor per §4).
- **Compile clean on iosSimulatorArm64 + wasmJs + jvm + androidHostTest** (§5.9 hard gate).
- One Maestro flow (iOS-sim or Android-emulator) where a tiny `@Composable` produces a `.rc` that the
  existing player renders as the 82 B oval doc — the app-level proof (PROJECT_CONTEXT §6; the analogue
  of REM-126's disk-CI anchor, since there is no render output to screenshot on the *authoring* side
  the byte-equality + a single end-to-end render is the functional evidence).

## 9. Impl slicing (for dev-3)

- **S1 (closes §0 MVP):** `:creation-compose` module skeleton; `RemoteComposeNode` hierarchy (Root +
  Canvas); `RemoteComposeApplier`; `captureSingleRemoteDocument` orchestrator reusing `document{}`
  (§0); additive `currentBufferSize()` accessor + W1 guard; **iOS+wasmJs+jvm+androidHostTest compile
  probe (§5.9)**; triple-pin vs `procedure_simple2` (§3). Do the compile-probe **first** — it gates the
  module choice.
- **S2:** container components (mirror the procedural container helpers) + triple-pin on a chosen
  container fixture.
- **S3:** modifier suite (mirror REM-96's set) — separate slice/epic.
- **S4:** streaming `Flow<ByteArray>` — own epic.

## 10. assist verdict
**GO on the design.** The applier-on-the-proven-context architecture makes byte-divergence structurally
impossible, and reusing `document{}` as the shell (§0) makes the Compose path differ from procedural
*only* in render-walk order — exactly what the Stage-1 pin checks. The §4 call is **Option B**, upgraded
from a hedge to a confident recommendation by verifying the driving API is already public (so B costs ≤
one additive byte-neutral accessor, not visibility churn) and it heals the REM-126 §4.1 diamond. Two
assist-added hard gates: the **wasmJs** compile-probe (§0 active target the draft missed) and the
structural W1 guard (no context reference in Phase A, not just the runtime check). Scope fences
(single-capture, RemoteCanvas-only, no second encoder) are correct. MVP is ungated — S1 can start, with
the compile-probe as its first step.
