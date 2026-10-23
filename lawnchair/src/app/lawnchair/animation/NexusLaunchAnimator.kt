package app.lawnchair.animation

import android.app.ActivityOptions
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.window.SplashScreen
import app.lawnchair.LawnchairLauncher
import app.lawnchair.compat.LawnchairQuickstepCompat
import app.lawnchair.util.getWindowCornerRadius
import app.lawnchair.util.supportsRoundedCornersOnWindows
import com.android.launcher3.BubbleTextView
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.ActivityOptionsWrapper
import com.android.launcher3.util.Executors
import com.android.launcher3.util.RunnableList
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.max

/**
 * Animación de apertura y cierre de apps de Nexus para cuando el launcher NO es el componente de
 * recientes del sistema (instalación como APK normal), caso en el que Quickstep y sus
 * animaciones remotas están desactivados.
 *
 * Enfoque: **cero bitmaps y cero vistas nuevas**. El propio icono del Workspace se transforma con
 * `translationX/Y` y `scaleX/Y` (matriz directa, aplicada por el RenderThread) desde su celda
 * hasta cubrir la pantalla, y se desvanece mientras la ventana de la app entra. Al volver al
 * inicio el resorte se redirige hacia el icono conservando la velocidad residual: interrupción
 * fluida a 120/140 Hz sin tirones ni reconstrucción de vistas.
 *
 * - Apertura: en Android 14+ con privilegios se entrega una transición remota que recorta y
 *   desplaza la superficie real de la tarea desde el icono hasta la ventana completa
 *   ([NexusAppLaunchTransition]); el icono la acompaña y se desvanece. Sin privilegios se usa el
 *   clip reveal del sistema, o la animación de tarea propia como respaldo.
 * - Cierre: el icono vuelve a su celda con el resorte de cierre, que deja un rebote de asentamiento.
 * - Fondo: solo un zoom sutil de muelle ([BackdropDepth]); sin desenfoques añadidos por Nexus.
 *
 * Compatibilidad: todos los caminos comparten el mismo motor de icono; si la transición de
 * superficie no está disponible se cae con transparencia a la transición del sistema.
 */
class NexusLaunchAnimator(private val launcher: LawnchairLauncher) {

    /** Zoom del último lanzamiento: el que acompaña a la app que se está abriendo ahora. */
    @Volatile
    private var session: ZoomSession? = null

    /**
     * Todos los zooms vivos, incluidos los que aún vuelven a su celda mientras se lanza otra app.
     * Conviven para que abrir otra app a medio cerrar la anterior no corte ninguna animación.
     */
    private val zooms = ArrayList<ZoomSession>(MAX_ZOOMS)

    /**
     * Última transición de superficie entregada al sistema. Se conserva para poder marcarla como
     * cancelada si el lanzamiento ni siquiera llega a producirse.
     */
    private var lastSurfaceTransition: CancellableTransition? = null

    private var launchPending = false
    private var wentToBackground = false
    private var launcherResumed = true

    /** Últimas opciones entregadas al sistema, para reconocerlas si el lanzamiento falla. */
    private var lastWrapper: WeakReference<ActivityOptionsWrapper>? = null

    /** Zoom sutil del fondo mientras hay una app abierta o abriéndose. */
    private val backdrop = BackdropDepth(
        frameRateView = { launcher.dragLayer },
        systemScale = { NexusMotion.systemScale(launcher) },
        depthController = { launcher.depthController },
    )

    private val failSafe = Runnable {
        // La app no llegó a ponerse delante. Si el launcher sigue visible hay que devolver el
        // icono a su sitio; si está en pausa, la vuelta la gestiona onLauncherResumed.
        if (launcherResumed) {
            launchPending = false
            returnZoom()
            backdrop.leave(NexusMotion.profile)
        }
    }

