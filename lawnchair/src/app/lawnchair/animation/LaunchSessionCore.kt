package app.lawnchair.animation

/**
 * Resumen de un cambio de ventana de una transición del sistema, sin tipos de Android para poder
 * probar las decisiones en la JVM.
 */
internal class ChangeSummary(
    val taskId: Int,
    val packageName: String?,
    val kind: Kind,
) {
    enum class Kind {
        /** Una app (no el inicio) que pasa a primer plano. */
        OPENING,

        /** Una app que se cierra o pasa al fondo. */
        CLOSING,

        /** El launcher que vuelve a primer plano. */
        HOME_OPENING,

        /** Fondo de pantalla, teclado, ventanas de sistema... */
        OTHER,
    }
}

/** Qué hacer con una transición que llega mientras otra se está animando. */
internal sealed interface MergeDecision {
    /** La app cierra hacia el inicio: la capa de [taskId] da la vuelta hacia su icono. */
    data class Reverse(val taskId: Int) : MergeDecision

    /** La misma app se vuelve a abrir mientras se cerraba: la capa de [taskId] cambia de rumbo. */
    data class Reopen(val taskId: Int) : MergeDecision

    /** Se abre otra app y hay un lanzamiento de Nexus esperando para animarla. */
    data class AdoptOpening(val change: ChangeSummary) : MergeDecision

    /**
     * No se absorbe: el contrato del sistema dice que entonces no se hace nada y la transición
     * espera su turno. Es siempre la opción segura.
     */
    data object Reject : MergeDecision
}

internal object MergePlanner {

    /**
     * @param layerTaskIds tareas que Nexus está animando ahora mismo.
     * @param changes cambios de la transición entrante.
     * @param hasPendingLaunch si hay un lanzamiento de Nexus pendiente para ese paquete.
     */
    fun decide(
        layerTaskIds: Collection<Int>,
        changes: List<ChangeSummary>,
        hasPendingLaunch: (packageName: String?) -> Boolean,
    ): MergeDecision {
        if (layerTaskIds.isEmpty()) return MergeDecision.Reject

        val opening = changes.filter { it.kind == ChangeSummary.Kind.OPENING }
        val closingOurs = changes.firstOrNull {
            it.kind == ChangeSummary.Kind.CLOSING && it.taskId in layerTaskIds
        }
        val homeComesBack = changes.any { it.kind == ChangeSummary.Kind.HOME_OPENING }

        // Cerrar una de nuestras apps hacia el inicio. Si a la vez se abre otra, o lo que
        // aparece debajo no es el launcher, el movimiento de vuelta al icono no tendría sentido.
        if (closingOurs != null) {
            return if (opening.isEmpty() && homeComesBack) {
                MergeDecision.Reverse(closingOurs.taskId)
            } else {
                MergeDecision.Reject
            }
        }

        // Una sola app que se abre: o es la misma que se estaba cerrando o es otra nueva.
        if (opening.size == 1) {
            val change = opening[0]
            if (change.taskId in layerTaskIds) return MergeDecision.Reopen(change.taskId)
            if (hasPendingLaunch(change.packageName)) return MergeDecision.AdoptOpening(change)
        }
        return MergeDecision.Reject
    }
}

/**
 * Cuenta las capas animadas de una transición y garantiza que la transición original termina
 * exactamente una vez: cuando se asienta la última capa o cuando se fuerza el final.
 *
 * Que la transición original nunca se quede sin terminar es lo que evita que el sistema deje
 * congeladas las siguientes (se encolan detrás de ella).
 *
 * No es seguro entre hilos: quien lo use debe sincronizar las llamadas.
 */
internal class SessionCore<L : SessionCore.Layer>(
    private val onFinished: (forced: Boolean) -> Unit,
) {

    interface Layer {
        val taskId: Int
    }

    private val active = ArrayList<L>(2)

    var finished = false
        private set

    /** Capas que aún se están animando. */
    fun snapshot(): List<L> = ArrayList(active)

    fun find(taskId: Int): L? = active.firstOrNull { it.taskId == taskId }

    /** `false` si la transición ya terminó: entonces no se puede absorber nada más. */
    fun add(layer: L): Boolean {
        if (finished) return false
        active.add(layer)
        return true
    }

    /** Una capa llegó a su destino. Si era la última, la transición termina. */
    fun settled(layer: L) {
        if (finished) return
        if (!active.remove(layer)) return
        if (active.isEmpty()) finish(forced = false)
    }

    /** Termina ya, esté como esté el movimiento (temporizador de seguridad, errores). */
    fun forceFinish() {
        if (finished) return
        finish(forced = true)
    }

    private fun finish(forced: Boolean) {
        finished = true
        active.clear()
        onFinished(forced)
    }
}
