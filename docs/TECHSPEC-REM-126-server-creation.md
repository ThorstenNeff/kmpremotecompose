# TECHSPEC — REM-126 Server-Creation-Seite (JVM-headless `.rc`-Authoring) (FINAL)

> **Status:** FINAL (assist-finalized from dev-3 draft `TECHSPEC-REM-126-server-creation-DRAFT.md`,
> commit `54eb356`). Supersedes the DRAFT. **PROJECT_CONTEXT §0** requires `.rc` documents to be
> authorable on a **Server (JVM, headless)**, byte-true. Upstream/format facts grounded against the
> live tree (`develop = 3940280`), not taken on faith.
>
> **The de-risk is real and verified:** the Creation-DSL already encodes byte-true on JVM. Confirmed
> against the tree: `shared/build.gradle.kts:46` `jvm()` target active; `document(...)` returns a
> `ByteArray` (`DocumentDsl.kt:45-75`); `CreationByteConformanceTest` proves **in-memory** byte-equality
> vs **five** `procedure_*` oracles (`simple2` + the four watchpoints `gradient1` / `center_text1` /
> `look_up1` / `text_path_effects`); `okio = 3.9.1` is already a dep (`libs.versions.toml:18,42`).
> **REM-126 therefore adds exactly one thing not already proven: the okio disk write→read path.** The
> whole TechSpec is organized around pinning *that* one new surface — not re-proving the DSL.

---

## 1. Problem & framing

`.rc` authoring works today only inside `:shared` `jvmTest` (`CreationByteConformanceTest`). There is no
server-shaped surface: no headless entry-point, no `.rc`-to-disk route, no consumable library artifact,
and — critically — **no byte-anchor that exercises the disk-write path** (the existing conformance test
compares the in-memory `ByteArray`, never a file). REM-126 wraps the proven DSL in a server surface and
pins the disk path.

**This is NOT "build a creation DSL for the server."** The DSL is done and corpus-complete (REM-119,
`develop = 3940280`). REM-126 is "expose the finished DSL as a server-shaped surface + prove the one new
byte path (disk IO)."

---

## 2. §2 — the hard gate (additive-only, holds by construction)

- **`commonMain` is not touched. At all.** Every REM-126 artifact lives in `:shared` `jvmMain` (the okio
  disk helper) and a new `:server` module (the executable). No op `write`/`read`/`equals`/`hashCode`
  change, no DSL-helper change. → the 173/173 corpus conformance **and** the 5 `CreationByteConformanceTest`
  stage-3 fixtures stay green *by construction*. A wire/DSL change in this epic = blocked.
- §2-guard for every REM-126 diff: `git diff develop..BR -- 'shared/src/commonMain/**'` MUST be empty.
  If it isn't, the change is out of scope.

---

## 3. 🔑 The acceptance anchor — read back **from disk** (the one non-vacuous pin)

This is the load-bearing decision of the whole spec. The existing `CreationByteConformanceTest` already
proves `document(simple2){} == oracle` for the **in-memory `ByteArray`**. The *only* thing REM-126 adds
on top is `okio.FileSystem.SYSTEM.write(...)`. Therefore:

> **The REM-126 §2-anchor MUST compare bytes read back *from the written file* against the corpus
> oracle — NOT the in-memory array.** Comparing the in-memory array proves nothing beyond what
> `CreationByteConformanceTest` already proves → that would be a **vacuous pin** of the server path.

Concretely the anchor does, end to end:
1. `bytes = document(300, 300, contentDescription="Clock") { …simple2 body… }` (the *exact* known-good
   body from `CreationByteConformanceTest.simple2_bytesMatchOracle`).
2. write to a temp path **through the server IO helper** (`RcDiskWriter.write(path, bytes)`, okio).
3. **read the bytes back from that file** (`RcDiskWriter.read(path)`, okio) — discard the in-memory array.
4. `assertContentEquals(oracle("procedure_simple2"), readBack)`.

The three supporting stages from the draft are kept, but their roles are stated honestly:
- **Stage 1 (self-consistency, 2× run → identical):** determinism insurance (W3). Self-referential —
  not the byte-truth pin.
- **Stage 2 (decode→reEncode == output):** L1-codec consistency. Also self-referential.
- **Stage 3 (read-back-from-disk == corpus oracle):** the **only** stage that checks against a source of
  truth we control (the upstream-produced fixture) **and** exercises the new disk path. **This is THE
  acceptance gate.** Stages 1–2 are necessary-but-not-sufficient supporting evidence.

---

## 4. Module boundary (§4 decision — assist call) + where each piece lives

dev-3 + PO lean `:server`; I concur, **with a split** that the draft left ambiguous and that the
oracle's location forces:

- **`RcDiskWriter` (okio disk helper) → `:shared` `jvmMain`.** Reason: the authoritative §2-anchor
  (§3) must read both the corpus oracle (a `:shared` **commonTest** resource, resolved via
  `RcCorpus.fixtureRoot`) *and* the disk helper. `:shared` cannot depend on `:server`, so the helper
  **must** sit in `:shared` for the anchor test to live next to the oracle in `:shared` `jvmTest`. It is
  the first okio-disk use in `jvmMain` (today okio-disk is only in commonTest) — small, java-free.
