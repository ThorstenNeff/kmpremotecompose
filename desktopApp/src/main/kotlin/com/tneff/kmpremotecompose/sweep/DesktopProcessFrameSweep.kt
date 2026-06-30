/*
 * Copyright 2026 The KmpRemoteCompose Authors
 * Licensed under the Apache License, Version 2.0.
 *
 * REM-143-S2 3rd-Leg Process-Frame Render Harness — mirrors `ParticleGateHarness.captureFrames`
 * (dev-1 §5b multi-frame capture-side) exactly, then RENDERS a chosen keyframe to PNG. The data-
 * oracle convergence (Sim S2 == independent particle reconstruction) is dev-1's
 * `Rem143EvolutionGateTest` (committed to develop in the S2-merge). This harness is its PIXEL-side
 * complement: by mirroring the same capture pattern byte-for-byte (one-pin, frame-major, fresh-ctx-
 * per-frame), the rendered keyframes carry the oracle-validated particle state into Skia pixels.
 *
 * Capture pattern (PO routing 2026-06-29 23:04 — dev-1 canonical Δt-spec):
 *  - DT = 1/30s (≈0.033333335f) uniform for all 6 particle docs.
 *  - frameCount = MAX_FRAMES = 90 → 91 paints (k=0..90); startAt = 0 for all 6 ⇒ window [0.0..3.0]s.
 *  - Frame 0 = Δt=0 (seed; no evolve/compare). Frames k≥1 = Δt=1/30 (evolve + compare).
 *  - ONE `RpnFloatEvaluator.seedRngForCapture(PARTICLE_SEED)` ONCE before frame 0 — then the global
 *    RNG advances CONTINUOUSLY across all 91 paints (NOT re-pinned per frame/system). This mirrors
 *    the sim's single continuous RAND draw (frame-major across systems within each frame).
 *  - Fresh `RemoteContext` + `RemoteComposePlayer` per frame (live-app reality — Δt comes from the
 *    persistent OP-field `ImpulseStart.lastFrameTime`, NOT a per-frame-reset ctx field). The shared
 *    `doc` instance carries that op-field across frames.
 *  - `animationEnabled = true` (live mode → `runImpulse`'s Δt-from-lastFrameTime gating fires).
 *  - Multi-system docs (particle.rc = 2 systems): the player walks the doc-ops in source order, so
 *    per-frame the systems step frame-major (sys0 then sys1) sharing the single RNG — no extra wiring
 *    needed on this harness side beyond a single-paint-per-frame.
 *
 * Output is a SEPARATE dir from the static-render goldens (default `screenshots/process-frame/desktop/`)
 * → this harness NEVER promotes a static golden. The formal §6/REM-123 gate is dev-1's data-oracle
 * convergence (already at `99c3628`/`d399499` in the S2-merge); this harness's PNGs prove the pixel-
 * pipeline carries that oracle-validated state forward deterministically.
 *
 * Usage (from repo root):
 *   ./gradlew :desktopApp:desktopProcessFrameSweep
 *   ./gradlew :desktopApp:desktopProcessFrameSweep --args="--frame 0,45,90 --docs maze --out /tmp/x"
 *
 * Args:
 *   --docs a,b,c   render only the named docs (default: PARTICLE_DOCS = the 6 S2 docs)
 *   --frame N      capture frame index (0..90; default 90 = the END frame at t=3.0s). Comma-list
 *                  emits multiple keyframes per doc (e.g. `--frame 0,45,90` → seed/mid/end). The
 *                  shared 91-paint sequence is run ONCE per doc regardless of how many frames are
 *                  captured (RNG remains continuous across all 91 paints).
 *   --density F    device-density seed (default 1.0)
 *   --out DIR      output PNG dir (default: screenshots/process-frame/desktop)
 *   --rc DIR       corpus dir   (default: androidApp/src/main/assets/rc)
 *   --csv FILE     classification CSV (default: <out>/_process_sweep.csv)
 */
