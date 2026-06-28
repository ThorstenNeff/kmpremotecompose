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

import com.tneff.kmpremotecompose.remote.core.operations.BitmapData
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawBitmap
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawBitmapInt
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawBitmapScaled

/**
 * Bitmap helpers (REM-87 / E3). [addBitmap] is id-bearing (region-0); the draw helpers reference an
 * existing image id and are not id-bearing.
 *
 * **Platform encoding is upstream's responsibility.** [addBitmap] takes raw encoded bytes ([data]) +
 * the format ([type], one of [BitmapData.TYPE_PNG_8888] / [BitmapData.TYPE_PNG] / [BitmapData.TYPE_RAW8]
 * / [BitmapData.TYPE_RAW8888] / [BitmapData.TYPE_PNG_ALPHA_8]) + the storage [encoding]
 * ([BitmapData.ENCODING_INLINE] for embedded bytes, [BitmapData.ENCODING_EMPTY] for a `DRAW_TO_BITMAP`
 * render target). A `bitmapFromImage(...)` helper that runs platform-side encoding lives behind
 * [RcPlatformServices] (E4 / per-target actual); this helper is the byte-faithful entry point.
 */

/**
 * `DATA_BITMAP` — register a bitmap under a freshly allocated region-0 id. Returns the id.
 * Defaults to [BitmapData.TYPE_PNG_8888] / [BitmapData.ENCODING_INLINE] (the byte-identical case for
 * a naive width/height + raw-bytes payload).
 */
fun RemoteComposeContext.addBitmap(
    width: Int,
    height: Int,
    data: ByteArray,
    type: Int = BitmapData.TYPE_PNG_8888,
    encoding: Int = BitmapData.ENCODING_INLINE,
): Int {
    val id = ids.nextId()
    add(BitmapData(imageId = id, width = width, height = height, data = data, type = type, encoding = encoding))
    return id
}

/**
 * `DRAW_BITMAP` — blit the bitmap at [id] into the rectangle ([left], [top], [right], [bottom]).
 * [descriptionId] is the DATA_TEXT id of an accessibility content-description (0 = none, mirrors
 * upstream `RemoteComposeWriter.drawBitmap` default).
 */
fun RemoteComposeContext.drawBitmap(
    id: Int,
    left: Number,
    top: Number,
    right: Number,
    bottom: Number,
    descriptionId: Int = 0,
) {
    add(
        DrawBitmap(
            id = id,
            left = left.toFloat(),
            top = top.toFloat(),
            right = right.toFloat(),
            bottom = bottom.toFloat(),
            descriptionId = descriptionId,
        ),
    )
}

/**
 * `DRAW_BITMAP_SCALED` — blit the bitmap at [imageId] from `(srcLeft..srcRight, srcTop..srcBottom)`
 * into `(dstLeft..dstRight, dstTop..dstBottom)`, aspect-adjusted by [scaleType] (one of
 * [SCALE_BITMAP_NONE] / [SCALE_BITMAP_INSIDE] / [SCALE_BITMAP_FILL_WIDTH] / [SCALE_BITMAP_FILL_HEIGHT]
 * / [SCALE_BITMAP_FIT] / [SCALE_BITMAP_CROP] / [SCALE_BITMAP_FILL_BOUNDS] / [SCALE_BITMAP_FIXED_SCALE]).
 * [scaleFactor] is only used by [SCALE_BITMAP_FIXED_SCALE].
 */
fun RemoteComposeContext.drawBitmapScaled(
    imageId: Int,
    srcLeft: Number,
    srcTop: Number,
    srcRight: Number,
    srcBottom: Number,
    dstLeft: Number,
    dstTop: Number,
    dstRight: Number,
    dstBottom: Number,
    scaleType: Int = SCALE_BITMAP_FIT,
    scaleFactor: Number = 1f,
    contentDescriptionId: Int = 0,
) {
    add(
        DrawBitmapScaled(
            imageId = imageId,
            srcLeft = srcLeft.toFloat(), srcTop = srcTop.toFloat(),
            srcRight = srcRight.toFloat(), srcBottom = srcBottom.toFloat(),
            dstLeft = dstLeft.toFloat(), dstTop = dstTop.toFloat(),
            dstRight = dstRight.toFloat(), dstBottom = dstBottom.toFloat(),
            scaleType = scaleType,
            scaleFactor = scaleFactor.toFloat(),
            contentDescriptionId = contentDescriptionId,
        ),
    )
}

/**
 * `DRAW_BITMAP_INT` (REM-113) — blit the bitmap at [imageId] from int-coord source rectangle
 * `(srcLeft..srcRight, srcTop..srcBottom)` into int-coord destination rectangle
 * `(dstLeft..dstRight, dstTop..dstBottom)`. Mirrors upstream
 * `RemoteComposeBuffer.addDrawBitmap(int, int, int, ..., int)` →
 * `DrawBitmapInt.apply` (`DrawBitmapInt.java:152-175`).
 *
 * Wire is **10 big-endian ints** — `imageId, src{L,T,R,B}, dst{L,T,R,B}, cdId`. Unlike
 * [drawBitmap] / [drawBitmapScaled] which take floats, this op carries integer pixel
 * coordinates verbatim — useful for the bitmap-text + texture corpus that uses integer pixel
 * addressing in the source bitmap.
 *
 * [imageId] is a region-0 plain id allocated by a prior [addBitmap] (this helper does NOT
 * allocate). [contentDescriptionId] is the optional DATA_TEXT id for a11y (0 = none).
 */
fun RemoteComposeContext.drawBitmapInt(
    imageId: Int,
    srcLeft: Int,
    srcTop: Int,
    srcRight: Int,
    srcBottom: Int,
    dstLeft: Int,
    dstTop: Int,
    dstRight: Int,
    dstBottom: Int,
    contentDescriptionId: Int = 0,
) {
    add(
        DrawBitmapInt(
            imageId = imageId,
            srcLeft = srcLeft, srcTop = srcTop, srcRight = srcRight, srcBottom = srcBottom,
            dstLeft = dstLeft, dstTop = dstTop, dstRight = dstRight, dstBottom = dstBottom,
            cdId = contentDescriptionId,
        ),
    )
}

// Scale types (mirrors upstream `ImageScaling.SCALE_*`).
const val SCALE_BITMAP_NONE: Int = 0
const val SCALE_BITMAP_INSIDE: Int = 1
const val SCALE_BITMAP_FILL_WIDTH: Int = 2
const val SCALE_BITMAP_FILL_HEIGHT: Int = 3
const val SCALE_BITMAP_FIT: Int = 4
const val SCALE_BITMAP_CROP: Int = 5
const val SCALE_BITMAP_FILL_BOUNDS: Int = 6
const val SCALE_BITMAP_FIXED_SCALE: Int = 7
