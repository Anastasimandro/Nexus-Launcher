package app.lawnchair.shizuku

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.os.Process
import android.util.Log
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper

/**
 * Lanza actividades con la identidad de ADB (`shell`) a través de Shizuku.
 *
 * Un APK normal no puede pedir al sistema ciertas animaciones de ventana: `SafeActivityOptions`
 * exige `START_TASKS_FROM_RECENTS` (o ser el asistente activo) para `overrideTaskTransition`, y
 * `CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS` para las transiciones remotas. El usuario `shell`
 * tiene ambos permisos, así que si el lanzamiento lo hace Shizuku en su nombre, el sistema los da
 * por buenos.
 *
 * Es opcional y degrada solo: si Shizuku no está activo, no se ha dado permiso o el sistema
 * rechaza la llamada, [startActivity] devuelve `false` sin haber lanzado nada y el launcher abre
 * la app como siempre. Tras [MAX_FAILURES] fallos seguidos se deja de intentar hasta que Shizuku
 * se vuelva a conectar.
 *
 * La llamada va directa a `IActivityTaskManager.startActivityAsUser` por reflexión, buscando el
 * método por nombre y rellenando los parámetros por tipo, para tolerar cambios de firma entre
 * versiones de Android.
 */
object ShizukuLauncher {

    private const val TAG = "NexusShizuku"
    private const val PERMISSION_REQUEST_CODE = 4107
    private const val SHELL_PACKAGE = "com.android.shell"
    private const val MAX_FAILURES = 2
    private const val PER_USER_RANGE = 100_000

    /** Códigos de `startActivity` que significan que la actividad arrancó (o ya estaba abierta). */
    private val SUCCESS_CODES = 0..3

    private var initialized = false
    private var permissionAsked = false

    @Volatile
    private var consecutiveFailures = 0

    @Volatile
    private var disabled = false

    private var activityTaskManager: Any? = null
    private var startMethod: Method? = null

    /** Engancha los avisos de Shizuku. Se llama una vez al arrancar la aplicación. */
    @Synchronized
    fun init() {
        if (initialized) return
        initialized = true
        runCatching {
            Shizuku.addBinderReceivedListener {
                Log.i(TAG, "Shizuku conectado (${describe()})")
                resetState()
            }
            Shizuku.addBinderDeadListener {
                Log.w(TAG, "Shizuku se desconectó")
                resetState()
            }
            Shizuku.addRequestPermissionResultListener { _, grantResult ->
                val granted = grantResult == PackageManager.PERMISSION_GRANTED
                Log.i(TAG, "Permiso de Shizuku: ${if (granted) "concedido" else "denegado"}")
                if (granted) resetState()
            }
        }.onFailure { Log.w(TAG, "No se pudo enganchar Shizuku", it) }
    }

