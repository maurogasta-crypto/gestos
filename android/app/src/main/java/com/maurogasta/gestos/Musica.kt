package com.maurogasta.gestos

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// Musica.kt — La teoría mínima del instrumento, traducida de `gestos/musica.js`
//
// Es la MISMA lógica que la página (gestos-musica 1.1): si cambia allá, cambia
// acá en la misma tanda. No sabe que existe una cámara ni un parlante, y por
// eso el banco la corre entera sin teléfono.
// ─────────────────────────────────────────────────────────────────────────────

object Musica {
    val CROMATICA = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    val LATINO = mapOf(
        "C" to "DO", "C#" to "DO#", "D" to "RE", "D#" to "RE#", "E" to "MI", "F" to "FA",
        "F#" to "FA#", "G" to "SOL", "G#" to "SOL#", "A" to "LA", "A#" to "LA#", "B" to "SI",
    )

    // La paleta oficial de colores tonales (copia de `NC` de musica.js, que a
    // su vez copia `src/App.jsx` de Harmonía). Tres copias: si cambia una,
    // cambian las tres.
    private val NC = mapOf(
        "C" to 0xFF002E93.toInt(), "D" to 0xFF357A25.toInt(), "E" to 0xFF845523.toInt(),
        "F" to 0xFFB8823C.toInt(), "G" to 0xFFF1B302.toInt(), "A" to 0xFFCD2821.toInt(),
        "B" to 0xFF672E87.toInt(), "C#" to 0xFF23879F.toInt(), "D#" to 0xFF91A51E.toInt(),
        "F#" to 0xFFECD9A3.toInt(), "G#" to 0xFFDC7212.toInt(), "A#" to 0xFFDA4571.toInt(),
    )

    data class Escala(val clave: String, val nombre: String, val ivs: List<Int>)

    val ESCALAS = listOf(
        Escala("pentaMenor", "Pentatónica menor", listOf(0, 3, 5, 7, 10)),
        Escala("pentaMayor", "Pentatónica mayor", listOf(0, 2, 4, 7, 9)),
        Escala("blues", "Blues", listOf(0, 3, 5, 6, 7, 10)),
        Escala("mayor", "Mayor (jónico)", listOf(0, 2, 4, 5, 7, 9, 11)),
        Escala("menorNatural", "Menor natural", listOf(0, 2, 3, 5, 7, 8, 10)),
        Escala("menorArmonica", "Menor armónica", listOf(0, 2, 3, 5, 7, 8, 11)),
        Escala("dorico", "Dórico", listOf(0, 2, 3, 5, 7, 9, 10)),
        Escala("frigio", "Frigio", listOf(0, 1, 3, 5, 7, 8, 10)),
        Escala("lidio", "Lidio", listOf(0, 2, 4, 6, 7, 9, 11)),
        Escala("mixolidio", "Mixolidio", listOf(0, 2, 4, 5, 7, 9, 10)),
        Escala("cromatica", "Cromática", (0..11).toList()),
    )

    fun escala(clave: String) = ESCALAS.firstOrNull { it.clave == clave } ?: ESCALAS[0]

    fun midiDe(nota: String, octava: Int) = CROMATICA.indexOf(nota) + (octava + 1) * 12
    fun nombreDe(midi: Int) = CROMATICA[((midi % 12) + 12) % 12]
    fun octavaDe(midi: Int) = floor(midi / 12.0).toInt() - 1
    fun colorDe(midi: Int) = NC[nombreDe(midi)] ?: 0xFF888888.toInt()
    fun etiquetaDe(midi: Int, latino: Boolean = false): String {
        val n = nombreDe(midi)
        return (if (latino) LATINO[n] ?: n else n) + octavaDe(midi)
    }

    fun frecuenciaDe(midi: Double, la: Double = 440.0) = la * 2.0.pow((midi - 69) / 12)

    // Las notas de la escala a lo largo del registro, cerrando en la tónica de arriba.
    fun rejilla(tonica: String, octava: Int, escala: String, octavas: Int): List<Int> {
        val ivs = escala(escala).ivs
        val base = midiDe(tonica, octava)
        val notas = mutableListOf<Int>()
        for (o in 0 until octavas) for (iv in ivs) notas.add(base + iv + o * 12)
        notas.add(base + octavas * 12)
        return notas
    }

    // ── El diapasón continuo ────────────────────────────────────────────────
    // La mano no llega al borde del cuadro sin que el modelo la pierda: el
    // margen hace tocables las dos puntas del registro.
    const val MARGEN = 0.06

    // k ∈ [0,1] (0 abajo, 1 arriba) → altura MIDI con decimales.
    fun alturaDe(k: Double, base: Int, semitonos: Int, margen: Double = MARGEN): Double {
        val r = ((k - margen) / (1 - 2 * margen)).coerceIn(0.0, 1.0)
        return base + r * semitonos
    }

    // La inversa exacta: dónde se dibuja una altura.
    fun posicionDe(midi: Double, base: Int, semitonos: Int, margen: Double = MARGEN) =
        margen + ((midi - base) / semitonos) * (1 - 2 * margen)

    // 0 = glissando puro; 1 = por notas.
    fun imanar(midi: Double, notas: List<Int>, fuerza: Double): Double {
        if (fuerza <= 0 || notas.isEmpty()) return midi
        val cerca = notas.minBy { abs(it - midi) }
        return cerca + (midi - cerca) * (1 - fuerza.coerceAtMost(1.0))
    }

    data class Afinacion(val nota: Int, val cents: Int)
    fun afinacionDe(midi: Double): Afinacion {
        val nota = midi.roundToInt()
        return Afinacion(nota, ((midi - nota) * 100).roundToInt())
    }

    fun etiquetaConCents(midi: Double, latino: Boolean): String {
        val a = afinacionDe(midi)
        val c = if (a.cents == 0) "" else " ${if (a.cents > 0) "+" else "−"}${abs(a.cents)}¢"
        return etiquetaDe(a.nota, latino) + c
    }
}
