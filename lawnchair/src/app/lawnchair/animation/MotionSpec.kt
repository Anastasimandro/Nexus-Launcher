package app.lawnchair.animation

import android.view.animation.Interpolator
import android.view.animation.PathInterpolator

/**
 * Cómo se mueve algo en Nexus. Es la única descripción de movimiento que entiende el motor:
 * quien anima no sabe si por debajo hay un resorte o una curva, así que los ajustes y los
 * estilos prediseñados solo tienen que cambiar estos valores.
 */
sealed interface MotionSpec {

    /**
     * Resorte con masa 1. Se puede interrumpir y redirigir conservando la velocidad.
     *
     * @param stiffness rigidez: más alta = más rápido.
     * @param dampingRatio amortiguación: 1 = sin rebote, menos de 1 = rebota.
     */
    data class Spring(val stiffness: Float, val dampingRatio: Float) : MotionSpec

    /** Curva con duración fija. Sirve para reproducir exactamente la curva de otro sistema. */
    data class Timing(val durationMs: Long, val interpolator: Interpolator) : MotionSpec
}

/**
 * Valores de movimiento de todo el launcher. Es lo que luego tocarán los ajustes y los
 * estilos (Nexus, OxygenOS, iOS...): el resto del código solo lee de aquí.
 *
 * @param speed multiplicador global de velocidad (2 = el doble de rápido).
 */
data class MotionProfile(
    val speed: Float = 1f,
    val open: MotionSpec,
    val close: MotionSpec,
    val homeReturn: MotionSpec,
) {
    companion object {
        // Misma curva que `nexus_open.xml`: arranque rápido y frenada muy suave.
        private val OPEN_CURVE = PathInterpolator(0.15f, 0.1f, 0.15f, 1f)

        /**
         * Perfil por defecto. Con estos resortes la apertura llega al 90 % en unos 230 ms y se
         * asienta en unos 350 ms, muy cerca de la curva de 400 ms que usábamos antes.
         */
        val Nexus = MotionProfile(
            open = MotionSpec.Spring(stiffness = 300f, dampingRatio = 0.95f),
            close = MotionSpec.Spring(stiffness = 340f, dampingRatio = 0.9f),
            homeReturn = MotionSpec.Spring(stiffness = 260f, dampingRatio = 1f),
        )

        /** La versión anterior, basada en curvas de duración fija. */
        val Classic = MotionProfile(
            open = MotionSpec.Timing(400L, OPEN_CURVE),
            close = MotionSpec.Timing(400L, OPEN_CURVE),
            homeReturn = MotionSpec.Timing(380L, OPEN_CURVE),
        )
    }
}
