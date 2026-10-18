// ─────────────────────────────────────────────────────────────────────────────
// instrumento.js — Donde el gesto se vuelve nota, y la nota se vuelve orden
//
// Este archivo es el único que sabe al mismo tiempo de manos y de sonido. Los
// otros dos no se conocen entre sí, a propósito: se puede cambiar el motor de
// audio sin tocar la cámara, y probar la cámara sin que suene nada.
//
// Desde gestos-instrumento 3.0 (18-oct-2026) es un theremin con colores: una
// mano da la ALTURA, continua y con todo el cromatismo, y la otra el VOLUMEN.
// Lo pidió Mauro: «la mano izquierda o derecha hace la nota y la otra mano
// hace el volumen, y el pasaje en la pantalla continuo y no cortado por nota».
// Hasta la 2.1 la derecha caía en franjas de una escala, el pellizco hacía
// sonar y la izquierda abierta sostenía un acorde: el acorde se fue, porque
// la mano que lo tocaba ahora es la del volumen.
//
// Hasta el 2026-09-09 esta línea decía «y de luces»: el instrumento también
// hablaba con `iot.js`, el cliente del puente a Tuya. Ese puente se retiró
// (`harmonia:H3`) y acá no quedó nada de él.
// ─────────────────────────────────────────────────────────────────────────────

import {
  rejilla, midiDe, colorDe, etiquetaDe, alturaDe, posicionDe, imanar, afinacionDe, LATINO, nombreDe,
} from "./musica.js";

export const VERSION = "gestos-instrumento 3.0";

// El pellizco de la mano de la altura ya no hace sonar: da vibrato. Cuanto más
// apretado, más tiembla — el instrumento responde a la presión, no sólo a la
// posición, y dos manos en el mismo lugar no suenan igual.
const PELLIZCO_VIBRATO = 0.55;

// La mano del volumen: arriba suena fuerte, abajo calla. Abajo del piso hay
// una franja de silencio de verdad, para poder dejar la mano en reposo sin que
// suene un hilo; arriba del techo ya está al máximo, porque estirar el brazo
// hasta el borde del cuadro es donde MediaPipe la pierde.
const VOLUMEN_PISO  = 0.85;
const VOLUMEN_TECHO = 0.20;

// y ∈ [0,1] (0 arriba) → nivel ∈ [0,1]. Al cuadrado, porque el oído oye la
// ganancia en escala logarítmica: lineal, toda la mitad de arriba sonaría igual.
export function nivelDeAltura(y) {
  const k = Math.max(0, Math.min(1, (VOLUMEN_PISO - y) / (VOLUMEN_PISO - VOLUMEN_TECHO)));
  return k * k;
}

export const AJUSTES_POR_DEFECTO = {
  tonica: "A",
  octava: 3,
  escala: "pentaMenor",   // ya no corta el sonido: marca las guías y es a donde tira el imán
  octavas: 2,
  latino: false,
  manoNota: "derecha",    // la otra mano es la del volumen
  iman: 0,                // 0 = glissando puro, 1 = por notas
};