package com.tneff.kmpremotecompose.sweep

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import com.tneff.kmpremotecompose.remote.core.document.DocumentReader
import com.tneff.kmpremotecompose.remote.core.document.RemoteComposeDocument
import com.tneff.kmpremotecompose.remote.core.operations.Builtins
import com.tneff.kmpremotecompose.remote.player.compose.ComposePaintContext
import com.tneff.kmpremotecompose.remote.player.compose.composePaintContextWithGeometry
import com.tneff.kmpremotecompose.remote.player.core.RemoteComposePlayer
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.player.core.RenderRngPins
import com.tneff.kmpremotecompose.remote.player.core.RpnFloatEvaluator
import com.tneff.kmpremotecompose.remote.player.core.TouchState
import com.tneff.kmpremotecompose.remote.player.core.renderOpaque
import com.tneff.kmpremotecompose.remote.player.core.seedHostPalette
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/** dev-1 canonical Δt-spec: 30 fps fixed (mirrors `ParticleFrameSchedule.DT`). */
private const val DT_SECONDS: Float = 1f / 30f

/**
 * dev-1 canonical cap (mirrors `ParticleFrameSchedule.MAX_FRAMES`). All 6 particle-doc durations
 * (particle dur=10s / confetti=20s / hearts=7.9s / maze×3=20000s) exceed 3.0s ⇒ cap always applies,
 * giving a uniform 91-paint sequence (k=0..90 at t=0..3.0s).
 */
private const val FRAME_COUNT: Int = 90

/** Frame-0 PNGs at startAt=0 are the SEED frame — should byte-match the static-render goldens. */
private const val SEED_FRAME: Int = 0
/** Frame-90 PNGs at t=3.0s are the END frame — the deepest evolution within the dev-1 capture window. */
private const val END_FRAME: Int = 90

/** The 6 S2 docs that have ImpulseStart + ParticlesLoop (3 of which also have ParticlesCompare). */
private val PARTICLE_DOCS = listOf(
    "impulse_demo_confetti_demo",
    "impulse_demo_hearts_demo",
    "particle",
    "maze",
    "maze1",
    "maze2",
)

/**
 * REM-143 S3-Re-Confirm — doc-specific ImpulseStart.duration (seconds) for the 3 docs that realistically
 * elapse within a tractable schedule. The maze docs carry duration=20000s ⇒ never elapse in any window
 * we can paint — Re-Trigger is not exercisable on them and `--reTrigger` skips them (loudly).
 */
private val RETRIGGER_DURATION_S: Map<String, Float> = mapOf(
    "impulse_demo_confetti_demo" to 20.0f,
    "impulse_demo_hearts_demo" to 7.9f,
    "particle" to 10.0f,
)

fun main(args: Array<String>) {
    val cfg = parseArgs(args)
    cfg.outDir.mkdirs()
    println("[REM-143-S2-3rd-Leg] corpus=${cfg.rcDir.absolutePath}")
    println("[REM-143-S2-3rd-Leg] out=${cfg.outDir.absolutePath}  density=${cfg.density}")
    println("[REM-143-S2-3rd-Leg] DT=$DT_SECONDS  frameCount=$FRAME_COUNT  reTrigger=${cfg.reTrigger}  captureFrames=${cfg.captureFrames}")

    val baseDocs = cfg.explicitDocs ?: PARTICLE_DOCS
    val docs = if (cfg.reTrigger) baseDocs.filter { it in RETRIGGER_DURATION_S } else baseDocs
    if (cfg.reTrigger) {
        val skipped = baseDocs - docs.toSet()
        if (skipped.isNotEmpty()) println("[REM-143-S3-Re-Confirm] skipped (no realistic elapse window): $skipped")
    }
    println("[REM-143-S2-3rd-Leg] rendering ${docs.size} docs (${if (cfg.reTrigger) "Re-Trigger schedule" else "frame-major one-pin"})")

    Builtins.register()
    val rows = mutableListOf<String>()
    rows += "doc,frame,t,surfaceW,surfaceH,drawCount,status,note"
    var ok = 0; var blank = 0; var error = 0
    for ((i, name) in docs.withIndex()) {
        val rcFile = File(cfg.rcDir, "$name.rc")
        if (!rcFile.exists()) {
            println("[%2d/%2d] %-36s  MISSING".format(i + 1, docs.size, name))
            rows += "$name,,,,,,MISSING,not in corpus dir"
            error++
            continue
        }
        val frames = if (cfg.reTrigger) {
            val duration = RETRIGGER_DURATION_S.getValue(name)
            renderRetriggerSequence(rcFile.readBytes(), duration, cfg.density)
        } else {
            renderProcessSequence(rcFile.readBytes(), cfg.captureFrames, cfg.density)
        }
        for (capture in frames) {
            val tag = capture.tag
            val outName = "${name}_$tag.png"
            val status = when {
                capture.result.throwMsg != null -> "ERROR"
                capture.result.drawCount == 0   -> "BLANK"
                else                            -> "RENDERS"
            }
            if (capture.result.pngBytes != null) {
                File(cfg.outDir, outName).writeBytes(capture.result.pngBytes)
            }
            val note = capture.result.throwMsg ?: ""
            println(
                "[%2d/%2d] %-44s  %-8s  %4d x %-4d  k=%-3d  t=%6.3fs  draws=%-4d  %s".format(
                    i + 1, docs.size, outName, status,
                    capture.result.width, capture.result.height,
                    capture.frameIndex, capture.tSeconds,
                    capture.result.drawCount, note.take(40),
                ),
            )
            rows += listOf(
                name, capture.frameIndex.toString(),
                "%.6f".format(capture.tSeconds),
                capture.result.width.toString(), capture.result.height.toString(),
                capture.result.drawCount.toString(), status, csvEscapeProcess(note),
            ).joinToString(",")
            when (status) { "RENDERS" -> ok++; "BLANK" -> blank++; "ERROR" -> error++ }
        }
    }
    cfg.csvOut.writeText(rows.joinToString("\n") + "\n")
    println("[REM-143-S2-3rd-Leg] DONE — RENDERS=$ok BLANK=$blank ERROR=$error  csv=${cfg.csvOut.absolutePath}")
}

