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
import android.util.Log
import android.view.Display
import android.view.RoundedCorner
import android.view.View
import android.window.SplashScreen
import app.lawnchair.LawnchairLauncher
import app.lawnchair.compat.LawnchairQuickstepCompat
import com.android.launcher3.BubbleTextView
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.util.ActivityOptionsWrapper
import com.android.launcher3.util.Executors
import com.android.launcher3.util.RunnableList
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.min

/**
 * Animación de lanzamiento de apps de Nexus para cuando el launcher NO es el componente de
 * recientes del sistema (instalación como APK normal), caso en el que Quickstep y sus
 * animaciones remotas están desactivados.
 *
 * Apertura: el sistema anima la propia ventana de la app desde el icono (clip reveal), así que
 * va sincronizada con la app y no necesita permisos especiales.
 *
 * Hoja propia (desactivada, ver [useLaunchSheet]): una "hoja" dibujada en el overlay del
 * launcher que crece desde el icono hasta pantalla completa, con un [MotionDriver] por
 * lanzamiento ([LaunchSession]), interrumpible y redirigible. Para que no se pisara con la
 * animación del sistema se probó una animación de ventana propia (`nexus_app_enter`) con
 * `overrideTaskTransition`, pero desde Android 14 el sistema solo acepta esa marca de quien
 * tiene `START_TASKS_FROM_RECENTS` o es el asistente activo (`SafeActivityOptions`): con un APK
 * normal `startActivity` lanza `SecurityException`. Por eso la hoja está apagada.
 *
 * Al volver al inicio la app la cierra el sistema (un launcher normal no controla su ventana),
 * así que Nexus anima lo suyo: el contenido del inicio entra con un zoom suave y el icono de
 * la app que se cerró "la recibe" con un pequeño rebote.
 *
 * Optimización: la hoja es un único Drawable con dos operaciones de dibujo; los pinceles y los
 * rectángulos se crean una vez y se reutilizan en cada fotograma.
 */
class NexusLaunchAnimator(private val launcher: LawnchairLauncher) {

    private val density = launcher.resources.displayMetrics.density

    /**
     * Dibujar la hoja propia al abrir. Apagado: necesita `overrideTaskTransition`, que el
     * sistema rechaza a una app sin `START_TASKS_FROM_RECENTS` (ver la documentación de la clase).
     */
    private val useLaunchSheet = false

    /** Lanzamiento cuya hoja sigue en pantalla (abriéndose o plegándose), si lo hay. */
    private var session: LaunchSession? = null

    /**
     * Número del último lanzamiento. Los avisos de fin del sistema llegan con retraso y a veces
     * son de un lanzamiento anterior: solo se atiende el del último.
     */
    private var generation = 0

    /** Último icono lanzado, para plegar la hoja hacia él al volver al inicio. */
    private var lastSource: WeakReference<BubbleTextView>? = null
    private var lastTag: Any? = null
    private var launchPending = false
    private var wentToBackground = false

    /** Últimas opciones entregadas al sistema, para reconocerlas si el lanzamiento falla. */
    private var lastWrapper: WeakReference<ActivityOptionsWrapper>? = null

    private val returnDriver = MotionDriver(
        frameRateView = { launcher.dragLayer },
        systemScale = { NexusMotion.systemScale(launcher) },
        onProgress = { progress -> applyReturn(progress) },
    )

    /** Icono que "recibe" a la app al volver al inicio, y el elemento que representaba. */
    private var landingIcon: WeakReference<View>? = null
    private var landingTag: Any? = null
    private val landingDriver = MotionDriver(
        frameRateView = { launcher.dragLayer },
        systemScale = { NexusMotion.systemScale(launcher) },
        onProgress = { progress -> applyLanding(progress) },
    )

    private val failSafe = Runnable {
        // Si la app tarda demasiado (o el lanzamiento falló) no dejamos la hoja tapando el inicio.
        launchPending = false
        endSession(animated = true)
    }

