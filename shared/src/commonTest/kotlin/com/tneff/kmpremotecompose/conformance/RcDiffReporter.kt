package com.tneff.kmpremotecompose.conformance

/**
 * REM-7 — **RcToString-Diff-Plumbing** (≙ TechSpec §3.3 Punkt 3 / Referenz `RcToString.java`).
 *
 * Macht eine Byte-Divergenz menschenlesbar: lokalisiert den ersten Unterschied, mappt ihn über die
 * Op-Spannen ([OpSpan], aus dem Reader-Debug-Dump §3.4) auf **welche Operation / welches Feld**
 * divergiert, und rendert ein Hex-Fenster beider Seiten.
 *
 * Die *Mechanik* (Lokalisieren + Op-Zuordnung + Rendern) gehört dev-2; der eigentliche verlustfreie
 * Binary↔JSON-Konverter ist Reader/QA-Sache und wird hier nur über [OpSpan] angebunden.
 */
object RcDiffReporter {

    /**
     * @param expected Golden-Bytes (Referenz)
     * @param actual   re-encodierte Bytes
     * @param spans    Op-Spannen des **expected**-Dokuments (aus dem Reader-Dump); leer erlaubt.
     */
    fun describe(
        expected: ByteArray,
        actual: ByteArray,
        spans: List<OpSpan> = emptyList(),
    ): String {
        val fd = ByteDiff.firstDivergence(expected, actual)
        if (fd == null) {
            return "IDENTISCH (${expected.size} Bytes)"
        }

        val sb = StringBuilder()
        sb.append("DIVERGENZ @ Offset ").append(fd)
        sb.append(" (0x").append(offsetHex(fd)).append(")\n")
        sb.append("  Größe: expected=").append(expected.size)
            .append(" actual=").append(actual.size).append('\n')

        val expByte = expected.getOrNullByte(fd)
        val actByte = actual.getOrNullByte(fd)
        sb.append("  expected=").append(expByte?.let { ByteDiff.hex(it) } ?: "<EOF>")
            .append("  actual=").append(actByte?.let { ByteDiff.hex(it) } ?: "<EOF>").append('\n')

        val span = spans.firstOrNull { it.contains(fd) }
        if (span != null) {
            val rel = fd - span.byteStart
            sb.append("  Operation: opcode=").append(span.opcode)
                .append(" [").append(span.byteStart).append("..").append(span.byteEnd).append(")")
                .append("  +").append(rel).append(" im Op\n")
            sb.append("  Felder: ").append(span.fields).append('\n')
        } else if (spans.isNotEmpty()) {
            sb.append("  Operation: <keine Op-Spanne deckt Offset ").append(fd)
                .append("> (Trailing/Header oder Dump unvollständig)\n")
        }

        sb.append("  expected ").append(ByteDiff.hexWindow(expected, fd)).append('\n')
        sb.append("  actual   ").append(ByteDiff.hexWindow(actual, fd))
        return sb.toString()
    }

    private fun ByteArray.getOrNullByte(i: Int): Byte? = if (i in indices) this[i] else null

    private fun offsetHex(v: Int): String {
        if (v == 0) return "0"
        val digits = "0123456789abcdef"
        var n = v
        val sb = StringBuilder()
        while (n > 0) {
            sb.append(digits[n and 0xF]); n = n ushr 4
        }
        return sb.reverse().toString()
    }
}
