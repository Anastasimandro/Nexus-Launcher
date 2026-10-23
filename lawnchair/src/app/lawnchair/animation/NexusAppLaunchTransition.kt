package app.lawnchair.animation

import android.app.WindowConfiguration
import android.graphics.Rect
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import android.view.SurfaceControl
import android.view.WindowManager
import android.window.IRemoteTransitionFinishedCallback
import android.window.RemoteTransitionStub
import android.window.TransitionInfo
import com.android.launcher3.util.Executors

/** Forma segura de guardar una transición sin nombrar las clases de Android 14+ en el campo. */
internal interface CancellableTransition {
    fun cancelIfPending()
}

/**
 * Transición remota de apertura: lleva la superficie real de la tarea desde el icono hasta la
 * pantalla completa con un único resorte, sin capturas ni bitmaps.
 *
 * El estado inicial se aplica en el hilo de binder (antes de devolver el control al sistema, que
 * asume que la transacción de arranque ya se aplicó) y el resorte corre en el hilo principal con
 * [MotionDriver]. Cada fotograma reutiliza la misma [SurfaceControl.Transaction].
 */
internal class NexusAppLaunchTransition(
    iconBounds: Rect,
    private val cornerRadius: Float,
    private val systemScale: Float,
    private val progressProvider: () -> Float,
) : RemoteTransitionStub(), CancellableTransition {

    private val icon = Rect(iconBounds)

    @Volatile
    private var cancelled = false

    @Volatile
    private var started = false

    @Volatile
    private var animation: SurfaceAnimation? = null

    override fun cancelIfPending() {
        if (!started) cancelled = true
    }

    override fun startAnimation(
        transition: IBinder,
        info: TransitionInfo,
        startT: SurfaceControl.Transaction,
        finishCallback: IRemoteTransitionFinishedCallback,
    ) {
        started = true
        if (cancelled) {
            finishImmediately(finishCallback)
            return
        }

        val change = findOpeningTask(info)
        if (change == null) {
            Log.i(TAG, "Sin tarea de apertura que animar; se deja terminar la transición")
            finishImmediately(finishCallback)
            return
        }

        val end = change.endAbsBounds
        if (end.width() <= 0 || end.height() <= 0) {
            Log.i(TAG, "La tarea de apertura no tiene límites ($end); sin animación")
            finishImmediately(finishCallback)
            return
        }
        if (change.startRotation != change.endRotation) {
            // La geometría del recorte no contempla rotaciones: mejor dejarla sin animación.
            Log.i(TAG, "La apertura cambia de rotación; sin animación de superficie")
            finishImmediately(finishCallback)
            return
        }

        val rootIndex = info.findRootIndex(change.endDisplayId)
        val root = if (rootIndex >= 0) info.getRoot(rootIndex) else null
        val rootOffsetX: Float = if (root != null) root.offset.x.toFloat() else 0f
        val rootOffsetY: Float = if (root != null) root.offset.y.toFloat() else 0f

        val animation = try {
            SurfaceAnimation(change.leash, end, rootOffsetX, rootOffsetY)
        } catch (t: Throwable) {
            Log.w(TAG, "No se pudo preparar la animación de la superficie", t)
            finishImmediately(finishCallback)
            return
        }

        try {
            animation.applyStart(startT)
        } catch (t: Throwable) {
            Log.w(TAG, "No se pudo aplicar el estado inicial de la superficie", t)
            finishImmediately(finishCallback)
            return
        }

        this.animation = animation
        Executors.MAIN_EXECUTOR.execute { animation.start(finishCallback) }
    }

    override fun mergeAnimation(
        transition: IBinder,
        info: TransitionInfo,
        t: SurfaceControl.Transaction,
        mergeTarget: IBinder,
        finishCallback: IRemoteTransitionFinishedCallback,
    ) {
        cancelled = true
        runCatching { t.close() }
        Executors.MAIN_EXECUTOR.execute { animation?.cancelFromSystem() }
        try {
            finishCallback.onTransitionFinished(null, null)
        } catch (e: RemoteException) {
            Log.w(TAG, "No se pudo cerrar la transición absorbida", e)
        }
    }

    override fun onTransitionConsumed(transition: IBinder, aborted: Boolean) {
        cancelled = true
        Log.i(TAG, "Transición consumida (aborted=$aborted); se detiene la animación")
        Executors.MAIN_EXECUTOR.execute { animation?.cancelFromSystem() }
    }

    private fun findOpeningTask(info: TransitionInfo): TransitionInfo.Change? {
        var best: TransitionInfo.Change? = null
        var bestArea = 0
        val changes = info.changes
        for (i in changes.indices) {
            val change = changes[i]
            val mode = change.mode
            if (mode != WindowManager.TRANSIT_OPEN && mode != WindowManager.TRANSIT_TO_FRONT) continue
            val flags = change.flags
            if (flags and (TransitionInfo.FLAG_IS_WALLPAPER or
                    TransitionInfo.FLAG_IS_INPUT_METHOD or
                    TransitionInfo.FLAG_IS_SYSTEM_WINDOW or
                    TransitionInfo.FLAG_NO_ANIMATION or
                    TransitionInfo.FLAG_IS_OCCLUDED) != 0
            ) {
                continue
            }
            val task = change.taskInfo ?: continue
            if (task.activityType == WindowConfiguration.ACTIVITY_TYPE_HOME) continue
            if (task.parentTaskId != -1) continue
            val bounds = change.endAbsBounds
            val area = bounds.width() * bounds.height()
            if (area <= 0) continue
            if (area > bestArea) {
                bestArea = area
                best = change
            }
        }
        return best
    }

    private fun finishImmediately(callback: IRemoteTransitionFinishedCallback) {
        try {
            callback.onTransitionFinished(null, null)
        } catch (e: RemoteException) {
            Log.w(TAG, "No se pudo cerrar la transición sin animación", e)
        }
    }

    private inner class SurfaceAnimation(
        private val leash: SurfaceControl,
        endBounds: Rect,
        private val rootOffsetX: Float,
        private val rootOffsetY: Float,
    ) {
        private val endPositionX = endBounds.left - rootOffsetX
        private val endPositionY = endBounds.top - rootOffsetY
        private val finalCrop = Rect(0, 0, endBounds.width(), endBounds.height())

        /** Misma geometría que el cierre de Quickstep: escala uniforme y recorte. */
        private val geometry = IconWindowGeometry(
            iconCenterX = icon.exactCenterX() - rootOffsetX,
            iconCenterY = icon.exactCenterY() - rootOffsetY,
            iconWidth = icon.width().toFloat(),
            iconHeight = icon.height().toFloat(),
            windowLeft = endPositionX,
            windowTop = endPositionY,
            windowWidth = endBounds.width().toFloat(),
            windowHeight = endBounds.height().toFloat(),
            endRadius = cornerRadius,
            roundIcon = cornerRadius > 0f,
        )
        private val geometryFrame = IconWindowGeometry.Frame()
        private val startProgress = progressProvider().coerceIn(0f, 1f)

        private val crop = Rect()
        private var frame: SurfaceControl.Transaction? = null
        private var driver: MotionDriver? = null
        private var finishCallback: IRemoteTransitionFinishedCallback? = null

        @Volatile
        private var finished = false

        @Volatile
        private var closed = false

        fun applyStart(startT: SurfaceControl.Transaction) {
            startT.show(leash)
            startT.setAlpha(leash, 1f)
            setTransform(startT, startProgress)
            startT.apply()
        }

        fun start(callback: IRemoteTransitionFinishedCallback) {
            if (cancelled) {
                closeFrame()
                return
            }
            finishCallback = callback
            val transaction = SurfaceControl.Transaction()
            frame = transaction
            val motion = MotionDriver(
                frameRateView = { null },
                systemScale = { systemScale },
                onProgress = { progress -> applyProgress(progress) },
            )
            driver = motion
            if (startProgress > 0f) motion.snapTo(startProgress)
            motion.animateTo(1f, NexusMotion.profile.open, NexusMotion.profile.speed) { completed ->
                if (completed) finish()
            }
        }

        fun cancelFromSystem() {
            if (finished) return
            cancelled = true
            finishCallback = null
            driver?.cancel()
            driver = null
            closeFrame()
        }

        private fun applyProgress(progress: Float) {
            if (cancelled) return
            val transaction = frame ?: return
            setTransform(transaction, progress.coerceIn(0f, 1f))
            transaction.apply()
        }

        private fun setTransform(t: SurfaceControl.Transaction, p: Float) {
            val g = geometry.at(p, geometryFrame)
            crop.set(g.cropLeft, g.cropTop, g.cropRight, g.cropBottom)
            t.setAlpha(leash, 1f)
            t.setPosition(leash, g.x, g.y)
            t.setMatrix(leash, g.scale, 0f, 0f, g.scale)
            t.setCrop(leash, crop)
            t.setCornerRadius(leash, g.localRadius)
        }

        private fun finish() {
            if (finished || cancelled) return
            finished = true
            val callback = finishCallback
            finishCallback = null
            if (callback != null) {
                val transaction = SurfaceControl.Transaction()
                transaction.setPosition(leash, endPositionX, endPositionY)
                transaction.setMatrix(leash, 1f, 0f, 0f, 1f)
                transaction.setCrop(leash, finalCrop)
                transaction.setCornerRadius(leash, cornerRadius)
                try {
                    callback.onTransitionFinished(null, transaction)
                } catch (e: RemoteException) {
                    Log.w(TAG, "No se pudo cerrar la transición de la superficie", e)
                } finally {
                    transaction.close()
                }
            }
            driver = null
            closeFrame()
        }

        private fun closeFrame() {
            if (closed) return
            closed = true
            runCatching { frame?.close() }
            frame = null
        }
    }

    private companion object {
        private const val TAG = "NexusAppLaunch"
    }
}