- **§2-anchor test → `:shared` `jvmTest`** (`ServerCreationDiskConformanceTest`). Where corpus + helper
  + `document{}` all coexist and the real disk round-trip happens. This is the gate.
- **`:server` module → thin executable only.** `application` plugin, `mainClass`, `dependsOn project(":shared")`
  (jvm variant) + okio. Its `main(args)` = `document{…} + RcDiskWriter.write(args[0], bytes)`. Its own
  test = a **CLI smoke**: run the JAR, assert a non-empty file appears (proves the executable wires up).
  The *byte-truth* is owned by the `:shared` anchor, not the smoke.
- `settings.gradle.kts`: add `include(":server")`. Server depends **only** on `:shared` + okio; pulls in
  nothing Android/UI of its own.

**Why not put the entry-point + verify both in `:server` (draft's shape):** the corpus oracle is a
`:shared` test resource; a `:server`-test byte-anchor would need test-fixture sharing or a copied
fixture to reach it — more wiring, and easy to accidentally compare the in-memory array (vacuous). The
split keeps the byte-anchor where the oracle already lives and makes the disk-read the natural thing to
assert.

### 4.1 🟡 Wiring watchpoint (non-blocking, NOT a byte risk)
`:shared`'s `commonMain` depends on Compose (`compose.runtime/foundation/material3/ui/components.resources`
+ lifecycle — `shared/build.gradle.kts:39-48`). So the `:shared` **jvm variant** drags Compose (and
transitively Skiko/desktop) onto the `:server` classpath even though the server never renders. This
**does not affect byte-correctness** (Compose is never called on the creation path; Skiko loads lazily
only on a render call the server never makes) and is fine for MVP. Flag for the impl: the server JAR will
be heavier than a pure-DSL artifact. A future "creation-DSL without Compose" extraction is a separate,
larger refactor — explicitly **out of REM-126 scope**, noted so nobody treats the bloat as a defect.

---

## 5. Decisions on the draft's 5 open questions

1. **Module boundary →** `:server` executable + `RcDiskWriter` in `:shared` `jvmMain` + anchor in
   `:shared` `jvmTest` (§4). (Refines dev-3's "`:server`" lean with the helper/test placement the
   oracle location requires.)
2. **Canonical §0 fixture →** **`procedure_simple2`** (82 B). Reuse the *exact* DSL body already proven
   in `CreationByteConformanceTest.simple2_bytesMatchOracle` — zero new decode/repro work, which is
   exactly the risk dev-3 raised against `simple1` (open-Q5). `simple1` deferred.
3. **CLI depth →** **minimal**, no framework. MVP runner = `main(args[0] = outPath)` building **one
   hardcoded doc** (the simple2 replica) and writing it. No Picocli/Clikt.
