package app.lawnchair.animation

/** Una línea del registro de animaciones, tal como se produjo (el formato se aplica al escribirla). */
internal class LogLine(
    val timeMillis: Long,
    val level: Char,
    val tag: String,
    val thread: String,
    val message: String,
)

/**
 * Cola acotada entre quien registra (cualquier hilo, sin esperar a disco) y el hilo que escribe.
 * Si el escritor va por detrás, se descartan las líneas más antiguas y se cuenta cuántas, para que
 * registrar nunca consuma memoria sin límite ni bloquee una animación.
 *
 * Sin dependencias de Android: se puede probar en la JVM.
 */
internal class LogBuffer(private val capacity: Int) {

    private val lines = ArrayDeque<LogLine>()
    private var dropped = 0

    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    @Synchronized
    fun offer(line: LogLine) {
        if (lines.size >= capacity) {
            lines.removeFirst()
            dropped++
        }
        lines.addLast(line)
    }

    /** Saca todo lo pendiente y devuelve cuántas líneas se perdieron desde la última vez. */
    @Synchronized
    fun drain(into: MutableList<LogLine>): Int {
        into.addAll(lines)
        lines.clear()
        val lost = dropped
        dropped = 0
        return lost
    }

    @get:Synchronized
    val size: Int get() = lines.size
}
