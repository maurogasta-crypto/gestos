// ─────────────────────────────────────────────────────────────────────────────
// audio.js — Motor de sonido del instrumento de gestos (Web Audio API)
//
// Decisión de diseño que importa: los osciladores se crean UNA vez y quedan
// sonando siempre, en silencio. Tocar una nota mueve una ganancia y una
// frecuencia; no crea nodos. Crear osciladores por nota es la causa más común
// de latencia irregular y de clics en el celular, justo lo que este
// instrumento no se puede permitir: la mano ya se movió.
//
// Timbre: parciales con dos lengüetas microdesafinadas por parcial, la misma
// idea del bandoneón de src/audio/engine.js, aligerada para tiempo real.
// ─────────────────────────────────────────────────────────────────────────────

import { frecuenciaDe } from "./musica.js";

export const VERSION = "gestos-audio 1.0";

// Parciales de la voz principal: armónico, volumen relativo, desafinación en
// cents entre las dos lengüetas.
const PARCIALES = [
  { h: 1, vol: 0.30, cents: 4 },
  { h: 2, vol: 0.16, cents: 3 },
  { h: 3, vol: 0.10, cents: 5 },
  { h: 4, vol: 0.05, cents: 3 },
];

const PISO = 0.0001; // las rampas exponenciales no pueden llegar a cero

