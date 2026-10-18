package com.maurogasta.gestos

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.View
import kotlin.math.sqrt

// ─────────────────────────────────────────────────────────────────────────────
// Lienzo.kt — Lo que se dibuja encima de la cámara (como `dibujar` de la página)
//
// Un degradé de abajo (grave) a arriba (agudo) con el color tonal de cada
// semitono en su lugar, una línea por semitono —marcada en la escala—, la
// línea de la altura con su nombre, la barra del volumen y el esqueleto de
// las manos.
// ─────────────────────────────────────────────────────────────────────────────

class Lienzo(contexto: Context) : View(contexto) {
    @Volatile var instrumento: Instrumento? = null
    @Volatile var manos: ManosDelCuadro? = null

    private val pincel = Paint(Paint.ANTI_ALIAS_FLAG)
    private val texto = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private var fondo: LinearGradient? = null
    private var firmaFondo = ""

    private val huesos = arrayOf(
        0 to 1, 1 to 2, 2 to 3, 3 to 4, 0 to 5, 5 to 6, 6 to 7, 7 to 8,
        5 to 9, 9 to 10, 10 to 11, 11 to 12, 9 to 13, 13 to 14, 14 to 15, 15 to 16,
        13 to 17, 17 to 18, 18 to 19, 19 to 20, 0 to 17,
    )

    override fun onDraw(c: Canvas) {
        val ins = instrumento ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        val base = ins.base
        val semis = ins.semitonos
        fun yDe(midi: Double) = ((1 - Musica.posicionDe(midi, base, semis)) * h).toFloat()

        val firma = "$base/$semis/$w/$h"
        if (firma != firmaFondo || fondo == null) {
            firmaFondo = firma
            val colores = IntArray(semis + 1) { Musica.colorDe(base + it) }
            val lugares = FloatArray(semis + 1) { it / semis.toFloat() }
            fondo = LinearGradient(0f, yDe(base.toDouble()), 0f, yDe((base + semis).toDouble()), colores, lugares, Shader.TileMode.CLAMP)
        }
        pincel.shader = fondo
        pincel.alpha = ((0.16 + 0.5 * ins.nivel) * 255).toInt()
        c.drawRect(0f, 0f, w, h, pincel)
        pincel.shader = null
        pincel.alpha = 255

        val chico = 11f * resources.displayMetrics.scaledDensity
        texto.textSize = chico
        texto.textAlign = Paint.Align.LEFT
        for (i in 0..semis) {
            val midi = base + i
            val y = yDe(midi.toDouble())
            val enEscala = midi in ins.guias
            pincel.color = Color.argb(if (enEscala) 97 else 26, 255, 255, 255)
            pincel.strokeWidth = if (enEscala) 3f else 1.5f
            c.drawLine(0f, y, w, y, pincel)
            if (enEscala) {
                texto.alpha = 190
                val n = Musica.nombreDe(midi)
                c.drawText(if (ins.ajustes.latino) Musica.LATINO[n] ?: n else n, 30f, y - 6f, texto)
            }
        }

        ins.altura?.let { a ->
            val y = yDe(a)
            pincel.color = Color.argb(if (ins.nivel > 0.02) 242 else 102, 255, 255, 255)
            pincel.strokeWidth = 5f
            c.drawLine(0f, y, w, y, pincel)
            texto.alpha = 255
            texto.textSize = chico * 2.3f
            texto.textAlign = Paint.Align.CENTER
            val et = Musica.etiquetaDe(Musica.afinacionDe(a).nota, ins.ajustes.latino)
            c.drawText(et, w / 2, if (y < h * 0.12f) y + texto.textSize + 8f else y - 12f, texto)
        }

        val m = manos ?: return
        val mn = m[ins.ajustes.manoNota]
        val mv = m[ins.ajustes.manoNota.otro]
        if (mv != null) {
            val bx = if (mv.x < 0.5) 8f else w - 26f
            pincel.color = Color.argb(38, 255, 255, 255)
            c.drawRect(bx, 40f, bx + 18f, h - 40f, pincel)
            pincel.color = if (mv.puno) Color.argb(242, 255, 190, 60) else Color.argb(230, 120, 200, 255)
            val alto = (h - 80f) * sqrt(ins.nivel).toFloat()
            c.drawRect(bx, h - 40f - alto, bx + 18f, h - 40f, pincel)
        }
        for (mano in listOfNotNull(mn, mv)) {
            val esNota = mano === mn
            pincel.color = if (esNota) Color.argb(190, 255, 255, 255) else Color.argb(180, 120, 200, 255)
            pincel.strokeWidth = 4f
            for ((a, b) in huesos) {
                val p = mano.puntos[a]; val q = mano.puntos[b]
                c.drawLine((p.x * w).toFloat(), (p.y * h).toFloat(), (q.x * w).toFloat(), (q.y * h).toFloat(), pincel)
            }
            pincel.color = when {
                esNota -> if (ins.nivel > 0.02) Color.WHITE else Color.argb(90, 255, 255, 255)
                mano.puno -> Color.argb(242, 255, 190, 60)
                else -> Color.argb(150, 120, 200, 255)
            }
            c.drawCircle((mano.x * w).toFloat(), (mano.y * h).toFloat(), 16f, pincel)
        }
    }
}
