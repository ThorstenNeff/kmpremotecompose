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
package com.tneff.kmpremotecompose.remote.player

import com.tneff.kmpremotecompose.remote.core.operations.DataListIds
import com.tneff.kmpremotecompose.remote.core.operations.TextLookup
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.wire.WireTypes
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * REM-59 I2 — TEXT_LOOKUP resolves a label from an ID_LIST: `dataSet[index] → text-id → string`
 * (upstream `TextLookup.apply`). Chart labels (good_pie_chart/pie_chart2), the REM-53-I1b bonus.
 */
class TextLookupTest {

    @Test
    fun dataListIds_storesIdArray() {
        val ctx = RemoteContext()
        DataListIds(id = 2097195, ids = intArrayOf(44, 45, 46)).apply(ctx)
        assertEquals(46, ctx.getIdArray(2097195)?.get(2))
    }

    @Test
    fun textLookup_literalIndex_resolvesLabel() {
        val ctx = RemoteContext()
        DataListIds(2097195, intArrayOf(44, 45, 46)).apply(ctx)
        ctx.putText(44, "Android"); ctx.putText(45, "iOS"); ctx.putText(46, "Web")
        TextLookup(textId = 56, dataSet = 2097195, index = 1f).paint(ctx, NoOpPaintContext(ctx))
        assertEquals("iOS", ctx.getText(56), "dataSet[1] = id45 → 'iOS'")
    }

    @Test
    fun textLookup_varIndex_resolvesPerLoopIndex() {
        val ctx = RemoteContext()
        DataListIds(2097195, intArrayOf(44, 45, 46)).apply(ctx)
        ctx.putText(44, "Android"); ctx.putText(45, "iOS"); ctx.putText(46, "Web")
        ctx.loadFloat(50, 2f) // the loop index var
        TextLookup(56, 2097195, WireTypes.asNan(50)).paint(ctx, NoOpPaintContext(ctx))
        assertEquals("Web", ctx.getText(56), "index=var50=2 → dataSet[2]=id46 → 'Web'")
    }
}
