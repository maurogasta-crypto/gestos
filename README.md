# gestos

Instrumento que se toca con las manos frente a la cámara del teléfono, como un
theremin con colores: una mano sube y baja la nota —continua, con todo el
cromatismo— y la otra el volumen. El sello de la versión se ve al pie.

**En vivo:** https://maurogasta-crypto.github.io/gestos/

Corre **entero en el navegador**. No le habla a ningún servidor: no hay backend,
no hay base de datos, no hay credenciales. La imagen de la cámara no sale del
teléfono — `MediaPipe` reconoce las manos ahí adentro.

## Cómo se toca

| Gesto | Qué hace |
|---|---|
| Mano de la nota (la izquierda, o la derecha si se elige), arriba o abajo | sube o baja la nota, **sin cortes**: el fondo es un degradé con el color de cada semitono, y las líneas marcadas son la escala |
| Pulgar e índice juntos | vibrato; cuanto más apretado, más |
| La otra mano, arriba o abajo | el volumen: arriba fuerte, abajo calla |
| La otra mano cerrada | calla en el acto |
| «Imán a la escala» | en 0, glissando puro; al máximo, por notas como antes |

Sin las dos manos a la vista no suena. Desde el 18-oct-2026; hasta entonces la
derecha caía en franjas y la izquierda sostenía un acorde (`GESTOS.md` de
Harmonía, § 2).

Qué mano es cuál lo decide el lado de la pantalla donde se ve; «Manos cambiadas» lo da vuelta: la cámara frontal invierte la
imagen y el modelo no siempre acierta de qué lado está cada una.

## La APK nativa

Desde el 18-oct-2026 el mismo instrumento existe como aplicación de Android,
para bajar la latencia: https://github.com/maurogasta-crypto/gestos/releases/tag/ultimo
(`gestos.apk`). El código está en `android/` y se explica en `android/README.md`.

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
Harmonía, `maurogasta-crypto/harmonia` (con su banco, 22 casos), y no en
`toromboto/harmonia`, al que esta sesión no llega. Hasta que entre allá con un
pull request desde esa copia, la de toromboto toca por notas.

La documentación completa del instrumento está en `GESTOS.md` de Harmonía.