    /**
     * Devuelve las opciones de lanzamiento de Nexus, o `null` si hay que usar la animación
     * por defecto (vista que no es un icono, animaciones del sistema desactivadas, etc.).
     * Abre con el reveal del sistema, que no necesita ningún permiso.
     */
    fun createLaunchOptions(v: View?): ActivityOptionsWrapper? {
        val target = resolveTarget(v) ?: return null
        val icon = target.view as? BubbleTextView ?: return null
        startZoom(target)
        val options = Utilities.allowBGLaunch(
            ActivityOptions.makeClipRevealAnimation(
                icon,
                (icon.width - target.bounds.width()) / 2,
                icon.paddingTop,
                target.bounds.width(),
                target.bounds.height(),
            ),
        )
        if (Utilities.ATLEAST_T) {
            options.splashScreenStyle = SplashScreen.SPLASH_SCREEN_STYLE_ICON
        }
        options.launchDisplayId = icon.display?.displayId ?: Display.DEFAULT_DISPLAY
        armFailSafe()
        Log.i(TAG, "Lanzamiento con reveal del sistema (sdk=${Build.VERSION.SDK_INT})")
        // No se registra en `lastWrapper`: son opciones normales y no hay nada que reintentar
        // (si falla, es que la app no está instalada y el aviso es el correcto).
        return ActivityOptionsWrapper(options, RunnableList())
    }

    /**
     * Opciones con la animación de Nexus, o `null` si no se pueden usar.
     * Solo valen para un lanzamiento con privilegios (módulo de sistema) o identidad `shell`
     * (Shizuku): el sistema rechaza las transiciones propias a un APK normal.
     *
     * En Android 14+ se usa una transición remota que mueve la superficie real de la tarea. Si no
     * está disponible, se mantiene la animación de ventana con `overrideTaskTransition`.
     */
    fun createSheetLaunchOptions(v: View?): ActivityOptionsWrapper? {
        val target = resolveTarget(v) ?: return null
        if (optionsRejected) return skip("el sistema rechazó antes las opciones de Nexus")

        // Las opciones se piden primero: si el sistema no iba a respetar la animación, no se
        // anima el icono y se usa la transición estándar.
        val callbacks = RunnableList()
        val options = makeRemoteOptions(target)
            ?: makeCustomOptions(target.view, callbacks)
            ?: return skip("no se pudieron crear las opciones")

        startZoom(target)
        armFailSafe()
        return ActivityOptionsWrapper(options, callbacks).also { lastWrapper = WeakReference(it) }
    }

    private fun makeRemoteOptions(target: LaunchTarget): ActivityOptions? {
        if (Build.VERSION.SDK_INT < REMOTE_TRANSITION_MIN_SDK) return null
        val icon = iconScreenBounds(target) ?: return null
        return runCatching {
            val radius = if (supportsRoundedCornersOnWindows(launcher)) {
                getWindowCornerRadius(launcher)
            } else {
                0f
            }
            val transition = NexusAppLaunchTransition(
                iconBounds = Rect().also { icon.round(it) },
                cornerRadius = radius,
                systemScale = NexusMotion.systemScale(launcher),
                expectedPackage = (target.view.tag as? ItemInfo)?.targetComponent?.packageName,
                progressProvider = { session?.driver?.progress ?: 0f },
                velocityProvider = { session?.driver?.velocity ?: 0f },
            )
            lastSurfaceTransition?.cancelIfPending()
            lastSurfaceTransition = transition
            LaunchHub.register(transition)

            val remote = LawnchairQuickstepCompat.remoteTransitionCompat
                .getRemoteTransition(transition, null, "NexusAppLaunch")
            val options = Utilities.allowBGLaunch(ActivityOptions.makeRemoteTransition(remote))
            if (Utilities.ATLEAST_T) {
                options.splashScreenStyle = SplashScreen.SPLASH_SCREEN_STYLE_ICON
            }
            options.launchDisplayId = target.view.display?.displayId ?: Display.DEFAULT_DISPLAY
            Log.i(
                TAG,
                "Lanzamiento con transición de superficie " +
                    "(icono=$icon, radio=$radius, sdk=${Build.VERSION.SDK_INT})",
            )
            options
        }.onFailure {
            lastSurfaceTransition?.cancelIfPending()
            lastSurfaceTransition = null
            Log.w(TAG, "No se pudo crear la transición de superficie; se usa la de ventana", it)
        }.getOrNull()
    }

