package com.maurogasta.gestos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.ln

// El banco de la APK: la misma teoría que `pruebas/gestos.mjs` de Harmonía
// (si allá cambia un caso, cambia acá), más lo que sólo existe en nativo —la
// voz que se desliza muestra por muestra.
class GestosTest {

    // ── Musica ──────────────────────────────────────────────────────────────
    @Test fun doCentralEsMidi60() {
        assertEquals(60, Musica.midiDe("C", 4))
        assertEquals("C4", Musica.etiquetaDe(60))
        assertEquals("DO4", Musica.etiquetaDe(60, latino = true))
        assertEquals(440.0, Musica.frecuenciaDe(69.0), 1e-9)
        assertEquals(2.0, Musica.frecuenciaDe(81.0) / Musica.frecuenciaDe(69.0), 1e-12)
    }

    @Test fun laRejillaCreceYCierraEnLaTonica() {
        for (e in Musica.ESCALAS) {
            val r = Musica.rejilla("C", 3, e.clave, 2)
            assertEquals(e.ivs.size * 2 + 1, r.size)
            for (i in 1 until r.size) assertTrue("${e.clave} no crece en $i", r[i] > r[i - 1])
        }
    }

    @Test fun laAlturaEsContinuaYPosicionEsSuInversa() {
        assertEquals(69.0, Musica.alturaDe(0.5, 57, 24), 1e-12)
        assertEquals(57.0, Musica.alturaDe(0.0, 57, 24), 1e-12)
        assertEquals(81.0, Musica.alturaDe(1.0, 57, 24), 1e-12)
        var m = 57.0
        while (m <= 81) {
            assertEquals(m, Musica.alturaDe(Musica.posicionDe(m, 57, 24), 57, 24), 1e-9)
            m += 0.25
        }
        for (i in 0 until 100) {
            val k = Musica.MARGEN + i / 100.0 * (1 - 2 * Musica.MARGEN)
            val d = Musica.alturaDe(k + 0.001, 57, 24) - Musica.alturaDe(k, 57, 24)
            assertTrue("salto de $d en $k", d > 0 && d < 0.05)
        }
    }

    @Test fun elImanAcercaYNuncaAleja() {
        val notas = Musica.rejilla("A", 3, "pentaMenor", 2)
        assertEquals(58.3, Musica.imanar(58.3, notas, 0.0), 1e-12)
        assertEquals(57.0, Musica.imanar(58.3, notas, 1.0), 1e-12)
        var m = 57.0
        while (m <= 81) {
            val d0 = notas.minOf { abs(it - m) }
            val d1 = notas.minOf { abs(it - Musica.imanar(m, notas, 0.6)) }
            assertTrue(d1 <= d0 + 1e-9)
            m += 0.1
        }
    }

    @Test fun laEtiquetaLlevaLosCents() {
        assertEquals("A4 +12¢", Musica.etiquetaConCents(69.12, false))
        assertEquals("A4 −30¢", Musica.etiquetaConCents(68.7, false))
        assertEquals("A4", Musica.etiquetaConCents(69.0, false))
    }

    // ── El instrumento ──────────────────────────────────────────────────────
    private class Registro : Salida {
        val alturas = mutableListOf<Double>(); val duras = mutableListOf<Double>(); val niveles = mutableListOf<Double>()
        override fun altura(midi: Double, dura: Double) { alturas.add(midi); duras.add(dura) }
        override fun nivel(v: Double) { niveles.add(v) }
        override fun vibrato(v: Double) {}
    }
    private fun mano(x: Double, y: Double, puno: Boolean = false, pellizco: Double = 1.0, palmo: Double = 0.2) =
        Mano(x, y, palmo, pellizco, puno, emptyList())

    @Test fun laIzquierdaEsLaNotaYSubirlaSubeLaNota() {
        val r = Registro()
        val ins = Instrumento(r)
        assertEquals(Lado.IZQUIERDA, ins.ajustes.manoNota)
        var antes = Double.NEGATIVE_INFINITY
        for (i in 19 downTo 1) {
            ins.cuadro(ManosDelCuadro(mano(0.3, i / 20.0), mano(0.8, 0.2)), i * 33L)
            assertTrue(r.alturas.last() >= antes); antes = r.alturas.last()
        }
        assertEquals(81.0, r.alturas.last(), 1e-9)
        // Moverla de costado o acercarla (palmo) no cambia la nota.
        ins.cuadro(ManosDelCuadro(mano(0.1, 0.5, palmo = 0.1), mano(0.8, 0.2)), 1000)
        val a = r.alturas.last()
        ins.cuadro(ManosDelCuadro(mano(0.45, 0.5, palmo = 0.35), mano(0.8, 0.2)), 1033)
        assertEquals(a, r.alturas.last(), 1e-12)
    }

    @Test fun sinLasDosManosOConElPunoCalla() {
        val r = Registro()
        val ins = Instrumento(r)
        val e = ins.cuadro(ManosDelCuadro(mano(0.3, 0.5), mano(0.8, 0.2)), 0)
        assertTrue(e.nivel > 0.5)
        assertEquals("A4", e.etiqueta)
        ins.cuadro(ManosDelCuadro(mano(0.3, 0.5), null), 33); assertEquals(0.0, r.niveles.last(), 0.0)
        ins.cuadro(ManosDelCuadro(mano(0.3, 0.5), mano(0.8, 0.2, puno = true)), 66); assertEquals(0.0, r.niveles.last(), 0.0)
        val sinNota = ins.cuadro(ManosDelCuadro(null, mano(0.8, 0.2)), 99)
        assertEquals(0.0, r.niveles.last(), 0.0)
        assertNull(sinNota.altura)
    }

