// ─────────────────────────────────────────────────────────────────────────────
// manos.js — Cámara + MediaPipe Hands, traducido a algo que la música entienda
//
// Lo que sale de acá no son 21 puntos por mano: son cinco números por mano que
// tienen sentido musical (dónde está, qué tan cerca, si pellizca, si cierra el
// puño). El resto de la aplicación no debería saber qué es un "landmark".
//
// MediaPipe llega por CDN como módulo ES. No hay npm ni build en esta página.
// ─────────────────────────────────────────────────────────────────────────────

export const VERSION = "gestos-manos 1.1";

const CDN    = "https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.14";
const BUNDLE = `${CDN}/vision_bundle.mjs`;
const WASM   = `${CDN}/wasm`;
const MODELO = "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task";

// MediaPipe se carga cuando se enciende la cámara, no cuando se abre la
// página. Con un `import` arriba de todo, un CDN que no contesta —un ascensor,
// un tren, una red que filtra— dejaba la página entera muerta y en blanco: ni
// los ajustes, ni la clave de la sala, ni un mensaje. Ahora lo que falla es el
// encendido, y lo dice.
let mediapipe = null;
async function cargarMediapipe() {
  if (mediapipe) return mediapipe;
  try {
    mediapipe = await import(/* @vite-ignore */ BUNDLE);
    return mediapipe;
  } catch (e) {
    throw new Error("no se pudo cargar MediaPipe desde el CDN — ¿hay internet?");
  }
}

// Índices de los 21 puntos que usamos.
const MUNECA = 0, PULGAR = 4, INDICE = 8, MEDIO = 12, ANULAR = 16, MENIQUE = 20;
const NUDILLOS = { 8: 5, 12: 9, 16: 13, 20: 17 };
const NUDILLO_MEDIO = 9;

const dist = (a, b) => Math.hypot(a.x - b.x, a.y - b.y);

// ─── Suavizado ───────────────────────────────────────────────────────────────
// Filtro de una euro simplificado: cuanto más rápido se mueve la mano, menos
// se la suaviza. Un promedio fijo o tiembla en reposo o llega tarde al ataque;
// esto no hace ni una cosa ni la otra.
function crearSuavizador({ minCorte = 1.2, beta = 0.02, dCorte = 1.0 } = {}) {
  let x = null, dx = 0, tPrev = null;
  const alfa = (corte, dt) => {
    const tau = 1 / (2 * Math.PI * corte);
    return 1 / (1 + tau / dt);
  };
  return (valor, t) => {
    if (x === null) { x = valor; tPrev = t; return x; }
    const dt = Math.max(0.001, (t - tPrev) / 1000);
    tPrev = t;
    const dBruto = (valor - x) / dt;
    dx = dx + alfa(dCorte, dt) * (dBruto - dx);
    const corte = minCorte + beta * Math.abs(dx);
    x = x + alfa(corte, dt) * (valor - x);
    return x;
  };
}

// ─── Lectura de una mano ─────────────────────────────────────────────────────
function leerMano(puntos) {
  const muneca = puntos[MUNECA];
  // Tamaño aparente de la mano: la distancia muñeca–nudillo del medio. Es la
  // medida menos sensible a que los dedos se doblen, así que sirve de vara
  // tanto para la profundidad como para normalizar el pellizco.
  const palmo = Math.max(0.02, dist(muneca, puntos[NUDILLO_MEDIO]));

  // Pellizco: pulgar contra índice, medido en palmos (no en píxeles), para
  // que valga igual con la mano cerca o lejos.
  const pellizco = dist(puntos[PULGAR], puntos[INDICE]) / palmo;

  // Dedos doblados: la punta está más cerca de la muñeca que su nudillo.
  let doblados = 0;
  for (const punta of [INDICE, MEDIO, ANULAR, MENIQUE]) {
    if (dist(puntos[punta], muneca) < dist(puntos[NUDILLOS[punta]], muneca) * 1.15) doblados++;
  }

  // El centro de mando es el nudillo del medio, no la punta del índice: se
  // mueve mucho menos cuando los dedos se abren y se cierran, así que la nota
  // no salta al pellizcar.
  const centro = puntos[NUDILLO_MEDIO];

  return {
    x: centro.x, y: centro.y,
    palmo,
    pellizco,
    doblados,
    puno: doblados >= 3 && pellizco < 1.1,
    abierta: doblados === 0,
    puntos,
  };
}