export function crearMotor({ la = 440 } = {}) {
  let ctx = null;
  let listo = false;

  let maestro, compresor;
  let voz = null;      // melodía (monofónica, con ligado)
  let pad = null;      // acorde de acompañamiento
  let afinacion = la;

  // ── Construcción perezosa: el AudioContext no puede crearse antes de que
  //    la persona toque la pantalla, o los navegadores lo dejan suspendido.
  function construir() {
    if (listo) return;
    ctx = new (window.AudioContext || window.webkitAudioContext)();

    compresor = ctx.createDynamicsCompressor();
    compresor.threshold.value = -18;
    compresor.ratio.value = 6;
    compresor.attack.value = 0.004;
    compresor.release.value = 0.15;

    maestro = ctx.createGain();
    maestro.gain.value = 0.9;

    compresor.connect(maestro);
    maestro.connect(ctx.destination);

    voz = construirVoz();
    pad = construirPad();
    listo = true;
  }

  function construirVoz() {
    const salida = ctx.createGain();
    salida.gain.value = PISO;

    const filtro = ctx.createBiquadFilter();
    filtro.type = "lowpass";
    filtro.frequency.value = 1200;
    filtro.Q.value = 0.9;

    filtro.connect(salida);
    salida.connect(compresor);

    // Vibrato compartido por todos los osciladores de la voz.
    const lfo = ctx.createOscillator();
    const lfoGain = ctx.createGain();
    lfo.type = "sine";
    lfo.frequency.value = 5.2;
    lfoGain.gain.value = 0;      // se abre con la profundidad de la mano
    lfo.connect(lfoGain);
    lfo.start();

    const oscs = [];
    for (const p of PARCIALES) {
      for (const signo of [-1, 1]) {
        const osc = ctx.createOscillator();
        const g = ctx.createGain();
        osc.type = "sawtooth";
        osc.detune.value = signo * p.cents;
        g.gain.value = p.vol / 2;
        lfoGain.connect(osc.detune);
        osc.connect(g);
        g.connect(filtro);
        osc.start();
        oscs.push({ osc, armonico: p.h });
      }
    }

    return { salida, filtro, oscs, lfoGain, sonando: false, midi: null };
  }

  function construirPad() {
    const salida = ctx.createGain();
    salida.gain.value = PISO;

    const filtro = ctx.createBiquadFilter();
    filtro.type = "lowpass";
    filtro.frequency.value = 900;

    filtro.connect(salida);
    salida.connect(compresor);

    // Tres voces fijas: alcanza para una tríada y no se pelea con la melodía.
    const voces = [];
    for (let i = 0; i < 3; i++) {
      const osc = ctx.createOscillator();
      const g = ctx.createGain();
      osc.type = "triangle";
      g.gain.value = 0.20;
      osc.connect(g);
      g.connect(filtro);
      osc.start();
      voces.push(osc);
    }
    return { salida, filtro, voces, sonando: false };
  }

  // ── Despertar: se llama desde un gesto real de la persona (un toque). ─────
  async function despertar() {
    construir();
    if (ctx.state === "suspended") { try { await ctx.resume(); } catch (e) {} }
    return ctx.state;
  }

  const ahora = () => (ctx ? ctx.currentTime : 0);

  // ── Melodía ──────────────────────────────────────────────────────────────
  // ligado = true → cambia la altura sin cortar el sonido (portamento corto).
  function notaOn(midi, { ligado = true, ataque = 0.012 } = {}) {
    if (!listo) return;
    const t = ahora();
    afinarVoz(midi, voz.sonando && ligado ? 0.045 : 0);
    if (!voz.sonando) {
      voz.salida.gain.cancelScheduledValues(t);
      voz.salida.gain.setValueAtTime(Math.max(voz.salida.gain.value, PISO), t);
      voz.salida.gain.exponentialRampToValueAtTime(1, t + ataque);
      voz.sonando = true;
    }
    voz.midi = midi;
  }

  function notaOff({ caida = 0.09 } = {}) {
    if (!listo || !voz.sonando) return;
    const t = ahora();
    voz.salida.gain.cancelScheduledValues(t);
    voz.salida.gain.setValueAtTime(Math.max(voz.salida.gain.value, PISO), t);
    voz.salida.gain.exponentialRampToValueAtTime(PISO, t + caida);
    voz.sonando = false;
    voz.midi = null;
  }

  function afinarVoz(midi, glide) {
    const f = frecuenciaDe(midi, afinacion);
    const t = ahora();
    for (const { osc, armonico } of voz.oscs) {
      const destino = f * armonico;
      if (glide > 0) {
        osc.frequency.cancelScheduledValues(t);
        osc.frequency.setValueAtTime(osc.frequency.value, t);
        osc.frequency.exponentialRampToValueAtTime(Math.max(destino, 1), t + glide);
      } else {
        osc.frequency.setValueAtTime(destino, t);
      }
    }
  }

  // ── Los tres controles continuos ─────────────────────────────────────────
  // brillo: altura de la mano  →  apertura del filtro (200 Hz .. 6 kHz)
  function brillo(v) {
    if (!listo) return;
    const k = Math.max(0, Math.min(1, v));
    const hz = 200 * Math.pow(30, k);
    voz.filtro.frequency.setTargetAtTime(hz, ahora(), 0.03);
  }

  // volumen: cercanía de la mano  →  ganancia general
  function volumen(v) {
    if (!listo) return;
    const k = Math.max(0, Math.min(1, v));
    maestro.gain.setTargetAtTime(0.25 + 0.75 * k, ahora(), 0.05);
  }

  // vibrato: cuánto tiembla la nota, en cents de amplitud
  function vibrato(v) {
    if (!listo) return;
    const k = Math.max(0, Math.min(1, v));
    voz.lfoGain.gain.setTargetAtTime(k * 22, ahora(), 0.08);
  }

  // ── Acorde de acompañamiento ─────────────────────────────────────────────
  function acordeOn(midis) {
    if (!listo || !midis || !midis.length) return;
    const t = ahora();
    pad.voces.forEach((osc, i) => {
      const m = midis[Math.min(i, midis.length - 1)];
      const f = frecuenciaDe(m, afinacion);
      osc.frequency.cancelScheduledValues(t);
      osc.frequency.setValueAtTime(osc.frequency.value || f, t);
      osc.frequency.exponentialRampToValueAtTime(Math.max(f, 1), t + 0.08);
    });
    if (!pad.sonando) {
      pad.salida.gain.cancelScheduledValues(t);
      pad.salida.gain.setValueAtTime(Math.max(pad.salida.gain.value, PISO), t);
      pad.salida.gain.exponentialRampToValueAtTime(0.5, t + 0.25);
      pad.sonando = true;
    }
  }

  function acordeOff() {
    if (!listo || !pad.sonando) return;
    const t = ahora();
    pad.salida.gain.cancelScheduledValues(t);
    pad.salida.gain.setValueAtTime(Math.max(pad.salida.gain.value, PISO), t);
    pad.salida.gain.exponentialRampToValueAtTime(PISO, t + 0.45);
    pad.sonando = false;
  }

  // ── Un golpe corto, para confirmar acciones sin mirar la pantalla ────────
  function chasquido(midi = 84, dur = 0.09) {
    if (!listo) return;
    const t = ahora();
    const osc = ctx.createOscillator();
    const g = ctx.createGain();
    osc.type = "square";
    osc.frequency.value = frecuenciaDe(midi, afinacion);
    g.gain.setValueAtTime(0.0001, t);
    g.gain.exponentialRampToValueAtTime(0.28, t + 0.005);
    g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
    osc.connect(g); g.connect(compresor);
    osc.start(t); osc.stop(t + dur + 0.02);
  }

  function silencio() { notaOff({ caida: 0.05 }); acordeOff(); }

  return {
    VERSION,
    despertar, silencio,
    notaOn, notaOff,
    brillo, volumen, vibrato,
    acordeOn, acordeOff, chasquido,
    set afinacionLa(v) { afinacion = v; },
    get afinacionLa() { return afinacion; },
    get estado() { return listo ? ctx.state : "sin-crear"; },
    get notaActual() { return voz ? voz.midi : null; },
    get latencia() { return listo ? (ctx.baseLatency || 0) + (ctx.outputLatency || 0) : null; },
  };
}
