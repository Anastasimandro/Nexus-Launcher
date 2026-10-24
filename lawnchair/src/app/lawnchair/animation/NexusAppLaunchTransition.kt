package app.lawnchair.animation

import android.app.ActivityManager
import android.app.WindowConfiguration
import android.graphics.Rect
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import android.view.SurfaceControl
import android.view.WindowManager
import android.window.IRemoteTransitionFinishedCallback
import android.window.RemoteTransitionStub
import android.window.TransitionInfo
import com.android.launcher3.util.Executors
import kotlin.math.max

/** Forma segura de guardar una transición sin nombrar las clases de Android 14+ en el campo. */
internal interface CancellableTransition {
    fun cancelIfPending()
}

/**
 * Transición remota de apertura de una app: lleva la superficie real de la tarea desde el icono
 * hasta la pantalla completa con un resorte, sin capturas ni bitmaps.
 *
 * Cada lanzamiento crea una instancia. Cuando el sistema la arranca se abre una
 * [TransitionSession] que anima la ventana y que además puede **absorber interrupciones** mientras
 * dura: volver al inicio a medio abrir (la ventana da la vuelta hacia el icono sin saltos),
 * reabrir la misma app, o abrir otra app encima (las dos animaciones conviven). Lo que no puede
 * absorber lo rechaza sin hacer nada, que es lo que manda el contrato del sistema: esa transición
 * espera su turno.
 */
internal class NexusAppLaunchTransition(
    iconBounds: Rect,
    internal val cornerRadius: Float,
    internal val systemScale: Float,
    /** Paquete que se lanza, para no confundir este lanzamiento con otro al absorber. */
    internal val expectedPackage: String?,
    internal val progressProvider: () -> Float,
    internal val velocityProvider: () -> Float,
) : RemoteTransitionStub(),
    CancellableTransition {

    internal val icon = Rect(iconBounds)

    /** Instante del toque (se crea al lanzar): sirve para medir cuándo aparece la ventana. */
    internal val createdNanos = System.nanoTime()

    @Volatile
    private var cancelled = false

    /** `true` cuando el sistema la arrancó o una sesión la adoptó: ya no está pendiente. */
    @Volatile
    private var started = false

    /** Un lanzamiento que aún puede ser arrancado o adoptado. */
    internal fun isPending() = !cancelled && !started

    /** Una sesión en marcha adopta este lanzamiento: el aviso de "consumida" ya no le afecta. */
    internal fun markAdopted() {
        started = true
    }

    override fun cancelIfPending() {
        if (started) return
        cancelled = true
        LaunchHub.unregister(this)
    }

    override fun startAnimation(
        transition: IBinder,
        info: TransitionInfo,
        startT: SurfaceControl.Transaction,
        finishCallback: IRemoteTransitionFinishedCallback,
    ) {
        started = true
        LaunchHub.unregister(this)
        val session = TransitionSession(transition, finishCallback, systemScale)
        if (cancelled) {
            session.giveUp(startT)
            return
        }
        LaunchHub.putSession(transition, session)
        session.begin(this, info, startT)
    }

    override fun mergeAnimation(
        transition: IBinder,
        info: TransitionInfo,
        t: SurfaceControl.Transaction,
        mergeTarget: IBinder,
        finishCallback: IRemoteTransitionFinishedCallback,
    ) {
        val session = LaunchHub.sessionFor(mergeTarget)
        val merged = try {
            session != null && session.tryMerge(info, t)
        } catch (e: Throwable) {
            NexusLog.w(TAG, "Fallo al absorber la transición; se rechaza", e)
            false
        }
        if (!merged) {
            // Rechazada: según el contrato no se hace nada y la transición espera su turno.
            runCatching { t.close() }
            return
        }
        // Absorbida: hay que avisar de inmediato, antes de terminar la transición original.
        try {
            finishCallback.onTransitionFinished(null, null)
        } catch (e: RemoteException) {
            NexusLog.w(TAG, "No se pudo confirmar la transición absorbida", e)
        }
    }

    override fun onTransitionConsumed(transition: IBinder, aborted: Boolean) {
        // Si una sesión la adoptó o ya arrancó, este aviso llega tarde y no significa nada.
        if (started) return
        cancelled = true
        LaunchHub.unregister(this)
        NexusLog.i(TAG, "Transición consumida antes de empezar (aborted=$aborted)")
    }

    internal companion object {
        const val TAG = "NexusAppLaunch"
    }
}

