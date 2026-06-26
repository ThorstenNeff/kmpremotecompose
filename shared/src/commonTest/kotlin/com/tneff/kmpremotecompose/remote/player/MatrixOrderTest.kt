package com.tneff.kmpremotecompose.remote.player
import com.tneff.kmpremotecompose.remote.player.core.MatrixOperations
import com.tneff.kmpremotecompose.remote.wire.WireTypes
import kotlin.test.Test
import kotlin.test.assertEquals
/**
 * REM-37 cube3d — ORDER-SENSITIVE matrix composition (assist review). A non-commuting pair
 * [ROT_Z(90), TRANSLATE_X(10)] applied to the origin distinguishes post- vs pre-multiply:
 * upstream Matrix.rotateX does `multiply(this, R)` with `multiply(a,b)=a×b` ⇒ this=this×R (POST),
 * so the built matrix is Rz·T(10) and origin → (0,10,0). Pre-multiply (T·Rz) would give (10,0,0).
 */
class MatrixOrderTest {
    private fun op(n: Int) = WireTypes.asNan(MatrixOperations.OFFSET + n)
    @Test fun composition_isPostMultiply_matchingUpstream() {
        // [90, ROT_Z(op4), 10, TRANSLATE_X(op5)]
        val m = MatrixOperations.eval(floatArrayOf(90f, op(4), 10f, op(5)))
        val out = FloatArray(3)
        MatrixOperations.multiplyVec(m, floatArrayOf(0f, 0f, 0f), out)
        assertEquals(0f, out[0], 1e-3f, "origin.x after Rz·T(10) (post-multiply, upstream)")
        assertEquals(10f, out[1], 1e-3f, "origin.y = 10 (Rz rotates T's +x onto +y) — post-multiply")
        assertEquals(0f, out[2], 1e-3f, "origin.z")
    }
}
