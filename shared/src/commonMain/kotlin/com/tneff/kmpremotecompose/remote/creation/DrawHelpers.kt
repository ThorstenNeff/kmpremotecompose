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

import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawArc
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawCircle
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawLine
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawOval
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawRect
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawSector

/**
 * Procedural draw helpers (REM-86 / E2): one Kotlin function per `DRAW_*` op, [Number]-typed so
 * callers can pass `Int`/`Long`/`Double` literals without `.toFloat()` ceremony — the helpers convert
 * once at the seam and forward to the L1 byte-proven `Operation.write()` path. These ops carry **no
 * paint state**: emit `paint { … }` (see [RcPaint]) first to set the player's paint bundle for the
 * draws that follow.
 *
 * **id-allocation (E5 byte-contract).** None of the `DRAW_*` ops are id-bearing — they don't pull
 * from [IdAllocator]. Path-bearing draws (`drawPath`/`drawTweenPath`) reference existing path ids
 * but allocate none themselves (path id-allocation lives in `pathCreate`; see `PathBuilder.kt`).
 */

/** `DRAW_CIRCLE` — circle centred at ([centerX], [centerY]) with radius [radius]. */
fun RemoteComposeContext.drawCircle(centerX: Number, centerY: Number, radius: Number) {
    add(DrawCircle(centerX.toFloat(), centerY.toFloat(), radius.toFloat()))
}

/** `DRAW_RECT` — rectangle with edges [left], [top], [right], [bottom]. */
fun RemoteComposeContext.drawRect(left: Number, top: Number, right: Number, bottom: Number) {
    add(DrawRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat()))
}

/** `DRAW_OVAL` — oval inscribed in the rectangle [left], [top], [right], [bottom]. */
fun RemoteComposeContext.drawOval(left: Number, top: Number, right: Number, bottom: Number) {
    add(DrawOval(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat()))
}

/** `DRAW_LINE` — line from ([x1], [y1]) to ([x2], [y2]). */
fun RemoteComposeContext.drawLine(x1: Number, y1: Number, x2: Number, y2: Number) {
    add(DrawLine(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat()))
}

/**
 * `DRAW_ARC` — arc of the oval bounded by the rectangle [left], [top], [right], [bottom].
 * [startAngle] is the start angle in degrees (0° = 3 o'clock); [sweepAngle] is the swept angle in
 * degrees (positive sweeps clockwise — matching upstream / Canvas convention).
 */
fun RemoteComposeContext.drawArc(
    left: Number,
    top: Number,
    right: Number,
    bottom: Number,
    startAngle: Number,
    sweepAngle: Number,
) {
    add(
        DrawArc(
            left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(),
            startAngle.toFloat(), sweepAngle.toFloat(),
        ),
    )
}

/**
 * `DRAW_SECTOR` — filled pie slice of the oval bounded by [left], [top], [right], [bottom], from
 * [startAngle] (degrees) over [sweepAngle] (degrees). Same conventions as [drawArc].
 */
fun RemoteComposeContext.drawSector(
    left: Number,
    top: Number,
    right: Number,
    bottom: Number,
    startAngle: Number,
    sweepAngle: Number,
) {
    add(
        DrawSector(
            left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(),
            startAngle.toFloat(), sweepAngle.toFloat(),
        ),
    )
}
