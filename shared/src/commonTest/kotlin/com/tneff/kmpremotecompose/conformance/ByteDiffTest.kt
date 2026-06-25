package com.tneff.kmpremotecompose.conformance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class ByteDiffTest {

    @Test
    fun equalArrays_noDivergence() {
        val a = byteArrayOf(0, 1, 2, 3)
        val b = byteArrayOf(0, 1, 2, 3)
        assertNull(ByteDiff.firstDivergence(a, b))
        assertTrue(ByteDiff.equal(a, b))
    }

    @Test
    fun differAtIndex() {
        val a = byteArrayOf(0, 1, 2, 3)
        val b = byteArrayOf(0, 1, 9, 3)
        assertEquals(2, ByteDiff.firstDivergence(a, b))
        assertFalse(ByteDiff.equal(a, b))
    }

    @Test
    fun lengthMismatch_divergesAtShorterEnd() {
        val a = byteArrayOf(0, 1, 2)
        val b = byteArrayOf(0, 1, 2, 3, 4)
        assertEquals(3, ByteDiff.firstDivergence(a, b))
        assertEquals(3, ByteDiff.firstDivergence(b, a))
    }

    @Test
    fun hex_isLowercaseTwoDigit() {
        assertEquals("00", ByteDiff.hex(0))
        assertEquals("0e", ByteDiff.hex(0x0E))
        assertEquals("ff", ByteDiff.hex(0xFF.toByte()))
    }

    @Test
    fun hexWindow_marksCenterAndPadsOutOfRange() {
        val bytes = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte())
        val w = ByteDiff.hexWindow(bytes, center = 0, radius = 2)
        // indices -2,-1 out of range, 0 marked, 1,2 present
        assertEquals("-- -- [aa] bb cc", w)
    }
}