    /**
     * Centro y tamaño del icono en coordenadas de pantalla, con la escala real que tenga en este
     * momento (puede estar dentro de una carpeta o de una página desplazada).
     */
    private fun iconScreenBounds(target: LaunchTarget): RectF? {
        val view = target.view
        val viewRect = Rect()
        val scale = launcher.dragLayer.getDescendantRectRelativeToSelf(view, viewRect)
        if (viewRect.isEmpty || scale <= 0f) return null
        val width = target.bounds.width() * scale
        val height = target.bounds.height() * scale
        if (width <= 0f || height <= 0f) return null
        val origin = IntArray(2)
        launcher.dragLayer.getLocationOnScreen(origin)
        val centerX = origin[0] + viewRect.left + target.bounds.exactCenterX() * scale
        val centerY = origin[1] + viewRect.top + target.bounds.exactCenterY() * scale
        return RectF(
            centerX - width / 2f,
            centerY - height / 2f,
            centerX + width / 2f,
            centerY + height / 2f,
        )
    }

    /**
     * Vista que se va a lanzar, con sus medidas ya comprobadas. Para un icono de app, [bounds] es
     * el recuadro del icono dentro de la vista; para cualquier otra vista (resultado de búsqueda,
     * acceso directo...) es la vista entera.
     */
    private class LaunchTarget(val view: View, val bounds: Rect)

    private fun resolveTarget(v: View?): LaunchTarget? {
        if (v == null) return skip("el lanzamiento no viene de una vista")
        if (!NexusMotion.isEnabled(launcher)) return skip("animaciones del sistema desactivadas")
        if (!v.isAttachedToWindow) return skip("la vista no está en la ventana")
        val dragLayer = launcher.dragLayer
        if (dragLayer.width == 0 || dragLayer.height == 0) {
            return skip("el launcher aún no tiene medidas (${dragLayer.width}x${dragLayer.height})")
        }
        if (v is BubbleTextView) {
            val icon = v.icon ?: return skip("el icono no tiene drawable")
            val bounds = Rect(icon.bounds)
            if (bounds.isEmpty || v.width == 0) {
                return skip("medidas vacías (icono=$bounds, vista=${v.width})")
            }
            return LaunchTarget(v, bounds)
        }
        if (v.width == 0 || v.height == 0) {
            return skip("la vista ${v.javaClass.simpleName} no tiene medidas (${v.width}x${v.height})")
        }
        return LaunchTarget(v, Rect(0, 0, v.width, v.height))
    }

    /**
     * Arranca (o redirige) el zoom del icono hacia pantalla completa. Si ya había un zoom del
     * mismo icono se conserva la velocidad; si era de otro, se devuelve a su sitio sin animar
     * porque el nuevo lanzamiento va a cubrir la pantalla.
     */
    private fun startZoom(target: LaunchTarget) {
        // Cualquier fallo al preparar el zoom no puede tumbar el lanzamiento: se cae con
        // transparencia a la transición del sistema.
        try {
            startZoomInternal(target)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo preparar el zoom del icono; se usa la transición normal", e)
            endZoomNow()
        }
    }

    private fun startZoomInternal(target: LaunchTarget) {
        val v = target.view
        val profile = NexusMotion.profile
        val same = zooms.firstOrNull { it.isFor(v, target.bounds) }
        if (same != null) {
            // Abrir y cerrar rápido la misma app: el resorte cambia de rumbo en caliente. Si había
            // otro zoom abriéndose, ese pasa a volver a su celda.
            session?.takeIf { it !== same }?.let { fold(it) }
            session = same
            same.driver.animateTo(1f, profile.open, profile.speed)
            backdrop.enter(profile)
            return
        }

        val viewRect = Rect()
        val scale = launcher.dragLayer.getDescendantRectRelativeToSelf(v, viewRect)
        if (viewRect.isEmpty || scale <= 0f) {
            // Sin geometría fiable no se anima: la app se abre con la transición normal.
            backdrop.enter(profile)
            return
        }

        // Otra app: el zoom anterior no se corta, sigue su camino de vuelta a la celda mientras
        // nace el nuevo.
        session?.let { fold(it) }
        val newSession = ZoomSession(v, v.tag, target.bounds, viewRect, scale)
        zooms.add(newSession)
        session = newSession
        trimZooms()
        newSession.driver.snapTo(0f)
        newSession.driver.animateTo(1f, profile.open, profile.speed)
        backdrop.enter(profile)
    }

    /**
     * Vuelta al inicio: todos los zooms vivos redirigen su resorte hacia su icono conservando la
     * velocidad residual (interrupción en tiempo real). Los de [keep] se dejan como están.
     */
    private fun returnZoom(keep: ZoomSession? = null) {
        for (zoom in ArrayList(zooms)) {
            if (zoom !== keep) fold(zoom)
        }
    }

