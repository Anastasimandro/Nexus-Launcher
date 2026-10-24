package app.lawnchair.animation

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Registro de las animaciones de Nexus en archivos de texto, para poder ver después qué pasó:
 * cada lanzamiento, cada interrupción (qué decisión se tomó), cada cierre y el ritmo de
 * fotogramas de cada animación. Además de escribir en el archivo, reenvía a logcat.
 *
 * Registrar no espera a disco: las líneas pasan a una cola acotada ([LogBuffer]) y un hilo propio
 * las escribe. No se registra nada por fotograma, solo eventos y un resumen al terminar.
 *
 * Dónde se guarda, en este orden (la primera que funcione; la elegida queda en la cabecera):
 * 1. `Nexus-Logs/animaciones/` en la raíz del almacenamiento, si la app tiene permiso para escribir
 *    ahí (acceso a todos los archivos).
 * 2. `Download/Nexus-Logs/` (Android 10+), que no pide ningún permiso.
 * 3. La carpeta privada de la app: `Android/data/<paquete>/files/Nexus-Logs/`.
 * Se conservan los [MAX_FILES] registros más recientes.
 */
object NexusLog {

    private const val MAX_FILES = 8
    private const val BUFFER_LINES = 600
    private const val MAX_FILE_BYTES = 1_500_000L
    private const val MAX_TRACE_CHARS = 1_800
    private const val FOLDER = "Nexus-Logs"
    private const val SUB_FOLDER = "animaciones"
    private const val FILE_PREFIX = "animaciones-"
    private const val FILE_SUFFIX = ".txt"

    /** Se puede apagar sin tocar el resto: entonces solo se reenvía a logcat. */
    @Volatile
    var enabled = true

    /** Dónde se está escribiendo, para mostrarlo; vacío hasta que se abre el archivo. */
    @Volatile
    var location: String = ""
        private set

    private val buffer = LogBuffer(BUFFER_LINES)
    private val lock = Any()
    private var handler: Handler? = null

    @Volatile
    private var out: OutputStream? = null
    private var written = 0L
    private var closedForSize = false
    private var drainPosted = false

    /** Debe llamarse una vez al arrancar la aplicación; es idempotente y no hace E/S en quien llama. */
    @JvmStatic
    fun init(context: Context) {
        synchronized(lock) {
            if (handler != null) return
            val thread = HandlerThread("NexusLog").apply { start() }
            handler = Handler(thread.looper).also { h ->
                val app = context.applicationContext
                h.post { open(app) }
            }
        }
    }

    @JvmStatic
    fun i(tag: String, message: String) = log('I', Log.INFO, tag, message, null)

    @JvmStatic
    @JvmOverloads
    fun w(tag: String, message: String, error: Throwable? = null) = log('W', Log.WARN, tag, message, error)

    @JvmStatic
    @JvmOverloads
    fun e(tag: String, message: String, error: Throwable? = null) = log('E', Log.ERROR, tag, message, error)

    private fun log(level: Char, priority: Int, tag: String, message: String, error: Throwable?) {
        Log.println(priority, tag, if (error == null) message else message + '\n' + Log.getStackTraceString(error))
        if (!enabled) return
        val text = if (error == null) message else message + '\n' + shortTrace(error)
        buffer.offer(LogLine(System.currentTimeMillis(), level, tag, Thread.currentThread().name, text))
        schedule()
    }

    private fun shortTrace(error: Throwable): String {
        val trace = Log.getStackTraceString(error)
        return if (trace.length <= MAX_TRACE_CHARS) trace else trace.substring(0, MAX_TRACE_CHARS) + "…"
    }

    private fun schedule() {
        val h: Handler
        synchronized(lock) {
            h = handler ?: return
            if (drainPosted) return
            drainPosted = true
        }
        h.post { drain() }
    }

    // ---- Hilo del registro

