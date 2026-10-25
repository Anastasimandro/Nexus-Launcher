package app.lawnchair.animation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSpringParamsTest {

    @Test
    fun closeProfileKeepsItsValues() {
        val close = MotionProfile.Nexus.close
        val p = HomeSpringParams.fromSpec(close)
        assertEquals(close.stiffness, p.stiffness, 0f)
        assertEquals(close.dampingRatio, p.dampingRatio, 0f)
    }

    @Test
    fun closeDampingIsPointEightAndOpenIsUntouched() {
        assertEquals(0.80f, MotionProfile.CLOSE_DAMPING, 0f)
        assertEquals(0.80f, MotionProfile.Nexus.close.dampingRatio, 0f)
        assertEquals(170f, MotionProfile.Nexus.open.stiffness, 0f)
        assertEquals(0.90f, MotionProfile.Nexus.open.dampingRatio, 0f)
    }

    @Test
    fun customSpecPassesThroughUnchangedWhenValid() {
        val p = HomeSpringParams.fromSpec(MotionSpec(stiffness = 450f, dampingRatio = 0.965f))
        assertEquals(450f, p.stiffness, 0f)
        assertEquals(0.965f, p.dampingRatio, 0f)
    }

    @Test
    fun invalidValuesAreClampedSoSpringForceDoesNotThrow() {
        val low = HomeSpringParams.fromSpec(MotionSpec(stiffness = 0f, dampingRatio = -1f))
        assertTrue(low.stiffness > 0f)
        assertTrue(low.dampingRatio >= 0f)

        val high = HomeSpringParams.fromSpec(MotionSpec(stiffness = 1e9f, dampingRatio = 1e9f))
        assertTrue(high.stiffness <= 5000f)
        assertTrue(high.dampingRatio <= 10f)
    }

    @Test
    fun nonFiniteValuesFallBackToTheDefaultClose() {
        val close = MotionProfile.Nexus.close
        val nan = HomeSpringParams.fromSpec(MotionSpec(Float.NaN, Float.NaN))
        assertEquals(close.stiffness, nan.stiffness, 0f)
        assertEquals(close.dampingRatio, nan.dampingRatio, 0f)

        val inf = HomeSpringParams.fromSpec(MotionSpec(Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY))
        assertEquals(close.stiffness, inf.stiffness, 0f)
        assertEquals(close.dampingRatio, inf.dampingRatio, 0f)
    }

    @Test
    fun branchLabelsDistinguishBothBranches() {
        val on = HomeSpringParams.branchLabel(true)
        val off = HomeSpringParams.branchLabel(false)
        assertNotEquals(on, off)
        assertTrue(on.contains("=true"))
        assertTrue(off.contains("=false"))
    }
}
