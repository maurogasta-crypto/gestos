package com.maurogasta.gestos

// ─────────────────────────────────────────────────────────────────────────────
// Instrumento.kt — Donde el gesto se vuelve sonido (gestos-instrumento 3.2)
//
// La misma lógica que `gestos/instrumento.js`: la mano de la nota (izquierda
// por defecto) sube y baja la altura sin cortes; la otra da el volumen, arriba
// fuerte y abajo calla; el puño calla; el pellizco de la mano de la nota da
// vibrato. Acercar o alejar la mano no cambia nada. Sin las dos manos a la
// vista no suena.
// ─────────────────────────────────────────────────────────────────────────────

interface Salida {
    fun altura(midi: Double, dura: Double)
    fun nivel(v: Double)
    fun vibrato(v: Double)
}

data class Ajustes(
    val tonica: String = "A",
    val octava: Int = 3,
    val escala: String = "pentaMenor",
    val octavas: Int = 2,
    val latino: Boolean = false,
    val manoNota: Lado = Lado.IZQUIERDA,
    val iman: Double = 0.0,
)

data class Estado(val altura: Double?, val nivel: Double, val etiqueta: String?)

class Instrumento(private val salida: Salida, ajustes: Ajustes = Ajustes()) {
    companion object {
        const val PELLIZCO_VIBRATO = 0.55
        private const val VOLUMEN_PISO = 0.85
        private const val VOLUMEN_TECHO = 0.20

        // y ∈ [0,1] (0 arriba) → nivel. Al cuadrado: el oído oye en logaritmo.
        fun nivelDeAltura(y: Double): Double {
            val k = ((VOLUMEN_PISO - y) / (VOLUMEN_PISO - VOLUMEN_TECHO)).coerceIn(0.0, 1.0)
            return k * k
        }
    }

    var ajustes = ajustes
        set(v) { field = v; recalcular() }

    var base = 0; private set
    var semitonos = 24; private set
    var guias: List<Int> = emptyList(); private set

    var altura: Double? = null; private set
    var nivel = 0.0; private set
    private var tAnterior = -1L

    init { recalcular() }

    private fun recalcular() {
        base = Musica.midiDe(ajustes.tonica, ajustes.octava)
        semitonos = ajustes.octavas * 12
        guias = Musica.rejilla(ajustes.tonica, ajustes.octava, ajustes.escala, ajustes.octavas)
    }

    fun cuadro(manos: ManosDelCuadro, tMs: Long): Estado {
        // La rampa del tono dura lo que tardó este cuadro, entre 15 y 90 ms.
        val dura = if (tAnterior < 0) 0.03 else ((tMs - tAnterior) / 1000.0).coerceIn(0.015, 0.09)
        tAnterior = tMs
        val mn = manos[ajustes.manoNota]
        val mv = manos[ajustes.manoNota.otro]
        if (mn != null) {
            val a = Musica.imanar(Musica.alturaDe(1 - mn.y, base, semitonos), guias, ajustes.iman)
            altura = a
            salida.altura(a, dura)
            salida.vibrato(((PELLIZCO_VIBRATO - mn.pellizco) / 0.45).coerceIn(0.0, 1.0) * 0.8)
        } else {
            altura = null
        }
        nivel = if (mn != null && mv != null && !mv.puno) nivelDeAltura(mv.y) else 0.0
        salida.nivel(nivel)
        return Estado(altura, nivel, altura?.let { Musica.etiquetaConCents(it, ajustes.latino) })
    }

    fun silencio() { nivel = 0.0; salida.nivel(0.0) }
}
