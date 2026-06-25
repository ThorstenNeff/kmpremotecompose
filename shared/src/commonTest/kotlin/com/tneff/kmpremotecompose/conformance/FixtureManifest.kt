package com.tneff.kmpremotecompose.conformance

/**
 * Ein Eintrag der `MANIFEST.tsv` — **Signatur fix vorgegeben von test-1** (PO-Relay 2026-06-25).
 * Spalten-Reihenfolge entspricht der TSV (siehe [ManifestParser.COLUMNS]).
 *
 * Die Manifest-*Daten* besitzen die Tester; dev-2 besitzt nur das **Parsing** dieser Struktur.
 */
data class FixtureEntry(
    val id: String,
    val targetName: String,
    val bytes: Int,
    val tier: String,
    val role: String,
    val sourcePath: String,
    val generator: String,
    val semantic: String,
)

/**
 * REM-7 — reiner Parser der `MANIFEST.tsv`. Keine IO (bekommt den Text); voll testbar.
 *
 * Format (mit test-1 final abzustimmen — hier provisorisch nach der gelieferten [FixtureEntry]-Signatur):
 *  - Tab-getrennt, **8 Spalten** in fester Reihenfolge.
 *  - Zeilen, die (nach Trim) mit `#` beginnen, sind Kommentare → übersprungen.
 *  - Leerzeilen → übersprungen.
 *  - `bytes` muss eine nicht-negative Ganzzahl sein.
 */
object ManifestParser {

    /** Spalten-Reihenfolge der TSV; dient Doku und der Header-Erzeugung durch die Tester. */
    val COLUMNS: List<String> = listOf(
        "id", "targetName", "bytes", "tier", "role", "sourcePath", "generator", "semantic",
    )

    private const val EXPECTED_COLS = 8

    /**
     * Parst den kompletten MANIFEST-Text in [FixtureEntry]s.
     * @throws IllegalArgumentException bei falscher Spaltenzahl oder ungültigem `bytes` (mit Zeilennummer).
     */
    fun parse(text: String): List<FixtureEntry> {
        val out = ArrayList<FixtureEntry>()
        // 1-basierte Zeilennummer für aussagekräftige Fehler.
        text.split('\n').forEachIndexed { idx, raw ->
            val lineNo = idx + 1
            val line = raw.trimEnd('\r')
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachIndexed

            val cols = line.split('\t')
            require(cols.size == EXPECTED_COLS) {
                "MANIFEST Zeile $lineNo: erwartet $EXPECTED_COLS Tab-Spalten, gefunden ${cols.size}: \"$line\""
            }
            val bytes = cols[2].trim().toIntOrNull()
            require(bytes != null && bytes >= 0) {
                "MANIFEST Zeile $lineNo: Spalte 'bytes' keine nicht-negative Ganzzahl: \"${cols[2]}\""
            }
            out += FixtureEntry(
                id = cols[0].trim(),
                targetName = cols[1].trim(),
                bytes = bytes,
                tier = cols[3].trim(),
                role = cols[4].trim(),
                sourcePath = cols[5].trim(),
                generator = cols[6].trim(),
                semantic = cols[7].trim(),
            )
        }
        return out
    }
}
