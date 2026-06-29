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
package com.tneff.kmpremotecompose.creation.compose

import com.tneff.kmpremotecompose.conformance.RcCorpus
import com.tneff.kmpremotecompose.remote.core.operations.Operations
import com.tneff.kmpremotecompose.remote.core.operations.layout.DimensionType
import com.tneff.kmpremotecompose.remote.creation.Profile
import com.tneff.kmpremotecompose.remote.creation.RcExpression
import com.tneff.kmpremotecompose.remote.creation.defaultRcPlatformServices
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * REM-144 S2 — Stage-2 byte-anchor for the higher-level RPN-DSL ([RcExpr]). **Reuses** the
 * `c_modifier_visibility.rc` corpus oracle (REM-141 T3) — the embedded FloatExpression RPN
 * `[asNan(1), 2.0, RcExpression.MOD]` decodes as exactly `CONTINUOUS_SEC % 2`, which the
 * high-level DSL must reproduce byte-for-byte.
 *
 * If the value-class boxing or the operator overloads reorder operands, repack NaN bits, or
 * emit different operator ids, this test fails — the same corpus that REM-141 anchored with
 * the FloatArray overload is now anchored with the high-level DSL.
 *
 * **W7 watchpoint:** NaN-bit preservation through `@JvmInline value class` boxing — the
 * `RcExpression.CONTINUOUS_SEC` constant is itself a NaN (rawBits `0xff800001`), and
 * `RcExpression.MOD` is a NaN (rawBits `0xffb10005`). If `RcExpr(rpn: FloatArray).rpn` lost
 * those bits, the test would fail at array byte 58 (the FloatExpression id) or one of the
 * RPN operand bytes.
 */
class RcExprAnchorTest {

    private val androidx: Profile = Profile(
        operationsProfiles = Operations.PROFILE_ANDROIDX,
        services = defaultRcPlatformServices(),
    )

    /**
     * Stage-2 corpus byte-anchor: high-level RPN-DSL produces byte-identical output to the
     * existing FloatArray-based REM-141 anchor (which itself byte-matches the corpus). Express
     * `CONTINUOUS_SEC % 2` through the operator overload and assert full-doc byte-match.
     */
    @Test
    fun stage2_rpnDsl_continuousSecMod2_matchesCModifierVisibilityOracle() = runBlocking {
        val expr: RcExpr = RcExpression.CONTINUOUS_SEC.rcVar() % 2f.rcLit()
        val produced = captureSingleRemoteDocument(
            width = 400, height = 400, profile = androidx, contentDescription = "",
        ) {
            RemoteRoot {
                val visSlot = rememberRemoteFloatSlot()
                RemoteFloatExpression(visSlot, expr)
                RemoteBoxLeaf(
                    modifier = RemoteModifier
                        .width(DimensionType.EXACT, 200f)
                        .height(DimensionType.EXACT, 200f)
                        .background(color = 0xffff0000.toInt())
                        .visibility(visSlot),
                )
            }
        }
        assertContentEquals(
            RcCorpus.readFixture("corpus/c_modifier_visibility.rc"),
            produced,
            "RPN-DSL (CONTINUOUS_SEC % 2) must byte-match c_modifier_visibility.rc — same corpus " +
                "the REM-141 FloatArray overload hit; high-level DSL must produce identical bytes.",
        )
    }

    /**
     * W7 belt-and-suspenders: NaN bit-pattern preservation through value-class boxing. The RPN
     * for `CONTINUOUS_SEC % 2` is exactly `[Float.fromBits(0xff800001), 2.0f, Float.fromBits(0xffb10005)]`.
     * The DSL must produce those exact bits without repack. Defends against signaling-NaN
     * rebanging in some toolchains (the classic §2 trap).
     */
    @Test
    fun rcExpr_preservesRawNaNBitsThroughValueClass() {
        val expr = RcExpression.CONTINUOUS_SEC.rcVar() % 2f.rcLit()
        assertEquals(3, expr.rpn.size, "RPN should be [continuousSec, 2.0, MOD]")
        assertEquals(0xff800001.toInt(), expr.rpn[0].toRawBits(), "operand 0 = asNan(1) = 0xff800001")
        assertEquals(2.0f.toRawBits(), expr.rpn[1].toRawBits(), "operand 1 = 2.0f literal")
        assertEquals(0xffb10005.toInt(), expr.rpn[2].toRawBits(), "operand 2 = MOD operator = asNan(0x310005) = 0xffb10005")
    }

    /**
     * Compose-DSL stage-1 equivalence: the high-level RPN-DSL and the lower-level
     * `FloatArray`-overload of `RemoteFloatExpression` produce byte-identical documents for
     * the same logical expression. Guarantees the @Composable RcExpr-overload is a thin
     * forwarder, no behavioural divergence.
     */
    @Test
    fun rpnDsl_byteIdenticalToFloatArrayOverload() = runBlocking {
        val viaDsl = captureSingleRemoteDocument(
            width = 400, height = 400, profile = androidx, contentDescription = "",
        ) {
            RemoteRoot {
                val slot = rememberRemoteFloatSlot()
                RemoteFloatExpression(slot, RcExpression.CONTINUOUS_SEC.rcVar() % 2f.rcLit())
                RemoteBoxLeaf(modifier = RemoteModifier.visibility(slot))
            }
        }
        val viaFloatArray = captureSingleRemoteDocument(
            width = 400, height = 400, profile = androidx, contentDescription = "",
        ) {
            RemoteRoot {
                val slot = rememberRemoteFloatSlot()
                RemoteFloatExpression(slot, value = floatArrayOf(
                    RcExpression.CONTINUOUS_SEC, 2f, RcExpression.MOD,
                ))
                RemoteBoxLeaf(modifier = RemoteModifier.visibility(slot))
            }
        }
        assertContentEquals(viaFloatArray, viaDsl, "RcExpr overload must be byte-identical to FloatArray overload.")
    }

    /**
     * Operator coverage smoke (Q7 close: ~10 common ops). Exercise each binary + unary +
     * ternary op once and verify the trailing operator id in the RPN matches the
     * `RcExpression.*` constant.
     */
    @Test
    fun operatorCoverage_appendsCorrectRpnOperatorIds() {
        val a = 1f.rcLit(); val b = 2f.rcLit(); val c = 3f.rcLit()
        assertOpTail(a + b, RcExpression.ADD)
        assertOpTail(a - b, RcExpression.SUB)
        assertOpTail(a * b, RcExpression.MUL)
        assertOpTail(a / b, RcExpression.DIV)
        assertOpTail(a % b, RcExpression.MOD)
        assertOpTail(a.sqrt(), RcExpression.SQRT)
        assertOpTail(a.abs(), RcExpression.ABS)
        assertOpTail(a.sin(), RcExpression.SIN)
        assertOpTail(a.cos(), RcExpression.COS)
        assertOpTail(a.clamp(b, c), RcExpression.CLAMP)
    }

    private fun assertOpTail(expr: RcExpr, opNan: Float) {
        assertTrue(expr.rpn.isNotEmpty(), "RPN must not be empty")
        assertEquals(
            opNan.toRawBits(), expr.rpn.last().toRawBits(),
            "Last RPN element must be the operator (raw NaN bits)",
        )
    }
}
