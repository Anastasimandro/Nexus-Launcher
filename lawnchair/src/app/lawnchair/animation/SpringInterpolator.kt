package app.lawnchair.animation

import android.view.animation.Interpolator

/**
 * Adapta una [SpringCurve] a un `Interpolator` de Android. No reserva memoria al evaluarse, así que
 * se crea una vez y se reutiliza en todos los fotogramas.
 */
class SpringInterpolator(private val curve: SpringCurve) : Interpolator {

    override fun getInterpolation(input: Float): Float = curve.fractionAt(input)
}
