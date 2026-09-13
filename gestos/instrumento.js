// ─────────────────────────────────────────────────────────────────────────────
// instrumento.js — Donde el gesto se vuelve nota, y la nota se vuelve orden
//
// Este archivo es el único que sabe al mismo tiempo de manos, de sonido y de
// luces. Los otros tres no se conocen entre sí, a propósito: se puede cambiar
// el motor de audio sin tocar la cámara, y probar la cámara sin que suene nada.
// ─────────────────────────────────────────────────────────────────────────────

import { rejilla, zonaDe, colorDe, etiquetaDe, nombreDe, acordeDeGrado, ESCALAS } from "./musica.js";

export const VERSION = "gestos-instrumento 2.0";

// Umbrales del pellizco, con histéresis. Dos números y no uno: con uno solo,
// la nota parpadea justo en el borde, que es exactamente donde la mano se
// queda cuando alguien está tocando despacio.
const PELLIZCO_CIERRA = 0.55;
const PELLIZCO_ABRE   = 0.78;


export const AJUSTES_POR_DEFECTO = {
  tonica: "A",
  octava: 3,
  escala: "pentaMenor",
  octavas: 2,
  latino: false,
  // IoT
  acompanamiento: true,
};

export function crearInstrumento({ video, canvas, motor, ajustes = {}, alEstado = null }) {
  const cfg = { ...AJUSTES_POR_DEFECTO, ...ajustes };
  const ctx2d = canvas.getContext("2d");

  let notas = rejilla(cfg);
  let sonando = false;
  let notaActual = null;
  let acordeActual = null;
  let ultimo = { manos: null, fps: 0 };

  const recalcular = () => { notas = rejilla(cfg); };

  const avisar = (extra = {}) => {
    if (!alEstado) return;
    alEstado({
      nota: notaActual,
      etiqueta: notaActual === null ? null : etiquetaDe(notaActual, cfg.latino),
      color: notaActual === null ? null : colorDe(notaActual),
      sonando,
      acorde: acordeActual,
      fps: ultimo.fps,
      ...extra,
    });
  };

  // ── Un cuadro de cámara ──────────────────────────────────────────────────
  function cuadro(manos, info) {
    ultimo = { manos, fps: info.fps };
    melodia(manos.derecha);
    armonia(manos.izquierda);
    dibujar(manos);
    avisar();
  }

  // ── Mano derecha: la melodía ─────────────────────────────────────────────
  function melodia(mano) {
    if (!mano) {
      if (sonando) { motor.notaOff(); sonando = false; notaActual = null; }
      return;
    }

    const { indice } = zonaDe(mano.x, notas.length);
    const midi = notas[indice];

    motor.brillo(1 - mano.y);
    motor.volumen(mano.cercania ?? 0.5);

    // Cuanto más apretado el pellizco, más vibrato: el instrumento responde a
    // la presión, no sólo a la posición. Es lo que hace que dos manos en el
    // mismo lugar no suenen igual.
    const presion = Math.max(0, Math.min(1, (PELLIZCO_CIERRA - mano.pellizco) / 0.45));
    motor.vibrato(presion * 0.8);

    const debeSonar = sonando ? mano.pellizco < PELLIZCO_ABRE : mano.pellizco < PELLIZCO_CIERRA;

    if (debeSonar) {
      const cambio = midi !== notaActual;
      if (!sonando || cambio) {
        motor.notaOn(midi, { ligado: sonando });
        notaActual = midi;
        sonando = true;
      }
    } else if (sonando) {
      motor.notaOff();
      sonando = false;
      notaActual = null;
    } else {
      // Sin sonar, la nota bajo la mano igual se muestra: se puede apuntar
      // antes de tocar, que es como se afina la puntería.
      notaActual = midi;
    }
  }

  // ── Mano izquierda: el acorde ────────────────────────────────────────────
  function armonia(mano) {
    if (!mano) {
      if (acordeActual) { motor.acordeOff(); acordeActual = null; }
      return;
    }

    if (cfg.acompanamiento && mano.abierta) {
      const ivs = (ESCALAS[cfg.escala] || ESCALAS.pentaMenor).ivs;
      const { indice } = zonaDe(mano.x, ivs.length);
      const acorde = acordeDeGrado(cfg.escala, cfg.tonica, cfg.octava, indice);
      const firma = acorde.join(",");
      if (firma !== (acordeActual || []).join(",")) {
        motor.acordeOn(acorde);
        acordeActual = acorde;
      }
    } else if (acordeActual) {
      motor.acordeOff();
      acordeActual = null;
    }
  }

  // ── Dibujo ───────────────────────────────────────────────────────────────
  function dibujar(manos) {
    const w = canvas.width, h = canvas.height;
    ctx2d.clearRect(0, 0, w, h);

    // Las zonas, pintadas con el color tonal de su nota. La pantalla es el
    // teclado: si no se ve dónde termina una nota, no se puede apuntar.
    const ancho = w / notas.length;
    for (let i = 0; i < notas.length; i++) {
      const midi = notas[i];
      const activa = notaActual === midi;
      ctx2d.fillStyle = colorDe(midi) + (activa ? (sonando ? "cc" : "55") : "22");
      ctx2d.fillRect(i * ancho, 0, ancho - 1, h);
      if (activa) {
        ctx2d.fillStyle = "#fff";
        ctx2d.font = `${Math.min(30, ancho * 0.7)}px system-ui, sans-serif`;
        ctx2d.textAlign = "center";
        ctx2d.fillText(etiquetaDe(midi, cfg.latino), i * ancho + ancho / 2, h - 14);
      }
    }

    for (const lado of ["derecha", "izquierda"]) {
      const m = manos[lado];
      if (!m || !m.trazo) continue;
      const esMelodia = lado === "derecha";
      ctx2d.strokeStyle = esMelodia ? "rgba(255,255,255,0.75)" : "rgba(120,200,255,0.7)";
      ctx2d.lineWidth = 2;
      for (const [a, b] of HUESOS) {
        ctx2d.beginPath();
        ctx2d.moveTo(m.trazo[a].x * w, m.trazo[a].y * h);
        ctx2d.lineTo(m.trazo[b].x * w, m.trazo[b].y * h);
        ctx2d.stroke();
      }
      // El punto de mando, del tamaño de la cercanía: se ve crecer al acercarse.
      const r = 6 + (m.cercania ?? 0) * 16;
      ctx2d.beginPath();
      ctx2d.arc(m.x * w, m.y * h, r, 0, Math.PI * 2);
      ctx2d.fillStyle = esMelodia
        ? (sonando ? "rgba(255,255,255,0.95)" : "rgba(255,255,255,0.35)")
        : (m.puno ? "rgba(255,190,60,0.95)" : "rgba(120,200,255,0.35)");
      ctx2d.fill();

    }
  }

  return {
    VERSION,
    cuadro,
    ajustar(parcial) {
      Object.assign(cfg, parcial);
      recalcular();
      if (sonando) { motor.notaOff(); sonando = false; notaActual = null; }
      if (acordeActual) { motor.acordeOff(); acordeActual = null; }
    },
    get ajustes() { return { ...cfg }; },
    get notas() { return [...notas]; },
    panico() { motor.silencio(); sonando = false; notaActual = null; acordeActual = null; },
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