// ─── Rastreador ──────────────────────────────────────────────────────────────
export function crearRastreador({
  video,
  // MediaPipe etiqueta las manos como si la imagen estuviera espejada. Con la
  // cámara frontal y el cuadro sin espejar, sale al revés — por eso es una
  // opción y no una constante: se comprueba mirando la pantalla, no leyendo
  // la documentación.
  intercambiarManos = true,
  espejo = true,
} = {}) {
  let landmarker = null;
  let corriendo = false;
  let ultimoT = -1;
  let onCuadro = null;
  let fps = 0, tFps = 0, cuadros = 0;

  const suaves = {
    derecha: { x: crearSuavizador(), y: crearSuavizador(), palmo: crearSuavizador({ minCorte: 0.8 }) },
    izquierda: { x: crearSuavizador(), y: crearSuavizador(), palmo: crearSuavizador({ minCorte: 0.8 }) },
  };

  async function iniciar() {
    const { FilesetResolver, HandLandmarker } = await cargarMediapipe();
    const vision = await FilesetResolver.forVisionTasks(WASM);
    try {
      landmarker = await HandLandmarker.createFromOptions(vision, {
        baseOptions: { modelAssetPath: MODELO, delegate: "GPU" },
        runningMode: "VIDEO",
        numHands: 2,
        minHandDetectionConfidence: 0.5,
        minHandPresenceConfidence: 0.5,
        minTrackingConfidence: 0.5,
      });
    } catch (e) {
      // Sin GPU disponible (pasa en algunos Android y en modo ahorro) cae a
      // CPU: anda más lento pero anda.
      landmarker = await HandLandmarker.createFromOptions(vision, {
        baseOptions: { modelAssetPath: MODELO, delegate: "CPU" },
        runningMode: "VIDEO",
        numHands: 2,
      });
    }
    return landmarker;
  }

  async function abrirCamara() {
    const stream = await navigator.mediaDevices.getUserMedia({
      video: {
        facingMode: "user",
        width: { ideal: 640 },
        height: { ideal: 480 },
        frameRate: { ideal: 30 },
      },
      audio: false,
    });
    video.srcObject = stream;
    await video.play();
    return stream;
  }

  function bucle() {
    if (!corriendo) return;
    const t = performance.now();

    if (video.readyState >= 2 && video.currentTime !== ultimoT) {
      ultimoT = video.currentTime;
      let res = null;
      try { res = landmarker.detectForVideo(video, t); } catch (e) { res = null; }

      const manos = { derecha: null, izquierda: null };
      if (res && res.landmarks) {
        for (let i = 0; i < res.landmarks.length; i++) {
          const cat = res.handedness?.[i]?.[0]?.categoryName || "Right";
          let lado = cat === "Left" ? "izquierda" : "derecha";
          if (intercambiarManos) lado = lado === "izquierda" ? "derecha" : "izquierda";
          const m = leerMano(res.landmarks[i]);
          // Si el mismo lado aparece dos veces, gana la mano más grande: es la
          // que está más cerca de la cámara, o sea la que está tocando.
          if (!manos[lado] || m.palmo > manos[lado].palmo) manos[lado] = m;
        }
      }

      for (const lado of ["derecha", "izquierda"]) {
        const m = manos[lado];
        if (!m) continue;
        const xCrudo = espejo ? 1 - m.x : m.x;
        m.x = suaves[lado].x(xCrudo, t);
        m.y = suaves[lado].y(m.y, t);
        m.palmo = suaves[lado].palmo(m.palmo, t);
        // El trazo va ya espejado, en las mismas coordenadas que la nota: quien
        // dibuja no tiene que acordarse de que la cámara frontal miente.
        m.trazo = m.puntos.map(p => ({ x: espejo ? 1 - p.x : p.x, y: p.y }));
        // Profundidad 0..1: un palmo de 0.10 es lejos, uno de 0.32 es encima.
        m.cercania = Math.max(0, Math.min(1, (m.palmo - 0.10) / 0.22));
      }

      cuadros++;
      if (t - tFps > 500) { fps = Math.round((cuadros * 1000) / (t - tFps)); cuadros = 0; tFps = t; }

      if (onCuadro) onCuadro(manos, { t, fps });
    }
    requestAnimationFrame(bucle);
  }

  return {
    VERSION,
    async arrancar(callback) {
      onCuadro = callback;
      // Primero el modelo, después la cámara: si el CDN no contesta, la luz
      // de la cámara no llega a encenderse para nada.
      await iniciar();
      await abrirCamara();
      corriendo = true;
      tFps = performance.now();
      requestAnimationFrame(bucle);
    },
    detener() {
      corriendo = false;
      const s = video.srcObject;
      if (s) { s.getTracks().forEach(p => p.stop()); video.srcObject = null; }
    },
    set intercambiar(v) { intercambiarManos = !!v; },
    get intercambiar() { return intercambiarManos; },
    get fps() { return fps; },
    get activo() { return corriendo; },
  };
}