    /**
     * Devuelve las opciones de lanzamiento de Nexus, o `null` si hay que usar la animación
     * por defecto (vista que no es un icono, animaciones del sistema desactivadas, etc.).
     */
    fun createLaunchOptions(v: View?): ActivityOptionsWrapper? {
        if (v !is BubbleTextView) return skip("la vista no es un icono (${v?.javaClass?.simpleName})")
        if (!NexusMotion.isEnabled(launcher)) return skip("animaciones del sistema desactivadas")
        val icon = v.icon ?: return skip("el icono no tiene drawable")
        val iconBounds = icon.bounds
        val dragLayer = launcher.dragLayer
        if (iconBounds.isEmpty || v.width == 0 || dragLayer.width == 0 || dragLayer.height == 0) {
            return skip(
                "medidas vacías (icono=$iconBounds, vista=${v.width}, " +
                    "capa=${dragLayer.width}x${dragLayer.height})",
            )
        }
        if (!useLaunchSheet) return createRevealOptions(v, iconBounds)
        // El sistema ya rechazó un lanzamiento con estas opciones: se usan las normales.
        if (optionsRejected) return skip("el sistema rechazó antes las opciones de Nexus")

        val start = iconRect(v) ?: return skip("no se pudo calcular la posición del icono")

        // Las opciones se piden primero: si el sistema no iba a respetar la animación de
        // ventana, no se dibuja la hoja y se usa la animación estándar del sistema.
        val callbacks = RunnableList()
        val options = makeCustomOptions(v, callbacks) ?: return skip("no se pudieron crear las opciones")

        // Si el zoom de vuelta al inicio sigue en marcha, se deja asentar en vez de cortarlo.
        settleReturnAnimation()
        settleLanding()

        // Mismo icono con su hoja todavía en pantalla: se reutiliza y se redirige. Si no, se
        // retira la anterior (con un fundido) y se crea una nueva.
        val active = session?.takeIf { it.isFor(v, start) }
            ?: createSession(v, icon, iconBounds, start)
            ?: return skip("no se pudo capturar el icono para la hoja")

        // Se recuerda el icono para que "reciba" a la app cuando se vuelva al inicio.
        lastSource = WeakReference(v)
        lastTag = v.tag

        val profile = NexusMotion.profile
        active.finishRequested = false
        active.driver.animateTo(1f, profile.open, profile.speed) { finished ->
            if (finished && active.finishRequested && session === active) endSession(animated = true)
        }

        val launchId = ++generation
        callbacks.add {
            if (launchId == generation && session === active) {
                // El aviso del sistema no debe cortar la apertura a medias: si el resorte sigue
                // en marcha, la hoja se retira cuando termine de abrirse.
                if (active.driver.isRunning) {
                    active.finishRequested = true
                } else {
                    endSession(animated = true)
                }
            }
        }

        launchPending = true
        wentToBackground = false
        dragLayer.removeCallbacks(failSafe)
        dragLayer.postDelayed(failSafe, FAIL_SAFE_MS)
        Log.i(TAG, "Lanzamiento #$launchId con la hoja y las opciones de Nexus (sdk=${Build.VERSION.SDK_INT})")
        return ActivityOptionsWrapper(options, callbacks).also { lastWrapper = WeakReference(it) }
    }

    /**
     * Apertura con la animación de ventana del sistema desde el icono (clip reveal): el sistema
     * anima la ventana real de la app, así que no hace falta ningún permiso. Además deja
     * preparado el retorno al inicio (zoom del contenido y rebote del icono).
     */
    private fun createRevealOptions(v: BubbleTextView, iconBounds: Rect): ActivityOptionsWrapper {
        // Si el zoom de vuelta al inicio sigue en marcha, se deja asentar en vez de cortarlo.
        settleReturnAnimation()
        settleLanding()

        // Se recuerda el icono para que "reciba" a la app cuando se vuelva al inicio.
        lastSource = WeakReference(v)
        lastTag = v.tag

        val options = Utilities.allowBGLaunch(
            ActivityOptions.makeClipRevealAnimation(
                v,
                (v.width - iconBounds.width()) / 2,
                v.paddingTop,
                iconBounds.width(),
                iconBounds.height(),
            ),
        )
        if (Utilities.ATLEAST_T) {
            options.splashScreenStyle = SplashScreen.SPLASH_SCREEN_STYLE_ICON
        }
        options.launchDisplayId = v.display?.displayId ?: Display.DEFAULT_DISPLAY

        val launchId = ++generation
        launchPending = true
        wentToBackground = false
        val dragLayer = launcher.dragLayer
        dragLayer.removeCallbacks(failSafe)
        dragLayer.postDelayed(failSafe, FAIL_SAFE_MS)
        Log.i(TAG, "Lanzamiento #$launchId con reveal del sistema (sdk=${Build.VERSION.SDK_INT})")
        // No se registra en `lastWrapper`: son opciones normales y no hay nada que reintentar
        // (si falla, es que la app no está instalada y el aviso es el correcto).
        return ActivityOptionsWrapper(options, RunnableList())
    }