    /** Pliega un zoom hacia su celda desde su estado actual, y lo libera al llegar. */
    private fun fold(zoom: ZoomSession) {
        if (!zoom.driver.isRunning && zoom.driver.progress == 0f) {
            endZoom(zoom)
            return
        }
        val profile = NexusMotion.profile
        zoom.driver.animateTo(0f, profile.close, profile.speed) { finished ->
            if (finished) endZoom(zoom)
        }
    }

    /** Libera un zoom y deja su icono como estaba. */
    private fun endZoom(zoom: ZoomSession) {
        zooms.remove(zoom)
        if (session === zoom) session = null
        zoom.release()
    }

    /** Libera todos los zooms sin animar. */
    private fun endZoomNow() {
        for (zoom in ArrayList(zooms)) endZoom(zoom)
        session = null
    }

    /** Evita que se acumulen zooms si el usuario lanza apps más rápido de lo que vuelven. */
    private fun trimZooms() {
        while (zooms.size > MAX_ZOOMS) {
            val oldest = zooms.firstOrNull { it !== session } ?: return
            endZoom(oldest)
        }
    }

    /** Deja armado el temporizador de seguridad del lanzamiento. */
    private fun armFailSafe() {
        launchPending = true
        wentToBackground = false
        val dragLayer = launcher.dragLayer
        dragLayer.removeCallbacks(failSafe)
        dragLayer.postDelayed(failSafe, FAIL_SAFE_MS)
    }

    /**
     * El sistema rechazó el lanzamiento (`startActivity` lanzó una excepción). Si las opciones
     * eran las de Nexus, se devuelve el icono sin animación y se pide reintentar con las opciones
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
        abortLaunch()
        optionsRejected = true
        return true
    }

    /**
     * El lanzamiento privilegiado no llegó a hacerse: se devuelve el icono sin animación para que
     * la apertura normal, con el reveal del sistema, no quede deformada.
     */
    fun onPrivilegedLaunchFailed() {
        Log.w(TAG, "El lanzamiento privilegiado falló; se abre con el reveal del sistema")
        abortLaunch()
    }

    /**
     * La actividad entregada ya estaba delante: la app está abierta pero el sistema no hará
     * ninguna transición de ventana, así que el zoom del icono no tiene nada que acompañar.
     */
    fun onLaunchWithoutTransition() {
        Log.i(TAG, "La actividad ya estaba delante: no hay transición, se devuelve el icono")
        val callbacks = lastWrapper?.get()?.onEndCallback
        abortLaunch()
        callbacks?.executeAllAndDestroy()
    }

    /** Deshace lo preparado para un lanzamiento que no se llegó a producir. */
    private fun abortLaunch() {
        lastWrapper = null
        launchPending = false
        wentToBackground = false
        launcher.dragLayer.removeCallbacks(failSafe)
        lastSurfaceTransition?.cancelIfPending()
        lastSurfaceTransition = null
        // Solo el zoom de este lanzamiento: los de apps anteriores siguen volviendo a su celda.
        session?.let { endZoom(it) }
        backdrop.leave(NexusMotion.profile)
    }

    /**
     * Resultado del reintento. Si también falla, el problema no eran las opciones (por ejemplo,
     * la app ya no está instalada) y la animación de Nexus se vuelve a permitir.
     */
    fun onLaunchRetried(success: Boolean) {
        if (!success) optionsRejected = false
    }

    fun onLauncherPaused() {
        launcherResumed = false
        Log.i(TAG, "Launcher en pausa (lanzamiento pendiente=$launchPending)")
        if (launchPending) wentToBackground = true
    }

