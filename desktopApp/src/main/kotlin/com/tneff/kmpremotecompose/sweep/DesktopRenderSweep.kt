/*
 * Copyright 2026 The KmpRemoteCompose Authors
 * Licensed under the Apache License, Version 2.0.
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
import com.tneff.kmpremotecompose.remote.player.compose.deferredPaintTagsOf
import com.tneff.kmpremotecompose.remote.player.core.RemoteComposePlayer
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.player.core.renderOpaque
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.system.exitProcess

/**
 * REM-78 (Epic A/B Desktop Render Sweep Harness) — headless Compose-Desktop render of the 173-doc corpus
 * for the Desktop target. Mirrors the RemoteComposeApp render block but bypasses the LaunchedEffect
 * decode/router state so render(nanoTime=0) sees a fully painted frame.
 *
 * Capture is doc-native size (REM-51 px-exact, matches the iOS+Android reference goldens). Static
 * t=0 by default; per-doc &t pin via args for time-driven docs (PO 3-sweep-cross-time methodology).
 *
 * Usage (from repo root):
 *   ./gradlew :desktopApp:desktopRenderSweep
 *   ./gradlew :desktopApp:desktopRenderSweep --args="--priority"
 *   ./gradlew :desktopApp:desktopRenderSweep --args="--docs cube3d,bit_draw2 --t 0"
 *
 * Args:
 *   --priority           render only the bitmap/offscreen/opaque-sensitive subset (REM-75 gate)
 *   --docs a,b,c         render the named docs only (comma-separated, no .rc)
 *   --t SECONDS          static-time pin (default 0)
 *   --out DIR            output PNG dir (default: screenshots/reference/desktop)
 *   --rc DIR             corpus dir   (default: androidApp/src/main/assets/rc)
 *   --csv FILE           classification CSV (default: <out>/_sweep.csv)
 */
fun main(args: Array<String>) {
    val cfg = parseArgs(args)
    cfg.outDir.mkdirs()
    println("[REM-78] corpus=${cfg.rcDir.absolutePath}")
    println("[REM-78] out=${cfg.outDir.absolutePath}")
    println("[REM-78] t=${cfg.staticTime}")

    val docs = selectDocs(cfg)
    if (docs.isEmpty()) {
        System.err.println("[REM-78] no docs to render — abort.")
        exitProcess(2)
    }
    println("[REM-78] rendering ${docs.size} docs")

    Builtins.register()
    val rows = mutableListOf<String>()
    // REM-78 follow-up: deferredTags column = 3. dispatch≠render-Dimension
    // (op-level deferred/unsupported tags from GeometryPaintDelegate.deferredPaintTags,
    // surfaced via :shared compose/ComposeRenderHarness.deferredPaintTagsOf).
    rows += "doc,surfaceW,surfaceH,drawCount,status,deferredTags,note"
    var ok = 0
    var blank = 0
    var error = 0
    for ((i, name) in docs.withIndex()) {
        val rcFile = File(cfg.rcDir, "$name.rc")
        if (!rcFile.exists()) {
            println("[%3d/%3d] %-44s  MISSING".format(i + 1, docs.size, name))
            rows += "$name,,,,MISSING,,not in corpus dir"
            error++
            continue
        }
        val result = renderOne(rcFile.readBytes(), cfg.staticTime)
        val status = when {
            result.throwMsg != null -> "ERROR"
            result.drawCount == 0   -> "BLANK"
            else                    -> "RENDERS"
        }
        val note = result.throwMsg ?: ""
        val tagsCell = result.deferredTags.sorted().joinToString(";")
        if (result.pngBytes != null) {
            File(cfg.outDir, "$name.png").writeBytes(result.pngBytes)
        }
        println(
            "[%3d/%3d] %-44s  %-8s  %4d x %-4d  draws=%-6d  defer=%-30s  %s".format(
                i + 1, docs.size, name, status, result.width, result.height, result.drawCount,
                tagsCell.take(30), note.take(40),
            ),
        )
        rows += listOf(
            name, result.width.toString(), result.height.toString(),
            result.drawCount.toString(), status, csvEscape(tagsCell), csvEscape(note),
        ).joinToString(",")
        when (status) {
            "RENDERS" -> ok++
            "BLANK"   -> blank++
            "ERROR"   -> error++
        }
    }
    cfg.csvOut.writeText(rows.joinToString("\n") + "\n")
    println("[REM-78] DONE — RENDERS=$ok  BLANK=$blank  ERROR=$error  csv=${cfg.csvOut.absolutePath}")
}

private data class RenderResult(
    val width: Int,
    val height: Int,
    val drawCount: Int,
    val deferredTags: Set<String>,
    val pngBytes: ByteArray?,
    val throwMsg: String?,
)