    /** Anota por qué este lanzamiento no usa la hoja de Nexus y devuelve `null`. */
    private fun skip(reason: String): ActivityOptionsWrapper? {
        Log.i(TAG, "Sin hoja de Nexus: $reason")
        return null
    }

    /**
     * El sistema rechazó el lanzamiento (`startActivity` lanzó una excepción). Si las opciones
     * eran las de Nexus, se retira la hoja sin animación y se pide reintentar con las opciones
     * normales; así la app se abre siempre, aunque sea con la animación estándar del sistema.
     *
     * Devuelve `true` si hay que reintentar.
     */
    fun onLaunchFailed(options: ActivityOptionsWrapper, error: Exception): Boolean {
        if (lastWrapper?.get() !== options) return false
        Log.w(
            TAG,
            "El sistema rechazó el lanzamiento con las opciones de Nexus " +
                "(${error.javaClass.simpleName}: ${error.message}); se reintenta con las normales",
        )
        lastWrapper = null
        generation++
        launchPending = false
        wentToBackground = false
        launcher.dragLayer.removeCallbacks(failSafe)
        endSession(animated = false)
        lastSource = null
        lastTag = null
        optionsRejected = true
        return true
    }

    /**
     * Resultado del reintento. Si también falla, el problema no eran las opciones (por ejemplo,
     * la app ya no está instalada) y la animación de Nexus se vuelve a permitir.
     */
    fun onLaunchRetried(success: Boolean) {
        if (!success) optionsRejected = false
    }

    fun onLauncherPaused() {
        Log.i(TAG, "Launcher en pausa (lanzamiento pendiente=$launchPending)")
        if (launchPending) wentToBackground = true
    }

    fun onLauncherResumed() {
        Log.i(TAG, "Launcher reanudado (venía de una apertura de Nexus=$wentToBackground)")
        if (!wentToBackground) return
        wentToBackground = false
        launchPending = false
        launcher.dragLayer.removeCallbacks(failSafe)

        if (!NexusMotion.isEnabled(launcher)) {
            endSession(animated = false)
            resetLanding()
            return
        }

        // Volvimos con la apertura aún en sus primeros instantes: la hoja se pliega al icono
        // conservando la velocidad que llevaba, en vez de cortarse de golpe.
        val current = session
        if (current != null && current.driver.progress < FOLD_MAX_PROGRESS) {
            foldIntoIcon(current)
            return
        }

        // Con la apertura ya avanzada la app estuvo a la vista y la está cerrando el sistema:
        // una hoja a pantalla completa aparecería de golpe al terminar su animación, así que se
        // retira sin fundido y se anima solo el contenido del inicio.
        if (current != null) endSession(animated = false)
        playReturnAnimation()
        playLanding()
    }

    fun onLauncherDestroyed() {
        launcher.dragLayer.removeCallbacks(failSafe)
        endSession(animated = false)
        resetReturnAnimation()
        resetLanding()
        lastSource = null
        lastTag = null
    }

    /** Posición del icono dentro del DragLayer (mismo sistema de coordenadas que el overlay). */
    private fun iconRect(v: BubbleTextView): RectF? {
        val iconBounds = v.icon?.bounds ?: return null
        if (iconBounds.isEmpty || v.width == 0) return null
        val viewRect = Rect()
        val scale = launcher.dragLayer.getDescendantRectRelativeToSelf(v, viewRect)
        val iconWidth = iconBounds.width() * scale
        val iconHeight = iconBounds.height() * scale
        val left = viewRect.left + (viewRect.width() - iconWidth) / 2f
        val top = viewRect.top + v.paddingTop * scale
        return RectF(left, top, left + iconWidth, top + iconHeight)
    }

