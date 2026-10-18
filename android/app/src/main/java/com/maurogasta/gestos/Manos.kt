package com.maurogasta.gestos

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot

// ─────────────────────────────────────────────────────────────────────────────
// Manos.kt — De 21 puntos por mano a cuatro números con sentido musical
//
// Traducción de `leerMano` y del suavizado de `gestos/manos.js` (gestos-manos
// 1.4). No sabe de MediaPipe: recibe puntos ya normalizados, así que el banco
// lo prueba con manos fabricadas.
// ─────────────────────────────────────────────────────────────────────────────

data class Punto(val x: Double, val y: Double)

data class Mano(
    val x: Double,           // 0 izquierda … 1 derecha, como se ve en pantalla
    val y: Double,           // 0 arriba … 1 abajo
    val palmo: Double,
    val pellizco: Double,    // distancia pulgar–índice, en palmos
    val puno: Boolean,
    val puntos: List<Punto>, // para dibujar el esqueleto
)

class ManosDelCuadro(val izquierda: Mano?, val derecha: Mano?) {
    operator fun get(lado: Lado) = if (lado == Lado.IZQUIERDA) izquierda else derecha
}

enum class Lado { IZQUIERDA, DERECHA;
    val otro get() = if (this == IZQUIERDA) DERECHA else IZQUIERDA
}

object LeerMano {
    private const val MUNECA = 0
    private const val PULGAR = 4
    private const val INDICE = 8
    private const val NUDILLO_MEDIO = 9
    private val PUNTAS = mapOf(8 to 5, 12 to 9, 16 to 13, 20 to 17)

    private fun dist(a: Punto, b: Punto) = hypot(a.x - b.x, a.y - b.y)

    // La posición se lee del nudillo del medio: casi no se mueve al pellizcar,
    // así que el vibrato no corre la nota.
    fun leer(p: List<Punto>): Mano {
        val muneca = p[MUNECA]
        val palmo = maxOf(0.02, dist(muneca, p[NUDILLO_MEDIO]))
        val pellizco = dist(p[PULGAR], p[INDICE]) / palmo
        var doblados = 0
        for ((punta, nudillo) in PUNTAS) {
            if (dist(p[punta], muneca) < dist(p[nudillo], muneca) * 1.15) doblados++
        }
        val c = p[NUDILLO_MEDIO]
        return Mano(c.x, c.y, palmo, pellizco, doblados >= 3 && pellizco < 1.1, p)
    }
}

// Filtro de una euro: cuanto más rápido se mueve la mano, menos se la suaviza.
// beta 3 con la velocidad en pantallas por segundo: con 0,02 (la página hasta
// gestos-manos 1.2) era un pasabajos fijo que llegaba 123 ms tarde.
class Suavizador(private val minCorte: Double = 1.2, private val beta: Double = 3.0, private val dCorte: Double = 1.0) {
    private var x = Double.NaN
    private var dx = 0.0
    private var tPrev = 0L

    private fun alfa(corte: Double, dt: Double): Double {
        val tau = 1 / (2 * PI * corte)
        return 1 / (1 + tau / dt)
    }

    fun filtrar(valor: Double, tMs: Long): Double {
        if (x.isNaN()) { x = valor; tPrev = tMs; return x }
        val dt = maxOf(0.001, (tMs - tPrev) / 1000.0)
        tPrev = tMs
        dx += alfa(dCorte, dt) * ((valor - x) / dt - dx)
        x += alfa(minCorte + beta * abs(dx), dt) * (valor - x)
        return x
    }

    fun olvidar() { x = Double.NaN; dx = 0.0 }
}
