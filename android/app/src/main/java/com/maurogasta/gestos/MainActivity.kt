package com.maurogasta.gestos

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat

// ─────────────────────────────────────────────────────────────────────────────
// MainActivity.kt — El cableado: cámara → instrumento → sonido y dibujo
//
// Ninguna decisión musical vive acá (están en Instrumento.kt y Musica.kt).
// Las vistas se arman en código y no en XML: son cuatro, y así se leen de una.
// ─────────────────────────────────────────────────────────────────────────────

class MainActivity : ComponentActivity() {
    private lateinit var sinte: Sinte
    private lateinit var instrumento: Instrumento
    private lateinit var camara: Camara
    private lateinit var lienzo: Lienzo
    private lateinit var vista: PreviewView
    private lateinit var nota: TextView
    private lateinit var datos: TextView
    private var error: String? = null

    private val pedirCamara = registerForActivityResult(ActivityResultContracts.RequestPermission()) { si ->
        if (si) encender() else datos.text = "Sin permiso de cámara no hay instrumento. Dalo en Ajustes → Aplicaciones → Gestos."
    }

    override fun onCreate(estado: Bundle?) {
        super.onCreate(estado)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        sinte = Sinte(this)
        instrumento = Instrumento(object : Salida {
            override fun altura(midi: Double, dura: Double) = sinte.voz.altura(midi, dura)
            override fun nivel(v: Double) { sinte.voz.nivelObjetivo = v }
            override fun vibrato(v: Double) { sinte.voz.vibratoObjetivo = v }
        }, Preferencias.leer(this))
        sinte.voz.afinacionLa = 440.0
        armarPantalla()
        camara = Camara(this, ::cuadro) { e -> error = e }
        // «cambiadas» y no «intercambiar»: desde gestos-apk-2 las manos se
        // reparten por lado, y el «intercambiar» guardado decía otra cosa.
        camara.cambiadas = getSharedPreferences(Preferencias.ARCHIVO, MODE_PRIVATE).getBoolean("cambiadas", false)
    }

