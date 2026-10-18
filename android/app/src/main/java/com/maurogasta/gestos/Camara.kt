package com.maurogasta.gestos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.SystemClock
import android.util.Range
import android.util.Size
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.util.concurrent.Executors

// ─────────────────────────────────────────────────────────────────────────────
// Camara.kt — Cámara frontal + MediaPipe, nativos
//
// Lo mismo que `gestos/manos.js`, sin el navegador en el medio: CameraX
// entrega cada cuadro apenas llega (sólo el último: si el modelo está ocupado,
// el viejo se tira, que un cuadro atrasado es peor que ninguno) y MediaPipe
// corre en la GPU del teléfono, con la CPU de respaldo.
// ─────────────────────────────────────────────────────────────────────────────

class Camara(
    private val contexto: Context,
    private val alCuadro: (ManosDelCuadro, Long) -> Unit,
    private val alError: (String) -> Unit,
) {
    @Volatile var intercambiar = true
    @Volatile var fps = 0; private set
    @Volatile var msReconocer = 0; private set
    @Volatile var delegado = "—"; private set
    @Volatile var resolucion = "—"; private set
    @Volatile var rangoFps = "—"; private set

    private var landmarker: HandLandmarker? = null
    private val hilo = Executors.newSingleThreadExecutor()
    private val suaves = mapOf(
        Lado.IZQUIERDA to (Suavizador() to Suavizador()),
        Lado.DERECHA to (Suavizador() to Suavizador()),
    )
    private var cuadros = 0
    private var tFps = 0L
    private var ultimoTs = 0L

    private fun crearLandmarker(delegate: Delegate): HandLandmarker {
        val base = BaseOptions.builder()
            .setModelAssetPath("hand_landmarker.task")
            .setDelegate(delegate)
            .build()
        val opciones = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(base)
            .setNumHands(2)
            .setMinHandDetectionConfidence(0.5f)
            .setMinHandPresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { r, _ -> resultado(r) }
            .setErrorListener { e -> alError("el reconocimiento falló: ${e.message}") }
            .build()
        return HandLandmarker.createFromOptions(contexto, opciones)
    }

    fun arrancar(dueno: LifecycleOwner, vista: PreviewView) {
        landmarker = try {
            crearLandmarker(Delegate.GPU).also { delegado = "GPU" }
        } catch (e: Exception) {
            crearLandmarker(Delegate.CPU).also { delegado = "CPU" }
        }
        val futuro = ProcessCameraProvider.getInstance(contexto)
        futuro.addListener({
            try { vincular(futuro.get(), dueno, vista) } catch (e: Exception) {
                alError("no se pudo abrir la cámara: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(contexto))
    }

    // El rango de cuadros por segundo más alto que la cámara frontal admite:
    // cada cuadro de más es retraso de menos.
    private fun mejorRango(): Range<Int>? = try {
        val cm = contexto.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cm.cameraIdList.map { cm.getCameraCharacteristics(it) }
            .firstOrNull { it.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT }
            ?.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            ?.maxWithOrNull(compareBy<Range<Int>>({ it.upper }, { it.lower }))
    } catch (e: Exception) { null }

    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    private fun vincular(proveedor: ProcessCameraProvider, dueno: LifecycleOwner, vista: PreviewView) {
        val tamano = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            // El modelo achica la imagen a 224 igual: más píxeles son sólo tiempo.
            .setResolutionStrategy(ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
            .build()
        val rango = mejorRango()
        rangoFps = rango?.toString() ?: "—"

        val previa = Preview.Builder().setResolutionSelector(tamano).apply {
            rango?.let { Camera2Interop.Extender(this).setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, it) }
        }.build()
        previa.surfaceProvider = vista.surfaceProvider

        val analisis = ImageAnalysis.Builder()
            .setResolutionSelector(tamano)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .apply {
                rango?.let { Camera2Interop.Extender(this).setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, it) }
            }
            .build()

        val giro = Matrix()
        analisis.setAnalyzer(hilo) { imagen ->
            try {
                val cruda = imagen.toBitmap()
                val grados = imagen.imageInfo.rotationDegrees
                val derecha: Bitmap = if (grados == 0) cruda else {
                    giro.reset(); giro.postRotate(grados.toFloat())
                    Bitmap.createBitmap(cruda, 0, 0, cruda.width, cruda.height, giro, true)
                }
                resolucion = "${derecha.width}×${derecha.height}"
                // El reloj de MediaPipe tiene que crecer siempre, aunque dos
                // cuadros lleguen en el mismo milisegundo.
                val ts = maxOf(SystemClock.uptimeMillis(), ultimoTs + 1)
                ultimoTs = ts
                landmarker?.detectAsync(BitmapImageBuilder(derecha).build(), ts)
            } catch (e: Exception) {
                alError("cuadro perdido: ${e.message}")
            } finally {
                imagen.close()
            }
        }

        proveedor.unbindAll()
        proveedor.bindToLifecycle(dueno, CameraSelector.DEFAULT_FRONT_CAMERA, previa, analisis)
    }

    private fun resultado(r: HandLandmarkerResult) {
        val ahora = SystemClock.uptimeMillis()
        msReconocer = ((msReconocer * 9 + (ahora - r.timestampMs()).toInt()) / 10)
        cuadros++
        if (ahora - tFps > 500) { fps = (cuadros * 1000 / (ahora - tFps)).toInt(); cuadros = 0; tFps = ahora }

        var izq: Mano? = null
        var der: Mano? = null
        for (i in r.landmarks().indices) {
            val cat = r.handedness().getOrNull(i)?.firstOrNull()?.categoryName() ?: "Right"
            var lado = if (cat == "Left") Lado.IZQUIERDA else Lado.DERECHA
            if (intercambiar) lado = lado.otro
            // La cámara frontal se ve espejada en pantalla: x → 1 − x, y la
            // mano queda donde uno la ve.
            val puntos = r.landmarks()[i].map { Punto(1.0 - it.x(), it.y().toDouble()) }
            val m = LeerMano.leer(puntos)
            if (lado == Lado.IZQUIERDA) { if (izq == null || m.palmo > izq.palmo) izq = m }
            else { if (der == null || m.palmo > der.palmo) der = m }
        }
        fun suavizar(m: Mano?, lado: Lado): Mano? {
            val (sx, sy) = suaves.getValue(lado)
            if (m == null) { sx.olvidar(); sy.olvidar(); return null }
            return m.copy(x = sx.filtrar(m.x, ahora), y = sy.filtrar(m.y, ahora))
        }
        alCuadro(ManosDelCuadro(suavizar(izq, Lado.IZQUIERDA), suavizar(der, Lado.DERECHA)), ahora)
    }

    fun detener() {
        landmarker?.close()
        landmarker = null
    }
}
