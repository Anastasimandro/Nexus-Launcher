package app.lawnchair.animation

import kotlin.math.abs
import kotlin.math.max
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comprueba la solución analítica del resorte contra una integración numérica independiente de la
 * ecuación `x'' = -k (x - 1) - c x'`, sin usar ninguna de las fórmulas de [SpringCurve].
 */
class SpringCurveTest {

    private data class Sample(val position: Double, val velocity: Double)

    /** Integración de Euler semi-implícito con paso de 20 µs: el error es muy inferior a 1e-3. */
    private fun integrate(stiffness: Float, damping: Float, seconds: Double): Sample {
        val k = stiffness.toDouble()
        val c = 2.0 * damping * kotlin.math.sqrt(k)
        val dt = 0.00002
        var x = 0.0
        var v = 0.0
        var t = 0.0
        while (t < seconds) {
            v += (-k * (x - 1.0) - c * v) * dt
            x += v * dt
            t += dt
        }
        return Sample(x, v)
    }

    private val cases = listOf(
        Triple(170f, 0.90f, 1f),
        Triple(320f, 0.88f, 1f),
        Triple(500f, 0.99f, 1f),
        Triple(260f, 1.00f, 1f),
        Triple(200f, 1.30f, 1f),
        Triple(120f, 0.55f, 1f),
    )

    @Test
    fun matchesNumericalIntegration() {
        for ((stiffness, damping, tempo) in cases) {
            val curve = SpringCurve(stiffness, damping, tempo)
            for (t in listOf(0.03, 0.08, 0.15, 0.3, 0.5)) {
                val reference = integrate(stiffness, damping, t)
                assertEquals(
                    "posición k=$stiffness z=$damping t=$t",
                    reference.position,
                    curve.valueAt(t.toFloat()).toDouble(),
                    2e-3,
                )
                assertEquals(
                    "velocidad k=$stiffness z=$damping t=$t",
                    reference.velocity,
                    curve.velocityAt(t.toFloat()).toDouble(),
                    2e-2 * max(1.0, abs(reference.velocity)),
                )
            }
        }
    }

    @Test
    fun startsAtZeroAndEndsExactlyAtOne() {
        for ((stiffness, damping, tempo) in cases) {
            val curve = SpringCurve(stiffness, damping, tempo)
            assertEquals(0f, curve.fractionAt(0f), 0f)
            assertEquals(0f, curve.fractionAt(-1f), 0f)
            assertEquals(1f, curve.fractionAt(1f), 0f)
            assertEquals(1f, curve.fractionAt(2f), 0f)
            assertTrue(curve.durationMs > 0)
        }
    }

    @Test
    fun staysWithinThresholdAfterSettling() {
        for ((stiffness, damping, tempo) in cases) {
            val curve = SpringCurve(stiffness, damping, tempo)
            var t = curve.settleSeconds
            while (t < curve.settleSeconds + 3f) {
                assertTrue(
                    "k=$stiffness z=$damping t=$t",
                    abs(1f - curve.valueAt(t)) <= SpringCurve.DEFAULT_THRESHOLD * 1.001f,
                )
                t += 0.01f
            }
            // Un milisegundo antes todavía no estaba asentado.
            assertTrue(abs(1f - curve.valueAt(curve.settleSeconds - 0.0015f)) > SpringCurve.DEFAULT_THRESHOLD * 0.99f)
        }
    }

    /** El primer pico de un resorte subamortiguado rebasa el destino en `exp(-πζ/√(1-ζ²))`. */
    @Test
    fun overshootMatchesClosedForm() {
        for ((stiffness, damping, tempo) in cases) {
            if (damping >= 0.999f) continue
            val curve = SpringCurve(stiffness, damping, tempo)
            var peak = 0f
            var t = 0f
            // El primer pico puede llegar después del instante de asentamiento (rebote menor que el umbral).
            while (t <= curve.settleSeconds + 1.5f) {
                peak = max(peak, curve.valueAt(t))
                t += 0.0002f
            }
            val z = damping.toDouble()
            val expected = kotlin.math.exp(-Math.PI * z / kotlin.math.sqrt(1.0 - z * z))
            assertEquals("k=$stiffness z=$damping", expected, (peak - 1f).toDouble(), 2e-4)
        }
    }

    @Test
    fun noBounceWhenCriticalOrOverdamped() {
        for ((stiffness, damping, tempo) in cases) {
            if (damping < 0.999f) continue
            val curve = SpringCurve(stiffness, damping, tempo)
            var previous = 0f
            var f = 0.001f
            while (f <= 1f) {
                val v = curve.fractionAt(f)
                assertTrue("k=$stiffness z=$damping f=$f no es monótona", v >= previous - 1e-5f)
                assertTrue("k=$stiffness z=$damping f=$f rebasa el destino", v <= 1.0005f)
                previous = v
                f += 0.001f
            }
        }
    }

    @Test
    fun tempoShortensDurationProportionally() {
        val normal = SpringCurve(170f, 0.9f, 1f)
        val double = SpringCurve(170f, 0.9f, 2f)
        assertEquals(normal.durationMs / 2.0, double.durationMs.toDouble(), 3.0)
    }

    @Test
    fun stiffnessSetsDuration() {
        val soft = SpringCurve(170f, 0.9f)
        val stiff = SpringCurve(320f, 0.9f)
        assertTrue(stiff.durationMs < soft.durationMs)
    }
}
