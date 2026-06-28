/*
 * Copyright 2026 The KmpRemoteCompose Authors
 * Licensed under the Apache License, Version 2.0.
 */
package com.tneff.kmpremotecompose.remote.player

import com.tneff.kmpremotecompose.remote.core.operations.NamedVariable
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * REM-68 theme-palette core: NamedVariable→VariableSupport registers name→colorId; a host palette
 * resolves the name to the doc's color via setNamedColorOverride; the override wins over a ColorConstant
 * fallback on the same id (precedence flag); and a doc that registers no matching name is unaffected
 * (the no-regression invariant). Pure RemoteContext logic → commonTest (jvm-runnable, no Skiko).
 */
class NamedColorOverrideTest {
    private val RED = 0xFFFF0000.toInt()
    private val BLUE = 0xFF0000FF.toInt()
    private val ACCENT = "system_accent1_500"

    @Test
    fun setNamedColorOverride_resolvesNameToColor() {
        val ctx = RemoteContext()
        ctx.loadVariableName(ACCENT, 5, 0)
        ctx.setNamedColorOverride(ACCENT, RED)
        assertEquals(RED, ctx.getColor(5))
    }

    @Test
    fun oneName_multipleIds_dupProtected() {
        val ctx = RemoteContext()
        ctx.loadVariableName(ACCENT, 5, 0)
        ctx.loadVariableName(ACCENT, 5, 0) // duplicate id → must not double-register
        ctx.loadVariableName(ACCENT, 6, 0)
        ctx.setNamedColorOverride(ACCENT, RED)
        assertEquals(RED, ctx.getColor(5))
        assertEquals(RED, ctx.getColor(6))
    }

    @Test
    fun pendingPalette_appliesWhenNameRegistersLater() {
        val ctx = RemoteContext()
        ctx.setThemePaletteByName(mapOf(ACCENT to RED)) // palette set BEFORE the doc registers the name
        ctx.loadVariableName(ACCENT, 5, 0)
        assertEquals(RED, ctx.getColor(5))
    }

    @Test
    fun override_winsOverLaterColorConstant_namedThenConstant() {
        val ctx = RemoteContext()
        ctx.loadVariableName(ACCENT, 5, 0)
        ctx.setNamedColorOverride(ACCENT, RED) // theme override on id 5
        ctx.loadColor(5, BLUE) // a later ColorConstant fallback on the same id must NOT clobber
        assertEquals(RED, ctx.getColor(5))
    }

    @Test
    fun override_winsOverEarlierColorConstant_constantThenNamed() {
        val ctx = RemoteContext()
        ctx.loadColor(5, BLUE) // ColorConstant fallback first (document order)
        ctx.setThemePaletteByName(mapOf(ACCENT to RED))
        ctx.loadVariableName(ACCENT, 5, 0) // registering applies the pending override → wins
        assertEquals(RED, ctx.getColor(5))
    }

    @Test
    fun unrelatedDoc_noRegression() {
        val ctx = RemoteContext()
        ctx.setThemePaletteByName(mapOf(ACCENT to RED))
        // a doc with NO matching accent name: a plain ColorConstant + an unrelated named var
        ctx.loadColor(7, BLUE)
        ctx.loadVariableName("some_other_var", 8, 0) // name not in palette → no override
        assertEquals(BLUE, ctx.getColor(7)) // unchanged
        assertEquals(0, ctx.getColor(8)) // unset stays transparent (= today's behavior)
    }

    @Test
    fun namedVariableApply_wiresThroughToOverride() {
        val ctx = RemoteContext()
        ctx.setThemePaletteByName(mapOf(ACCENT to RED))
        NamedVariable(varId = 5, varType = 0, name = ACCENT).apply(ctx) // op-level apply (Phase A)
        assertEquals(RED, ctx.getColor(5))
    }

    @Test
    fun phaseAReRun_idempotent() {
        val ctx = RemoteContext()
        ctx.setThemePaletteByName(mapOf(ACCENT to RED))
        val op = NamedVariable(varId = 5, varType = 0, name = ACCENT)
        op.apply(ctx) // frame 1
        op.apply(ctx) // frame 2 (per-frame Phase-A re-run)
        assertEquals(RED, ctx.getColor(5))
    }
}