4. **Output path →** caller working-directory / caller-supplied path, no env-var magic (POSIX norm,
   dev-3's lean — accepted).
5. **`simple1` reproducibility →** moot: we picked `simple2` (already 1:1-validated). `simple1` only if a
   future ticket wants it, with its own decode→repro spike first.

### 5.1 Fixture-by-name selection is DEFERRED — and why (a real trap, not just minimalism)
The draft's open-Q3 floated `args[1] = fixtureName`. **Deferred**, because picking a fixture by a CLI flag
decouples the doc-author code (which owns the `profile`) from the runtime selection → it risks the **W2
profile/auto-form mismatch** (baseline⇒flat-form api6 vs non-baseline⇒map-form api7; `DocumentDsl.kt:52-72`).
A name-driven runner that doesn't carry the author's `profile` would emit the wrong header form →
byte-divergent. MVP = one hardcoded doc whose `profile` is bound in code. Multi-fixture/name selection is
a later generalization that must thread `profile` from author code, never from a flag.

---

## 6. 🔴 Watchpoints (where byte-divergence would hide)

- **(W1) Density** — server sets **no** density default; all dims are author-supplied Float/Int, encoded
  1:1 (player evaluates density, PROJECT_CONTEXT §5). If anyone later adds inline density resolution it
  stays behind the player abstraction and must not touch the server path.
- **(W2) Profile / auto-form selection** — `profile` comes from the author code, never a CLI flag (see
  §5.1). Form is a function of profile (`DocumentDsl.kt`), already byte-proven; the server changes nothing.
- **(W3) Determinism between runs** — `ids.nextId()` + TextData pool are per-document deterministic
  (REM-90); stage-1 (2× run → identical) is the cheap insurance.
- **(W4) Filesystem byte-faithfulness — the one the anchor specifically exercises.** `okio`'s
  `BufferedSink.write(ByteArray)` / `BufferedSource.readByteArray()` are binary-exact (no newline/UTF
  translation). The §3 read-back is precisely what proves W4 end-to-end; the helper opens binary, no
  text mode. This is the novel surface and the reason the anchor reads from disk rather than memory.
- **Explicitly NOT watchpoints:** endianness, table encoding, op-ordering — all in `commonMain`, already
  proven, untouched here.

---

## 7. Hard rules (review-enforced)

- `commonMain` stays java-free **and unchanged** (§2). All new code in `:shared` `jvmMain` + `:server`.
- **okio-only IO** on the server surface — no `java.io.File`, no `java.nio`, no `DataOutputStream`.
  (Self-imposed beyond the §5 commonMain rule: keeps the IO helper promotable to a `commonMain`
  `expect`/`actual` if a native CLI target is ever added. For MVP the helper is `jvmMain`-only since the
  only server target is JVM.)
- **No wire/equals/hashCode change** to any existing op or DSL helper → additive-only (S3b pattern).
- **E6 Compose-Creation-DSL is out of scope** (separate epic; PO mark). Server produces the *procedural*
  form only. No Builder API parallel to the receiver-lambda DSL (would weaken id-allocation, REM-90/96).
- **0-corpus long-tail (G3 bitmap-font, G6 macros, G7 host-actions, G8 attributes, G9 particles) not
  touched** — parked stays parked; no corpus consumer, no server use-case driver.

---

## 8. Slicing (impl, for dev-3)

- **S1 — disk helper + the anchor (the §2 proof; do this first).** `RcDiskWriter` in `:shared` `jvmMain`
  (okio write/read). `ServerCreationDiskConformanceTest` in `:shared` `jvmTest` per §3 (document(simple2)
  → write → **read back** → `assertContentEquals` vs `procedure_simple2` oracle; + stage-1 determinism;
  + stage-2 round-trip). **This slice alone closes REM-126 §0 acceptance** — it proves the server byte
  path against the oracle. Everything after is surface ergonomics.
- **S2 — `:server` executable.** New module, `application` plugin, `main(args[0]=outPath)` → simple2
  replica → `RcDiskWriter.write`. `settings.gradle.kts` `include(":server")`. CLI-smoke test (JAR runs,
  non-empty file).
- **S3 — CI hook.** A Gradle task that runs S1's anchor (and optionally invokes the S2 JAR + diffs the
  output vs the fixture). PROJECT_CONTEXT §6 "not 'compiles'" equivalent: the server renders nothing, so
  there is no Maestro flow — the disk-read byte-diff in CI **is** the functional proof.

---

## 9. Verification (the "done" bar)

- **The gate:** §3 read-back-from-disk == `procedure_simple2` oracle, green (stage-3). Stages 1–2 green
  as supporting evidence.
- 173/173 corpus conformance + the 5 `CreationByteConformanceTest` stage-3 fixtures **unchanged**
  (§2 — additive proof; `git diff develop..BR -- 'shared/src/commonMain/**'` empty).
- `:server` JAR builds + runs headless + emits a non-empty `.rc` (CLI smoke).
- CI task runs the anchor deterministically. No render → no Maestro; the byte-diff is the proof.
- **assist GO is necessary-not-sufficient:** the byte-anchor is the proof here (unlike render tickets
  there is no separate tester render-gate — the server produces bytes, not pixels — so the in-CI
  read-back diff is both the gate and the evidence).

---

## 10. Deferred / out of scope (documented, not forgotten)

- Fixture-by-name CLI selection (must thread `profile` from author code, not a flag — §5.1).
- `procedure_simple1` as a fixture (needs its own decode→repro spike; simple2 chosen instead).
- Extending the anchor to the other 4 watchpoint fixtures (gradient1/center_text1/look_up1/
  text_path_effects) on the disk path — cheap once S1 lands (they're already in-memory-pinned), nice-to-have.
- A Compose-free creation-DSL extraction to slim the server JAR (§4.1 wiring bloat) — larger refactor.
- E6 Compose-Creation-DSL; 0-corpus long-tail; server render side (render is the mobile/REM-78/81 path);
  any REST/online API over `.rc` (an embedder builds that on top of this library).
- 🟡 **Carry-over §0 radar (from REM-119 review):** when the Creation-DSL is exercised harder on the
  server, the `nextId()` vs `cacheData()` (content-dedup) divergence for `textLookup`/`addIntegerExpression`
  becomes producible (no corpus divergence today — decode-cleared). Track if a server author emits
  duplicate-content docs.

## 11. assist verdict on the design
**GO on the design.** The de-risk premise is verified against the tree (DSL byte-true on JVM today; only
the disk path is new). The single most important lock is §3: the anchor reads back **from disk** vs the
corpus oracle, so the one new surface is actually proven and the pin is non-vacuous. §2 holds by
construction (commonMain untouched). Module split (§4) is dictated by the oracle's location and keeps the
byte-anchor where it belongs. The W2 profile-mismatch trap is fenced (§5.1). Wiring bloat (§4.1) is
flagged as non-blocking and explicitly out of scope. Impl is ungated (no S2b/S4-style dependency) — S1
can start immediately and alone satisfies §0 acceptance.