/**
 * Registro de lanzamientos pendientes y de sesiones en marcha. Permite a la sesión de una app
 * encontrar el lanzamiento de la siguiente cuando el sistema se la ofrece para absorber.
 */
internal object LaunchHub {

    private const val MAX_PENDING = 6

    private val lock = Any()
    private val pending = ArrayList<NexusAppLaunchTransition>(MAX_PENDING)
    private val sessions = HashMap<IBinder, TransitionSession>(4)

    fun register(launch: NexusAppLaunchTransition) {
        synchronized(lock) {
            pending.add(launch)
            // Los lanzamientos que nunca llegaron a producir transición no se acumulan.
            while (pending.size > MAX_PENDING) pending.removeAt(0).cancelIfPending()
        }
    }

    fun unregister(launch: NexusAppLaunchTransition) {
        synchronized(lock) { pending.remove(launch) }
    }

    fun hasPending(packageName: String?): Boolean = synchronized(lock) {
        pending.any { it.isPending() && matches(it, packageName) }
    }

    /** Entrega, y quita de la lista, el lanzamiento pendiente más antiguo de ese paquete. */
    fun takePending(packageName: String?): NexusAppLaunchTransition? = synchronized(lock) {
        val found = pending.firstOrNull { it.isPending() && matches(it, packageName) }
        if (found != null) {
            pending.remove(found)
            found.markAdopted()
        }
        found
    }

    fun putSession(token: IBinder, session: TransitionSession) {
        synchronized(lock) { sessions[token] = session }
    }

    fun sessionFor(token: IBinder): TransitionSession? = synchronized(lock) { sessions[token] }

    fun removeSession(token: IBinder) {
        synchronized(lock) { sessions.remove(token) }
    }

    private fun matches(launch: NexusAppLaunchTransition, packageName: String?): Boolean {
        val expected = launch.expectedPackage
        return expected == null || packageName == null || expected == packageName
    }
}

/**
 * Una transición del sistema que Nexus está animando. Puede llevar varias [WindowLayer] a la vez
 * (por ejemplo, una app que vuelve a su icono mientras otra se abre).
 *
 * Garantías:
 * - La transición del sistema termina **exactamente una vez**, cuando se asienta la última capa o a
 *   la fuerza si algo falla o se pasa de tiempo. Si no terminara, el sistema encolaría todas las
 *   siguientes detrás de ella.
 * - Cada superficie se deja en su estado neutro al terminar; lo que debe quedar oculto lo oculta el
 *   propio sistema con las transacciones de las transiciones absorbidas.
 * - Los métodos pueden llamarse desde el hilo de binder; el movimiento corre en el principal. El
 *   estado compartido se protege con [lock].
 */
