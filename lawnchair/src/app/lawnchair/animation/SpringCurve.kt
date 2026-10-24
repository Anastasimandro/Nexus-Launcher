package app.lawnchair.animation

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt

/**
 * Respuesta exacta de un resorte amortiguado (masa 1) que va de 0 a 1, partiendo del reposo o con
 * la velocidad que llevaba el dedo al soltar ([startVelocity]).
 *
 * Sirve para llevar un resorte a las animaciones que ya tienen duración fija y las gestiona el
 * sistema (transiciones remotas de Quickstep): la curva que se aplica es la solución analítica del
 * mismo resorte que usa [MotionDriver], no una aproximación con Bézier. La duración es el instante
 * a partir del cual el resorte ya no se separa más de [threshold] de su destino.
 *
 * No depende de Android: se puede probar en la JVM.
 *
 * @param stiffness rigidez del resorte ([MotionSpec.stiffness]).
 * @param dampingRatio amortiguación ([MotionSpec.dampingRatio]): 1 = sin rebote.
 * @param tempo multiplicador de velocidad ([MotionProfile.speed]); 2 = el doble de rápido.
 * @param threshold distancia al destino (sobre un recorrido de 1) por debajo de la cual se da el
 *   resorte por asentado. 0,002 es la misma que usa [MotionDriver].
 * @param startVelocity velocidad inicial hacia el destino, en recorridos por segundo (1 = recorre
 *   todo el trayecto en un segundo). Con ella el movimiento continúa el del dedo sin tirón.
 */
