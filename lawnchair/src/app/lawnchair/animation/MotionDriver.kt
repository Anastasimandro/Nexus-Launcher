package app.lawnchair.animation

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.View
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

/**
 * Motor de progreso: lleva un valor entre 0 y 1 (por ejemplo "cuánto está abierta la app")
 * y avisa en cada fotograma. Todo lo visual se calcula a partir de ese progreso.
 *
 * - Se puede redirigir en cualquier momento (`animateTo` con otro destino): con resorte, la
 *   velocidad que llevaba se conserva, así que abrir y cerrar de golpe no produce saltos.
 * - Con resorte el progreso puede pasarse de 1 o bajar de 0 (rebote); quien dibuja lo admite.
 * - Mientras corre pide frecuencia de refresco alta sobre [frameRateView].
 * - No reserva memoria durante la animación: el resorte y los oyentes se crean una vez.
 *
 * Solo se debe usar desde el hilo principal.
 *
 * @param systemScale escala de animación del sistema; los resortes no la aplican solos.
 */
class MotionDriver(
    private val frameRateView: () -> View?,
    private val systemScale: () -> Float,
    private val onProgress: (Float) -> Unit,
) {

    var progress = 0f
        private set

    val isRunning: Boolean
        get() = spring.isRunning || timing != null

    private val holder = FloatValueHolder(0f)
    private val spring = SpringAnimation(holder)
    private var timing: ValueAnimator? = null
    private var onEnd: ((finished: Boolean) -> Unit)? = null
    private var boosting = false

    init {
        spring.setSpring(SpringForce(0f))
        // El umbral por defecto es 1 unidad: con un rango de 0 a 1 terminaría al instante.
        spring.setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE)
        spring.addUpdateListener { _, value, _ -> publish(value) }
        spring.addEndListener { _, canceled, _, _ ->
            if (!canceled) {
                publish(spring.spring.finalPosition)
                finish(true)
            }
        }
    }

    /** Coloca el progreso sin animar y detiene lo que hubiera en marcha. */
    fun snapTo(value: Float) {
        stop()
        holder.value = value
        publish(value)
    }

    /** Detiene la animación sin avisar a nadie y deja el progreso donde esté. */
    fun cancel() {
        stop()
    }

    /**
     * Anima hacia [target]. Si ya había una animación en marcha se redirige (con resorte,
     * conservando la velocidad) y a quien la pidió se le avisa con `finished = false`.
     *
     * @param tempo multiplicador de velocidad (normalmente `MotionProfile.speed`).
     */
    fun animateTo(
        target: Float,
        spec: MotionSpec,
        tempo: Float = 1f,
        onEnd: ((finished: Boolean) -> Unit)? = null,
    ) {
        val previous = this.onEnd
        this.onEnd = onEnd
        beginBoost()
        when (spec) {
            is MotionSpec.Spring -> animateSpring(target, spec, tempo)
            is MotionSpec.Timing -> animateTiming(target, spec, tempo)
        }
        previous?.invoke(false)
    }

    private fun animateSpring(target: Float, spec: MotionSpec.Spring, tempo: Float) {
        stopTiming()
        // La frecuencia natural es la raíz de la rigidez: para ir `t` veces más rápido hay
        // que multiplicarla por t al cuadrado.
        val speedUp = (tempo / systemScale().coerceAtLeast(MIN_SCALE)).coerceAtLeast(MIN_SCALE)
        val force = spring.spring
        force.setStiffness((spec.stiffness * speedUp * speedUp).coerceAtLeast(MIN_STIFFNESS))
        force.setDampingRatio(spec.dampingRatio.coerceAtLeast(0.05f))
        if (!spring.isRunning) {
            holder.value = progress
            spring.setStartVelocity(0f)
        }
        spring.animateToFinalPosition(target)
    }

    private fun animateTiming(target: Float, spec: MotionSpec.Timing, tempo: Float) {
        stopSpring()
        stopTiming()
        val animator = ValueAnimator.ofFloat(progress, target)
        // ValueAnimator ya aplica la escala del sistema por su cuenta.
        animator.duration = (spec.durationMs / tempo.coerceAtLeast(MIN_SCALE)).toLong().coerceAtLeast(1L)
        animator.interpolator = spec.interpolator
        animator.addUpdateListener { publish(it.animatedValue as Float) }
        animator.addListener(
            object : AnimatorListenerAdapter() {
                private var canceled = false

                override fun onAnimationCancel(animation: Animator) {
                    canceled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (canceled) return
                    if (timing === animation) timing = null
                    finish(true)
                }
            },
        )
        timing = animator
        animator.start()
    }

    private fun publish(value: Float) {
        progress = value
        onProgress(value)
    }

    private fun finish(finished: Boolean) {
        endBoost()
        val callback = onEnd
        onEnd = null
        callback?.invoke(finished)
    }

    private fun stop() {
        onEnd = null
        stopSpring()
        stopTiming()
        endBoost()
    }

    private fun stopSpring() {
        if (spring.isRunning) spring.cancel()
    }

    private fun stopTiming() {
        timing?.cancel()
        timing = null
    }

    private fun beginBoost() {
        if (boosting) return
        val view = frameRateView() ?: return
        boosting = true
        FrameRateBoost.acquire(view)
    }

    private fun endBoost() {
        if (!boosting) return
        boosting = false
        frameRateView()?.let(FrameRateBoost::release)
    }

    private companion object {
        const val MIN_SCALE = 0.01f
        const val MIN_STIFFNESS = 1f
    }
}
