package com.tneff.kmpremotecompose.conformance

/** Ergebnis eines Decode→Re-Encode-Byte-Vergleichs für ein einzelnes Fixture. */
data class ByteEqualityResult(
    val fixtureId: String,
    val matched: Boolean,
    val expectedSize: Int,
    val actualSize: Int,
    /** Offset des ersten divergierenden Bytes, oder `null` wenn identisch. */
    val firstDivergence: Int?,
    /** Menschenlesbarer Diff-Report (leer wenn identisch). */
    val report: String,
    /** Gesetzt, wenn Decode/Re-Encode eine Exception warf (statt eines Byte-Diffs). */
    val error: String? = null,
) {
    val ok: Boolean get() = matched && error == null
}

/** Aggregat eines Korpus-Laufs (P2: sammelt Fehler, **nicht** fail-fast). */
data class CorpusReport(
    val results: List<ByteEqualityResult>,
) {
    val total: Int get() = results.size
    val passed: Int get() = results.count { it.ok }
    val failures: List<ByteEqualityResult> get() = results.filter { !it.ok }
    val allPassed: Boolean get() = failures.isEmpty()

    fun summary(): String = buildString {
        append("Korpus: ").append(passed).append('/').append(total).append(" ok")
        if (failures.isNotEmpty()) {
            append(", ").append(failures.size).append(" Fehler:")
            failures.forEach { f ->
                append("\n  - ").append(f.fixtureId).append(": ")
                append(f.error ?: "Byte-Diff @ ${f.firstDivergence} (exp=${f.expectedSize} act=${f.actualSize})")
            }
        }
    }
}

/** Ein benanntes Bytes-Paar für Korpus-Läufe. */
data class NamedBytes(val name: String, val bytes: ByteArray) {
    // ByteArray braucht eigenes equals/hashCode für korrektes data-class-Verhalten.
    override fun equals(other: Any?): Boolean =
        this === other || (other is NamedBytes && name == other.name && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = 31 * name.hashCode() + bytes.contentHashCode()
}

/**
 * REM-7 — Decode→Re-Encode-Mechanik (TechSpec §3.2). Operiert über den [RcCodec]-Seam; die
 * eigentlichen Test-Assertions besitzen die Tester.
 *
 *  - [roundTrip] (≙ `BinaryRoundTripTest`): Selbstkonsistenz `bytes → ops → bytes` byte-gleich.
 *  - [writerByteEquality] (≙ `RemoteComposeComparisonTest`): `decode(golden) → reEncode` ==? `golden`.
 *    Mechanisch identischer Pfad — getrennte Benennung passend zu den Test-Methoden
 *    `roundTrip_*` und `writerByteEquality_*`.
 *  - [runCorpus]: über viele Fixtures, **sammelt** Ergebnisse (P2), wirft nicht beim ersten Diff.
 */
object ConformanceEngine {

    /** Selbstkonsistenz: `input → decode → reEncode` muss byte-gleich zu `input` sein. */
    fun roundTrip(fixtureId: String, input: ByteArray, codec: RcCodec): ByteEqualityResult =
        decodeReEncode(fixtureId, input, codec)

    /** Orakel-Pfad: re-encodiertes Golden muss byte-gleich zum Golden sein. */
    fun writerByteEquality(fixtureId: String, golden: ByteArray, codec: RcCodec): ByteEqualityResult =
        decodeReEncode(fixtureId, golden, codec)

    /** Kern: decode → opSpans (für Reporting) → reEncode → Byte-Vergleich. Exceptions werden gefangen. */
    private fun decodeReEncode(fixtureId: String, expected: ByteArray, codec: RcCodec): ByteEqualityResult {
        val readback = try {
            codec.decode(expected)
        } catch (t: Throwable) {
            return failure(fixtureId, expected, actualSize = 0, error = "decode: ${t.message ?: t::class.simpleName}")
        }
        val spans = try {
            readback.opSpans()
        } catch (t: Throwable) {
            emptyList()
        }
        val actual = try {
            readback.reEncode()
        } catch (t: Throwable) {
            return failure(fixtureId, expected, actualSize = 0, error = "reEncode: ${t.message ?: t::class.simpleName}")
        }

        val fd = ByteDiff.firstDivergence(expected, actual)
        return if (fd == null) {
            ByteEqualityResult(
                fixtureId = fixtureId,
                matched = true,
                expectedSize = expected.size,
                actualSize = actual.size,
                firstDivergence = null,
                report = "",
            )
        } else {
            ByteEqualityResult(
                fixtureId = fixtureId,
                matched = false,
                expectedSize = expected.size,
                actualSize = actual.size,
                firstDivergence = fd,
                report = RcDiffReporter.describe(expected, actual, spans),
            )
        }
    }

    private fun failure(fixtureId: String, expected: ByteArray, actualSize: Int, error: String) =
        ByteEqualityResult(
            fixtureId = fixtureId,
            matched = false,
            expectedSize = expected.size,
            actualSize = actualSize,
            firstDivergence = null,
            report = error,
            error = error,
        )

    /**
     * Läuft [writerByteEquality] über viele Fixtures und **sammelt** alle Ergebnisse (P2-Modus,
     * nicht fail-fast). Die Tester entscheiden danach, welche Tiers hart asserten.
     */
    fun runCorpus(fixtures: List<NamedBytes>, codec: RcCodec): CorpusReport =
        CorpusReport(fixtures.map { writerByteEquality(it.name, it.bytes, codec) })
}