private fun renderOne(rcBytes: ByteArray, staticTime: Float): RenderResult {
    val doc: RemoteComposeDocument = try {
        DocumentReader.inflate(rcBytes)
    } catch (t: Throwable) {
        return RenderResult(0, 0, 0, emptySet(), null, "decode: ${t.message ?: t::class.simpleName}")
    }
    val w = if (doc.width > 0) doc.width else 500
    val h = if (doc.height > 0) doc.height else 500
    val ctx = RemoteContext().apply {
        setDensity(1f)
        animationEnabled = false
    }
    var thrown: String? = null
    var paintContextRef: ComposePaintContext? = null
    val scene = ImageComposeScene(width = w, height = h, density = Density(1f)) {
        RenderDocCanvas(doc, ctx, w, h, staticTime, { pc -> paintContextRef = pc }) { thrown = it }
    }
    return try {
        val skiaImage = scene.render(nanoTime = 0L)
        val png = skiaImage.encodeToData(EncodedImageFormat.PNG)?.bytes
        val tags = paintContextRef?.let(::deferredPaintTagsOf).orEmpty()
        RenderResult(w, h, ctx.drawCount, tags, png, thrown)
    } catch (t: Throwable) {
        val tags = paintContextRef?.let(::deferredPaintTagsOf).orEmpty()
        RenderResult(w, h, ctx.drawCount, tags, null, "render: ${t.message ?: t::class.simpleName}")
    } finally {
        scene.close()
    }
}

@Composable
private fun RenderDocCanvas(
    doc: RemoteComposeDocument,
    ctx: RemoteContext,
    pxW: Int,
    pxH: Int,
    staticTime: Float,
    onPaintContext: (ComposePaintContext) -> Unit,
    onThrow: (String) -> Unit,
) {
    val fontResolver = LocalFontFamilyResolver.current
    Canvas(Modifier.pxSize(pxW, pxH)) {
        val canvas = drawContext.canvas
        try {
            renderOpaque(canvas, size.width.toInt(), size.height.toInt(), 0xFFFFFFFF.toInt()) { target ->
                val pc = composePaintContextWithGeometry(ctx, target, fontResolver)
                // Hand the harness a ref so it can read deferredPaintTags from pc.geometry after paint.
                onPaintContext(pc)
                RemoteComposePlayer(ctx).paint(
                    doc, pc,
                    frameTimeSeconds = 0f,
                    staticTimeSeconds = staticTime,
                )
            }
        } catch (t: Throwable) {
            onThrow("paint: ${t.message ?: t::class.simpleName}")
        }
    }
}

private fun Modifier.pxSize(width: Int, height: Int): Modifier = layout { measurable, _ ->
    val placeable = measurable.measure(Constraints.fixed(width, height))
    layout(width, height) { placeable.place(0, 0) }
}

// ---- args + doc selection ----

private data class Config(
    val rcDir: File,
    val outDir: File,
    val csvOut: File,
    val staticTime: Float,
    val priorityOnly: Boolean,
    val explicitDocs: List<String>?,
)

private val PRIORITY_DOCS = listOf(
    // Bitmap-decoding (ImageDecode.jvm): DRAW_BITMAP, DRAW_BITMAP_SCALED, DRAW_BITMAP_FONT_TEXT
    "demo_bitmap_drawing_bit_draw1",
    "demo_bitmap_drawing_bit_draw2",
    "hostile_actor1",
    "hostile_actor1_c",
    "bitmap_font_watch",
    "c_image",
    // Offscreen / matrix-composited (Offscreen.jvm flush): cube3d uses matrix + offscreen path
    "cube3d",
    // Controls — should render with current stubs (sanity that the harness itself works)
    "procedure_simple1",
    "basic_path",
    "all_path",
)

private fun parseArgs(args: Array<String>): Config {
    var rcDir = "androidApp/src/main/assets/rc"
    var outDir = "screenshots/reference/desktop"
    var csv: String? = null
    var t = 0f
    var priority = false
    var docs: List<String>? = null
    var i = 0
    while (i < args.size) {
        when (val a = args[i]) {
            "--rc"       -> { rcDir = args[++i] }
            "--out"      -> { outDir = args[++i] }
            "--csv"      -> { csv = args[++i] }
            "--t"        -> { t = args[++i].toFloat() }
            "--priority" -> { priority = true }
            "--docs"     -> { docs = args[++i].split(",").map { it.trim() }.filter { it.isNotEmpty() } }
            else         -> System.err.println("[REM-78] unknown arg: $a")
        }
        i++
    }
    val out = File(outDir)
    return Config(
        rcDir = File(rcDir),
        outDir = out,
        csvOut = csv?.let(::File) ?: File(out, "_sweep.csv"),
        staticTime = t,
        priorityOnly = priority,
        explicitDocs = docs,
    )
}

private fun selectDocs(cfg: Config): List<String> {
    cfg.explicitDocs?.let { return it }
    if (cfg.priorityOnly) return PRIORITY_DOCS
    return cfg.rcDir.listFiles { f -> f.isFile && f.name.endsWith(".rc") }
        ?.map { it.nameWithoutExtension }?.sorted().orEmpty()
}

private fun csvEscape(s: String): String =
    if (s.contains(',') || s.contains('"') || s.contains('\n')) {
        "\"" + s.replace("\"", "\"\"") + "\""
    } else s