class SpringCurve(
    stiffness: Float,
    dampingRatio: Float,
    tempo: Float = 1f,
    threshold: Float = DEFAULT_THRESHOLD,
    startVelocity: Float = 0f,
) {

    /** Velocidad inicial hacia el destino, acotada para que un valor erróneo no dispare el rebote. */
    private val v0: Double = startVelocity.toDouble().coerceIn(0.0, MAX_START_VELOCITY)

    /** Frecuencia natural en rad/s, con el tempo ya aplicado. */
    private val omega: Double = sqrt(stiffness.coerceAtLeast(MIN_STIFFNESS).toDouble()) *
        tempo.coerceAtLeast(MIN_TEMPO)

    private val zeta: Double = dampingRatio.toDouble().coerceIn(MIN_DAMPING, MAX_DAMPING)

    /** Frecuencia amortiguada (zeta < 1) o su análoga hiperbólica (zeta > 1); 0 si es crítico. */
    private val shape: Double = when {
        abs(zeta - 1.0) < CRITICAL_BAND -> 0.0
        zeta < 1.0 -> omega * sqrt(1.0 - zeta * zeta)
        else -> omega * sqrt(zeta * zeta - 1.0)
    }

    /**
     * Coeficiente del término seno/senh de la solución, que fija la velocidad inicial. Sin velocidad
     * inicial vale `ζω/s`; en el caso crítico es el coeficiente del término lineal (`ω - v0`).
     */
    private val carry: Double = when {
        shape == 0.0 -> omega - v0
        else -> (zeta * omega - v0) / shape
    }

    /** Segundos hasta que el resorte queda asentado. */
    val settleSeconds: Float

    /** Duración de la animación equivalente, en milisegundos (al menos 1). */
    val durationMs: Long

    /** Valor físico en el instante de asentamiento; se usa para que la curva acabe justo en 1. */
    private val endValue: Double

    init {
        val limit = threshold.coerceIn(MIN_THRESHOLD, MAX_THRESHOLD).toDouble()
        var lastOutside = 0.0
        var step = 1
        while (step <= MAX_SETTLE_STEPS) {
            val t = step * STEP_SECONDS
            if (abs(1.0 - raw(t)) > limit) lastOutside = t
            step++
        }
        val settle = lastOutside + STEP_SECONDS
        settleSeconds = settle.toFloat()
        durationMs = Math.round(settle * 1000.0).coerceAtLeast(1L)
        endValue = raw(settle)
    }

    /** Posición del resorte (0 → 1) pasados [seconds] segundos. Sin normalizar. */
    fun valueAt(seconds: Float): Float = raw(seconds.toDouble().coerceAtLeast(0.0)).toFloat()

    /** Velocidad del resorte (en unidades de recorrido por segundo) pasados [seconds] segundos. */
    fun velocityAt(seconds: Float): Float {
        val t = seconds.toDouble().coerceAtLeast(0.0)
        val decay = exp(-zeta * omega * t)
        // La velocidad es menos la derivada de la distancia al destino (ver [raw]).
        val drag = -zeta * omega
        val rate = when {
            shape == 0.0 -> decay * (carry - omega * (1.0 + carry * t))
            zeta < 1.0 -> decay * ((drag + shape * carry) * cos(shape * t) + (drag * carry - shape) * sin(shape * t))
            else -> decay * ((drag + shape * carry) * cosh(shape * t) + (drag * carry + shape) * sinh(shape * t))
        }
        return (-rate).toFloat()
    }

    /**
     * Curva normalizada para un animador de duración fija: [fraction] es el tiempo transcurrido
     * (0..1 de [durationMs]) y el resultado vale 0 al empezar y exactamente 1 al terminar.
     * Con una amortiguación menor que 1 puede superar 1 durante el rebote.
     */
    fun fractionAt(fraction: Float): Float {
        if (fraction <= 0f) return 0f
        if (fraction >= 1f) return 1f
        val value = raw(fraction.toDouble() * settleSeconds)
        return (value / endValue).toFloat()
    }

    /** Posición (0 → 1): 1 menos la distancia al destino, que es `e^(-ζωt)·(...)`. */
    private fun raw(t: Double): Double {
        if (t <= 0.0) return 0.0
        val decay = exp(-zeta * omega * t)
        val oscillation = when {
            shape == 0.0 -> 1.0 + carry * t
            zeta < 1.0 -> cos(shape * t) + carry * sin(shape * t)
            else -> cosh(shape * t) + carry * sinh(shape * t)
        }
        return 1.0 - decay * oscillation
    }

    companion object {
        const val DEFAULT_THRESHOLD = 0.002f

        /** Tope de la velocidad inicial: 50 recorridos por segundo, más que cualquier gesto real. */
        private const val MAX_START_VELOCITY = 50.0

        private const val MIN_STIFFNESS = 1f
        private const val MIN_TEMPO = 0.01f
        private const val MIN_DAMPING = 0.05
        private const val MAX_DAMPING = 10.0
        private const val MIN_THRESHOLD = 0.0001f
        private const val MAX_THRESHOLD = 0.05f

        /** Banda alrededor de 1 en la que se usa la fórmula del amortiguamiento crítico. */
        private const val CRITICAL_BAND = 1e-4

        /** Paso (1 ms) y límite (10 s) de la búsqueda del instante de asentamiento. */
        private const val STEP_SECONDS = 0.001
        private const val MAX_SETTLE_STEPS = 10_000

        fun of(spec: MotionSpec, tempo: Float = 1f): SpringCurve = SpringCurve(spec.stiffness, spec.dampingRatio, tempo)

        /**
         * Resorte de [spec] ajustado para que se asiente en [durationMs] (la duración que ya fija
         * el sistema) y arranque con [startVelocity] (recorridos por segundo): el movimiento
         * continúa el del dedo y termina a tiempo. La duración depende ligeramente de la velocidad
         * inicial, así que el ajuste se afina unas pocas veces.
         */
        fun fitted(spec: MotionSpec, durationMs: Long, startVelocity: Float): SpringCurve {
            val target = durationMs.coerceAtLeast(1L) / 1000.0
            var tempo = (SpringCurve(spec.stiffness, spec.dampingRatio).settleSeconds / target).toFloat()
            var curve = SpringCurve(spec.stiffness, spec.dampingRatio, tempo, startVelocity = startVelocity)
            repeat(FIT_PASSES) {
                tempo *= (curve.settleSeconds / target).toFloat()
                curve = SpringCurve(spec.stiffness, spec.dampingRatio, tempo, startVelocity = startVelocity)
            }
            return curve
        }

        private const val FIT_PASSES = 3
    }
}