private data class ProcessRenderResult(
    val width: Int, val height: Int, val drawCount: Int,
    val pngBytes: ByteArray?, val throwMsg: String?,
)

private data class FrameCapture(val frameIndex: Int, val result: ProcessRenderResult, val tag: String, val tSeconds: Float)

/**
 * Run the 91-paint sequence (k=0..[FRAME_COUNT]) ONCE for [rcBytes], capturing PNGs at the indices in
 * [captureFrames]. Decode-ONCE → ONE RNG pin → 91 paints (frame-major); non-capture frames are
 * painted-and-discarded so the RNG state advances identically across runs. The shared `doc` carries
 * `ImpulseStart.lastFrameTime` across paints; each paint creates a FRESH `RemoteContext` and
 * `RemoteComposePlayer` (mirrors `ParticleGateHarness.captureFrames`).
 */
private fun renderProcessSequence(
    rcBytes: ByteArray, captureFrames: List<Int>, density: Float,
): List<FrameCapture> {
    val doc: RemoteComposeDocument = try {
        DocumentReader.inflate(rcBytes)
    } catch (t: Throwable) {
        return captureFrames.map { FrameCapture(it, ProcessRenderResult(0, 0, 0, null, "decode: ${t.message ?: t::class.simpleName}"), "decode-err", 0f) }
    }
    val w = if (doc.width > 0) doc.width else 500
    val h = if (doc.height > 0) doc.height else 500
    val captureSet = captureFrames.toSet()

    // ONE pin before frame 0 — the global RNG then advances continuously across all 91 paints.
    RpnFloatEvaluator.seedRngForCapture(RenderRngPins.PARTICLE_SEED)

    val captures = ArrayList<FrameCapture>(captureFrames.size)
    for (k in 0..FRAME_COUNT) {
        val t = k * DT_SECONDS  // startAt=0 for all 6 particle docs per dev-1 spec
        if (k in captureSet) {
            val tag = if (k == SEED_FRAME) "seed" else if (k == END_FRAME) "end" else "f$k"
            captures += FrameCapture(k, paintAndCapture(doc, w, h, density, t, touch = null), tag, t)
        } else {
            paintDiscarded(doc, w, h, density, t, touch = null)
        }
    }
    // Preserve caller-requested order (e.g. --frame 90,0 prints end first).
    return captureFrames.mapNotNull { idx -> captures.firstOrNull { it.frameIndex == idx } }
}

