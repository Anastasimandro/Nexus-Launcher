package app.lawnchair.animation

import android.view.animation.Interpolator

/**
 * Adapta una [SpringCurve] a un `Interpolator` de Android. No reserva memoria al evaluarse, así que
 * se crea una vez y se reutiliza en todos los fotogramas.
 */
class SpringInterpolator(private val curve: SpringCurve) : Interpolator {

    override fun getInterpolation(input: Float): Float = curve.fractionAt(input)
}

/**
 * Como [SpringInterpolator], pero la entrada es la fracción de una animación de [spanMs] ms y el
 * resorte corre en tiempo real: si se asienta antes de que acabe la animación, se queda en 1.
 * Es lo que permite un eje (el horizontal) más rápido que el resto dentro de un mismo animador.
 */
class TimedSpringInterpolator(private val curve: SpringCurve, spanMs: Long) : Interpolator {

    private val spanSeconds = spanMs.coerceAtLeast(1L) / MS_PER_SECOND

    override fun getInterpolation(input: Float): Float = curve.fractionAtSeconds(input * spanSeconds)

    private companion object {
        const val MS_PER_SECOND = 1000f
    }
}
