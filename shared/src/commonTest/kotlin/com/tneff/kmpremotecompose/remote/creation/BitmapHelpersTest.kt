/*
 * Copyright 2026 The KmpRemoteCompose Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tneff.kmpremotecompose.remote.creation

import com.tneff.kmpremotecompose.remote.core.document.DocumentReader
import com.tneff.kmpremotecompose.remote.core.operations.BitmapData
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawBitmap
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawBitmapScaled
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * REM-90 (E3) — Bitmap-helper byte tests. `addBitmap` is region-0 id-bearing; `drawBitmap` /
 * `drawBitmapScaled` reference an existing id and don't allocate.
 */
class BitmapHelpersTest {

    private fun fakePngBytes(): ByteArray = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    @Test
    fun addBitmap_allocatesPlainId_andEmitsDataBitmap() {
        var id = -1
        val bytes = document(width = 100, height = 100, contentDescription = "Clock") {
            id = addBitmap(width = 32, height = 32, data = fakePngBytes())
        }
        assertEquals(43, id)
        val op = DocumentReader.inflate(bytes).operations.first { it is BitmapData } as BitmapData
        assertEquals(43, op.imageId)
        assertEquals(32, op.width); assertEquals(32, op.height)
        assertEquals(BitmapData.TYPE_PNG_8888, op.type)
        assertEquals(BitmapData.ENCODING_INLINE, op.encoding)
        assertTrue(op.data.contentEquals(fakePngBytes()))
    }

    @Test
    fun drawBitmap_emitsOp_withDestinationRect() {
        var bmpId = -1
        val bytes = document(width = 100, height = 100) {
            bmpId = addBitmap(width = 16, height = 16, data = fakePngBytes())
            drawBitmap(bmpId, left = 5, top = 10, right = 21, bottom = 26)
        }
        val op = DocumentReader.inflate(bytes).operations.first { it is DrawBitmap } as DrawBitmap
        assertEquals(bmpId, op.id)
        assertEquals(5f, op.left); assertEquals(10f, op.top)
        assertEquals(21f, op.right); assertEquals(26f, op.bottom)
        assertEquals(0, op.descriptionId)
    }

    @Test
    fun drawBitmapScaled_emitsOp_withScaleTypeAndDescription() {
        var bmpId = -1
        var descId = -1
        val bytes = document(width = 100, height = 100, contentDescription = "Clock") {
            bmpId = addBitmap(width = 16, height = 16, data = fakePngBytes())
            descId = addText("an icon")
            drawBitmapScaled(
                imageId = bmpId,
                srcLeft = 0, srcTop = 0, srcRight = 16, srcBottom = 16,
                dstLeft = 0, dstTop = 0, dstRight = 100, dstBottom = 100,
                scaleType = SCALE_BITMAP_FIT,
                contentDescriptionId = descId,
            )
        }
        val op = DocumentReader.inflate(bytes).operations.first { it is DrawBitmapScaled } as DrawBitmapScaled
        assertEquals(bmpId, op.imageId)
        assertEquals(SCALE_BITMAP_FIT, op.scaleType)
        assertEquals(1f, op.scaleFactor)
        assertEquals(descId, op.contentDescriptionId)
    }

    @Test
    fun bitmapHelpers_idBearingVsNot_followW3Contract() {
        document(width = 100, height = 100, contentDescription = "Clock") {
            assertEquals(43, ids.peek())
            val a = addBitmap(width = 4, height = 4, data = fakePngBytes()) // 43
            val b = addBitmap(width = 8, height = 8, data = fakePngBytes()) // 44
            assertEquals(45, ids.peek())
            drawBitmap(a, 0, 0, 4, 4)
            drawBitmapScaled(b, 0, 0, 8, 8, 0, 0, 100, 100)
            assertEquals(45, ids.peek(), "draws must not allocate ids")
        }
    }
}
