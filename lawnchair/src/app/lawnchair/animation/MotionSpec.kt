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
 * @param depth cuánto se aleja y se desenfoca el fondo con la app abierta, de 0 (nada) a 1
 *   (profundidad máxima de Launcher3). El zoom del fondo y el desenfoque salen de este único valor.
 */
data class MotionProfile(
    val speed: Float = 1f,
    val open: MotionSpec,
    val close: MotionSpec,
    val homeReturn: MotionSpec,
    val depth: Float = 0f,
) {
    companion object {
        // Misma curva que `nexus_open.xml`: arranque rápido y frenada muy suave.
        private val OPEN_CURVE = PathInterpolator(0.15f, 0.1f, 0.15f, 1f)

        /** Rigidez y amortiguación del resorte de apertura (ver [Nexus]). */
        const val OPEN_STIFFNESS = 170f
        const val OPEN_DAMPING = 0.95f

        /** Punto de partida del zoom y desenfoque del fondo: hay que afinarlo a ojo en el móvil. */
        const val NEXUS_DEPTH = 0.35f

        /**
         * Perfil por defecto. La apertura llega al 90 % en unos 280 ms y se asienta en unos
         * 450 ms (con 300 / 0,95 eran 210 y 340 ms: demasiado rápida). La animación de ventana
         * de la app (`nexus_app_enter` y `nexus_spring_open`) está calculada para este mismo
         * resorte: si se cambian estos valores hay que recalcular esas curvas.
         */
        val Nexus = MotionProfile(
            open = MotionSpec.Spring(stiffness = OPEN_STIFFNESS, dampingRatio = OPEN_DAMPING),
            close = MotionSpec.Spring(stiffness = 340f, dampingRatio = 0.9f),
            homeReturn = MotionSpec.Spring(stiffness = 260f, dampingRatio = 1f),
            depth = NEXUS_DEPTH,
        )

        /** La versión anterior, basada en curvas de duración fija. */
        val Classic = MotionProfile(
            open = MotionSpec.Timing(400L, OPEN_CURVE),
            close = MotionSpec.Timing(400L, OPEN_CURVE),
            homeReturn = MotionSpec.Timing(380L, OPEN_CURVE),
        )
    }
}
