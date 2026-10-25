package app.lawnchair.animation

/**
 * Parámetros de los resortes (x, y y escala) con los que la ventana de la app vuelve al icono al
 * soltar el gesto de inicio. Salen de un [MotionSpec] (normalmente `NexusMotion.profile.close`) y
 * no de los recursos de Launcher3, así los tres ejes comparten el mismo carácter de resorte.
 *
 * Es lógica pura: no depende de Android, se prueba en la JVM. `RectFSpringAnim` la llama una sola
 * vez al iniciar la animación, nunca por fotograma.
 */
class HomeSpringParams private constructor(
    @JvmField val stiffness: Float,
    @JvmField val dampingRatio: Float,
) {

    companion object {
        /** Mismos límites que usa [SpringCurve], para que ambos modelos acepten lo mismo. */
        private const val MIN_STIFFNESS = 1f
        private const val MAX_STIFFNESS = 5000f
        private const val MIN_DAMPING = 0.05f
        private const val MAX_DAMPING = 10f

        /** Etiquetas de la rama de `enableScalingRevealHomeAnimation`, para el registro. */
        const val BRANCH_SCALING_REVEAL = "enableScalingRevealHomeAnimation=true (resortes x/y)"
        const val BRANCH_FLING = "enableScalingRevealHomeAnimation=false (fling x/y)"

        /**
         * Convierte un [MotionSpec] en parámetros válidos para `SpringForce`, que lanza una
         * excepción con rigidez <= 0 o amortiguación negativa. Un valor no finito (NaN, infinito)
         * se sustituye por el de cierre por defecto.
         */
        @JvmStatic
        fun fromSpec(spec: MotionSpec): HomeSpringParams {
            val fallback = MotionProfile.Nexus.close
            val k = if (spec.stiffness.isFinite()) spec.stiffness else fallback.stiffness
            val z = if (spec.dampingRatio.isFinite()) spec.dampingRatio else fallback.dampingRatio
            return HomeSpringParams(
                stiffness = k.coerceIn(MIN_STIFFNESS, MAX_STIFFNESS),
                dampingRatio = z.coerceIn(MIN_DAMPING, MAX_DAMPING),
            )
        }

        /** Parámetros del perfil activo de Nexus. */
        @JvmStatic
        fun current(): HomeSpringParams = fromSpec(NexusMotion.profile.close)

        /** Nombre legible de la rama activa, para `NexusGesture`. */
        @JvmStatic
        fun branchLabel(scalingReveal: Boolean): String = if (scalingReveal) BRANCH_SCALING_REVEAL else BRANCH_FLING
    }
}
