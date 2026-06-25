package com.tneff.kmpremotecompose.conformance

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RcCorpusReaderTest {

    /**
     * In-memory [CorpusIo] — Pfade relativ zu `rc-corpus/`. Vermeidet `okio-fakefilesystem`
     * (kotlinx-datetime-Konflikt mit compose-material3) und hält die Loader-Logik voll testbar.
     */
    private class FakeCorpusIo(
        private val files: Map<String, ByteArray>,
    ) : CorpusIo {
        override fun exists(relative: String): Boolean =
            files.containsKey(relative) || files.keys.any { it.startsWith("$relative/") }

        override fun readBytes(relative: String): ByteArray =
            files[relative] ?: throw okio.IOException("missing $relative")

        override fun readText(relative: String): String =
            (files[relative] ?: throw okio.IOException("missing $relative")).decodeToString()

        override fun listFiles(relativeDir: String): List<String> =
            files.keys
                .filter { it.startsWith("$relativeDir/") }
                .map { it.removePrefix("$relativeDir/") }
                .filter { it.isNotEmpty() && !it.contains('/') }
                .sorted()
    }

    private fun reader() = RcCorpusReader(
        FakeCorpusIo(
            mapOf(
                "procedure_simple1.rc" to byteArrayOf(0, 4, 0x8C.toByte(), 0, 1),
                RcCorpusReader.MANIFEST_FILE to (
                    "# id\ttargetName\tbytes\ttier\trole\tsourcePath\tgenerator\tsemantic\n" +
                        "F1\tprocedure_simple1.rc\t5\tP0\twriter-byte-equality\tsrc\tjvm-ref\tcircle\n"
                    ).encodeToByteArray(),
                "${RcCorpusReader.CORPUS_SUBDIR}/b.rc" to byteArrayOf(1),
                "${RcCorpusReader.CORPUS_SUBDIR}/a.rc" to byteArrayOf(2),
            ),
        ),
    )

    @Test
    fun readFixture_returnsBytes() {
        assertContentEquals(byteArrayOf(0, 4, 0x8C.toByte(), 0, 1), reader().readFixture("procedure_simple1.rc"))
    }

    @Test
    fun readFixture_missing_throws() {
        assertFailsWith<okio.IOException> { reader().readFixture("does_not_exist.rc") }
    }

    @Test
    fun manifest_parsesEntries() {
        val m = reader().manifest()
        assertEquals(1, m.size)
        assertEquals("F1", m[0].id)
        assertEquals(5, m[0].bytes)
    }

    @Test
    fun corpusNames_listsSortedRegularFiles() {
        assertEquals(listOf("a.rc", "b.rc"), reader().corpusNames())
    }

    @Test
    fun corpusNames_emptyWhenSubdirMissing() {
        val empty = RcCorpusReader(FakeCorpusIo(mapOf("only_a_file.rc" to byteArrayOf(0))))
        assertEquals(emptyList(), empty.corpusNames())
    }
}
