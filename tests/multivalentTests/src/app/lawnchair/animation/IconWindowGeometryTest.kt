package app.lawnchair.animation

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IconWindowGeometryTest {

    private class Scenario(
        val name: String,
        val windowWidth: Float,
        val windowHeight: Float,
        val iconCenterX: Float,
        val iconCenterY: Float,
        val iconWidth: Float,
        val iconHeight: Float,
        val windowLeft: Float = 0f,
        val windowTop: Float = 0f,
        val radius: Float = 48f,
    ) {
        fun geometry() = IconWindowGeometry(
            iconCenterX, iconCenterY, iconWidth, iconHeight,
            windowLeft, windowTop, windowWidth, windowHeight, radius,
        )
    }

    private val scenarios = listOf(
        Scenario("vertical, icono arriba a la izquierda", 1080f, 2400f, 130f, 330f, 150f, 150f),
        Scenario("vertical, icono en el dock", 1080f, 2400f, 540f, 2250f, 150f, 150f),
        Scenario("vertical, icono a la derecha", 1080f, 2400f, 950f, 1200f, 132f, 132f),
        Scenario("horizontal", 2400f, 1080f, 300f, 800f, 150f, 150f),
        Scenario("tableta", 2000f, 1200f, 1700f, 1000f, 160f, 160f),
        Scenario("casi cuadrada (plegable)", 1840f, 2208f, 900f, 1500f, 140f, 140f),
        Scenario("icono rectangular (carpeta/widget)", 1080f, 2400f, 540f, 700f, 400f, 220f),
        Scenario("ventana desplazada (raíz con offset)", 1080f, 2300f, 540f, 1100f, 150f, 150f, 0f, 100f),
        Scenario("icono pequeño", 1080f, 2400f, 200f, 400f, 48f, 48f),
        Scenario("icono grande", 1080f, 2400f, 540f, 1200f, 300f, 300f),
    )

    @Test
    fun startsExactlyOnTheIcon() {
        for (s in scenarios) {
            val f = IconWindowGeometry.Frame()
            s.geometry().at(0f, f)
            val tol = 1e-3f
            assertEquals("${s.name} izquierda", s.iconCenterX - s.iconWidth / 2, f.screenLeft, tol)
            assertEquals("${s.name} arriba", s.iconCenterY - s.iconHeight / 2, f.screenTop, tol)
            assertEquals("${s.name} derecha", s.iconCenterX + s.iconWidth / 2, f.screenRight, tol)
            assertEquals("${s.name} abajo", s.iconCenterY + s.iconHeight / 2, f.screenBottom, tol)
            assertEquals("${s.name} radio", min(s.iconWidth, s.iconHeight) / 2, f.screenRadius, tol)
            val expectedScale = min(1f, max(s.iconWidth / s.windowWidth, s.iconHeight / s.windowHeight))
            assertEquals("${s.name} escala", expectedScale, f.scale, 1e-6f)
        }
    }

    @Test
    fun endsExactlyOnTheWindow() {
        for (s in scenarios) {
            val f = IconWindowGeometry.Frame()
            s.geometry().at(1f, f)
            assertEquals("${s.name} escala", 1f, f.scale, 0f)
            assertEquals("${s.name} x", s.windowLeft, f.x, 1e-3f)
            assertEquals("${s.name} y", s.windowTop, f.y, 1e-3f)
            assertEquals("${s.name} recorte izq", 0, f.cropLeft)
            assertEquals("${s.name} recorte arriba", 0, f.cropTop)
            assertEquals("${s.name} recorte der", s.windowWidth.toInt(), f.cropRight)
            assertEquals("${s.name} recorte abajo", s.windowHeight.toInt(), f.cropBottom)
            assertEquals("${s.name} radio", s.radius, f.screenRadius, 1e-4f)
        }
    }

    @Test
    fun visibleAreaAlwaysCoversTheTargetRectWithoutDistortion() {
        for (s in scenarios) {
            val g = s.geometry()
            val f = IconWindowGeometry.Frame()
            var p = 0f
            while (p <= 1f) {
                g.at(p, f)
                val shownWidth = (f.cropRight - f.cropLeft) * f.scale
                val shownHeight = (f.cropBottom - f.cropTop) * f.scale
                val wanted = f.screenRight - f.screenLeft
                val wantedH = f.screenBottom - f.screenTop
                // Cubre el rectángulo (nunca queda un borde sin pintar) y se pasa como mucho un
                // píxel local por el redondeo hacia fuera del recorte.
                assertTrue("${s.name} p=$p ancho $shownWidth < $wanted", shownWidth >= wanted - 1e-2f)
                assertTrue("${s.name} p=$p alto $shownHeight < $wantedH", shownHeight >= wantedH - 1e-2f)
                assertTrue("${s.name} p=$p ancho sobrante", shownWidth <= wanted + 2f * f.scale + 1e-2f)
                assertTrue("${s.name} p=$p alto sobrante", shownHeight <= wantedH + 2f * f.scale + 1e-2f)
                // La escala es única (sin deformar) y el recorte cabe en la ventana.
                assertTrue(f.cropLeft >= 0 && f.cropTop >= 0)
                assertTrue(f.cropRight <= s.windowWidth.toInt() && f.cropBottom <= s.windowHeight.toInt())
                assertTrue(f.scale in 0f..1f)
                p += 0.005f
            }
        }
    }

    @Test
    fun leftAndTopEdgesLandOnTheTargetRect() {
        for (s in scenarios) {
            val g = s.geometry()
            val f = IconWindowGeometry.Frame()
            var p = 0f
            while (p <= 1f) {
                g.at(p, f)
                // El punto (cropLeft, cropTop) de la superficie debe caer en la esquina del rectángulo.
                assertEquals("${s.name} p=$p x", f.screenLeft, f.x + f.cropLeft * f.scale, 1e-3f)
                assertEquals("${s.name} p=$p y", f.screenTop, f.y + f.cropTop * f.scale, 1e-3f)
                p += 0.005f
            }
        }
    }

    @Test
    fun motionIsContinuousAndMonotonic() {
        for (s in scenarios) {
            val g = s.geometry()
            var prev = IconWindowGeometry.Frame().also { g.at(0f, it) }
            val cur = IconWindowGeometry.Frame()
            val travel = max(
                abs(s.windowLeft + s.windowWidth / 2 - s.iconCenterX),
                abs(s.windowTop + s.windowHeight / 2 - s.iconCenterY),
            )
            val steps = 1000
            for (i in 1..steps) {
                g.at(i / steps.toFloat(), cur)
                // Sin saltos: cada paso de 0,1 % mueve como mucho su parte proporcional + redondeo.
                val bound = travel / steps * 3f + 2f
                assertTrue("${s.name} i=$i x", abs(cur.x - prev.x) <= bound)
                assertTrue("${s.name} i=$i y", abs(cur.y - prev.y) <= bound)
                assertTrue("${s.name} i=$i escala", abs(cur.scale - prev.scale) <= 0.01f)
                assertTrue("${s.name} i=$i escala no decrece", cur.scale >= prev.scale - 1e-6f)
                assertTrue("${s.name} i=$i radio", abs(cur.screenRadius - prev.screenRadius) <= 2f)
                prev = IconWindowGeometry.Frame().also {
                    it.x = cur.x
                    it.y = cur.y
                    it.scale = cur.scale
                    it.screenRadius = cur.screenRadius
                }
            }
        }
    }

    @Test
    fun localRadiusTimesScaleIsTheVisibleRadius() {
        for (s in scenarios) {
            val g = s.geometry()
            val f = IconWindowGeometry.Frame()
            var p = 0f
            while (p <= 1f) {
                g.at(p, f)
                assertEquals("${s.name} p=$p", f.screenRadius, f.localRadius * f.scale, 1e-3f)
                p += 0.01f
            }
        }
    }

    @Test
    fun outOfRangeProgressIsClamped() {
        val g = scenarios[0].geometry()
        val a = IconWindowGeometry.Frame()
        val b = IconWindowGeometry.Frame()
        g.at(-0.5f, a)
        g.at(0f, b)
        assertEquals(b.x, a.x, 0f)
        g.at(1.5f, a)
        g.at(1f, b)
        assertEquals(b.x, a.x, 0f)
    }

    @Test
    fun degenerateSizesDoNotProduceNaN() {
        val g = IconWindowGeometry(10f, 10f, 0f, 0f, 0f, 0f, 0f, 0f, 20f)
        val f = IconWindowGeometry.Frame()
        var p = 0f
        while (p <= 1f) {
            g.at(p, f)
            assertTrue(!f.x.isNaN() && !f.y.isNaN() && !f.scale.isNaN() && !f.localRadius.isNaN())
            p += 0.25f
        }
    }
}
