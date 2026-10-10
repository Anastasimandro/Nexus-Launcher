package app.lawnchair.animation

import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.View
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
