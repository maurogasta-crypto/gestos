// ─────────────────────────────────────────────────────────────────────────────
// musica.js — Teoría mínima para el instrumento de gestos
// Sin dependencias, sin build: se sirve tal cual desde public/.
//
// Deliberadamente NO importa de src/theory/: esta página no pasa por Vite y
// tiene que poder abrirse sola. Lo que sí comparte es la PALETA OFICIAL de
// colores tonales, copiada de src/App.jsx (fuente canónica). Si allá cambia,
// acá se actualiza a mano — está anotado en GESTOS.md.
// ─────────────────────────────────────────────────────────────────────────────

export const VERSION = "gestos-musica 1.0";

// ─── Colores tonales (copia de la paleta oficial de src/App.jsx) ─────────────
export const NC = {
  C:"#002e93", D:"#357a25", E:"#845523", F:"#b8823c",
  G:"#f1b302", A:"#cd2821", B:"#672e87",
  "C#":"#23879f","Db":"#23879f",
  "D#":"#91a51e","Eb":"#91a51e",
  "F#":"#ecd9a3","Gb":"#ecd9a3",
  "G#":"#dc7212","Ab":"#dc7212",
  "A#":"#da4571","Bb":"#da4571",
};

export const CROMATICA = ["C","C#","D","D#","E","F","F#","G","G#","A","A#","B"];

export const LATINO = {
  C:"DO","C#":"DO#",D:"RE","D#":"RE#",E:"MI",F:"FA",
  "F#":"FA#",G:"SOL","G#":"SOL#",A:"LA","A#":"LA#",B:"SI",
};

// ─── Escalas ─────────────────────────────────────────────────────────────────
// Cada una es la lista de semitonos desde la tónica.
export const ESCALAS = {
  pentaMenor:    { nombre:"Pentatónica menor", ivs:[0,3,5,7,10] },
  pentaMayor:    { nombre:"Pentatónica mayor", ivs:[0,2,4,7,9] },
  blues:         { nombre:"Blues",             ivs:[0,3,5,6,7,10] },
  mayor:         { nombre:"Mayor (jónico)",    ivs:[0,2,4,5,7,9,11] },
  menorNatural:  { nombre:"Menor natural",     ivs:[0,2,3,5,7,8,10] },
  menorArmonica: { nombre:"Menor armónica",    ivs:[0,2,3,5,7,8,11] },
  dorico:        { nombre:"Dórico",            ivs:[0,2,3,5,7,9,10] },
  frigio:        { nombre:"Frigio",            ivs:[0,1,3,5,7,8,10] },
  lidio:         { nombre:"Lidio",             ivs:[0,2,4,6,7,9,11] },
  mixolidio:     { nombre:"Mixolidio",         ivs:[0,2,4,5,7,9,10] },
  cromatica:     { nombre:"Cromática",         ivs:[0,1,2,3,4,5,6,7,8,9,10,11] },
};

// MIDI 60 = DO central. La tónica se da como nombre de nota + octava.
export const midiDe = (nota, octava) => CROMATICA.indexOf(nota) + (octava + 1) * 12;

export const nombreDe = (midi) => CROMATICA[((midi % 12) + 12) % 12];
export const octavaDe  = (midi) => Math.floor(midi / 12) - 1;
export const colorDe   = (midi) => NC[nombreDe(midi)] || "#888";
export const etiquetaDe = (midi, latino = false) => {
  const n = nombreDe(midi);
  return (latino ? (LATINO[n] || n) : n) + octavaDe(midi);
};

// La afinación de referencia es un parámetro, no una constante escondida:
// un bandoneón de tango rara vez está en 440 clavado.
export const frecuenciaDe = (midi, la = 440) => la * Math.pow(2, (midi - 69) / 12);

// ─── La rejilla ──────────────────────────────────────────────────────────────
// Devuelve la lista de notas MIDI que ocupan la pantalla de izquierda a
// derecha: la escala repetida durante `octavas` octavas desde la tónica.
export function rejilla({ tonica = "A", octava = 3, escala = "pentaMenor", octavas = 2 } = {}) {
  const ivs = (ESCALAS[escala] || ESCALAS.pentaMenor).ivs;
  const base = midiDe(tonica, octava);
  const notas = [];
  for (let o = 0; o < octavas; o++) {
    for (const iv of ivs) notas.push(base + iv + o * 12);
  }
  notas.push(base + octavas * 12); // cierra con la tónica de arriba
  return notas;
}

// x ∈ [0,1] → índice dentro de la rejilla. Devuelve también el borde de la
// zona, para saber cuánto falta para saltar a la nota siguiente (útil para
// dibujar, y para el "casi cambia" que evita el temblor).
export function zonaDe(x, cantidad) {
  const ancho = 1 / cantidad;
  const bruto = x / ancho;
  const i = Math.max(0, Math.min(cantidad - 1, Math.floor(bruto)));
  return { indice: i, dentro: bruto - i, ancho };
}

// Acorde de tres notas construido por grados de la escala (0, 2, 4) sobre el
// grado que toca. No es un análisis armónico: es un pad que acompaña.
export function acordeDeGrado(escala, tonica, octava, grado) {
  const ivs = (ESCALAS[escala] || ESCALAS.pentaMenor).ivs;
  const base = midiDe(tonica, octava);
  const n = ivs.length;
  return [0, 2, 4].map(salto => {
    const idx = grado + salto;
    return base + ivs[idx % n] + Math.floor(idx / n) * 12;
  });
}
