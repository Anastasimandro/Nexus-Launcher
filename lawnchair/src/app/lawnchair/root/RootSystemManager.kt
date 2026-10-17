package app.lawnchair.root

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Diagnóstico de los privilegios de sistema de Nexus.
 *
 * No abre ningún shell root (para eso está [RootHelperManager]): mira lo que el sistema ya le ha
 * concedido a la app. Con el módulo Magisk/KernelSU instalado, Android trata a Nexus como app
 * privilegiada de `/product` y le da los permisos de `privapp-permissions-nexus.xml`; cuando el
 * usuario actualiza el APK de forma normal (queda en `/data/app`), el sistema lo sigue tratando
 * como actualización de una app de sistema y conserva esos permisos sin volver a flashear.
 *
 * Nexus decide con [isSystemPrivilegedActive] cómo lanzar las apps:
 * - `true`: puede pedir al sistema transiciones de ventana propias (`overrideTaskTransition` y
 *   animaciones remotas), que `SafeActivityOptions` solo acepta con `START_TASKS_FROM_RECENTS` y
 *   `CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS`.
 * - `false`: modo alternativo (Shizuku si está activo, o el reveal normal del sistema).
 *
 * El estado se decide por los permisos realmente concedidos, no por de dónde sale el APK: es la
 * misma comprobación que hace el sistema, así que no puede dar un falso positivo. El origen
 * ([Origin]) solo sirve para explicar un fallo.
 *
 * Nota: Android no tiene un permiso `BIND_QUICKSTEP_SERVICE`. El servicio de Quickstep
 * (`TouchInteractionService`) exige `STATUS_BAR_SERVICE` a quien se enlaza (SystemUI), y Nexus
 * pide además el resto de permisos de Quickstep; todos se informan en [Status.granted] y
 * [Status.missing].
 */
object RootSystemManager {

    private const val TAG = "NexusRootSystem"
    private const val START_TASKS_FROM_RECENTS = "android.permission.START_TASKS_FROM_RECENTS"
    private const val CONTROL_REMOTE_ANIMATIONS = "android.permission.CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS"

    /** Archivo que genera el módulo; si se ve, el módulo está montado. */
    private const val ALLOWLIST_FILE = "/product/etc/permissions/privapp-permissions-nexus.xml"

    /** Si no se fuerza, no se vuelve a comprobar antes de este tiempo. */
    private const val REFRESH_MIN_INTERVAL_MS = 30_000L

    /** Sin estos dos el sistema rechaza las transiciones de ventana propias. */
    private val TRANSITION_PERMISSIONS = setOf(START_TASKS_FROM_RECENTS, CONTROL_REMOTE_ANIMATIONS)

    /**
     * El resto de lo que Nexus pide en quickstep/AndroidManifest.xml. Debe coincidir con
     * magisk-module/permissions/allow.txt: ahí se conceden, aquí se comprueba que llegaron.
     */
    private val QUICKSTEP_PERMISSIONS = setOf(
        "android.permission.MANAGE_ACTIVITY_TASKS",
        "android.permission.REMOVE_TASKS",
        "android.permission.STOP_APP_SWITCHES",
        "android.permission.STATUS_BAR",
        "android.permission.STATUS_BAR_SERVICE",
        "android.permission.READ_FRAME_BUFFER",
        "android.permission.MONITOR_INPUT",
        "android.permission.ALLOW_SLIPPERY_TOUCHES",
        "android.permission.INTERNAL_SYSTEM_WINDOW",
        "android.permission.SET_ORIENTATION",
        "android.permission.ACCESS_SHORTCUTS",
        "android.permission.MANAGE_ACCESSIBILITY",
        "android.permission.BROADCAST_CLOSE_SYSTEM_DIALOGS",
        "android.permission.MANAGE_KEY_GESTURES",
        "android.permission.ACCESS_HIDDEN_PROFILES",
        "android.permission.ACCESS_HIDDEN_PROFILES_FULL",
        "android.permission.SYSTEM_APPLICATION_OVERLAY",
    )

    private val TRACKED_PERMISSIONS = TRANSITION_PERMISSIONS + QUICKSTEP_PERMISSIONS

    /**
     * Textos con los que el sistema rechaza las opciones de animación privilegiadas. Son los mismos
     * que reconoce ShizukuLauncher: una `SecurityException` con otro motivo (una app que no se
     * puede abrir) no debe apagar las animaciones de todas las demás.
     */
    private val ANIMATION_DENIAL_MARKERS = listOf(
        "START_TASKS_FROM_RECENTS",
        "CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS",
        "overrideTaskTransition",
        "SafeActivityOptions",
    )

    /** De dónde sale la copia de Nexus que se está ejecutando. */
    enum class Origin {
        /** APK normal: sin privilegios de sistema salvo que algo más se los haya dado. */
        USER_APP,

        /** Copia de la partición del sistema, por ejemplo la que el módulo pone en `/product/priv-app`. */
        SYSTEM_PARTITION,

        /** Actualización en `/data` de una app de sistema: la que hereda los permisos (hot-swap). */
        SYSTEM_UPDATED_IN_DATA,
    }

