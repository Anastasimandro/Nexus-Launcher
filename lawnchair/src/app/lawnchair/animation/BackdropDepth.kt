package app.lawnchair.animation

import android.view.View
import com.android.launcher3.statehandlers.DepthController

/**
 * Profundidad del fondo durante un lanzamiento: mientras la app se abre, el fondo de pantalla
 * hace un zoom out leve y se desenfoca; al volver al inicio recupera su estado.
 *
 * No dibuja nada por su cuenta: lleva un progreso de 0 a 1 con un [MotionDriver] propio (resorte,
 * redirigible conservando la velocidad) y se lo entrega al [DepthController] de Launcher3, que ya
 * sabe aplicar el zoom del fondo y el desenfoque de la ventana con una sola transacción por
 * fotograma y descarta los cambios imperceptibles. Respeta el ajuste "efecto de profundidad del
 * fondo": con él apagado el controlador ignora todo.
 *
 * Es independiente de la hoja del lanzamiento, así que funciona igual con el reveal del sistema
 * (sin Shizuku) que con la hoja propia.
 *
 * Solo se debe usar desde el hilo principal.
 */
class BackdropDepth(
    frameRateView: () -> View?,
    systemScale: () -> Float,
    private val depthController: () -> DepthController?,
) {

    /** Profundidad que se alcanza con el progreso al máximo (se fija al empezar cada entrada). */
    private var peak = 0f

    private val driver = MotionDriver(frameRateView, systemScale) { progress -> apply(progress) }

    /** El fondo se va hacia atrás: la app se está abriendo. */
    fun enter(profile: MotionProfile) {
        if (profile.depth <= 0f) {
            leave(profile)
            return
        }
        peak = profile.depth
        driver.animateTo(1f, profile.open, profile.speed)
    }

    /** El fondo vuelve a su sitio: la app se cerró o el lanzamiento no llegó a producirse. */
    fun leave(profile: MotionProfile) {
        if (!driver.isRunning && driver.progress == 0f) return
        // Sin rebote: un rebote del fondo se leería como parpadeo del desenfoque.
        driver.animateTo(0f, profile.homeReturn, profile.speed)
    }

    /** Deja el fondo en su estado normal sin animar. */
    fun reset() {
        driver.snapTo(0f)
    }

    private fun apply(progress: Float) {
        // El resorte puede pasarse de 0..1: el fondo no admite menos de nada ni más de lo previsto.
        val depth = (progress * peak).coerceIn(0f, 1f)
        depthController()?.launchDepth?.setValue(depth)
    }
}