    private fun armarPantalla() {
        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(10, 10, 26))
            setPadding(16, 16, 16, 16)
        }
        // La escena tiene la forma de la imagen (3:4, de pie), así el dibujo
        // cae exactamente sobre la mano y no corrido.
        val escena = object : FrameLayout(this) {
            override fun onMeasure(ancho: Int, alto: Int) {
                val w = MeasureSpec.getSize(ancho)
                val hMax = MeasureSpec.getSize(alto)
                var ew = w
                var eh = w * 4 / 3
                if (eh > hMax && hMax > 0) { eh = hMax; ew = hMax * 3 / 4 }
                super.onMeasure(MeasureSpec.makeMeasureSpec(ew, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(eh, MeasureSpec.EXACTLY))
            }
        }
        vista = PreviewView(this).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
        lienzo = Lienzo(this).apply { instrumento = this@MainActivity.instrumento }
        escena.addView(vista, FrameLayout.LayoutParams(-1, -1))
        escena.addView(lienzo, FrameLayout.LayoutParams(-1, -1))
        raiz.addView(escena, LinearLayout.LayoutParams(-1, 0, 1f).apply { gravity = Gravity.CENTER_HORIZONTAL })

        val lectura = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 12, 0, 12) }
        nota = TextView(this).apply { textSize = 28f; setTextColor(Color.GRAY); text = "—"; minWidth = 260 }
        datos = TextView(this).apply { textSize = 12f; setTextColor(Color.rgb(139, 140, 168)); text = "Encendiendo…" }
        lectura.addView(nota)
        lectura.addView(datos, LinearLayout.LayoutParams(0, -2, 1f))
        raiz.addView(lectura)

        val fila = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun boton(t: String, al: () -> Unit) = Button(this).apply { text = t; isAllCaps = false; setOnClickListener { al() } }
            .also { fila.addView(it, LinearLayout.LayoutParams(0, -2, 1f)) }
        boton("Silencio") { instrumento.silencio() }
        boton("Manos cambiadas") {
            camara.cambiadas = !camara.cambiadas
            getSharedPreferences(Preferencias.ARCHIVO, MODE_PRIVATE).edit().putBoolean("cambiadas", camara.cambiadas).apply()
        }
        boton("Ajustes") { mostrarAjustes() }
        raiz.addView(fila)
        setContentView(raiz)
    }

    override fun onStart() {
        super.onStart()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) encender()
        else pedirCamara.launch(Manifest.permission.CAMERA)
    }

    private fun encender() {
        sinte.arrancar()
        camara.arrancar(this, vista)
    }

    // Al irse de la pantalla, callar y soltar todo: un instrumento que suena
    // de fondo es lo primero que hace que se desinstale.
    override fun onStop() {
        super.onStop()
        instrumento.silencio()
        sinte.detener()
        camara.detener()
    }

    private var ultimoTexto = 0L
    private fun cuadro(manos: ManosDelCuadro, tMs: Long) {
        val e = instrumento.cuadro(manos, tMs)
        lienzo.manos = manos
        lienzo.postInvalidateOnAnimation()
        // El texto no necesita 30 repintados por segundo.
        if (tMs - ultimoTexto < 120) return
        ultimoTexto = tMs
        runOnUiThread {
            nota.text = e.etiqueta ?: "—"
            nota.setTextColor(if (e.nivel > 0.02 && e.altura != null) Color.WHITE else Color.GRAY)
            val partes = mutableListOf(
                if (e.nivel > 0.02) "sonando · volumen ${(Math.sqrt(e.nivel) * 100).toInt()} %" else "en silencio",
                "${camara.fps} cuadros/s",
                "reconoce ${camara.msReconocer} ms (${camara.delegado}; ${camara.eleccion})",
                "audio ${sinte.latenciaMs} ms",
                "cámara ${camara.resolucion} · ${camara.rangoFps}",
            )
            error?.let { partes.add("⚠ $it") }
            datos.text = partes.joinToString(" · ")
        }
    }

    private fun mostrarAjustes() {
        val a = instrumento.ajustes
        val caja = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 24, 48, 0) }
        fun fila(rotulo: String, v: View) {
            caja.addView(TextView(this).apply { text = rotulo; setPadding(0, 18, 0, 0) })
            caja.addView(v, ViewGroup.LayoutParams(-1, -2))
        }
        fun lista(opciones: List<String>, elegida: Int) = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, opciones)
            setSelection(elegida.coerceAtLeast(0))
        }
        val tonica = lista(Musica.CROMATICA.map { "$it · ${Musica.LATINO[it]}" }, Musica.CROMATICA.indexOf(a.tonica))
        val escala = lista(Musica.ESCALAS.map { it.nombre }, Musica.ESCALAS.indexOfFirst { it.clave == a.escala })
        val octava = lista((1..6).map { "$it" }, a.octava - 1)
        val octavas = lista((1..4).map { "$it" }, a.octavas - 1)
        val mano = lista(listOf("izquierda", "derecha"), if (a.manoNota == Lado.IZQUIERDA) 0 else 1)
        val iman = SeekBar(this).apply { max = 20; progress = (a.iman * 20).toInt() }
        val latino = CheckBox(this).apply { text = "Nombres en DO RE MI"; isChecked = a.latino }
        fila("La nota con la mano", mano)
        fila("Tónica", tonica)
        fila("Escala (las líneas marcadas, y adonde tira el imán)", escala)
        fila("Octava de partida", octava)
        fila("Cuántas octavas", octavas)
        fila("Imán a la escala (0 = glissando puro)", iman)
        caja.addView(latino)
        AlertDialog.Builder(this)
            .setTitle("Ajustes")
            .setView(caja)
            .setPositiveButton("Listo") { _, _ ->
                val nuevos = Ajustes(
                    tonica = Musica.CROMATICA[tonica.selectedItemPosition],
                    octava = octava.selectedItemPosition + 1,
                    escala = Musica.ESCALAS[escala.selectedItemPosition].clave,
                    octavas = octavas.selectedItemPosition + 1,
                    latino = latino.isChecked,
                    manoNota = if (mano.selectedItemPosition == 0) Lado.IZQUIERDA else Lado.DERECHA,
                    iman = iman.progress / 20.0,
                )
                instrumento.ajustes = nuevos
                Preferencias.guardar(this, nuevos)
                lienzo.invalidate()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}

object Preferencias {
    const val ARCHIVO = "gestos"
    fun leer(c: Context): Ajustes {
        val p = c.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)
        val d = Ajustes()
        return Ajustes(
            tonica = p.getString("tonica", d.tonica) ?: d.tonica,
            octava = p.getInt("octava", d.octava),
            escala = p.getString("escala", d.escala) ?: d.escala,
            octavas = p.getInt("octavas", d.octavas),
            latino = p.getBoolean("latino", d.latino),
            manoNota = if (p.getString("manoNota", "izquierda") == "derecha") Lado.DERECHA else Lado.IZQUIERDA,
            iman = p.getFloat("iman", 0f).toDouble(),
        )
    }
    fun guardar(c: Context, a: Ajustes) {
        c.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE).edit()
            .putString("tonica", a.tonica).putInt("octava", a.octava).putString("escala", a.escala)
            .putInt("octavas", a.octavas).putBoolean("latino", a.latino)
            .putString("manoNota", if (a.manoNota == Lado.DERECHA) "derecha" else "izquierda")
            .putFloat("iman", a.iman.toFloat()).apply()
    }
}
