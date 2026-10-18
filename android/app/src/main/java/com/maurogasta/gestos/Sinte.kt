package com.maurogasta.gestos

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────────────────────
// Sinte.kt — El sonido, hecho acá y escrito directo al parlante
//
// Por qué no Web Audio ni una biblioteca: la latencia. Chrome en Android suma
// 40–100 ms de salida; un AudioTrack en modo de baja latencia, con el búfer
// del tamaño de una ráfaga del sistema, anda en 10–30 ms en un teléfono que lo
// soporte. Y por qué no Oboe: pediría C++ y el NDK para ganar unos pocos ms
// más; si algún día hacen falta, éste es el archivo que cambia.
//
// La voz es la de la página —parciales con dos lengüetas microdesafinadas, la
// idea del bandoneón de Harmonía— pero aditiva (senos), así que no hay
// aliasing en los agudos.
// ─────────────────────────────────────────────────────────────────────────────

// La parte que hace las cuentas, sin AudioTrack: el banco la corre en la JVM.
class Voz(private val tasa: Int) {
    private companion object {
        const val TABLA = 4096
        val SENO = FloatArray(TABLA) { sin(2 * PI * it / TABLA).toFloat() }
        // Armónico n con volumen 1/n, como una sierra, recortada por el brillo.
        val VOLUMEN = DoubleArray(8) { i -> 0.30 / (i + 1) * 0.86.pow(i) }
        val CENTS = doubleArrayOf(-4.0, 4.0)
    }

    // Lo que escribe el hilo de la cámara. @Volatile: lo lee el hilo del audio.
    @Volatile private var objetivoLog = ln(220.0) / ln(2.0)
    @Volatile private var rampaS = 0.03
    @Volatile private var turno = 0
    @Volatile var nivelObjetivo = 0.0
    @Volatile var vibratoObjetivo = 0.0   // 0..1
    @Volatile var afinacionLa = 440.0

    private var log2f = objetivoLog
    private var paso = 0.0
    private var faltan = 0
    private var turnoVisto = 0
    private var nivel = 0.0
    private var vibrato = 0.0
    private var fasLfo = 0.0
    private val fases = DoubleArray(VOLUMEN.size * CENTS.size)

    private val kNivel = 1 - exp(-1.0 / (0.015 * tasa))
    private val kVibrato = 1 - exp(-1.0 / (0.08 * tasa))

    // La altura se DESLIZA hasta el valor nuevo durante `dura` segundos —lo que
    // tardó en llegar el cuadro de la cámara—, en línea recta sobre el
    // logaritmo de la frecuencia: un violín, no un teclado.
    fun altura(midi: Double, dura: Double) {
        objetivoLog = ln(Musica.frecuenciaDe(midi, afinacionLa)) / ln(2.0)
        rampaS = dura.coerceIn(0.005, 0.12)
        turno++
    }

    val frecuenciaActual get() = 2.0.pow(log2f)

    fun render(salida: FloatArray, cuadros: Int) {
        if (turno != turnoVisto) {
            turnoVisto = turno
            faltan = maxOf(1, (rampaS * tasa).toInt())
            paso = (objetivoLog - log2f) / faltan
        }
        val nivelObj = nivelObjetivo
        val vibObj = vibratoObjetivo
        for (i in 0 until cuadros) {
            if (faltan > 0) { log2f += paso; faltan-- }
            nivel += (nivelObj - nivel) * kNivel
            vibrato += (vibObj - vibrato) * kVibrato
            fasLfo += 5.2 / tasa
            if (fasLfo >= 1) fasLfo -= 1
            // Hasta ±18 cents de vibrato, como la página (0,8 × 22).
            val vib = vibrato * 18.0 * sin(2 * PI * fasLfo) / 1200.0
            val f = 2.0.pow(log2f + vib)
            var suma = 0.0
            if (nivel > 1e-5) {
                var k = 0
                for (h in VOLUMEN.indices) {
                    val fh = f * (h + 1)
                    for (c in CENTS) {
                        val d = fh * (1 + c / 1731.0)   // ≈ 2^(c/1200) para c chico
                        if (d < tasa * 0.45) {
                            fases[k] += d / tasa
                            if (fases[k] >= 1) fases[k] -= fases[k].toInt()
                            suma += VOLUMEN[h] * 0.5 * SENO[(fases[k] * TABLA).toInt() and (TABLA - 1)]
                        }
                        k++
                    }
                }
            }
            // Un recorte suave: nunca pasa de ±1, y abajo de 0,5 casi no toca.
            val x = (suma * nivel * 1.6).coerceIn(-1.0, 1.0)
            salida[i] = (x * (1.5 - 0.5 * x * x)).toFloat()
        }
    }
}

class Sinte(contexto: Context) {
    private val am = contexto.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    // La tasa y la ráfaga NATIVAS del teléfono: con cualquier otra, Android
    // remuestrea y el camino rápido se pierde.
    val tasa = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 48000
    val rafaga = am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull() ?: 192
    val voz = Voz(tasa)

    private var pista: AudioTrack? = null
    @Volatile private var andando = false
    private var hilo: Thread? = null

    // Lo que tarda en salir lo que se escribe, en ms: el búfer de la pista.
    @Volatile var latenciaMs = 0
        private set

    fun arrancar() {
        if (andando) return
        val p = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(tasa)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(rafaga * 8 * 4)
            .build()
        // Empieza con dos ráfagas y crece sola si el teléfono no da abasto
        // (cada corte se oye como un clic): es lo que hace Oboe.
        p.bufferSizeInFrames = rafaga * 2
        pista = p
        andando = true
        p.play()
        hilo = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val buf = FloatArray(rafaga)
            var cortes = p.underrunCount
            while (andando) {
                voz.render(buf, rafaga)
                p.write(buf, 0, rafaga, AudioTrack.WRITE_BLOCKING)
                val ahora = p.underrunCount
                if (ahora > cortes) {
                    cortes = ahora
                    val mas = minOf(p.bufferCapacityInFrames, p.bufferSizeInFrames + rafaga)
                    p.bufferSizeInFrames = mas
                }
                latenciaMs = p.bufferSizeInFrames * 1000 / tasa
            }
        }, "sinte").apply { start() }
    }

    fun detener() {
        andando = false
        hilo?.join(300)
        hilo = null
        pista?.run { try { stop() } catch (_: Exception) {}; release() }
        pista = null
    }
}
