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
    private fun integrate(stiffness: Float, damping: Float, seconds: Double, startVelocity: Double = 0.0): Sample {
        val k = stiffness.toDouble()
        val c = 2.0 * damping * kotlin.math.sqrt(k)
        val dt = 0.00002
        var x = 0.0
        var v = startVelocity
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

    // ---- Velocidad inicial: continuidad con el dedo al soltar

    @Test
    fun startVelocityMatchesNumericalIntegration() {
        for ((stiffness, damping, _) in cases) {
            for (v0 in listOf(0.5f, 2f, 6f)) {
                val curve = SpringCurve(stiffness, damping, 1f, startVelocity = v0)
                for (t in listOf(0.02, 0.1, 0.25, 0.5)) {
                    val reference = integrate(stiffness, damping, t, v0.toDouble())
                    assertEquals(
                        "posición k=$stiffness z=$damping v0=$v0 t=$t",
                        reference.position,
                        curve.valueAt(t.toFloat()).toDouble(),
                        3e-3,
                    )
                    assertEquals(
                        "velocidad k=$stiffness z=$damping v0=$v0 t=$t",
                        reference.velocity,
                        curve.velocityAt(t.toFloat()).toDouble(),
                        3e-2 * max(1.0, abs(reference.velocity)),
                    )
                }
            }
        }
    }

    @Test
    fun startsExactlyAtTheReleaseVelocity() {
        for ((stiffness, damping, _) in cases) {
            for (v0 in listOf(0f, 1f, 4f)) {
                val curve = SpringCurve(stiffness, damping, 1f, startVelocity = v0)
                assertEquals("k=$stiffness z=$damping", v0.toDouble(), curve.velocityAt(0f).toDouble(), 1e-3)
            }
        }
    }

    @Test
    fun fittedCurveSettlesInTheRequestedDurationAndKeepsTheVelocity() {
        val spec = MotionSpec(320f, 0.88f)
        for (duration in listOf(120L, 250L, 350L)) {
            for (perSecond in listOf(0f, 1f, 3f)) {
                val curve = SpringCurve.fitted(spec, duration, perSecond)
                assertTrue(
                    "duración pedida $duration obtenida ${curve.durationMs}",
                    abs(curve.durationMs - duration) <= duration * 0.05,
                )
                assertEquals(0f, curve.fractionAt(0f), 0f)
                assertEquals(1f, curve.fractionAt(1f), 0f)
                // Pendiente de la curva normalizada en el origen = velocidad del dedo × duración.
                val dt = 1e-3f
                val slope = curve.fractionAt(dt) / dt
                assertEquals(
                    "pendiente inicial d=$duration v=$perSecond",
                    perSecond * duration / 1000.0,
                    slope.toDouble(),
                    0.05 + 0.03 * perSecond,
                )
            }
        }
    }

    @Test
    fun releasedAtRestStartsWithoutAJump() {
        val curve = SpringCurve.fitted(MotionSpec(320f, 0.88f), 300L, 0f)
        assertTrue(curve.fractionAt(0.01f) < 0.01f)
    }

    @Test
    fun fasterReleaseCoversMoreGroundEarly() {
        val spec = MotionSpec(320f, 0.88f)
        val slow = SpringCurve.fitted(spec, 300L, 0.5f).fractionAt(0.1f)
        val fast = SpringCurve.fitted(spec, 300L, 4f).fractionAt(0.1f)
        assertTrue("slow=$slow fast=$fast", fast > slow)
    }

    @Test
    fun releaseVelocityOverridesNegativeAndAbsurdValues() {
        val negative = SpringCurve(320f, 0.88f, 1f, startVelocity = -5f)
        assertEquals(0.0, negative.velocityAt(0f).toDouble(), 1e-3)
        val absurd = SpringCurve(320f, 0.88f, 1f, startVelocity = 1e9f)
        assertTrue(absurd.durationMs < 5_000)
    }

    @Test
    fun realTimeFractionStartsAtZeroAndStaysAtOnceSettled() {
        for ((stiffness, damping, _) in cases) {
            val curve = SpringCurve(stiffness, damping)
            assertEquals(0f, curve.fractionAtSeconds(0f), 0f)
            assertEquals(0f, curve.fractionAtSeconds(-1f), 0f)
            assertEquals(1f, curve.fractionAtSeconds(curve.settleSeconds), 0f)
            assertEquals(1f, curve.fractionAtSeconds(curve.settleSeconds + 5f), 0f)
        }
    }

    @Test
    fun realTimeFractionIsTheSameCurveAsTheNormalizedOne() {
        val curve = SpringCurve(170f, 0.9f)
        for (fraction in listOf(0.1f, 0.35f, 0.6f, 0.9f)) {
            assertEquals(
                curve.fractionAt(fraction),
                curve.fractionAtSeconds(fraction * curve.settleSeconds),
                1e-5f,
            )
        }
    }

    @Test
    fun lateralSpringFinishesBeforeTheOpeningAndNeverBounces() {
        val profile = MotionProfile.Nexus
        val opening = SpringCurve.of(profile.open)
        val lateral = SpringCurve.of(profile.openLateral)
        assertTrue(
            "lateral=${lateral.durationMs} ms apertura=${opening.durationMs} ms",
            lateral.durationMs < opening.durationMs,
        )
        // Con la animación de la apertura como marco temporal, el eje lateral ya está en su sitio
        // mientras el principal aún se asienta, y nunca se pasa de largo (amortiguación crítica).
        var previous = 0f
        for (step in 0..100) {
            val seconds = step / 100f * opening.settleSeconds
            val value = lateral.fractionAtSeconds(seconds)
            assertTrue("retrocede en $seconds s: $previous -> $value", value >= previous - 1e-6f)
            assertTrue("rebota en $seconds s: $value", value <= 1f + 1e-6f)
            previous = value
        }
        assertEquals(1f, lateral.fractionAtSeconds(lateral.settleSeconds), 0f)
    }

    @Test
    fun lateralSpringTracksTheAospPathAfterItsSlowStart() {
        // La curva lateral de AOSP (app_open_x) es una Bézier por tramos; estos puntos están
        // evaluados de sus puntos de control. El resorte parte del reposo y no puede copiar su
        // arranque lento, así que la comparación se hace en el resto del recorrido.
        val aospX = listOf(0.15f to 0.476f, 0.2f to 0.841f, 0.3f to 0.927f, 0.5f to 0.976f)
        val opening = SpringCurve.of(MotionProfile.Nexus.open)
        val lateral = SpringCurve.of(MotionProfile.Nexus.openLateral)
        for ((fraction, expected) in aospX) {
            val actual = lateral.fractionAtSeconds(fraction * opening.settleSeconds)
            assertEquals("fracción $fraction", expected.toDouble(), actual.toDouble(), 0.15)
        }
    }
}
