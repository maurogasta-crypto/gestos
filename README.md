# gestos

Instrumento que se toca con las manos frente a la cámara del teléfono. La mano
derecha elige y hace sonar la nota; la izquierda acompaña con un acorde.

**En vivo:** https://maurogasta-crypto.github.io/gestos/

Corre **entero en el navegador**. No le habla a ningún servidor: no hay backend,
no hay base de datos, no hay credenciales. La imagen de la cámara no sale del
teléfono — `MediaPipe` reconoce las manos ahí adentro.

## Cómo se toca

| Gesto | Qué hace |
|---|---|
| Mano derecha, de izquierda a derecha | elige la nota; las franjas de color son el teclado |
| Pulgar e índice juntos | hace sonar. Separarlos calla. Cuanto más apretado, más vibrato |
| Subir la mano | abre el brillo |
| Acercarla a la cámara | sube el volumen |
| Mano izquierda abierta | suena un acorde del grado donde esté |
| Mano izquierda cerrada | calla el acorde |

Si las manos salen cambiadas, «Cambiar mano»: la cámara frontal invierte la
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

La documentación completa del instrumento está en `GESTOS.md` de Harmonía.
