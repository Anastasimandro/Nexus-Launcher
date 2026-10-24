package app.lawnchair.animation

/**
 * Un resorte con masa 1. No hay curvas de duración fija: todo el motor es físico, redirigible
 * en caliente y sin asignaciones por fotograma.
 *
 * @param stiffness rigidez: más alta = más rápido (la frecuencia natural es su raíz).
 * @param dampingRatio amortiguación: 1 = sin rebote, menos de 1 = rebote sutil.
 */
data class MotionSpec(
    val stiffness: Float,
    val dampingRatio: Float,
)

/**
 * Valores de movimiento de todo el launcher. El resto del código solo lee de aquí.
 *
 * @param speed multiplicador global de velocidad (2 = el doble de rápido).
 * @param openLateral resorte del movimiento horizontal del centro de la ventana al abrir. Va más
 *   rápido que [open] (que lleva la posición vertical, el tamaño y las esquinas), de modo que la
 *   ventana describe un arco en vez de una diagonal. Solo lo usa la apertura de Quickstep, donde
 *   cada propiedad es una función pura del tiempo; ver [OPEN_LATERAL_STIFFNESS].
 * @param depth cuánto se aleja el fondo con la app abierta, de 0 (nada) a 1 (profundidad
 *   máxima de Launcher3). Se mantiene bajo: el zoom del fondo debe ser sutil y su coste (una
 *   transacción de superficie por fotograma) no debe crecer con la gama del dispositivo.
 */
data class MotionProfile(
    val speed: Float = 1f,
    val open: MotionSpec,
    val openLateral: MotionSpec,
    val close: MotionSpec,
    val homeReturn: MotionSpec,
    val depth: Float = 0f,
) {
    companion object {
        /** Resorte de apertura. Sobre un rango 0..1 se asienta en ~450 ms. */
        const val OPEN_STIFFNESS = 170f
        const val OPEN_DAMPING = 0.90f

        /**
         * Resorte lateral de la apertura: amortiguación crítica (sin rebote horizontal) y rigidez
         * 800, que asienta en ~0,30 s frente a los ~0,45 s de [OPEN_STIFFNESS]. Sustituye a la curva
         * Bézier `app_open_x` que Quickstep traía de AOSP: un resorte desde el reposo no puede imitar
         * su arranque lento, así que se ajustó por mínimos cuadrados al resto de su recorrido (error
         * cuadrático medio del 6 % del trayecto; ver `MotionProfileTest`).
         *
         * Es un punto de partida técnico derivado de AOSP, **no una medida de OxygenOS**: hay que
         * ajustarlo viendo la animación en el móvil.
         */
        const val OPEN_LATERAL_STIFFNESS = 800f
        const val OPEN_LATERAL_DAMPING = 1f

        /** Resorte de cierre: mismo camino de vuelta al icono, algo más rápido y con rebote leve. */
        const val CLOSE_STIFFNESS = 320f
        const val CLOSE_DAMPING = 0.88f

        /** Vuelta del fondo de pantalla: sin rebote perceptible (un rebote se lee como parpadeo). */
        const val RETURN_STIFFNESS = 260f
        const val RETURN_DAMPING = 0.98f

        /** Zoom del fondo: sutil, apto para GPU de gama entrada. */
        const val NEXUS_DEPTH = 0.15f

        /**
         * Perfil por defecto. Si se cambia [OPEN_STIFFNESS] o [OPEN_DAMPING] hay que recalcular
         * `nexus_spring_open.xml`, que es la aproximación que usa la ventana de la app para moverse
         * al mismo ritmo que el icono.
         */
        val Nexus = MotionProfile(
            open = MotionSpec(stiffness = OPEN_STIFFNESS, dampingRatio = OPEN_DAMPING),
            openLateral = MotionSpec(stiffness = OPEN_LATERAL_STIFFNESS, dampingRatio = OPEN_LATERAL_DAMPING),
            close = MotionSpec(stiffness = CLOSE_STIFFNESS, dampingRatio = CLOSE_DAMPING),
            homeReturn = MotionSpec(stiffness = RETURN_STIFFNESS, dampingRatio = RETURN_DAMPING),
            depth = NEXUS_DEPTH,
        )
    }
}