    /** `true` si el servicio de Shizuku está en marcha (haya o no permiso). */
    fun isRunning(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    /** `true` si Shizuku está en marcha, Nexus tiene permiso y no se ha desactivado por fallos. */
    fun isReady(): Boolean = !disabled && hasPermission()

    private fun hasPermission(): Boolean {
        if (!isRunning()) return false
        return runCatching {
            !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    /**
     * Pide el permiso a Shizuku una vez por proceso si el servicio está activo y aún no se ha
     * concedido ni denegado para siempre. Se llama cuando el launcher pasa a primer plano.
     */
    fun requestPermissionIfNeeded() {
        if (permissionAsked || !isRunning()) return
        try {
            when {
                Shizuku.isPreV11() -> {
                    permissionAsked = true
                    Log.w(TAG, "La versión de Shizuku es demasiado antigua")
                }
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> {
                    Log.i(TAG, "Shizuku listo (${describe()})")
                }
                Shizuku.shouldShowRequestPermissionRationale() -> {
                    permissionAsked = true
                    Log.i(TAG, "El permiso de Shizuku está denegado de forma permanente")
                }
                else -> {
                    permissionAsked = true
                    Log.i(TAG, "Pidiendo permiso a Shizuku")
                    Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "No se pudo consultar el permiso de Shizuku", e)
        }
    }

    /**
     * Lanza [intent] con la identidad `shell`. Devuelve `true` si el sistema aceptó el
     * lanzamiento; con `false` no se lanzó nada y quien llama debe abrir la app por su cuenta.
     */
    fun startActivity(intent: Intent, options: Bundle?): Boolean {
        if (!isReady()) return false
        return try {
            val service = obtainActivityTaskManager() ?: return fail("sin IActivityTaskManager")
            val method = startMethod ?: findStartMethod() ?: return fail("sin startActivityAsUser")
            val args = buildArgs(method, intent, options) ?: return fail("firma no reconocida")
            val code = method.invoke(service, *args) as? Int
            if (code != null && code in SUCCESS_CODES) {
                consecutiveFailures = 0
                Log.i(TAG, "Lanzado con identidad shell (código $code): ${intent.component}")
                true
            } else {
                fail("el sistema devolvió el código $code")
            }
        } catch (e: InvocationTargetException) {
            val cause = e.targetException ?: e
            fail("${cause.javaClass.simpleName}: ${cause.message}")
        } catch (e: Throwable) {
            fail("${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun fail(reason: String): Boolean {
        consecutiveFailures++
        Log.w(TAG, "Falló el lanzamiento con Shizuku ($consecutiveFailures/$MAX_FAILURES): $reason")
        if (consecutiveFailures >= MAX_FAILURES) {
            disabled = true
            Log.w(TAG, "Se desactiva el lanzamiento con Shizuku hasta que se vuelva a conectar")
        }
        return false
    }

    private fun resetState() {
        activityTaskManager = null
        consecutiveFailures = 0
        disabled = false
    }

    private fun describe(): String = runCatching {
        "uid=${Shizuku.getUid()}, versión=${Shizuku.getVersion()}"
    }.getOrDefault("sin datos")

    /**
     * `IActivityTaskManager` con las llamadas reenviadas por Shizuku: el sistema las recibe como
     * hechas por el proceso de Shizuku (uid `shell` o root) y no por Nexus.
     */
    private fun obtainActivityTaskManager(): Any? {
        activityTaskManager?.let { return it }
        return runCatching {
            val remote = Class.forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java)
                .invoke(null, "activity_task") as? IBinder
                ?: error("el servicio activity_task no existe")
            Class.forName("android.app.IActivityTaskManager\$Stub")
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, ShizukuBinderWrapper(remote))
        }.onFailure {
            Log.w(TAG, "No se pudo obtener IActivityTaskManager", it)
        }.getOrNull()?.also { activityTaskManager = it }
    }

    private fun findStartMethod(): Method? {
        val method = runCatching {
            Class.forName("android.app.IActivityTaskManager").methods
                .filter { it.name == "startActivityAsUser" }
                .maxByOrNull { it.parameterCount }
        }.onFailure {
            Log.w(TAG, "No se pudo buscar startActivityAsUser", it)
        }.getOrNull()
        if (method == null) {
            Log.w(TAG, "startActivityAsUser no existe en esta versión de Android")
            return null
        }
        Log.i(TAG, "startActivityAsUser(${method.parameterTypes.joinToString { it.simpleName }})")
        startMethod = method
        return method
    }

    /**
     * Rellena los parámetros por tipo: el primer `String` es el paquete que llama (`shell`, el
     * que coincide con el uid de Shizuku), los tres `int` son código de petición, flags de
     * arranque y usuario, y el resto (hilo de la app, resultado, perfilador) va a `null`.
     * Devuelve `null` si la firma no encaja con lo esperado.
     */
    private fun buildArgs(method: Method, intent: Intent, options: Bundle?): Array<Any?>? {
        val types = method.parameterTypes
        if (types.count { it == Intent::class.java } != 1) return signatureMismatch(method)
        if (types.count { it == Int::class.javaPrimitiveType } != 3) return signatureMismatch(method)

        var strings = 0
        var ints = 0
        val args = arrayOfNulls<Any>(types.size)
        for ((index, type) in types.withIndex()) {
            args[index] = when {
                type == Intent::class.java -> intent
                type == Bundle::class.java -> options
                type == String::class.java -> if (strings++ == 0) SHELL_PACKAGE else null
                type == Int::class.javaPrimitiveType -> when (ints++) {
                    0 -> -1
                    1 -> 0
                    else -> Process.myUid() / PER_USER_RANGE
                }
                type.isPrimitive -> return signatureMismatch(method)
                else -> null
            }
        }
        return args
    }

    private fun signatureMismatch(method: Method): Array<Any?>? {
        Log.w(TAG, "Firma inesperada de ${method.name}: ${method.parameterTypes.joinToString { it.name }}")
        return null
    }
}