    fun onLauncherResumed() {
        launcherResumed = true
        // Si hay un lanzamiento armado y el launcher aún no pasó a segundo plano, este "resumed"
        // es anterior a ese lanzamiento (por ejemplo, el cierre de la app anterior que llega
        // tarde): no debe replegar el zoom de la app que se está abriendo ahora.
        val launching = launchPending && !wentToBackground
        Log.i(TAG, "Launcher reanudado (venía de una apertura de Nexus=$wentToBackground, lanzando=$launching)")
        wentToBackground = false
        if (!launching) {
            launchPending = false
            launcher.dragLayer.removeCallbacks(failSafe)
        }

        if (!NexusMotion.isEnabled(launcher)) {
            endZoomNow()
            backdrop.reset()
            return
        }
        if (launching) {
            returnZoom(keep = session)
            return
        }
        // El fondo vuelve a su sitio con el mismo resorte, redirigido desde donde esté.
        backdrop.leave(NexusMotion.profile)
        // Interrupción: los iconos cambian de rumbo y se pliegan a su celda conservando la
        // velocidad que llevaban, en vez de cortarse de golpe.
        returnZoom()
    }

    fun onLauncherDestroyed() {
        launcher.dragLayer.removeCallbacks(failSafe)
        lastSurfaceTransition?.cancelIfPending()
        lastSurfaceTransition = null
        endZoomNow()
        backdrop.reset()
        lastWrapper = null
    }

    /** Anota por qué este lanzamiento no usa la animación de Nexus y devuelve `null`. */
    private fun <T : Any> skip(reason: String): T? {
        Log.i(TAG, "Sin animación de Nexus: $reason")
        return null
    }

