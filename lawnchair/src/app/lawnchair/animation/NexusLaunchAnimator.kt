package app.lawnchair.animation

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.ActivityOptions
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.Settings
import android.view.Display
import android.view.RoundedCorner
import android.view.View
import android.view.animation.PathInterpolator
import android.window.SplashScreen
import app.lawnchair.LawnchairLauncher
import app.lawnchair.compat.LawnchairQuickstepCompat
import com.android.launcher3.BubbleTextView
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.util.ActivityOptionsWrapper
import com.android.launcher3.util.Executors
import com.android.launcher3.util.RunnableList
import kotlin.math.min

/**
 * Motor de animación de Nexus para cuando el launcher NO es el componente de recientes del
 * sistema (instalación como APK normal), caso en el que Quickstep y sus animaciones remotas
 * están desactivados.
 *
 * Funciona con dos piezas que no necesitan permisos especiales:
 *  1. Una "hoja" que se dibuja en el overlay del propio launcher y crece desde el icono hasta
 *     pantalla completa (400 ms, curva Nexus).
 *  2. Una animación de ventana por recursos (`nexus_app_enter`) que hace aparecer la app
 *     encima de la hoja mientras el launcher se mantiene visible (`nexus_launcher_hold`).
 *
 * Optimización: la hoja es un único Drawable con dos operaciones de dibujo; el interpolador,
 * los pinceles y los rectángulos se crean una vez y se reutilizan en cada fotograma. Durante
 * la animación se pide la categoría de frecuencia de refresco alta y se libera al terminar.
 */
class NexusLaunchAnimator(private val launcher: LawnchairLauncher) {

    private val density = launcher.resources.displayMetrics.density
    private var sheet: LaunchSheet? = null
    private var sheetAnimator: ValueAnimator? = null
    private var launchPending = false
    private var wentToBackground = false

    private val failSafe = Runnable {
        // Si la app tarda demasiado (o el lanzamiento falló) no dejamos la hoja tapando el inicio.
        launchPending = false
        dismiss(animated = true)
    }

