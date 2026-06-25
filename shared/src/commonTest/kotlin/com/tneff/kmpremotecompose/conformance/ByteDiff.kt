package com.tneff.kmpremotecompose.conformance

/**
 * REM-7 — reine Byte-Vergleichs-Mechanik. Keine IO, keine Reader/Writer-Abhängigkeit; voll testbar.
 * Grundlage des Assertion-Helfers der Tester (`assertRcBytesEqual`) und von [RcDiffReporter].
 */
object ByteDiff {

    /**
     * Index des **ersten** divergierenden Bytes zweier Arrays.
     *
     * - Gleich lang & inhaltsgleich → `null`.
     * - Unterscheiden sie sich an Index `i` → `i`.
     * - Sind sie bis zur kürzeren Länge gleich, aber unterschiedlich lang → `min(size)`
     *   (die Stelle, an der eines „ausgeht").
     */
    fun firstDivergence(a: ByteArray, b: ByteArray): Int? {
        val common = minOf(a.size, b.size)
        var i = 0
        while (i < common) {
            if (a[i] != b[i]) return i
            i++
        }
        return if (a.size == b.size) null else common
    }

    /** true, wenn beide Arrays byte-identisch sind (Länge + Inhalt). */
    fun equal(a: ByteArray, b: ByteArray): Boolean = firstDivergence(a, b) == null

    /** Ein einzelnes Byte als zweistelliges Hex (`0x` ohne Präfix), z. B. `0e`, `ff`. */
    fun hex(byte: Byte): String {
        val v = byte.toInt() and 0xFF
        val digits = "0123456789abcdef"
        return "${digits[v ushr 4]}${digits[v and 0xF]}"
    }

    /**
     * Hex-Fenster um [center] herum (`[center-radius, center+radius]`), je Byte durch Space getrennt,
     * das Byte an [center] in eckigen Klammern markiert. Out-of-range wird als `--` dargestellt.
     */
    fun hexWindow(bytes: ByteArray, center: Int, radius: Int = 8): String {
        val from = center - radius
        val to = center + radius
        val sb = StringBuilder()
        for (i in from..to) {
            if (i > from) sb.append(' ')
            val cell = if (i in bytes.indices) hex(bytes[i]) else "--"
            if (i == center) sb.append('[').append(cell).append(']') else sb.append(cell)
        }
        return sb.toString()
    }
}
