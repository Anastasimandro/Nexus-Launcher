package app.lawnchair.animation

import android.view.View
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

/**
 * Destino del progreso sin boxing: `(Float) -> Unit` empaqueta un `java.lang.Float` en cada
 * fotograma; con una interfaz de primitivos el camino caliente queda libre de asignaciones.
 */
fun interface FloatSink {
    fun onValue(value: Float)
}

/**
 * Motor de progreso: lleva un valor (por ejemplo "cuánto está abierto el icono", de 0 a 1) y avisa
 * en cada fotograma. Todo lo visual se calcula a partir de ese progreso.
 *
 * - Un único [SpringAnimation] por driver, reutilizado durante toda la vida del objeto: no se
 *   crea nada al animar.
 * - [animateTo] redirige el objetivo en caliente conservando la velocidad residual del resorte,
 *   así que abrir y cerrar a medias no produce saltos ni reinicios de curva.
 * - Mientras corre pide frecuencia de refresco alta sobre [frameRateView] y la libera al terminar
 *   (recordando la vista exacta que la pidió, para no dejar la petición colgada).
 *
 * Solo se debe usar desde el hilo principal.
 *
 * @param systemScale escala de animación del sistema; los resortes no la aplican solos.
 */
class MotionDriver(
    private val frameRateView: () -> View?,
    private val systemScale: () -> Float,
    private val onProgress: FloatSink,
) {

    /** Se lee también desde el hilo de binder al sincronizar la transición remota. */
    @Volatile
    var progress = 0f
        private set

    /**
     * Velocidad actual del progreso, en unidades por segundo (positiva hacia 1). Sirve para que
     * otro resorte arranque con el mismo impulso y el traspaso no tenga un tirón de velocidad.
     */
    @Volatile
    var velocity = 0f
        private set

    val isRunning: Boolean
        get() = spring.isRunning

    private val holder = FloatValueHolder(0f)
    private val spring = SpringAnimation(holder)
    private var onEnd: ((finished: Boolean) -> Unit)? = null
    private var boostedView: View? = null

    init {
        spring.setSpring(SpringForce(0f))
        // El umbral por defecto es 1 unidad: con un rango de 0 a 1 terminaría al instante.
        spring.setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE)
        spring.addUpdateListener { _, value, v ->
            velocity = v
            publish(value)
        }
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
        velocity = 0f
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
     * @param startVelocity velocidad con la que arranca si no había nada en marcha; si el resorte
     *   ya corría se ignora y se conserva la suya, que es la real.
     */
    fun animateTo(
        target: Float,
        spec: MotionSpec,
        tempo: Float = 1f,
        startVelocity: Float = 0f,
        onEnd: ((finished: Boolean) -> Unit)? = null,
    ) {
        // El callback anterior se desengancha ANTES de arrancar: si redirige, no reentra en un
        // driver a medio configurar. Si él mismo lanza otra animación, se respeta la suya.
        val previous = this.onEnd
        this.onEnd = null
        previous?.invoke(false)
        if (this.onEnd == null) this.onEnd = onEnd

        beginBoost()
        // La frecuencia natural es la raíz de la rigidez: para ir `t` veces más rápido hay
        // que multiplicarla por t al cuadrado.
        val speedUp = (tempo / systemScale().coerceAtLeast(MIN_SCALE)).coerceAtLeast(MIN_SCALE)
        val force = spring.spring
        force.setStiffness((spec.stiffness * speedUp * speedUp).coerceAtLeast(MIN_STIFFNESS))
        force.setDampingRatio(spec.dampingRatio.coerceIn(0.05f, 1f))
        if (!spring.isRunning) {
            holder.value = progress
            spring.setStartVelocity(startVelocity)
        }
        spring.animateToFinalPosition(target)
    }

    private fun publish(value: Float) {
        progress = value
        onProgress.onValue(value)
    }

    private fun finish(finished: Boolean) {
        velocity = 0f
        endBoost()
        val callback = onEnd
        onEnd = null
        callback?.invoke(finished)
    }

    private fun stop() {
        onEnd = null
        if (spring.isRunning) spring.cancel()
        velocity = 0f
        endBoost()
    }

    private fun beginBoost() {
        if (boostedView != null) return
        val view = frameRateView() ?: return
        boostedView = view
        FrameRateBoost.acquire(view)
    }

    private fun endBoost() {
        val view = boostedView ?: return
        boostedView = null
        FrameRateBoost.release(view)
    }

    private companion object {
        const val MIN_SCALE = 0.01f
        const val MIN_STIFFNESS = 1f
    }
}