    data class Status(
        val origin: Origin,
        /** Permisos de [TRACKED_PERMISSIONS] que el sistema ha concedido. */
        val granted: Set<String>,
        /** Permisos de [TRACKED_PERMISSIONS] que no están concedidos. */
        val missing: Set<String>,
        /**
         * `true` si se ve el archivo que genera el módulo. `false` no es concluyente: puede que el
         * módulo no esté o que el sistema no deje a la app ver ese archivo.
         */
        val allowlistFileVisible: Boolean,
        /** El sistema rechazó un lanzamiento con las opciones privilegiadas en este proceso. */
        val launchRejected: Boolean,
        val apkPath: String,
    ) {
        val canOverrideTaskTransition: Boolean = START_TASKS_FROM_RECENTS in granted
        val canUseRemoteAnimations: Boolean = CONTROL_REMOTE_ANIMATIONS in granted

        /** Nexus puede pedir transiciones de ventana propias; si no, hay que usar el modo alternativo. */
        val isSystemPrivilegedActive: Boolean =
            canOverrideTaskTransition && canUseRemoteAnimations && !launchRejected

        /** Por qué se usa el modo alternativo, o `null` si no se usa. */
        val fallbackReason: String? = when {
            isSystemPrivilegedActive -> null

            launchRejected -> "el sistema rechazó un lanzamiento privilegiado"

            origin == Origin.USER_APP ->
                "Nexus se ejecuta como app normal: falta instalar el módulo o reiniciar tras flashearlo"

            else ->
                "el sistema no ha concedido " +
                    TRANSITION_PERMISSIONS.filter { it !in granted }.joinToString { it.substringAfterLast('.') }
        }

        /** Una línea para el registro y el informe de errores. */
        fun summary(): String = buildString {
            append(if (isSystemPrivilegedActive) "privilegiado" else "alternativo")
            append(", origen=").append(origin)
            append(", concedidos=").append(granted.size).append('/').append(TRACKED_PERMISSIONS.size)
            if (missing.isNotEmpty()) {
                append(", faltan=").append(missing.joinToString { it.substringAfterLast('.') })
            }
            append(", listaBlanca=").append(if (allowlistFileVisible) "visible" else "no visible")
            fallbackReason?.let { append(", motivo=").append(it) }
        }

        companion object {
            /** Estado antes de la primera comprobación: modo alternativo. */
            val UNCHECKED = Status(
                origin = Origin.USER_APP,
                granted = emptySet(),
                missing = TRACKED_PERMISSIONS,
                allowlistFileVisible = false,
                launchRejected = false,
                apkPath = "",
            )
        }
    }

    private val _status = MutableStateFlow(Status.UNCHECKED)

    /** Estado actual, para quien quiera reaccionar a los cambios (pantalla de diagnóstico...). */
    val status: StateFlow<Status> = _status.asStateFlow()

    /** Lectura directa para el camino de lanzamiento: un único acceso a una variable. */
    val isSystemPrivilegedActive: Boolean get() = _status.value.isSystemPrivilegedActive

    @Volatile
    private var launchRejected = false

    private var lastRefresh = 0L

    /** Primera comprobación. Se llama una vez al arrancar la aplicación. */
    fun init(context: Context) {
        refresh(context, force = true)
    }

    /**
     * Vuelve a leer del sistema los permisos concedidos. Es una sola llamada al gestor de paquetes,
     * y si no se fuerza no se repite antes de [REFRESH_MIN_INTERVAL_MS].
     */
    @Synchronized
    fun refresh(context: Context, force: Boolean = false): Status {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastRefresh < REFRESH_MIN_INTERVAL_MS) return _status.value
        lastRefresh = now

        val next = readStatus(context.applicationContext)
        if (next != _status.value) {
            Log.i(TAG, "Privilegios de sistema: ${next.summary()} (${next.apkPath})")
            _status.value = next
        }
        return next
    }

    /**
     * El sistema rechazó un lanzamiento hecho con opciones privilegiadas. Si el motivo son los
     * permisos de animación, se pasa al modo alternativo hasta que el proceso se reinicie (una
     * actualización del APK o un reinicio tras flashear el módulo lo reinician).
     *
     * Devuelve `true` si era ese motivo; con `false` la excepción es de otra cosa y quien llama
     * debe tratarla como siempre.
     */
    fun reportLaunchRejected(context: Context, error: SecurityException): Boolean {
        val message = error.message.orEmpty()
        if (ANIMATION_DENIAL_MARKERS.none { message.contains(it) }) return false
        launchRejected = true
        Log.w(TAG, "El sistema rechazó las animaciones privilegiadas: $message")
        refresh(context, force = true)
        return true
    }

    private fun readStatus(context: Context): Status {
        val granted = grantedPermissions(context)
        val info = context.applicationInfo
        return Status(
            origin = originOf(info),
            granted = granted,
            missing = TRACKED_PERMISSIONS - granted,
            allowlistFileVisible = runCatching { File(ALLOWLIST_FILE).exists() }.getOrDefault(false),
            launchRejected = launchRejected,
            apkPath = info.sourceDir.orEmpty(),
        )
    }

    private fun originOf(info: ApplicationInfo): Origin = when {
        info.flags and ApplicationInfo.FLAG_SYSTEM == 0 -> Origin.USER_APP
        info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0 -> Origin.SYSTEM_UPDATED_IN_DATA
        else -> Origin.SYSTEM_PARTITION
    }

    /**
     * Permisos de [TRACKED_PERMISSIONS] concedidos. Una sola llamada al gestor de paquetes devuelve
     * los pedidos y el estado de cada uno (también los de instalación, como los privilegiados).
     */
    private fun grantedPermissions(context: Context): Set<String> {
        val info = runCatching { packageInfoWithPermissions(context) }
            .onFailure { Log.w(TAG, "No se pudieron leer los permisos de Nexus", it) }
            .getOrNull() ?: return emptySet()
        val names = info.requestedPermissions ?: return emptySet()
        val flags = info.requestedPermissionsFlags ?: return emptySet()

        val granted = HashSet<String>()
        for (index in names.indices) {
            val isGranted = flags.getOrElse(index) { 0 } and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0
            if (isGranted && names[index] in TRACKED_PERMISSIONS) granted += names[index]
        }
        return granted
    }

    private fun packageInfoWithPermissions(context: Context): PackageInfo {
        val packageManager = context.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        }
    }
}
