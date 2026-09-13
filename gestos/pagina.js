// ─────────────────────────────────────────────────────────────────────────────
// pagina.js — El cableado de gestos.html
//
// Sólo une piezas: lee los controles, se los pasa al instrumento y muestra lo
// que el instrumento devuelve. Ninguna decisión musical vive acá.
// ─────────────────────────────────────────────────────────────────────────────

import { crearMotor } from "./audio.js";
import { crearRastreador } from "./manos.js";
import { crearInstrumento } from "./instrumento.js";
import { CROMATICA, ESCALAS, LATINO } from "./musica.js";

export const VERSION = "gestos-pagina 2.0";

const $ = (id) => document.getElementById(id);
const AJUSTES_GUARDADOS = "harmonia_gestos_ajustes_v1";

const video = $("video");
const lienzo = $("lienzo");
const tapa = $("tapa");

// ─── Bitácora ────────────────────────────────────────────────────────────────
const bitacora = $("bitacora");
function anotar(nivel, texto) {
  const linea = document.createElement("div");
  linea.className = nivel;
  const hora = new Date().toTimeString().slice(0, 8);
  linea.textContent = `${hora}  ${texto}`;
  bitacora.prepend(linea);
  while (bitacora.childElementCount > 40) bitacora.lastElementChild.remove();
}

// ─── Piezas ──────────────────────────────────────────────────────────────────
const motor = crearMotor();
let instrumento = null;
let rastreador = null;

// ─── Ajustes: se guardan en este teléfono, no en el repositorio ─────────────
function cargarAjustes() {
  try { return JSON.parse(localStorage.getItem(AJUSTES_GUARDADOS) || "{}"); } catch (e) { return {}; }
}
function guardarAjustes(a) {
  try { localStorage.setItem(AJUSTES_GUARDADOS, JSON.stringify(a)); } catch (e) {}
}

// ─── Poblar los desplegables ────────────────────────────────────────────────
for (const n of CROMATICA) {
  $("tonica").add(new Option(`${n} · ${LATINO[n]}`, n));
}
for (const [clave, e] of Object.entries(ESCALAS)) {
  $("escala").add(new Option(e.nombre, clave));
}
// ─── Estado inicial de los controles ────────────────────────────────────────
const guardados = cargarAjustes();
$("tonica").value  = guardados.tonica  ?? "A";
$("escala").value  = guardados.escala  ?? "pentaMenor";
$("octava").value  = guardados.octava  ?? 3;
$("octavas").value = guardados.octavas ?? 2;
$("latino").checked = guardados.latino ?? false;
$("acomp").checked  = guardados.acompanamiento ?? true;
$("afinacion").value = guardados.afinacionLa ?? 440;
$("afinacionVal").textContent = $("afinacion").value;

function ajustesActuales() {
  return {
    tonica: $("tonica").value,
    escala: $("escala").value,
    octava: Number($("octava").value) || 3,
    octavas: Math.max(1, Math.min(4, Number($("octavas").value) || 2)),
    latino: $("latino").checked,
    acompanamiento: $("acomp").checked,
    afinacionLa: Number($("afinacion").value) || 440,
  };
}

function aplicar() {
  const a = ajustesActuales();
  guardarAjustes(a);
  motor.afinacionLa = a.afinacionLa;
  $("afinacionVal").textContent = a.afinacionLa;
  if (!instrumento) return;
  instrumento.ajustar({
    tonica: a.tonica, escala: a.escala, octava: a.octava, octavas: a.octavas,
    latino: a.latino, acompanamiento: a.acompanamiento,
  });
}

for (const id of ["tonica","escala","octava","octavas","latino","acomp","afinacion"]) {
  $(id).addEventListener("change", aplicar);
}
$("afinacion").addEventListener("input", () => { $("afinacionVal").textContent = $("afinacion").value; });

// ─── Lectura en vivo ─────────────────────────────────────────────────────────
const lecturaNota = $("lecturaNota");
const lecturaDatos = $("lecturaDatos");
let ultimoPintado = 0;

function mostrar(e) {
  // La cámara da 30 cuadros por segundo; el texto no necesita tantos, y
  // repintarlo 30 veces por segundo se nota en la batería.
  const t = performance.now();
  if (t - ultimoPintado < 90) return;
  ultimoPintado = t;

  lecturaNota.textContent = e.etiqueta || "—";
  lecturaNota.style.color = e.sonando ? (e.color || "#fff") : "#555";
  const partes = [
    e.sonando ? "sonando" : "en silencio",
    `${e.fps || 0} cuadros/s`,
  ];
  if (e.acorde) partes.push("acorde sostenido");
  lecturaDatos.textContent = partes.join(" · ");
}

// ─── Encendido ───────────────────────────────────────────────────────────────
$("empezar").addEventListener("click", async () => {
  const boton = $("empezar");
  boton.disabled = true;
  boton.textContent = "Preparando…";
  try {
    const estado = await motor.despertar();
    if (estado !== "running") anotar("aviso", "el audio quedó suspendido; tocá otra vez");

    rastreador = crearRastreador({ video, intercambiarManos: true, espejo: true });
    instrumento = crearInstrumento({ video, canvas: lienzo, motor, alEstado: mostrar });
    aplicar();

    await rastreador.arrancar((manos, info) => instrumento.cuadro(manos, info));

    // El lienzo se ajusta al tamaño real del video, no al de la pantalla: si
    // no, las franjas quedan corridas respecto de donde está la mano.
    if (video.videoWidth) { lienzo.width = video.videoWidth; lienzo.height = video.videoHeight; }

    tapa.style.display = "none";
    anotar("ok", "cámara y audio listos");
  } catch (e) {
    boton.disabled = false;
    boton.textContent = "Reintentar";
    const problemaDeCamara = e && ["NotAllowedError","NotFoundError","NotReadableError","SecurityError"].includes(e.name);
    const detalle = e && e.name === "NotAllowedError"
      ? "no diste permiso de cámara"
      : (e && e.message) || "no se pudo iniciar";
    // La aclaración del https sólo cuando el problema es la cámara: pegarla
    // siempre manda a buscar por el lado equivocado.
    const aclaracion = problemaDeCamara ? " La cámara sólo funciona en https (o en localhost)." : "";
    const punto = /[.?!]$/.test(detalle) ? "" : ".";
    $("mensajeTapa").textContent = `No arrancó: ${detalle}${punto}${aclaracion}`;
    anotar("err", detalle);
  }
});

$("panico").addEventListener("click", () => { if (instrumento) instrumento.panico(); });
$("intercambiar").addEventListener("click", () => {
  if (!rastreador) return;
  rastreador.intercambiar = !rastreador.intercambiar;
  anotar("aviso", `manos ${rastreador.intercambiar ? "intercambiadas" : "sin intercambiar"}`);
});

// Al irse de la página o apagar la pantalla, callar. Un instrumento que sigue
// sonando en una pestaña de fondo es lo primero que hace que se cierre.
document.addEventListener("visibilitychange", () => {
  if (document.hidden && instrumento) instrumento.panico();
});