/**
 * REM-143 S3-Re-Confirm — Tap-Injection @T → Re-Burst pixel-render (mirrors `Rem143S3RetriggerGateTest`'s
 * sim-side schedule). Decode-ONCE → ONE RNG pin → 4 phases:
 *   Phase 1 (k=0..[FRAME_COUNT], 91 paints at t=k·DT): standard EvolutionGate window. Captures: `seed`
 *           (k=0) for the static-render cross-id, `end` (k=90) for the deepest pre-elapse evolution.
 *   Phase 2 (elapse-jump, 1 paint at t = duration+0.5): `now > startAt+duration` → `runImpulse` hits the
 *           elapse branch → `ParticlesCreate.resetSeed()` + `lastFrameTime` cleared. NO body paint, NO RAND
 *           consumed (the elapse branch returns before any of that).
 *   Phase 3 (tap, 1 paint at t = duration+1.0 with `TouchState.down()`): `dispatchTouch` consumes the DOWN
 *           → `touchEventTime = tap_t` → id29 reloaded → impulse re-enters `[tap_t, tap_t+duration]`,
 *           `isInitialPass=true` → Phase-A's `ParticlesCreate.apply` re-seeds (consumes init RAND from
 *           CURRENT continuous RNG). Capture: `reburst`.
 *   Phase 4 (post-tap evolution, 10 paints at t = tap_t + k·DT): standard evolution from the new seed.
 *           Capture: `post10` (the 10th post-burst frame, t = tap_t + 10·DT).
 *
 * Position-level Sim==Recon for these 3 docs is gate-validated by `Rem143S3RetriggerGateTest` (3/3 GREEN);
 * this harness produces the matching PIXEL-render (oracle-implied via the EvolutionGate + Re-Trigger gate
 * chains). NOT promoted to goldens — purely visual evidence + per-frame draw-count classification.
 */
private fun renderRetriggerSequence(rcBytes: ByteArray, durationSeconds: Float, density: Float): List<FrameCapture> {
    val doc: RemoteComposeDocument = try {
        DocumentReader.inflate(rcBytes)
    } catch (t: Throwable) {
        return listOf(FrameCapture(0, ProcessRenderResult(0, 0, 0, null, "decode: ${t.message ?: t::class.simpleName}"), "decode-err", 0f))
    }
    val w = if (doc.width > 0) doc.width else 500
    val h = if (doc.height > 0) doc.height else 500
    val elapseT = durationSeconds + 0.5f
    val tapT = durationSeconds + 1.0f
    val captures = ArrayList<FrameCapture>(4)

    // ONE pin before frame 0 — continuous RNG across the WHOLE re-trigger schedule.
    RpnFloatEvaluator.seedRngForCapture(RenderRngPins.PARTICLE_SEED)

    // Phase 1: 91 baseline paints (k=0..FRAME_COUNT) at t=k·DT, no tap → touch=null
    for (k in 0..FRAME_COUNT) {
        val t = k * DT_SECONDS
        when (k) {
            SEED_FRAME -> captures += FrameCapture(k, paintAndCapture(doc, w, h, density, t, touch = null), "seed", t)
            END_FRAME  -> captures += FrameCapture(k, paintAndCapture(doc, w, h, density, t, touch = null), "preElapse_end", t)
            else       -> paintDiscarded(doc, w, h, density, t, touch = null)
        }
    }
    // Phase 2: elapse-jump → resetSeed() + lastFrameTime=NaN. No RAND consumed. Not captured (impulse silent).
    paintDiscarded(doc, w, h, density, elapseT, touch = null)
    // Phase 3: tap-injection → re-burst. touch.down() BEFORE paint so dispatchTouch consumes DOWN this frame.
    val touch = TouchState().also { it.down(150f, 150f) }
    captures += FrameCapture(FRAME_COUNT + 2, paintAndCapture(doc, w, h, density, tapT, touch = touch), "reburst", tapT)
    // Phase 4: 10 post-tap paints; capture the 10th (= "post10").
    for (k in 1..10) {
        val t = tapT + k * DT_SECONDS
        if (k == 10) {
            captures += FrameCapture(FRAME_COUNT + 2 + k, paintAndCapture(doc, w, h, density, t, touch = touch), "post10", t)
        } else {
            paintDiscarded(doc, w, h, density, t, touch = touch)
        }
    }
    return captures
}

private fun paintDiscarded(doc: RemoteComposeDocument, w: Int, h: Int, density: Float, frameTimeSeconds: Float, touch: TouchState?) {
    val ctx = RemoteContext().apply { setDensity(density); animationEnabled = true; seedHostPalette() }
    val scene = ImageComposeScene(width = w, height = h, density = Density(density)) {
        ProcessRenderDocCanvas(doc, ctx, w, h, frameTimeSeconds, touch, { /* discard */ }) { /* discard */ }
    }
    try { scene.render(nanoTime = 0L) } finally { scene.close() }
}