internal class TransitionSession(
    private val token: IBinder,
    private var callback: IRemoteTransitionFinishedCallback?,
    private val systemScale: Float,
) {

    private val lock = Any()
    private val everyLayer = ArrayList<WindowLayer>(2)
    private val core = SessionCore<WindowLayer> { forced -> complete(forced) }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val watchdog = Runnable {
        synchronized(lock) {
            if (!core.finished) {
                NexusLog.w(TAG, "La transición no terminó a tiempo; se fuerza el final")
                core.forceFinish()
            }
        }
    }

    // ---- Entradas del sistema

    /** Primera animación: la app que se abre. */
    fun begin(
        launch: NexusAppLaunchTransition,
        info: TransitionInfo,
        startT: SurfaceControl.Transaction,
    ) {
        try {
            synchronized(lock) {
                val change = findOpeningChange(info)
                val layer = if (change != null) buildLayer(launch, info, change) else null
                if (layer == null) {
                    NexusLog.i(TAG, "Sin tarea de apertura que animar; se deja terminar la transición")
                    startT.apply()
                    startT.close()
                    core.forceFinish()
                } else {
                    everyLayer.add(layer)
                    core.add(layer)
                    layer.writeStart(startT)
                    startT.apply()
                    startT.close()
                    armWatchdog()
                    Executors.MAIN_EXECUTOR.execute { layer.startForward() }
                }
            }
        } catch (e: Throwable) {
            NexusLog.w(TAG, "No se pudo preparar la animación de la superficie", e)
            runCatching { startT.apply() }
            runCatching { startT.close() }
            synchronized(lock) { core.forceFinish() }
        }
    }

    /** El lanzamiento se canceló antes de empezar: se deja pasar sin animar. */
    fun giveUp(startT: SurfaceControl.Transaction) {
        runCatching { startT.apply() }
        runCatching { startT.close() }
        synchronized(lock) { core.forceFinish() }
    }

    /**
     * Intenta absorber una transición que llega mientras esta se anima. `true` solo si se absorbió;
     * en ese caso la transacción [t] ya se aplicó y se cerró aquí.
     */
    fun tryMerge(info: TransitionInfo, t: SurfaceControl.Transaction): Boolean = synchronized(lock) { tryMergeLocked(info, t) }

    private fun tryMergeLocked(info: TransitionInfo, t: SurfaceControl.Transaction): Boolean {
        if (core.finished) return false
        val summary = summarize(info)
        val decision = MergePlanner.decide(
            layerTaskIds = core.snapshot().map { it.taskId },
            changes = summary,
            hasPendingLaunch = { LaunchHub.hasPending(it) },
        )
        return when (decision) {
            is MergeDecision.Reverse -> {
                val layer = core.find(decision.taskId)
                if (layer == null) {
                    false
                } else {
                    NexusLog.i(TAG, "Interrupción: la app cierra a medio abrir; la ventana vuelve al icono")
                    applyMerged(layer, t)
                    Executors.MAIN_EXECUTOR.execute { layer.reverse() }
                    true
                }
            }

            is MergeDecision.Reopen -> {
                val layer = core.find(decision.taskId)
                if (layer == null) {
                    false
                } else {
                    NexusLog.i(TAG, "Interrupción: la app se reabre mientras volvía al icono; cambia de rumbo")
                    // El lanzamiento pendiente de esa misma app queda atendido por esta capa.
                    LaunchHub.takePending(summary.firstOrNull { it.taskId == decision.taskId }?.packageName)
                    applyMerged(layer, t)
                    Executors.MAIN_EXECUTOR.execute { layer.forward() }
                    true
                }
            }

            is MergeDecision.AdoptOpening -> adoptOpening(decision.change, info, t)

            MergeDecision.Reject -> false
        }
    }

    /** Aplica la transacción absorbida dejando la ventana tal como está en este instante. */
    private fun applyMerged(layer: WindowLayer, t: SurfaceControl.Transaction) {
        layer.writeCurrent(t)
        t.apply()
        t.close()
        armWatchdog()
    }

    private fun adoptOpening(
        summary: ChangeSummary,
        info: TransitionInfo,
        t: SurfaceControl.Transaction,
    ): Boolean {
        val change = info.changes.firstOrNull { it.taskInfo?.taskId == summary.taskId } ?: return false
        val launch = LaunchHub.takePending(summary.packageName) ?: return false
        val layer = buildLayer(launch, info, change) ?: return false
        if (!core.add(layer)) return false
        NexusLog.i(TAG, "Interrupción: se abre otra app mientras se anima la anterior; conviven")
        everyLayer.add(layer)
        layer.writeStart(t)
        t.apply()
        t.close()
        armWatchdog()
        Executors.MAIN_EXECUTOR.execute { layer.startForward() }
        return true
    }

    // ---- Desde las capas (hilo principal)

    fun layerSettled(layer: WindowLayer) {
        synchronized(lock) { core.settled(layer) }
    }

    // ---- Internos

    private fun armWatchdog() {
        mainHandler.removeCallbacks(watchdog)
        mainHandler.postDelayed(watchdog, (WATCHDOG_MS * max(1f, systemScale)).toLong())
    }

    /** Termina la transición del sistema una sola vez. Se llama dentro de [lock]. */
    private fun complete(forced: Boolean) {
        mainHandler.removeCallbacks(watchdog)
        LaunchHub.removeSession(token)
        val toFinish = callback
        callback = null
        // Primero se marca todo como muerto: ningún fotograma pendiente debe tocar una superficie
        // que el sistema va a liberar.
        everyLayer.forEach { it.kill() }
        val finalState = SurfaceControl.Transaction()
        everyLayer.forEach { runCatching { it.writeNeutral(finalState) } }
        try {
            toFinish?.onTransitionFinished(null, finalState)
        } catch (e: RemoteException) {
            NexusLog.w(TAG, "No se pudo cerrar la transición de la superficie", e)
        } finally {
            runCatching { finalState.close() }
        }
        val layers = ArrayList(everyLayer)
        everyLayer.clear()
        Executors.MAIN_EXECUTOR.execute { layers.forEach { it.release() } }
        if (forced) NexusLog.i(TAG, "Transición terminada a la fuerza")
    }

    private fun buildLayer(
        launch: NexusAppLaunchTransition,
        info: TransitionInfo,
        change: TransitionInfo.Change,
    ): WindowLayer? {
        val end = change.endAbsBounds
        if (end.width() <= 0 || end.height() <= 0) {
            NexusLog.i(TAG, "La tarea de apertura no tiene límites ($end); sin animación")
            return null
        }
        if (change.startRotation != change.endRotation) {
            // La geometría del recorte no contempla rotaciones: mejor dejarla sin animación.
            NexusLog.i(TAG, "La apertura cambia de rotación; sin animación de superficie")
            return null
        }
        val taskId = change.taskInfo?.taskId ?: return null
        val rootIndex = info.findRootIndex(change.endDisplayId)
        val root = if (rootIndex >= 0) info.getRoot(rootIndex) else null
        val offsetX = if (root != null) root.offset.x.toFloat() else 0f
        val offsetY = if (root != null) root.offset.y.toFloat() else 0f
        return try {
            WindowLayer(this, launch, taskId, change.leash, end, offsetX, offsetY)
        } catch (t: Throwable) {
            NexusLog.w(TAG, "No se pudo preparar la capa de la ventana", t)
            null
        }
    }

    private fun findOpeningChange(info: TransitionInfo): TransitionInfo.Change? {
        var best: TransitionInfo.Change? = null
        var bestArea = 0
        for (change in info.changes) {
            if (kindOf(change) != ChangeSummary.Kind.OPENING) continue
            val bounds = change.endAbsBounds
            val area = bounds.width() * bounds.height()
            if (area > bestArea) {
                bestArea = area
                best = change
            }
        }
        return best
    }

    private fun summarize(info: TransitionInfo): List<ChangeSummary> {
        val out = ArrayList<ChangeSummary>(info.changes.size)
        for (change in info.changes) {
            val task = change.taskInfo
            out.add(
                ChangeSummary(
                    taskId = task?.taskId ?: -1,
                    packageName = if (task != null) packageOf(task) else null,
                    kind = kindOf(change),
                ),
            )
        }
        return out
    }

    private fun packageOf(task: ActivityManager.RunningTaskInfo): String? = try {
        task.baseIntent?.component?.packageName ?: task.topActivity?.packageName
    } catch (e: Throwable) {
        null
    }

    private fun kindOf(change: TransitionInfo.Change): ChangeSummary.Kind {
        val ignored = TransitionInfo.FLAG_IS_WALLPAPER or
            TransitionInfo.FLAG_IS_INPUT_METHOD or
            TransitionInfo.FLAG_IS_SYSTEM_WINDOW or
            TransitionInfo.FLAG_NO_ANIMATION or
            TransitionInfo.FLAG_IS_OCCLUDED
        if (change.flags and ignored != 0) return ChangeSummary.Kind.OTHER
        val task = change.taskInfo ?: return ChangeSummary.Kind.OTHER
        val mode = change.mode
        val comingToFront = mode == WindowManager.TRANSIT_OPEN || mode == WindowManager.TRANSIT_TO_FRONT
        val goingAway = mode == WindowManager.TRANSIT_CLOSE || mode == WindowManager.TRANSIT_TO_BACK
        if (task.activityType == WindowConfiguration.ACTIVITY_TYPE_HOME) {
            return if (comingToFront) ChangeSummary.Kind.HOME_OPENING else ChangeSummary.Kind.OTHER
        }
        if (task.parentTaskId != -1) return ChangeSummary.Kind.OTHER
        return when {
            comingToFront -> ChangeSummary.Kind.OPENING
            goingAway -> ChangeSummary.Kind.CLOSING
            else -> ChangeSummary.Kind.OTHER
        }
    }

    private companion object {
        const val TAG = NexusAppLaunchTransition.TAG

        /** Margen máximo de una animación (un resorte se asienta en menos de 0,6 s). */
        const val WATCHDOG_MS = 2000L
    }
}