    @Test fun elVolumenBajaAlBajarLaMano() {
        assertEquals(1.0, Instrumento.nivelDeAltura(0.1), 0.0)
        assertEquals(0.0, Instrumento.nivelDeAltura(0.9), 0.0)
        var antes = 2.0
        for (i in 0..100) { val n = Instrumento.nivelDeAltura(i / 100.0); assertTrue(n <= antes); antes = n }
    }

    @Test fun laRampaDuraLoQueTardoElCuadro() {
        val r = Registro()
        val ins = Instrumento(r)
        var t = 0L
        for (i in 0 until 5) { t += 66; ins.cuadro(ManosDelCuadro(mano(0.3, 0.5), mano(0.8, 0.2)), t) }
        assertEquals(0.066, r.duras.last(), 1e-9)
        assertTrue(r.duras.all { it in 0.015..0.09 })
    }

    // ── El suavizado ────────────────────────────────────────────────────────
    @Test fun elSuavizadoLlegaATiempo() {
        val f = Suavizador()
        var atraso = 0.0; var n = 0
        for (i in 0 until 60) {
            val real = i * 0.0333
            val visto = f.filtrar(real, (i * 33.3).toLong())
            if (i > 20) { atraso += real - visto; n++ }
        }
        assertTrue("llega ${atraso / n * 1000} ms tarde", atraso / n * 1000 < 40)
    }

    // ── La voz: un violín, no un teclado ────────────────────────────────────
    @Test fun laVozSeDeslizaSinEscalones() {
        val tasa = 48000
        val voz = Voz(tasa)
        voz.nivelObjetivo = 1.0
        val buf = FloatArray(96)
        voz.altura(57.0, 0.005)
        repeat(20) { voz.render(buf, 96) }
        // La cámara manda una altura cada 33 ms, subiendo medio semitono por vez.
        // Entre dos mandatos la frecuencia tiene que seguir moviéndose, de a
        // poco, y nunca quedarse quieta para después saltar.
        val log = mutableListOf<Double>()
        for (k in 1..10) {
            voz.altura(57.0 + k * 0.5, 0.033)
            repeat(16) { voz.render(buf, 96); log.add(ln(voz.frecuenciaActual)) }  // 16 × 2 ms = 32 ms
        }
        val pasos = log.zipWithNext { a, b -> b - a }
        val medioSemitono = ln(2.0) / 24
        for (p in pasos) {
            assertTrue("se quedó quieta o bajó: $p", p > 0)
            assertTrue("saltó ${p / medioSemitono} medios semitonos en 2 ms", p < medioSemitono * 0.2)
        }
    }

    @Test fun laVozNoSeSaleDeRangoNiDaNaN() {
        val voz = Voz(48000)
        voz.nivelObjetivo = 1.0
        voz.vibratoObjetivo = 1.0
        val buf = FloatArray(256)
        for (m in listOf(24.0, 57.0, 96.0, 110.0)) {
            voz.altura(m, 0.01)
            repeat(50) {
                voz.render(buf, buf.size)
                for (s in buf) assertTrue("muestra $s en MIDI $m", !s.isNaN() && s in -1f..1f)
            }
        }
        voz.nivelObjetivo = 0.0
        repeat(200) { voz.render(buf, buf.size) }
        assertTrue("sigue sonando con nivel 0", buf.all { abs(it) < 1e-3 })
    }

    // ── Qué mano es cuál: por el lado de la pantalla ────────────────────────
    @Test fun lasManosSeRepartenPorElLado() {
        val a = mano(0.7, 0.5); val b = mano(0.2, 0.4)
        val r = Repartir.porLado(listOf(a, b), cambiadas = false)
        assertTrue(r.izquierda === b && r.derecha === a)
        val c = Repartir.porLado(listOf(a, b), cambiadas = true)
        assertTrue(c.izquierda === a && c.derecha === b)
        // Una sola mano: el lado de la pantalla donde está.
        assertTrue(Repartir.porLado(listOf(b), false).izquierda === b)
        assertTrue(Repartir.porLado(listOf(a), false).derecha === a)
        // Tres manos (alguien atrás): tocan las dos más grandes.
        val lejos = mano(0.5, 0.5, palmo = 0.05)
        val t = Repartir.porLado(listOf(lejos, a, b), false)
        assertTrue(t.izquierda === b && t.derecha === a)
        val nada = Repartir.porLado(emptyList(), false)
        assertNull(nada.izquierda); assertNull(nada.derecha)
    }

    // ── El manual no puede mentir ───────────────────────────────────────────
    @Test fun elManualNombraCadaBotonYLasDosManos() {
        val todo = Manual.partes.joinToString(" ") { it.first + " " + it.second }
        for (b in Manual.BOTONES) assertTrue("el manual no nombra «$b»", b in todo)
        for (p in listOf("izquierda", "derecha", "volumen", "imán", "pulgar")) assertTrue("falta «$p»", p in todo)
    }
}
