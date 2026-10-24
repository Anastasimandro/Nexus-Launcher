package app.lawnchair.animation

import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.animation.Interpolator
import java.util.WeakHashMap

/**
 * Punto central del sistema de animación de Nexus.
 *
 * El perfil vive en memoria y se cambia una sola vez cuando el usuario toca un ajuste. Las
 * animaciones lo leen al empezar y nunca consultan almacenamiento mientras se ejecutan.
 */
object NexusMotion {

    @Volatile
    var profile: MotionProfile = MotionProfile.Nexus

    private var curveProfile: MotionProfile? = null
    private var openCurve: SpringCurve? = null

    /**
     * Respuesta temporal del resorte de apertura del perfil actual, para las animaciones de
     * duración fija que gestiona el sistema (transiciones remotas de Quickstep). Asentar el
     * resorte cuesta ~1 ms, así que se recalcula solo cuando cambia el perfil.
     */
    @JvmStatic
    @Synchronized
    fun openCurve(): SpringCurve {
        val current = profile
        val cached = openCurve
        if (cached != null && curveProfile === current) return cached
        return SpringCurve.of(current.open, current.speed).also {
            openCurve = it
            curveProfile = current
        }
    }

    /** Duración, en ms, de una apertura con el perfil actual (hasta que el resorte se asienta). */
    @JvmStatic
    fun appLaunchDurationMs(): Long = openCurve().durationMs

    /**
     * Curva para terminar un gesto que el sistema completa solo (volver a la app al cancelar):
     * resorte de cierre del perfil, ajustado a [durationMs] y arrancando con la velocidad con la
     * que el dedo se acercaba al destino, para que no haya tirón al soltar.
     *
     * @param travel recorrido que queda, en unidades de progreso del gesto.
     * @param velocityTowardPerMs velocidad hacia el destino, en unidades de progreso por ms
     *   (negativa si el dedo se alejaba).
     */
    @JvmStatic
    fun gestureEndInterpolator(travel: Float, velocityTowardPerMs: Float, durationMs: Long): Interpolator {
        val perSecond = if (travel > MIN_TRAVEL) velocityTowardPerMs * MS_PER_SECOND / travel else 0f
        val curve = SpringCurve.fitted(profile.close, durationMs, perSecond)
        NexusLog.i(
            "NexusGesture",
            "Cancelar gesto: recorrido=$travel velocidad hacia la app=${perSecond}x recorrido/s " +
                "duración pedida=$durationMs ms, resorte=${curve.durationMs} ms",
        )
        return SpringInterpolator(curve)
    }

    private const val MIN_TRAVEL = 1e-4f
    private const val MS_PER_SECOND = 1000f

    /** Escala de animación del sistema (opciones de desarrollador). 1 = normal. */
    fun systemScale(context: Context): Float = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    )

    /** `false` si el usuario o el ahorro de batería han desactivado las animaciones. */
    fun isEnabled(context: Context): Boolean {
        if (!ValueAnimator.areAnimatorsEnabled()) return false
        val windowScale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.WINDOW_ANIMATION_SCALE,
            1f,
        )
        return windowScale > 0f
    }
}

/**
 * Pide la categoría de frecuencia de refresco alta mientras haya alguna animación en marcha
 * sobre la vista y la libera cuando termina la última. Cuenta las peticiones para que dos
 * animaciones a la vez no se pisen. Solo existe desde Android 15; antes no hace nada.
 */
object FrameRateBoost {

    private val holders = WeakHashMap<View, Int>()

    fun acquire(view: View) {
        if (Build.VERSION.SDK_INT < 35) return
        val count = holders[view] ?: 0
        holders[view] = count + 1
        if (count == 0) view.setRequestedFrameRate(View.REQUESTED_FRAME_RATE_CATEGORY_HIGH)
    }

    fun release(view: View) {
        if (Build.VERSION.SDK_INT < 35) return
        val count = holders[view] ?: return
        if (count > 1) {
            holders[view] = count - 1
        } else {
            holders.remove(view)
            view.setRequestedFrameRate(View.REQUESTED_FRAME_RATE_CATEGORY_DEFAULT)
        }
    }
}