    private fun foldIntoIcon(target: LaunchSession) {
        val profile = NexusMotion.profile
        target.driver.animateTo(0f, profile.close, profile.speed) { finished ->
            if (finished && session === target) endSession(animated = false)
        }
    }

    /**
     * Opciones con la animación de ventana de Nexus, o `null` si el sistema no la respetaría.
     *
     * En Android 14+ una animación personalizada solo se aplica a las ventanas de tarea (las de
     * una app lanzada desde el inicio) si las opciones llevan `overrideTaskTransition`; el
     * método de la API que lo hace está oculto, así que se activa por reflexión (el launcher
     * ya levanta las restricciones de API ocultas al arrancar).
     */
    private fun makeCustomOptions(v: View, callbacks: RunnableList): ActivityOptions? {
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
        if (Build.VERSION.SDK_INT >= TASK_OVERRIDE_MIN_SDK && !overrideTaskTransition(base)) {
            Log.w(TAG, "No se pudo pedir la animación de tarea: se usa la del sistema")
            return null
        }
        val options = Utilities.allowBGLaunch(base)
        if (Utilities.ATLEAST_T) {
            options.splashScreenStyle = SplashScreen.SPLASH_SCREEN_STYLE_ICON
        }
        options.launchDisplayId = v.display?.displayId ?: Display.DEFAULT_DISPLAY
        return options
    }

    /**
     * Marca las opciones para que la animación personalizada se aplique también a las ventanas
     * de tarea. Prueba el método oculto y, si no existe, el campo que este rellena. Devuelve
     * `true` solo si se comprobó que la marca quedó puesta.
     */
    private fun overrideTaskTransition(options: ActivityOptions): Boolean {
        val type = ActivityOptions::class.java

        // 1) El método oculto, que es lo que usa el propio sistema.
        val viaMethod = runCatching {
            type.getDeclaredMethod(OVERRIDE_TASK_SETTER, java.lang.Boolean.TYPE)
                .apply { isAccessible = true }
                .invoke(options, true)
        }
        viaMethod.exceptionOrNull()?.let {
            Log.w(TAG, "$OVERRIDE_TASK_SETTER no disponible: ${it.javaClass.simpleName}: ${it.message}")
        }

        // 2) Se comprueba leyendo el campo; si el método no existía, se rellena directamente.
        val field = runCatching {
            type.getDeclaredField(OVERRIDE_TASK_FIELD).apply { isAccessible = true }
        }.getOrNull()
        if (field != null) {
            runCatching { if (!field.getBoolean(options)) field.setBoolean(options, true) }
            val set = runCatching { field.getBoolean(options) }.getOrDefault(false)
            Log.i(TAG, "overrideTaskTransition: campo=$set (método ok=${viaMethod.isSuccess})")
            return set
        }
        Log.w(TAG, "El campo $OVERRIDE_TASK_FIELD no existe en esta versión de Android")

        // 3) Sin el campo, se comprueba con el método de lectura.
        val viaGetter = runCatching {
            type.getDeclaredMethod(OVERRIDE_TASK_GETTER)
                .apply { isAccessible = true }
                .invoke(options) as? Boolean
        }.getOrNull() == true
        Log.i(TAG, "overrideTaskTransition: lectura por método=$viaGetter")
        return viaGetter
    }

    /**
     * Crea el lanzamiento de un icono y pone su hoja en pantalla, o devuelve `null` si no se
     * pudo capturar el icono. La hoja anterior, si la hay, se retira antes con un fundido.
     */
    private fun createSession(
        v: View,
        icon: Drawable,
        iconBounds: Rect,
        start: RectF,
    ): LaunchSession? {
        endSession(animated = true)
        val snapshot = snapshotOf(icon, iconBounds) ?: return null

        val dragLayer = launcher.dragLayer
        val screen = RectF(0f, 0f, dragLayer.width.toFloat(), dragLayer.height.toFloat())
        val sheet = LaunchSheet(
            start = start,
            screen = screen,
            icon = snapshot,
            startColor = averageColor(snapshot),
            endColor = surfaceColor(),
            startRadius = min(start.width(), start.height()) * ICON_CORNER_FRACTION,
            endRadius = deviceCornerRadius(),
        )
        sheet.setBounds(0, 0, dragLayer.width, dragLayer.height)
        dragLayer.overlay.add(sheet)

        return LaunchSession(v, v.tag, start, sheet).also { session = it }
    }