/**
 * Una ventana que se anima entre su icono y la pantalla completa. Su progreso 0..1 lo lleva un
 * resorte que puede cambiar de rumbo en cualquier momento conservando la velocidad: abrir, dar la
 * vuelta y volver a abrir no produce saltos de posición, escala, recorte ni esquinas.
 */
internal class WindowLayer(
    private val owner: TransitionSession,
    launch: NexusAppLaunchTransition,
    override val taskId: Int,
    private val leash: SurfaceControl,
    endBounds: Rect,
    rootOffsetX: Float,
    rootOffsetY: Float,
) : SessionCore.Layer {

    private val systemScale = launch.systemScale
    private val launchedNanos = launch.createdNanos
    private val cornerRadius = launch.cornerRadius
    private val endPositionX = endBounds.left - rootOffsetX
    private val endPositionY = endBounds.top - rootOffsetY
    private val neutralCrop = Rect(0, 0, endBounds.width(), endBounds.height())

    /** Misma geometría que el cierre de Quickstep: escala uniforme y recorte. */
    private val geometry = IconWindowGeometry(
        iconCenterX = launch.icon.exactCenterX() - rootOffsetX,
        iconCenterY = launch.icon.exactCenterY() - rootOffsetY,
        iconWidth = launch.icon.width().toFloat(),
        iconHeight = launch.icon.height().toFloat(),
        windowLeft = endPositionX,
        windowTop = endPositionY,
        windowWidth = endBounds.width().toFloat(),
        windowHeight = endBounds.height().toFloat(),
        endRadius = cornerRadius,
        roundIcon = cornerRadius > 0f,
    )

    private val startProgress = launch.progressProvider().coerceIn(0f, 1f)
    private val startVelocity = launch.velocityProvider()

    /** Solo hilo principal. */
    private var driver: MotionDriver? = null
    private var frame: SurfaceControl.Transaction? = null
    private val mainFrame = IconWindowGeometry.Frame()
    private val mainCrop = Rect()

    @Volatile
    private var reversing = false

    @Volatile
    private var dead = false

    // Ritmo de fotogramas de esta animación (hilo principal); se resume en el registro al asentarse.
    private var startedNanos = 0L
    private var lastFrameNanos = 0L
    private var frames = 0
    private var slowFrames = 0
    private var longestGapNanos = 0L

    private fun currentProgress(): Float = (driver?.progress ?: startProgress).coerceIn(0f, 1f)

    // ---- Cualquier hilo: escriben en la transacción que pasa quien llama

    fun writeStart(t: SurfaceControl.Transaction) {
        t.show(leash)
        write(t, startProgress, false, IconWindowGeometry.Frame(), Rect())
    }

    fun writeCurrent(t: SurfaceControl.Transaction) {
        t.show(leash)
        write(t, currentProgress(), reversing, IconWindowGeometry.Frame(), Rect())
    }

    /** Estado final neutro: sin escala, recorte ni transparencia. El sistema oculta lo que toque. */
    fun writeNeutral(t: SurfaceControl.Transaction) {
        t.setAlpha(leash, 1f)
        t.setPosition(leash, endPositionX, endPositionY)
        t.setMatrix(leash, 1f, 0f, 0f, 1f)
        t.setCrop(leash, neutralCrop)
        t.setCornerRadius(leash, cornerRadius)
    }

    fun kill() {
        dead = true
    }

    private fun write(
        t: SurfaceControl.Transaction,
        p: Float,
        reverse: Boolean,
        out: IconWindowGeometry.Frame,
        crop: Rect,
    ) {
        val g = geometry.at(p, out)
        crop.set(g.cropLeft, g.cropTop, g.cropRight, g.cropBottom)
        // Al volver al icono la ventana se funde en el último tramo para que el icono que vuelve a
        // su celda la sustituya sin un corte; al abrirse es opaca desde el primer fotograma.
        val alpha = if (reverse) (p / REVERSE_FADE_UNTIL).coerceIn(0f, 1f) else 1f
        t.setAlpha(leash, alpha)
        t.setPosition(leash, g.x, g.y)
        t.setMatrix(leash, g.scale, 0f, 0f, g.scale)
        t.setCrop(leash, crop)
        t.setCornerRadius(leash, g.localRadius)
    }

    // ---- Hilo principal

    fun startForward() {
        if (dead) return
        startedNanos = System.nanoTime()
        // Cuánto tarda el sistema en entregar la ventana desde el toque: con una starting window
        // lenta es el tiempo durante el que el icono ya se mueve sin ventana debajo.
        val sinceTapMs = (startedNanos - launchedNanos) / NANOS_PER_MS
        NexusLog.i(
            TAG,
            "Apertura tarea=$taskId: la ventana aparece ${fmt(sinceTapMs)} ms después del toque, " +
                "con progreso=${fmt(startProgress)} velocidad=${fmt(startVelocity)}/s " +
                "ventana=${neutralCrop.width()}x${neutralCrop.height()} radio=${fmt(cornerRadius)}",
        )
        frame = SurfaceControl.Transaction()
        val motion = MotionDriver(
            frameRateView = { null },
            systemScale = { systemScale },
            onProgress = { progress -> applyProgress(progress) },
        )
        driver = motion
        if (startProgress > 0f) motion.snapTo(startProgress)
        reversing = false
        motion.animateTo(
            1f,
            NexusMotion.profile.open,
            NexusMotion.profile.speed,
            startVelocity,
        ) { completed -> settled(completed) }
    }

    /** Cambia de rumbo hacia el icono conservando la velocidad que lleve. */
    fun reverse() {
        if (dead) return
        val motion = driver ?: return
        reversing = true
        NexusLog.i(
            TAG,
            "Tarea=$taskId: vuelve al icono desde progreso=${fmt(currentProgress())} velocidad=${fmt(motion.velocity)}/s",
        )
        motion.animateTo(0f, NexusMotion.profile.close, NexusMotion.profile.speed) { completed ->
            settled(completed)
        }
    }

    /** Cambia de rumbo hacia la pantalla completa conservando la velocidad que lleve. */
    fun forward() {
        if (dead) return
        val motion = driver ?: return
        reversing = false
        NexusLog.i(
            TAG,
            "Tarea=$taskId: vuelve a abrirse desde progreso=${fmt(currentProgress())} velocidad=${fmt(motion.velocity)}/s",
        )
        motion.animateTo(1f, NexusMotion.profile.open, NexusMotion.profile.speed) { completed ->
            settled(completed)
        }
    }

    fun release() {
        driver?.cancel()
        driver = null
        runCatching { frame?.close() }
        frame = null
    }

    private fun applyProgress(progress: Float) {
        if (dead) return
        val transaction = frame ?: return
        write(transaction, progress.coerceIn(0f, 1f), reversing, mainFrame, mainCrop)
        transaction.apply()
        countFrame()
    }

    private fun countFrame() {
        val now = System.nanoTime()
        if (lastFrameNanos != 0L) {
            val gap = now - lastFrameNanos
            if (gap > longestGapNanos) longestGapNanos = gap
            if (gap > SLOW_FRAME_NANOS) slowFrames++
        }
        lastFrameNanos = now
        frames++
    }

    /** Un resorte llegó a su destino (o lo cambió otro): se anota el resumen de fluidez. */
    private fun settled(completed: Boolean) {
        if (!completed) return
        val elapsedMs = (System.nanoTime() - startedNanos) / NANOS_PER_MS
        NexusLog.i(
            TAG,
            "Tarea=$taskId asentada: ${fmt(elapsedMs)} ms, $frames fotogramas, " +
                "mayor intervalo ${fmt(longestGapNanos / NANOS_PER_MS)} ms, $slowFrames lentos (>${SLOW_FRAME_MS.toInt()} ms)",
        )
        owner.layerSettled(this)
    }

    private fun fmt(value: Float): String = String.format(java.util.Locale.US, "%.2f", value)

    private companion object {
        /** Por debajo de este progreso, al volver, la ventana se desvanece hasta el icono. */
        const val REVERSE_FADE_UNTIL = 0.25f

        /**
         * Un intervalo mayor que esto entre fotogramas cuenta como lento en el registro: 1,5
         * fotogramas a 60 Hz. Es solo un umbral de diagnóstico, no afecta al movimiento.
         */
        const val SLOW_FRAME_MS = 24f
        const val SLOW_FRAME_NANOS = 24_000_000L
        const val NANOS_PER_MS = 1_000_000f
        const val TAG = NexusAppLaunchTransition.TAG
    }
}