    /**
     * Devuelve las opciones de lanzamiento de Nexus, o `null` si hay que usar la animación
     * por defecto (vista que no es un icono, animaciones del sistema desactivadas, etc.).
     */
    fun createLaunchOptions(v: View?): ActivityOptionsWrapper? {
        if (v !is BubbleTextView || !animationsEnabled()) return null
        val icon = v.icon ?: return null
        val iconBounds = icon.bounds
        val dragLayer = launcher.dragLayer
        if (iconBounds.isEmpty || v.width == 0 || dragLayer.width == 0 || dragLayer.height == 0) return null

        dismiss(animated = false)
        resetReturnAnimation()

        val snapshot = snapshotOf(icon, iconBounds) ?: return null

        // Posición del icono dentro del DragLayer (mismo sistema de coordenadas que el overlay).
        val viewRect = Rect()
        val scale = dragLayer.getDescendantRectRelativeToSelf(v, viewRect)
        val iconWidth = iconBounds.width() * scale
        val iconHeight = iconBounds.height() * scale
        val left = viewRect.left + (viewRect.width() - iconWidth) / 2f
        val top = viewRect.top + v.paddingTop * scale
        val start = RectF(left, top, left + iconWidth, top + iconHeight)
        val screen = RectF(0f, 0f, dragLayer.width.toFloat(), dragLayer.height.toFloat())

        val newSheet = LaunchSheet(
            start = start,
            screen = screen,
            icon = snapshot,
            startColor = averageColor(snapshot),
            endColor = surfaceColor(),
            startRadius = min(iconWidth, iconHeight) * ICON_CORNER_FRACTION,
            endRadius = deviceCornerRadius(),
        )
        newSheet.setBounds(0, 0, dragLayer.width, dragLayer.height)
        dragLayer.overlay.add(newSheet)
        sheet = newSheet
        requestHighFrameRate(true)

        sheetAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = LAUNCH_DURATION_MS
            interpolator = OPEN_INTERPOLATOR
            addUpdateListener {
                newSheet.progress = it.animatedValue as Float
                newSheet.invalidateSelf()
            }
            addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        requestHighFrameRate(false)
                    }
                },
            )
            start()
        }

        val callbacks = RunnableList()
        callbacks.add { dismiss(animated = true, only = newSheet) }
        val options = makeCustomOptions(v, callbacks)

        launchPending = true
        wentToBackground = false
        dragLayer.removeCallbacks(failSafe)
        dragLayer.postDelayed(failSafe, FAIL_SAFE_MS)
        return ActivityOptionsWrapper(options, callbacks)
    }

    fun onLauncherPaused() {
        if (launchPending) wentToBackground = true
    }

    fun onLauncherResumed() {
        if (!wentToBackground) return
        wentToBackground = false
        launchPending = false
        launcher.dragLayer.removeCallbacks(failSafe)
        dismiss(animated = false)
        playReturnAnimation()
    }

    fun onLauncherDestroyed() {
        launcher.dragLayer.removeCallbacks(failSafe)
        dismiss(animated = false)
        resetReturnAnimation()
    }

    private fun makeCustomOptions(v: View, callbacks: RunnableList): ActivityOptions {
        val base = if (Utilities.ATLEAST_Q) {
            LawnchairQuickstepCompat.activityOptionsCompat.makeCustomAnimation(
                launcher,
                R.anim.nexus_app_enter,
                R.anim.nexus_launcher_hold,
                Executors.MAIN_EXECUTOR.handler,
                null,
            ) {
                callbacks.executeAllAndDestroy()
            }
        } else {
            ActivityOptions.makeCustomAnimation(
                launcher,
                R.anim.nexus_app_enter,
                R.anim.nexus_launcher_hold,
            )
        }
        val options = Utilities.allowBGLaunch(base)
        if (Utilities.ATLEAST_T) {
            options.splashScreenStyle = SplashScreen.SPLASH_SCREEN_STYLE_ICON
        }
        options.launchDisplayId = v.display?.displayId ?: Display.DEFAULT_DISPLAY
        return options
    }

    /**
     * Quita la hoja; con `animated` hace un fundido corto para que no haya un salto visible.
     * Con `only` se ignora la llamada si la hoja activa ya es otra: los avisos de fin de un
     * lanzamiento anterior llegan tarde y no deben cortar la animación del siguiente.
     */
    private fun dismiss(animated: Boolean, only: LaunchSheet? = null) {
        val current = sheet ?: return
        if (only != null && only !== current) return
        sheet = null
        sheetAnimator?.cancel()
        sheetAnimator = null
        requestHighFrameRate(false)
        val overlay = launcher.dragLayer.overlay
        if (!animated) {
            overlay.remove(current)
            return
        }
        ValueAnimator.ofFloat(1f, 0f).apply {
            duration = FADE_OUT_MS
            addUpdateListener {
                current.fade = it.animatedValue as Float
                current.invalidateSelf()
            }
            addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        overlay.remove(current)
                    }
                },
            )
            start()
        }
    }

    /** Al volver al inicio, el contenido entra con un zoom suave en vez de aparecer de golpe. */
    private fun playReturnAnimation() {
        if (!animationsEnabled()) return
        val target = launcher.dragLayer
        target.animate().cancel()
        target.scaleX = RETURN_START_SCALE
        target.scaleY = RETURN_START_SCALE
        target.alpha = RETURN_START_ALPHA
        requestHighFrameRate(true)
        target.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(RETURN_DURATION_MS)
            .setInterpolator(OPEN_INTERPOLATOR)
            .withLayer()
            .withEndAction { requestHighFrameRate(false) }
            .start()
    }

    private fun resetReturnAnimation() {
        val target = launcher.dragLayer
        target.animate().cancel()
        target.scaleX = 1f
        target.scaleY = 1f
        target.alpha = 1f
    }

    private fun animationsEnabled(): Boolean {
        if (!ValueAnimator.areAnimatorsEnabled()) return false
        val windowScale = Settings.Global.getFloat(
            launcher.contentResolver,
            Settings.Global.WINDOW_ANIMATION_SCALE,
            1f,
        )
        return windowScale > 0f
    }

    private fun requestHighFrameRate(high: Boolean) {
        if (Build.VERSION.SDK_INT < 35) return
        launcher.dragLayer.setRequestedFrameRate(
            if (high) {
                View.REQUESTED_FRAME_RATE_CATEGORY_HIGH
            } else {
                View.REQUESTED_FRAME_RATE_CATEGORY_DEFAULT
            },
        )
    }

    private fun snapshotOf(icon: Drawable, bounds: Rect): Bitmap? = runCatching {
        Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888).also {
            val canvas = Canvas(it)
            canvas.translate(-bounds.left.toFloat(), -bounds.top.toFloat())
            icon.draw(canvas)
        }
    }.getOrNull()

    private fun averageColor(bitmap: Bitmap): Int {
        val pixel = Bitmap.createScaledBitmap(bitmap, 1, 1, true).getPixel(0, 0)
        return Color.rgb(Color.red(pixel), Color.green(pixel), Color.blue(pixel))
    }

    private fun surfaceColor(): Int {
        val night = launcher.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        return if (night) SURFACE_DARK else SURFACE_LIGHT
    }

    private fun deviceCornerRadius(): Float {
        if (Build.VERSION.SDK_INT >= 31) {
            val radius = launcher.display?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius
            if (radius != null && radius > 0) return radius.toFloat()
        }
        return DEFAULT_SCREEN_CORNER_DP * density
    }

    /** Rectángulo redondeado que crece del icono a pantalla completa, con el icono encima. */
    private class LaunchSheet(
        private val start: RectF,
        private val screen: RectF,
        private val icon: Bitmap,
        private val startColor: Int,
        private val endColor: Int,
        private val startRadius: Float,
        private val endRadius: Float,
    ) : Drawable() {

        var progress = 0f
        var fade = 1f

        private val rect = RectF()
        private val sheetPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        override fun draw(canvas: Canvas) {
            val p = progress
            rect.set(
                lerp(start.left, screen.left, p),
                lerp(start.top, screen.top, p),
                lerp(start.right, screen.right, p),
                lerp(start.bottom, screen.bottom, p),
            )
            val radius = lerp(startRadius, endRadius, p)

            // El fondo aparece en los primeros fotogramas para que el primero sea idéntico al icono.
            val backdrop = (p / BACKDROP_FADE_IN).coerceIn(0f, 1f)
            sheetPaint.color = lerpColor(startColor, endColor, (p / COLOR_SETTLE).coerceAtMost(1f))
            sheetPaint.alpha = (255 * backdrop * fade).toInt()
            canvas.drawRoundRect(rect, radius, radius, sheetPaint)

            val iconAlpha = (1f - p / ICON_FADE_END).coerceIn(0f, 1f) * fade
            if (iconAlpha > 0f) {
                iconPaint.alpha = (255 * iconAlpha).toInt()
                val scale = lerp(1f, ICON_END_SCALE, p)
                val saved = canvas.save()
                canvas.translate(rect.centerX(), rect.centerY())
                canvas.scale(scale, scale)
                canvas.drawBitmap(icon, -icon.width / 2f, -icon.height / 2f, iconPaint)
                canvas.restoreToCount(saved)
            }
        }

        override fun setAlpha(alpha: Int) {}

        override fun setColorFilter(colorFilter: ColorFilter?) {}

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    companion object {
        private const val LAUNCH_DURATION_MS = 400L
        private const val FADE_OUT_MS = 140L
        private const val FAIL_SAFE_MS = 3000L
        private const val RETURN_DURATION_MS = 380L
        private const val RETURN_START_SCALE = 1.05f
        private const val RETURN_START_ALPHA = 0.55f

        private const val ICON_CORNER_FRACTION = 0.28f
        private const val DEFAULT_SCREEN_CORNER_DP = 24f
        private const val BACKDROP_FADE_IN = 0.12f
        private const val COLOR_SETTLE = 0.65f
        private const val ICON_FADE_END = 0.4f
        private const val ICON_END_SCALE = 1.5f

        private val SURFACE_DARK = Color.rgb(0x12, 0x12, 0x16)
        private val SURFACE_LIGHT = Color.rgb(0xF6, 0xF6, 0xFA)

        // Misma curva que `nexus_open.xml`: arranque rápido y frenada muy suave.
        // Se crea una sola vez y se reutiliza en todas las animaciones.
        private val OPEN_INTERPOLATOR = PathInterpolator(0.15f, 0.1f, 0.15f, 1f)

        private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

        private fun lerpColor(a: Int, b: Int, t: Float): Int = Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt(),
        )
    }
}