    private fun open(context: Context) {
        val name = FILE_PREFIX + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + FILE_SUFFIX
        val target = openPublicRoot(name) ?: openDownloads(context, name) ?: openPrivate(context, name)
        if (target == null) {
            Log.w(TAG, "No se pudo abrir ningún archivo de registro")
            return
        }
        out = target.first
        location = target.second
        val profile = NexusMotion.profile
        write(
            "Nexus — registro de animaciones\n" +
                "Archivo: ${target.second}\n" +
                "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                "Perfil: apertura k=${profile.open.stiffness} z=${profile.open.dampingRatio}, " +
                "cierre k=${profile.close.stiffness} z=${profile.close.dampingRatio}, " +
                "vuelta k=${profile.homeReturn.stiffness} z=${profile.homeReturn.dampingRatio}, velocidad=${profile.speed}\n" +
                "Escala de animación del sistema: ${systemScale(context)}\n\n",
        )
        drain()
    }

    private fun systemScale(context: Context): Float = runCatching { NexusMotion.systemScale(context) }.getOrDefault(1f)

    @Suppress("DEPRECATION")
    private fun openPublicRoot(name: String): Pair<OutputStream, String>? = runCatching {
        val dir = File(File(Environment.getExternalStorageDirectory(), FOLDER), SUB_FOLDER)
        if (!dir.isDirectory && !dir.mkdirs()) return null
        prune(dir.listFiles()?.filter { it.name.startsWith(FILE_PREFIX) }.orEmpty(), MAX_FILES - 1)
        val file = File(dir, name)
        FileOutputStream(file, true) to file.absolutePath
    }.getOrNull()

    private fun openDownloads(context: Context, name: String): Pair<OutputStream, String>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching {
            val resolver = context.contentResolver
            val path = "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER/"
            pruneDownloads(context, path)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, path)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            val stream = resolver.openOutputStream(uri, "wa") ?: return null
            stream to "$path$name"
        }.getOrNull()
    }

    private fun openPrivate(context: Context, name: String): Pair<OutputStream, String>? = runCatching {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(base, FOLDER)
        if (!dir.isDirectory && !dir.mkdirs()) return null
        prune(dir.listFiles()?.filter { it.name.startsWith(FILE_PREFIX) }.orEmpty(), MAX_FILES - 1)
        val file = File(dir, name)
        FileOutputStream(file, true) to file.absolutePath
    }.getOrNull()

    private fun prune(files: List<File>, keep: Int) {
        files.sortedByDescending { it.lastModified() }.drop(keep).forEach { runCatching { it.delete() } }
    }

    /** Borra, entre los registros que esta app creó en Descargas, los más antiguos. */
    private fun pruneDownloads(context: Context, relativePath: String) {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val ids = ArrayList<Long>()
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
            arrayOf(relativePath, "$FILE_PREFIX%"),
            "${MediaStore.MediaColumns.DATE_ADDED} DESC",
        )?.use { cursor ->
            while (cursor.moveToNext()) ids.add(cursor.getLong(0))
        }
        ids.drop(MAX_FILES - 1).forEach { id ->
            runCatching { resolver.delete(ContentUris.withAppendedId(collection, id), null, null) }
        }
    }

    private fun drain() {
        synchronized(lock) { drainPosted = false }
        val stream = out ?: return
        if (closedForSize) {
            val discard = ArrayList<LogLine>()
            buffer.drain(discard)
            return
        }
        val lines = ArrayList<LogLine>()
        val lost = buffer.drain(lines)
        if (lines.isEmpty() && lost == 0) return
        val formatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        val text = StringBuilder()
        if (lost > 0) text.append("··· se perdieron ").append(lost).append(" líneas (el escritor iba por detrás)\n")
        for (line in lines) {
            text.append(formatter.format(Date(line.timeMillis)))
                .append(' ').append(line.level).append('/').append(line.tag)
                .append(" [").append(line.thread).append("] ")
                .append(line.message).append('\n')
        }
        write(text.toString())
        if (written > MAX_FILE_BYTES) {
            closedForSize = true
            write("··· archivo lleno: no se escribe más en esta sesión\n")
            runCatching { stream.close() }
        }
    }

    private fun write(text: String) {
        val stream = out ?: return
        runCatching {
            val bytes = text.toByteArray(Charsets.UTF_8)
            stream.write(bytes)
            stream.flush()
            written += bytes.size
        }.onFailure { Log.w(TAG, "No se pudo escribir en el registro", it) }
    }

    private const val TAG = "NexusLog"
}
