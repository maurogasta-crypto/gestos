# gestos

Instrumento que se toca con las manos frente a la cámara del teléfono, como un
theremin con colores: una mano da la altura —continua, con todo el cromatismo—
y la otra el volumen.

**En vivo:** https://maurogasta-crypto.github.io/gestos/

Corre **entero en el navegador**. No le habla a ningún servidor: no hay backend,
no hay base de datos, no hay credenciales. La imagen de la cámara no sale del
teléfono — `MediaPipe` reconoce las manos ahí adentro.

## Cómo se toca

| Gesto | Qué hace |
|---|---|
| Mano de la nota (la derecha, o la izquierda si se elige), de izquierda a derecha | la altura, **sin cortes**: el fondo es un degradé con el color de cada semitono, y las líneas marcadas son la escala |
| Subirla | abre el brillo |
| Pulgar e índice juntos | vibrato; cuanto más apretado, más |
| La otra mano, arriba o abajo | el volumen: arriba fuerte, abajo calla |
| La otra mano cerrada | calla en el acto |
| «Imán a la escala» | en 0, glissando puro; al máximo, por notas como antes |

Sin las dos manos a la vista no suena. Desde el 18-oct-2026; hasta entonces la
derecha caía en franjas y la izquierda sostenía un acorde (`GESTOS.md` de
Harmonía, § 2).

Si las manos salen cambiadas, «Manos cambiadas»: la cámara frontal invierte la
imagen y el modelo no siempre acierta de qué lado está cada una.

## Qué necesita

| | |
|---|---|
| **https** | `getUserMedia` no existe en http salvo `localhost`. Pages lo da solo |
| **Internet en la primera carga** | dos CDN: el bundle y el `wasm` de MediaPipe (`cdn.jsdelivr.net`) y el modelo de manos (`storage.googleapis.com`). Se bajan **al encender la cámara**, no al abrir la página — así, si no llegan, falla el encendido y lo dice, en vez de quedar en blanco |
| **Un toque** | el navegador no deja abrir la cámara ni sonar audio sin un gesto del usuario. Para eso está «Encender la cámara» |

## Los archivos

| | |
|---|---|
| `index.html` | la página. Estilos adentro, sin dependencias |
| `gestos/musica.js` | escalas, colores, MIDI → Hz. No sabe que existe una cámara |
| `gestos/audio.js` | Web Audio. No sabe que existe una mano |
| `gestos/manos.js` | cámara + MediaPipe. No sabe que existe el sonido |
| `gestos/instrumento.js` | el único que los conoce a todos: gesto → nota |
| `gestos/pagina.js` | el cableado de los controles |

Se puede cambiar el motor de audio sin tocar la cámara, y probar la cámara sin
que suene nada. Lo que sale de `manos.js` no son 21 puntos por mano: son cinco
números con sentido musical.

## De dónde viene, y la regla que importa

**La fuente es `toromboto/harmonia`**, en `public/gestos.html` y
`public/gestos/`. Esto es la **incubadora**: el mismo instrumento, en un
repositorio del que Mauro sí es dueño.

Los archivos de `gestos/` son **byte por byte idénticos** a los de allá, a
propósito: así sincronizar es copiar y no comparar. `index.html` es el mismo
archivo que `public/gestos.html`, con otro nombre porque acá es la portada.

> Por qué existe esta incubadora: en `toromboto/harmonia` Mauro es
> **colaborador, no dueño**, así que no puede ver con qué cuenta se despliega ni
> administrar el proyecto cuando algo falle. Es `harmonia:H1` en el panel.

**Ojo, desde el 18-oct-2026:** el theremin entró en la copia de Mauro de
Harmonía, `maurogasta-crypto/harmonia` (con su banco, 17 casos), y no en
`toromboto/harmonia`, al que esta sesión no llega. Hasta que entre allá con un
pull request desde esa copia, la de toromboto toca por notas.

La documentación completa del instrumento está en `GESTOS.md` de Harmonía.