export function crearInstrumento({ video, canvas, motor, ajustes = {}, alEstado = null }) {
  const cfg = { ...AJUSTES_POR_DEFECTO, ...ajustes };
  const ctx2d = canvas.getContext("2d");

  let base = 0, semitonos = 24, guias = [];
  let altura = null;        // MIDI con decimales, o null si no hay mano
  let nivel = 0;
  let ultimo = { fps: 0 };

  const recalcular = () => {
    base = midiDe(cfg.tonica, cfg.octava);
    semitonos = cfg.octavas * 12;
    guias = rejilla(cfg);
  };
  recalcular();

  const ladoVolumen = () => (cfg.manoNota === "izquierda" ? "derecha" : "izquierda");
  const sonando = () => nivel > 0.02;

  const avisar = () => {
    if (!alEstado) return;
    const a = altura === null ? null : afinacionDe(altura);
    const cents = a && a.cents ? ` ${a.cents > 0 ? "+" : "−"}${Math.abs(a.cents)}¢` : "";
    alEstado({
      nota: a ? a.nota : null,
      altura,
      etiqueta: a ? etiquetaDe(a.nota, cfg.latino) + cents : null,
      color: a ? colorDe(a.nota) : null,
      sonando: sonando(),
      nivel,
      fps: ultimo.fps,
    });
  };

  // ── Un cuadro de cámara ──────────────────────────────────────────────────
  function cuadro(manos, info) {
    ultimo = { fps: info.fps };
    const mn = manos[cfg.manoNota], mv = manos[ladoVolumen()];
    tocar(mn, mv);
    dibujar(manos, mn, mv);
    avisar();
  }

  function tocar(mn, mv) {
    if (mn) {
      // La altura se mueve aunque no suene: así, cuando la otra mano abre el
      // volumen, entra ya en la nota a la que se apuntó, sin un glissando
      // desde la última que sonó.
      altura = imanar(alturaDe(mn.x, base, semitonos), guias, cfg.iman);
      motor.altura(altura);
      motor.brillo(1 - mn.y);
      const presion = Math.max(0, Math.min(1, (PELLIZCO_VIBRATO - mn.pellizco) / 0.45));
      motor.vibrato(presion * 0.8);
    } else {
      altura = null;
    }
    // Sin las dos manos no suena: si se pierde la de la altura, seguir sonando
    // en la última nota es una nota que nadie está tocando. El puño calla.
    nivel = mn && mv && !mv.puno ? nivelDeAltura(mv.y) : 0;
    motor.nivel(nivel);
  }

  // ── Dibujo ───────────────────────────────────────────────────────────────
  function dibujar(manos, mn, mv) {
    const w = canvas.width, h = canvas.height;
    ctx2d.clearRect(0, 0, w, h);

    // El fondo es UN degradé con el color tonal de cada semitono en su lugar:
    // entre dos notas, el color también está a mitad de camino. Se enciende
    // con el volumen.
    const x0 = posicionDe(base, base, semitonos) * w;
    const x1 = posicionDe(base + semitonos, base, semitonos) * w;
    const fondo = ctx2d.createLinearGradient(x0, 0, x1, 0);
    for (let i = 0; i <= semitonos; i++) fondo.addColorStop(i / semitonos, colorDe(base + i));
    ctx2d.globalAlpha = 0.16 + 0.5 * nivel;
    ctx2d.fillStyle = fondo;
    ctx2d.fillRect(0, 0, w, h);
    ctx2d.globalAlpha = 1;

    // Las guías: una línea por semitono, más marcada en las notas de la escala.
    // Son referencias para apuntar, no fronteras: la mano pasa por encima.
    ctx2d.textAlign = "center";
    ctx2d.font = "12px system-ui, sans-serif";
    for (let i = 0; i <= semitonos; i++) {
      const midi = base + i;
      const x = posicionDe(midi, base, semitonos) * w;
      const enEscala = guias.includes(midi);
      ctx2d.strokeStyle = enEscala ? "rgba(255,255,255,0.38)" : "rgba(255,255,255,0.10)";
      ctx2d.lineWidth = enEscala ? 2 : 1;
      ctx2d.beginPath(); ctx2d.moveTo(x, 0); ctx2d.lineTo(x, h); ctx2d.stroke();
      if (enEscala) {
        ctx2d.fillStyle = "rgba(255,255,255,0.75)";
        const n = nombreDe(midi);
        ctx2d.fillText(cfg.latino ? (LATINO[n] || n) : n, x, 16);
      }
    }

    // El cursor de la altura: donde está, exactamente, con sus cents.
    if (altura !== null) {
      const x = posicionDe(altura, base, semitonos) * w;
      const a = afinacionDe(altura);
      ctx2d.strokeStyle = sonando() ? "rgba(255,255,255,0.95)" : "rgba(255,255,255,0.4)";
      ctx2d.lineWidth = 3;
      ctx2d.beginPath(); ctx2d.moveTo(x, 24); ctx2d.lineTo(x, h); ctx2d.stroke();
      ctx2d.fillStyle = "#fff";
      ctx2d.font = "26px system-ui, sans-serif";
      ctx2d.textAlign = x < w * 0.15 ? "left" : x > w * 0.85 ? "right" : "center";
      ctx2d.fillText(etiquetaDe(a.nota, cfg.latino), x, h - 14);
    }

    // El volumen, como una barra en el borde de la mano que lo da.
    if (mv) {
      const izq = mv.x < 0.5;
      const bx = izq ? 4 : w - 14;
      ctx2d.fillStyle = "rgba(255,255,255,0.15)";
      ctx2d.fillRect(bx, 24, 10, h - 48);
      ctx2d.fillStyle = mv.puno ? "rgba(255,190,60,0.95)" : "rgba(120,200,255,0.9)";
      const alto = (h - 48) * Math.sqrt(nivel);
      ctx2d.fillRect(bx, h - 24 - alto, 10, alto);
    }

    for (const lado of ["derecha", "izquierda"]) {
      const m = manos[lado];
      if (!m || !m.trazo) continue;
      const esAltura = m === mn;
      ctx2d.strokeStyle = esAltura ? "rgba(255,255,255,0.75)" : "rgba(120,200,255,0.7)";
      ctx2d.lineWidth = 2;
      for (const [a, b] of HUESOS) {
        ctx2d.beginPath();
        ctx2d.moveTo(m.trazo[a].x * w, m.trazo[a].y * h);
        ctx2d.lineTo(m.trazo[b].x * w, m.trazo[b].y * h);
        ctx2d.stroke();
      }
      const r = 6 + (m.cercania ?? 0) * 16;
      ctx2d.beginPath();
      ctx2d.arc(m.x * w, m.y * h, r, 0, Math.PI * 2);
      ctx2d.fillStyle = esAltura
        ? (sonando() ? "rgba(255,255,255,0.95)" : "rgba(255,255,255,0.35)")
        : (m.puno ? "rgba(255,190,60,0.95)" : "rgba(120,200,255,0.6)");
      ctx2d.fill();
    }
  }

  return {
    VERSION,
    cuadro,
    ajustar(parcial) {
      Object.assign(cfg, parcial);
      recalcular();
      nivel = 0; motor.nivel(0);
    },
    get ajustes() { return { ...cfg }; },
    get guias() { return [...guias]; },
    panico() { motor.silencio(); nivel = 0; },
  };
}

// Conexiones entre los 21 puntos de la mano, para dibujar el esqueleto.
const HUESOS = [
  [0,1],[1,2],[2,3],[3,4],
  [0,5],[5,6],[6,7],[7,8],
  [5,9],[9,10],[10,11],[11,12],
  [9,13],[13,14],[14,15],[15,16],
  [13,17],[17,18],[18,19],[19,20],
  [0,17],
];