private fun paintAndCapture(doc: RemoteComposeDocument, w: Int, h: Int, density: Float, frameTimeSeconds: Float, touch: TouchState?): ProcessRenderResult {
    val ctx = RemoteContext().apply { setDensity(density); animationEnabled = true; seedHostPalette() }
    var thrown: String? = null
    var paintContextRef: ComposePaintContext? = null
    val scene = ImageComposeScene(width = w, height = h, density = Density(density)) {
        ProcessRenderDocCanvas(doc, ctx, w, h, frameTimeSeconds, touch, { pc -> paintContextRef = pc }) { thrown = it }
    }
    return try {
        val skiaImage = scene.render(nanoTime = 0L)
        val png = skiaImage.encodeToData(EncodedImageFormat.PNG)?.bytes
        ProcessRenderResult(w, h, ctx.drawCount, png, thrown)
    } catch (t: Throwable) {
        ProcessRenderResult(w, h, ctx.drawCount, null, "render: ${t.message ?: t::class.simpleName}")
    } finally { scene.close() }
}

@Composable
private fun ProcessRenderDocCanvas(
    doc: RemoteComposeDocument, ctx: RemoteContext, pxW: Int, pxH: Int, frameTimeSeconds: Float,
    touch: TouchState?,
    onPaintContext: (ComposePaintContext) -> Unit, onThrow: (String) -> Unit,
) {
    val fontResolver = LocalFontFamilyResolver.current
    Canvas(Modifier.pxSizeP(pxW, pxH)) {
        val canvas = drawContext.canvas
        try {
            renderOpaque(canvas, size.width.toInt(), size.height.toInt(), 0xFFFFFFFF.toInt()) { target ->
                val pc = composePaintContextWithGeometry(ctx, target, fontResolver)
                onPaintContext(pc)
                // Only frameTimeSeconds drives the active clock (animationEnabled=true). staticTimeSeconds
                // is the static-mode pin and is unused here; mirror `ParticleGateHarness.captureFrames`
                // (passes only frameTimeSeconds → staticTimeSeconds defaults to 0f).
                RemoteComposePlayer(ctx).paint(doc, pc, frameTimeSeconds = frameTimeSeconds, touchState = touch)
            }
        } catch (t: Throwable) {
            onThrow("paint: ${t.message ?: t::class.simpleName}")
        }
    }
}

private fun Modifier.pxSizeP(width: Int, height: Int): Modifier = layout { measurable, _ ->
    val placeable = measurable.measure(Constraints.fixed(width, height))
    layout(width, height) { placeable.place(0, 0) }
}

private data class ProcessConfig(
    val rcDir: File, val outDir: File, val csvOut: File,
    val density: Float, val explicitDocs: List<String>?, val captureFrames: List<Int>,
    val reTrigger: Boolean,
)

private fun parseArgs(args: Array<String>): ProcessConfig {
    var rcDir = "androidApp/src/main/assets/rc"
    var outDir = "screenshots/process-frame/desktop"
    var csv: String? = null
    var density = 1f
    var docs: List<String>? = null
    var captureFrames: List<Int> = listOf(END_FRAME)
    var reTrigger = false
    var i = 0
    while (i < args.size) {
        when (val a = args[i]) {
            "--rc"        -> { rcDir = args[++i] }
            "--out"       -> { outDir = args[++i] }
            "--csv"       -> { csv = args[++i] }
            "--density"   -> { density = args[++i].toFloat() }
            "--docs"      -> { docs = args[++i].split(",").map { it.trim() }.filter { it.isNotEmpty() } }
            "--reTrigger" -> { reTrigger = true }
            "--frame"     -> {
                captureFrames = args[++i].split(",").map { it.trim().toInt() }
                require(captureFrames.all { it in 0..FRAME_COUNT }) {
                    "--frame indices must lie in 0..$FRAME_COUNT (1/30s-step schedule)"
                }
            }
            else          -> System.err.println("[REM-143-S2-3rd-Leg] unknown arg: $a")
        }
        i++
    }
    val out = File(outDir)
    return ProcessConfig(
        rcDir = File(rcDir), outDir = out,
        csvOut = csv?.let(::File) ?: File(out, "_process_sweep.csv"),
        density = density, explicitDocs = docs, captureFrames = captureFrames, reTrigger = reTrigger,
    )
}

private fun csvEscapeProcess(s: String): String =
    if (s.contains(',') || s.contains('"') || s.contains('\n')) {
        "\"" + s.replace("\"", "\"\"") + "\""
    } else s
