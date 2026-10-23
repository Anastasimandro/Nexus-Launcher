package app.lawnchair.animation

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Geometría de la transición entre un icono y la ventana de su aplicación. Es la misma relación
 * que usan la apertura y el cierre de Quickstep (escala uniforme + recorte), de modo que abrir es
 * exactamente el cierre al revés y no hay dos geometrías distintas que se puedan desajustar.
 *
 * En cada instante el rectángulo visible `R(p)` interpola entre el del icono (`p = 0`) y el de la
 * ventana (`p = 1`) por centro y tamaño. La superficie de la app se escala de forma uniforme lo
 * justo para cubrir `R(p)` (`s = max(w / W, h / H)`) y se recorta a la parte que cae dentro. Así el
 * contenido nunca se deforma y, a `p = 1`, la escala es 1 y el recorte es la ventana entera.
 *
 * No depende de Android, así que se puede probar en la JVM. Todas las coordenadas están en el
 * mismo espacio que usa la superficie (relativo a la raíz de la transición).
 *
 * @param iconCenterX centro del icono en pantalla.
 * @param iconWidth ancho del icono tal como se ve en pantalla.
 * @param windowLeft esquina de la ventana en su posición final.
 * @param windowWidth tamaño de la ventana en su posición final.
 * @param endRadius radio de las esquinas de la ventana a pantalla completa (en pantalla).
 */
class IconWindowGeometry(
    private val iconCenterX: Float,
    private val iconCenterY: Float,
    iconWidth: Float,
    iconHeight: Float,
    windowLeft: Float,
    windowTop: Float,
    windowWidth: Float,
    windowHeight: Float,
    private val endRadius: Float,
    roundIcon: Boolean = true,
) {
    private val iconWidth = iconWidth.coerceAtLeast(MIN_SIZE)
    private val iconHeight = iconHeight.coerceAtLeast(MIN_SIZE)
    private val windowWidth = windowWidth.coerceAtLeast(MIN_SIZE)
    private val windowHeight = windowHeight.coerceAtLeast(MIN_SIZE)
    private val windowCenterX = windowLeft + this.windowWidth / 2f
    private val windowCenterY = windowTop + this.windowHeight / 2f

    /** El icono es redondo: la ventana arranca con las esquinas de ese círculo. */
    private val startRadius = if (roundIcon) min(this.iconWidth, this.iconHeight) / 2f else 0f

    /**
     * Estado de la superficie en un instante. Se reutiliza para no asignar memoria por fotograma:
     * [at] lo rellena y el que llama copia lo que necesite.
     */
    class Frame {
        /** Recorte en coordenadas locales de la superficie (antes de escalar). */
        var cropLeft = 0
        var cropTop = 0
        var cropRight = 0
        var cropBottom = 0

        /** Escala uniforme aplicada a la superficie. */
        var scale = 1f

        /** Posición de la superficie (esquina de su origen local) tras escalar. */
        var x = 0f
        var y = 0f

        /** Radio de esquina en el espacio local: el sistema lo escala junto con la superficie. */
        var localRadius = 0f

        /** Rectángulo que ocupa lo visible en pantalla, para colocar la vista del icono. */
        var screenLeft = 0f
        var screenTop = 0f
        var screenRight = 0f
        var screenBottom = 0f

        /** Radio de esquina tal como se ve en pantalla. */
        var screenRadius = 0f
    }

    /** Rellena [out] con la geometría para el progreso [p] (se limita a 0..1). */
    fun at(p: Float, out: Frame): Frame {
        val t = p.coerceIn(0f, 1f)
        val centerX = lerp(iconCenterX, windowCenterX, t)
        val centerY = lerp(iconCenterY, windowCenterY, t)
        val w = lerp(iconWidth, windowWidth, t)
        val h = lerp(iconHeight, windowHeight, t)

        // Escala mínima que cubre el rectángulo visible; nunca amplía por encima de 1.
        val scale = min(1f, max(w / windowWidth, h / windowHeight))
        val cropWidth = w / scale
        val cropHeight = h / scale
        val cropLeft = (windowWidth - cropWidth) / 2f
        val cropTop = (windowHeight - cropHeight) / 2f

        // El recorte se redondea hacia fuera (nunca queda un borde sin pintar); la posición se
        // calcula con el recorte ya redondeado para que el borde izquierdo y el superior
        // coincidan con el rectángulo visible exactamente.
        val left = floor(cropLeft).toInt().coerceAtLeast(0)
        val top = floor(cropTop).toInt().coerceAtLeast(0)
        out.cropLeft = left
        out.cropTop = top
        out.cropRight = ceil(cropLeft + cropWidth).toInt().coerceAtMost(windowWidth.toInt())
        out.cropBottom = ceil(cropTop + cropHeight).toInt().coerceAtMost(windowHeight.toInt())
        out.scale = scale

        out.screenLeft = centerX - w / 2f
        out.screenTop = centerY - h / 2f
        out.screenRight = centerX + w / 2f
        out.screenBottom = centerY + h / 2f
        out.x = out.screenLeft - left * scale
        out.y = out.screenTop - top * scale

        out.screenRadius = lerp(startRadius, endRadius, t)
        out.localRadius = out.screenRadius / scale
        return out
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private companion object {
        const val MIN_SIZE = 1f
    }
}
