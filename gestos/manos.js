// ─────────────────────────────────────────────────────────────────────────────
// manos.js — Cámara + MediaPipe Hands, traducido a algo que la música entienda
//
// Lo que sale de acá no son 21 puntos por mano: son cinco números por mano que
// tienen sentido musical (dónde está, qué tan cerca, si pellizca, si cierra el
// puño). El resto de la aplicación no debería saber qué es un "landmark".
//
// MediaPipe llega por CDN como módulo ES. No hay npm ni build en esta página.
// ─────────────────────────────────────────────────────────────────────────────

export const VERSION = "gestos-manos 1.5";

const CDN    = "https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.14";
const BUNDLE = `${CDN}/vision_bundle.mjs`;
const WASM   = `${CDN}/wasm`;
const MODELO = "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task";

// MediaPipe se carga cuando se enciende la cámara, no cuando se abre la
// página. Con un `import` arriba de todo, un CDN que no contesta —un ascensor,
// un tren, una red que filtra— dejaba la página entera muerta y en blanco: ni
// los ajustes, ni la bitácora, ni un mensaje. Ahora lo que falla es el
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
export function crearSuavizador({ minCorte = 1.2, beta = 0.02, dCorte = 1.0 } = {}) {
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
// Qué mano es cuál, por DÓNDE está en la pantalla y no por lo que adivina el
// modelo (gestos-manos 1.5, 18-oct-2026). MediaPipe dice «Left» o «Right»
// mirando la forma de la mano, y con la cámara frontal, una mano de canto o un
// movimiento rápido se equivoca: la primera prueba de la APK salió con la nota
// en la mano derecha. La pantalla es un espejo, así que la mano que se ve a la
// izquierda ES la izquierda. Con dos manos manda el orden; con una, el lado.
// `lista` lleva la x ya en coordenadas de pantalla. Igual que `Repartir` de la APK.
export function repartirPorLado(lista, cambiadas = false) {
  let izquierda = null, derecha = null;
  if (lista.length >= 2) {
    // Si hay más de dos, las dos más grandes: son las que tocan.
    const dos = [...lista].sort((a, b) => b.palmo - a.palmo).slice(0, 2).sort((a, b) => a.x - b.x);
    [izquierda, derecha] = dos;
  } else if (lista.length === 1) {
    if (lista[0].x < 0.5) izquierda = lista[0]; else derecha = lista[0];
  }
  return cambiadas ? { izquierda: derecha, derecha: izquierda } : { izquierda, derecha };
}

export function crearRastreador({
  video,
  // «Manos cambiadas»: da vuelta el reparto por lado, para quien toque cruzado.
  // Hasta gestos-manos 1.4 era `intercambiarManos = true` y daba vuelta lo que
  // adivinaba el modelo.
  cambiadas = false,
  espejo = true,
} = {}) {
  let landmarker = null;
  let corriendo = false;
  let ultimoT = -1;
  let onCuadro = null;
  let fps = 0, tFps = 0, cuadros = 0;
  let delegado = null, msReconocer = 0;

  // beta 3 en la posición, desde gestos-manos 1.3 (18-oct-2026). Con 0,02
  // el filtro casi no se abría al mover la mano —la velocidad va en pantallas
  // por segundo, no en píxeles— y quedaba un pasabajos fijo de 1,2 Hz: 123 ms
  // de retraso en un glissando de una pantalla por segundo. Con 3 son 25 ms, y
  // el temblor en reposo es el mismo (1,6 cents). Lo mide el banco.
  const nuevo = () => ({ x: crearSuavizador({ beta: 3 }), y: crearSuavizador({ beta: 3 }), palmo: crearSuavizador({ minCorte: 0.8 }) });
  const suaves = { derecha: nuevo(), izquierda: nuevo() };

  async function iniciar() {
    const { FilesetResolver, HandLandmarker } = await cargarMediapipe();
    const vision = await FilesetResolver.forVisionTasks(WASM);
    try {
      delegado = "GPU";
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
      delegado = "CPU";
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
        // 480×360 y hasta 60 cuadros (gestos-manos 1.4): el modelo achica la
        // imagen a 224 igual, así que más píxeles son sólo tiempo de subirla;
        // y cada cuadro de más es retraso de menos, si la cámara lo da.
        width: { ideal: 480 },
        height: { ideal: 360 },
        frameRate: { ideal: 60 },
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
      // Cuánto tarda el modelo en cada cuadro, promediado: es la parte del
      // retraso que sale del teléfono y no de la cámara ni del audio.
      msReconocer = msReconocer * 0.9 + (performance.now() - t) * 0.1;

      const leidas = (res && res.landmarks ? res.landmarks : []).map(leerMano);
      // El reparto mira la x como se ve en pantalla (espejada).
      const manos = repartirPorLado(leidas.map(m => ({ m, x: espejo ? 1 - m.x : m.x, palmo: m.palmo })), cambiadas);
      for (const lado of ["derecha", "izquierda"]) if (manos[lado]) manos[lado] = manos[lado].m;

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
    pedirCuadro();
  }

  // Con requestVideoFrameCallback el bucle corre cuando LLEGA un cuadro de la
  // cámara, no cuando toca repintar la pantalla: con requestAnimationFrame
  // cada cuadro esperaba hasta 16 ms sin que nadie lo mirara.
  const pedirCuadro = () =>
    video.requestVideoFrameCallback ? video.requestVideoFrameCallback(bucle) : requestAnimationFrame(bucle);

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
      pedirCuadro();
    },
    detener() {
      corriendo = false;
      const s = video.srcObject;
      if (s) { s.getTracks().forEach(p => p.stop()); video.srcObject = null; }
    },
    set cambiadas(v) { cambiadas = !!v; },
    get cambiadas() { return cambiadas; },
    get fps() { return fps; },
    get delegado() { return delegado; },
    get msReconocer() { return Math.round(msReconocer); },
    get activo() { return corriendo; },
  };
}