    /**
     * Retira la hoja activa; con `animated` hace un fundido corto para que no haya un salto
     * visible. La hoja que se funde conserva su controlador y sigue moviéndose mientras tanto.
     */
    private fun endSession(animated: Boolean) {
        val current = session ?: return
        session = null
        if (animated) {
            fadeOut(current)
        } else {
            discard(current)
        }
    }

    private fun fadeOut(old: LaunchSession) {
        ValueAnimator.ofFloat(1f, 0f).apply {
            duration = FADE_OUT_MS
            addUpdateListener {
                old.sheet.fade = it.animatedValue as Float
                old.sheet.invalidateSelf()
            }
            addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        discard(old)
                    }
                },
            )
            start()
        }
    }

    private fun discard(old: LaunchSession) {
        old.driver.cancel()
        launcher.dragLayer.overlay.remove(old.sheet)
    }

    /** Al volver al inicio, el contenido entra con un zoom suave en vez de aparecer de golpe. */
    private fun playReturnAnimation() {
        if (!NexusMotion.isEnabled(launcher)) return
        val profile = NexusMotion.profile
        val target = launcher.dragLayer
        target.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        returnDriver.snapTo(0f)
        returnDriver.animateTo(1f, profile.homeReturn, profile.speed) { finished ->
            if (finished) target.setLayerType(View.LAYER_TYPE_NONE, null)
        }
    }

    /**
     * Un lanzamiento nuevo no corta el zoom de vuelta al inicio: si sigue en marcha se redirige
     * a su posición final conservando la velocidad, en vez de saltar de golpe a escala 1.
     */
    private fun settleReturnAnimation() {
        if (!returnDriver.isRunning) {
            resetReturnAnimation()
            return
        }
        val profile = NexusMotion.profile
        returnDriver.animateTo(1f, profile.homeReturn, profile.speed) { finished ->
            if (finished) launcher.dragLayer.setLayerType(View.LAYER_TYPE_NONE, null)
        }
    }

    private fun applyReturn(progress: Float) {
        val target = launcher.dragLayer
        val scale = lerp(RETURN_START_SCALE, 1f, progress)
        target.scaleX = scale
        target.scaleY = scale
        target.alpha = lerp(RETURN_START_ALPHA, 1f, progress).coerceIn(0f, 1f)
    }

    private fun resetReturnAnimation() {
        returnDriver.cancel()
        applyReturn(1f)
        launcher.dragLayer.setLayerType(View.LAYER_TYPE_NONE, null)
    }

    /**
     * El icono de la app que se cerró "la recibe": entra pequeño y recupera su tamaño con el
     * resorte de cierre del perfil. No hay hoja ni ventana que simular: es el propio icono.
     */
    private fun playLanding() {
        val v = lastSource?.get() ?: return
        if (!v.isAttachedToWindow || v.tag !== lastTag) return
        resetLanding()
        landingIcon = WeakReference(v)
        landingTag = v.tag
        val profile = NexusMotion.profile
        landingDriver.snapTo(0f)
        landingDriver.animateTo(1f, profile.close, profile.speed) { finished ->
            if (finished) resetLanding()
        }
    }

    /** Un lanzamiento nuevo deja que el icono termine de asentarse en vez de cortarlo. */
    private fun settleLanding() {
        if (!landingDriver.isRunning) {
            resetLanding()
            return
        }
        val profile = NexusMotion.profile
        landingDriver.animateTo(1f, profile.close, profile.speed) { finished ->
            if (finished) resetLanding()
        }
    }

    private fun applyLanding(progress: Float) {
        val v = landingIcon?.get() ?: return
        // Si la vista se reutilizó para otro elemento, se deja como estaba.
        val scale = if (v.tag === landingTag) lerp(LANDING_START_SCALE, 1f, progress) else 1f
        v.scaleX = scale
        v.scaleY = scale
    }

    private fun resetLanding() {
        landingDriver.cancel()
        landingIcon?.get()?.let {
            it.scaleX = 1f
            it.scaleY = 1f
        }
        landingIcon = null
        landingTag = null
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

    /** Un lanzamiento: la hoja que crece del icono y el controlador que lleva su progreso. */
    private inner class LaunchSession(
        private val source: View,
        private val tag: Any?,
        private val start: RectF,
        val sheet: LaunchSheet,
    ) {

        /** El sistema ya avisó del fin del lanzamiento; la hoja se retira al terminar de abrirse. */
        var finishRequested = false

        val driver = MotionDriver(
            frameRateView = { launcher.dragLayer },
            systemScale = { NexusMotion.systemScale(launcher) },
            onProgress = { progress ->
                sheet.progress = progress
                sheet.invalidateSelf()
            },
        )

        /** `true` si [view] es el mismo icono: misma vista, mismo elemento y misma posición. */
        fun isFor(view: View, bounds: RectF): Boolean {
            return source === view && tag === view.tag && start.isCloseTo(bounds)
        }
    }

    /**
     * Rectángulo redondeado que crece del icono a pantalla completa, con el icono encima.
     * Admite progresos fuera de 0..1 (rebote del resorte): la geometría se pasa un poco y el
     * resto de valores se limita para no salirse de rango.
     */
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
            val radius = lerp(startRadius, endRadius, p).coerceAtLeast(0f)

            // El fondo aparece en los primeros fotogramas para que el primero sea idéntico al icono.
            val backdrop = (p / BACKDROP_FADE_IN).coerceIn(0f, 1f)
            sheetPaint.color = lerpColor(startColor, endColor, (p / COLOR_SETTLE).coerceIn(0f, 1f))
            sheetPaint.alpha = (255 * backdrop * fade).toInt()
            canvas.drawRoundRect(rect, radius, radius, sheetPaint)

            val iconAlpha = (1f - p / ICON_FADE_END).coerceIn(0f, 1f) * fade
            if (iconAlpha > 0f) {
                iconPaint.alpha = (255 * iconAlpha).toInt()
                val scale = lerp(1f, ICON_END_SCALE, p.coerceAtLeast(0f))
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
        private const val TAG = "NexusLaunchAnimator"

        /**
         * `true` cuando el sistema rechazó un lanzamiento con las opciones de Nexus y el reintento
         * con las normales funcionó: no se vuelven a pedir mientras viva el proceso.
         */
        @Volatile
        private var optionsRejected = false

        private const val FADE_OUT_MS = 140L
        private const val FAIL_SAFE_MS = 3000L
        private const val POSITION_TOLERANCE_PX = 1.5f

        /** Hasta aquí de abierta, volver al inicio pliega la hoja; a partir de aquí se retira. */
        private const val FOLD_MAX_PROGRESS = 0.6f

        /**
         * El contenido del inicio entra casi transparente: así el primer fotograma tras volver
         * no "salta" ni se ve a medio fundir sobre la app que se está cerrando.
         */
        private const val RETURN_START_SCALE = 1.05f
        private const val RETURN_START_ALPHA = 0.2f
        private const val LANDING_START_SCALE = 0.74f

        private const val TASK_OVERRIDE_MIN_SDK = 34
        private const val OVERRIDE_TASK_FIELD = "mOverrideTaskTransition"
        private const val OVERRIDE_TASK_SETTER = "setOverrideTaskTransition"
        private const val OVERRIDE_TASK_GETTER = "getOverrideTaskTransition"

        private const val ICON_CORNER_FRACTION = 0.28f
        private const val DEFAULT_SCREEN_CORNER_DP = 24f
        private const val BACKDROP_FADE_IN = 0.12f
        private const val COLOR_SETTLE = 0.65f
        private const val ICON_FADE_END = 0.4f
        private const val ICON_END_SCALE = 1.5f

        private val SURFACE_DARK = Color.rgb(0x12, 0x12, 0x16)
        private val SURFACE_LIGHT = Color.rgb(0xF6, 0xF6, 0xFA)

        private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

        private fun RectF.isCloseTo(other: RectF): Boolean {
            return abs(left - other.left) < POSITION_TOLERANCE_PX &&
                abs(top - other.top) < POSITION_TOLERANCE_PX &&
                abs(right - other.right) < POSITION_TOLERANCE_PX &&
                abs(bottom - other.bottom) < POSITION_TOLERANCE_PX
        }

        private fun lerpColor(a: Int, b: Int, t: Float): Int = Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt(),
        )
    }
}
