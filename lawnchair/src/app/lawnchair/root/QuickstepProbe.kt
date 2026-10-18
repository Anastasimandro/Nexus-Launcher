package app.lawnchair.root

import android.content.Context
import android.os.Build
import android.util.Log
import com.android.systemui.shared.system.InputConsumerCompat
import java.io.File

/**
 * Comprueba en este Android si el Quickstep que lleva Nexus puede funcionar, antes de que nadie
 * lo convierta en proveedor de recientes.
 *
 * Convertir a Nexus en proveedor (overlay de `config_recentsComponentName`, ver
 * `magisk-module/README.md`) hace que SystemUI le entregue el gesto de Inicio y de recientes. Si en
 * esa versión de Android falta algo de lo que Quickstep usa, el servicio se cae al arrancar y el
 * sistema se queda sin gestos hasta deshacerlo. Por eso el resultado se escribe en
 * `files/quickstep-probe.txt`, que `nexus-quickstep enable` lee como root y por el que se niega a
 * activar el overlay si algo falta (salvo `--force`).
 *
 * Qué se comprueba, y es solo lo que se sabe que ha cambiado entre versiones:
 * - `IWindowManager.createInputConsumer/destroyInputConsumer`: en Android 17 cambió la firma y el
 *   servicio de Quickstep se caía en bucle al desbloquear. [InputConsumerCompat] ya cubre las dos
 *   formas; aquí se verifica que el sistema tiene alguna.
 * - Las constantes de `DesktopModeFlags` y `DesktopExperienceFlags` que lee Quickstep. Android 17
 *   quitó algunas, y leer una que no existe lanza `NoSuchFieldError` en el proceso del launcher.
 *
 * No prueba que todo lo demás funcione (por ejemplo las transacciones AIDL de WM Shell): solo
 * detecta lo que ya se conoce. Un resultado correcto no es una garantía; uno incorrecto sí es un
 * motivo para no activar el overlay.
 */
object QuickstepProbe {

    private const val TAG = "NexusQuickstepProbe"
    private const val FILE_NAME = "quickstep-probe.txt"

    private const val DESKTOP_MODE_FLAGS_CLASS = "android.window.DesktopModeFlags"
    private const val DESKTOP_EXPERIENCE_FLAGS_CLASS = "android.window.DesktopExperienceFlags"

    /** Constantes de `DesktopModeFlags` que lee el código de quickstep/. */
    private val DESKTOP_MODE_FLAGS = listOf(
        "ENABLE_DESKTOP_APP_LAUNCH_ALTTAB_TRANSITIONS_BUGFIX",
        "ENABLE_DESKTOP_APP_LAUNCH_TRANSITIONS_BUGFIX",
        "ENABLE_DESKTOP_TRAMPOLINE_CLOSE_ANIMATION_BUGFIX",
        "ENABLE_DESKTOP_WINDOWING_EXIT_TRANSITIONS_BUGFIX",
        "ENABLE_DESKTOP_WINDOWING_EXIT_BY_MINIMIZE_TRANSITION_BUGFIX",
        "ENABLE_DESKTOP_WINDOWING_QUICK_SWITCH",
        "ENABLE_DESKTOP_WINDOWING_TASKBAR_RUNNING_APPS",
        "ENABLE_QUICKSWITCH_DESKTOP_SPLIT_BUGFIX",
        "ENABLE_START_LAUNCH_TRANSITION_FROM_TASKBAR_BUGFIX",
        "ENABLE_TASKBAR_OVERFLOW",
        "ENABLE_TASKBAR_RECENTS_LAYOUT_TRANSITION",
    )

    /** Constantes de `DesktopExperienceFlags` que lee el código de quickstep/. */
    private val DESKTOP_EXPERIENCE_FLAGS = listOf(
        "ENABLE_DESKTOP_APP_LAUNCH_BUGFIX",
        "ENABLE_DESKTOP_FIRST_FULLSCREEN_REFOCUS_BUGFIX",
        "ENABLE_DESKTOP_TASKBAR_ON_FREEFORM_DISPLAYS",
        "ENABLE_DESKTOP_WINDOWING_PIP",
        "ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT",
        "ENABLE_FREEFORM_DISPLAY_LAUNCH_PARAMS",
        "ENABLE_MULTIPLE_DESKTOPS_BACKEND",
        "ENABLE_MULTIPLE_DESKTOPS_FRONTEND",
        "ENABLE_PINNING_APP_WITH_CONTEXT_MENU",
        "ENABLE_REJECT_HOME_TRANSITION",
        "ENABLE_SYS_DECORS_CALLBACKS_VIA_WM",
        "ENABLE_TASKBAR_CONNECTED_DISPLAYS",
        "ENABLE_TASKBAR_RECENT_TASKS_THROTTLE_BUGFIX",
    )

    data class Result(
        val sdk: Int,
        /** Lo que falta en este Android; vacío si no se echó nada en falta. */
        val missing: List<String>,
    ) {
        val passed: Boolean get() = missing.isEmpty()
    }

    /** Hace la comprobación. No escribe nada. */
    fun evaluate(): Result {
        val missing = ArrayList<String>()

        if (!runCatching { InputConsumerCompat.isSupported() }.getOrDefault(false)) {
            missing += "IWindowManager.createInputConsumer"
        }
        missing += missingConstants(DESKTOP_MODE_FLAGS_CLASS, DESKTOP_MODE_FLAGS)
        missing += missingConstants(DESKTOP_EXPERIENCE_FLAGS_CLASS, DESKTOP_EXPERIENCE_FLAGS)

        return Result(sdk = Build.VERSION.SDK_INT, missing = missing)
    }

    /** Hace la comprobación y deja el resultado en `files/quickstep-probe.txt`. */
    fun run(context: Context): Result {
        val result = evaluate()
        Log.i(TAG, "sdk=${result.sdk} passed=${result.passed} missing=${result.missing}")
        runCatching {
            // Se escribe en un archivo temporal y se renombra, para que root nunca lea uno a medias.
            val target = File(context.filesDir, FILE_NAME)
            val temp = File(context.filesDir, "$FILE_NAME.tmp")
            temp.writeText(format(result))
            if (!temp.renameTo(target)) error("no se pudo renombrar $temp")
        }.onFailure { Log.w(TAG, "No se pudo escribir $FILE_NAME", it) }
        return result
    }

    /** El formato que lee `nexus-quickstep`: una pareja `clave=valor` por línea. */
    private fun format(result: Result): String = buildString {
        appendLine("sdk=${result.sdk}")
        appendLine("passed=${result.passed}")
        appendLine("missing=${result.missing.joinToString(",")}")
    }

    /**
     * Nombres de [names] que no existen como constante de la enumeración [className]. Si la clase
     * no existe, se informa de ella y no de cada constante. Si no se puede leer, se informa
     * también: es preferible un falso aviso (se puede forzar) a dar por bueno algo que no se vio.
     */
    private fun missingConstants(className: String, names: List<String>): List<String> {
        val type = runCatching { Class.forName(className) }.getOrElse { return listOf(className) }
        val present = runCatching { type.enumConstants?.map { (it as Enum<*>).name }?.toSet() }
            .getOrNull() ?: return listOf("$className (no legible)")
        return names.filter { it !in present }.map { "${type.simpleName}.$it" }
    }
}