    /**
     * Zoom de un icono: lleva el progreso 0..1 con su propio [MotionDriver] y lo traduce a
     * transformaciones matriciales directas sobre la vista. No captura nada: el icono que se
     * mueve es el de verdad, y sus transformaciones y las de sus padres (clipping) se restauran
     * al terminar.
     *
     * Solo se crea un objeto por lanzamiento; durante la animación no se asigna memoria.
     */
    private inner class ZoomSession(
        view: View,
        private val tag: Any?,
        private val iconBounds: Rect,
        viewRect: Rect,
        viewScale: Float,
    ) {

        private val viewRef = WeakReference(view)

        private val originalAlpha = view.alpha
        private val originalScaleX = view.scaleX
        private val originalScaleY = view.scaleY
        private val originalTranslationX = view.translationX
        private val originalTranslationY = view.translationY
        private val originalPivotX = view.pivotX
        private val originalPivotY = view.pivotY

        /** Centro del icono dentro de la vista: el pivote para que el zoom nazca del icono exacto. */
        private val pivotX = iconBounds.exactCenterX()
        private val pivotY = iconBounds.exactCenterY()

        private val screenWidth = launcher.dragLayer.width.toFloat()
        private val screenHeight = launcher.dragLayer.height.toFloat()

        private val iconWidth = (iconBounds.width() * viewScale).coerceAtLeast(1f)
        private val iconHeight = (iconBounds.height() * viewScale).coerceAtLeast(1f)

        /** Escala uniforme que hace que el icono cubra la pantalla sin deformarse. */
        private val coverScale = max(screenWidth / iconWidth, screenHeight / iconHeight)

        /** Desplazamiento que lleva el centro del icono al centro de la pantalla. */
        private val dx = screenWidth / 2f - (viewRect.left + pivotX * viewScale)
        private val dy = screenHeight / 2f - (viewRect.top + pivotY * viewScale)

        val driver = MotionDriver(
            frameRateView = { launcher.dragLayer },
            systemScale = { NexusMotion.systemScale(launcher) },
            onProgress = { p -> applyTransform(p) },
        )

        /**
         * Permite que el icono crezca por encima de su celda; se restaura al terminar. Se crea al
         * final para que, si algo anterior falla, no queden flags de recorte desactivados.
         */
        private val clipping = ClippingGuard(launcher.dragLayer, view)

        init {
            // La etiqueta del icono no debe ampliarse con él.
            (view as? BubbleTextView)?.setTextVisibility(false)
        }

        private fun applyTransform(p: Float) {
            val v = viewRef.get() ?: return
            // Si la vista se recicló para otro elemento, no se toca.
            if (v.tag !== tag) return
            v.pivotX = pivotX
            v.pivotY = pivotY
            val scale = lerp(1f, coverScale, p)
            v.scaleX = scale
            v.scaleY = scale
            v.translationX = dx * p
            v.translationY = dy * p
            // El icono se desvanece mientras la ventana de la app lo cubre.
            v.alpha = (1f - p / FADE_AT).coerceIn(0f, 1f)
        }

        /** `true` si [v] es el mismo icono: misma vista, mismo elemento y misma posición. */
        fun isFor(v: View, bounds: Rect): Boolean {
            return viewRef.get() === v && tag === v.tag && isClose(iconBounds, bounds)
        }

        fun release() {
            driver.cancel()
            clipping.release()
            val v = viewRef.get() ?: return
            (v as? BubbleTextView)?.setTextVisibility(true)
            if (v.tag === tag) {
                v.alpha = originalAlpha
                v.scaleX = originalScaleX
                v.scaleY = originalScaleY
                v.translationX = originalTranslationX
                v.translationY = originalTranslationY
                v.pivotX = originalPivotX
                v.pivotY = originalPivotY
            } else {
                // Vista reciclada: se deja en su estado neutro.
                v.alpha = 1f
                v.scaleX = 1f
                v.scaleY = 1f
                v.translationX = 0f
                v.translationY = 0f
            }
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

    companion object {
        private const val TAG = "NexusLaunchAnimator"

        /**
         * `true` cuando el sistema rechazó un lanzamiento con las opciones de Nexus y el reintento
         * con las normales funcionó: no se vuelven a pedir mientras viva el proceso.
         */
        @Volatile
        private var optionsRejected = false

        private const val FAIL_SAFE_MS = 2500L

        /** Zooms de icono que pueden convivir (una app abriéndose y las que aún vuelven). */
        private const val MAX_ZOOMS = 3
        private const val POSITION_TOLERANCE_PX = 1.5f

        /** A partir de este progreso el icono ya se ha desvanecido del todo. */
        private const val FADE_AT = 0.55f

        private const val REMOTE_TRANSITION_MIN_SDK = 34
        private const val TASK_OVERRIDE_MIN_SDK = 34
        private const val OVERRIDE_TASK_FIELD = "mOverrideTaskTransition"
        private const val OVERRIDE_TASK_SETTER = "setOverrideTaskTransition"
        private const val OVERRIDE_TASK_GETTER = "getOverrideTaskTransition"

        private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

        private fun isClose(a: Rect, b: Rect): Boolean {
            return abs(a.left - b.left) < POSITION_TOLERANCE_PX &&
                abs(a.top - b.top) < POSITION_TOLERANCE_PX &&
                abs(a.right - b.right) < POSITION_TOLERANCE_PX &&
                abs(a.bottom - b.bottom) < POSITION_TOLERANCE_PX
        }
    }
}

/**
 * Desactiva el recorte en la cadena de padres de [view] hasta [root] para que el icono pueda crecer
 * por encima de su celda (igual que hace el sistema al abrir una carpeta), y lo restaura después.
 * Solo asigna al crearse; no toca nada por fotograma.
 *
 * Varios iconos de la misma página pueden estar animándose a la vez: el estado original de cada
 * padre se guarda una sola vez y solo se restaura cuando se libera el último.
 */
private class ClippingGuard(root: View, view: View?) {

    private val parents = ArrayList<ViewGroup>(4)
    private var released = false

    init {
        var parent: ViewParent? = view?.parent
        while (parent is ViewGroup && parent !== root) {
            parents.add(parent)
            ClippingRegistry.acquire(parent)
            parent = parent.parent
        }
    }

    fun release() {
        if (released) return
        released = true
        for (parent in parents) ClippingRegistry.release(parent)
        parents.clear()
    }
}

/** Cuenta quién ha desactivado el recorte de cada vista y recuerda cómo estaba. */
private object ClippingRegistry {

    private class Entry(
        var count: Int,
        val clipChildren: Boolean,
        val clipToPadding: Boolean,
        val clipToOutline: Boolean,
    )

    private val entries = WeakHashMap<ViewGroup, Entry>()

    fun acquire(group: ViewGroup) {
        val entry = entries[group]
        if (entry != null) {
            entry.count += 1
            return
        }
        entries[group] = Entry(1, group.clipChildren, group.clipToPadding, group.clipToOutline)
        group.clipChildren = false
        group.clipToPadding = false
        group.clipToOutline = false
    }

    fun release(group: ViewGroup) {
        val entry = entries[group] ?: return
        entry.count -= 1
        if (entry.count > 0) return
        entries.remove(group)
        group.clipChildren = entry.clipChildren
        group.clipToPadding = entry.clipToPadding
        group.clipToOutline = entry.clipToOutline
    }
}
