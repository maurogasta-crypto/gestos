# Gestos · la APK nativa

El mismo instrumento que la página, como aplicación de Android, para bajar la
latencia. Lo pidió Mauro el 18-oct-2026: «la latencia sigue siendo mucha».

**Bajarla:** https://github.com/maurogasta-crypto/gestos/releases/tag/ultimo →
`gestos.apk`, y se instala tocándola desde el teléfono. Cada push que toca
`android/` la vuelve a compilar (`.github/workflows/apk.yml`).

## Cómo se toca

Igual que la página (gestos-instrumento 3.2):

| Gesto | Qué hace |
|---|---|
| Mano izquierda, arriba o abajo | sube o baja la nota, sin cortes, con todo el cromatismo |
| Pulgar e índice de esa mano | vibrato |
| Mano derecha, arriba o abajo | el volumen: arriba fuerte, abajo calla |
| Puño derecho | calla en el acto |
| Acercar o alejar | nada, a propósito |

En «Ajustes» se elige qué mano da la nota, la tónica, la escala (las líneas
marcadas), las octavas y el imán. Si las manos salen al revés, «Manos
cambiadas».

## Dónde se gana el tiempo, y cómo se ve

| | La página | La APK |
|---|---|---|
| Cámara | lo que dé el navegador (30 cuadros) | el rango más alto que admite la cámara frontal (`Camara.mejorRango`) |
| Reconocimiento | MediaPipe en WebAssembly | MediaPipe nativo, en la GPU con la CPU de respaldo |
| Sonido | Web Audio: 40–100 ms en Chrome para Android | `AudioTrack` de baja latencia, con la tasa y la ráfaga nativas, búfer de dos ráfagas que crece sólo si el teléfono no da abasto |
| El deslizamiento del tono | una rampa por cuadro | muestra por muestra, en el hilo del audio |

La línea gris debajo de la nota dice cada parte: cuadros por segundo, cuánto
tarda el reconocimiento (y si es GPU o CPU), el búfer de audio en ms, la
resolución y el rango de cuadros de la cámara. Con esos números se sabe qué
sigue.

## Las piezas

| | |
|---|---|
| `Musica.kt` | la teoría: la MISMA que `gestos/musica.js` |
| `Instrumento.kt` | gesto → altura, volumen, vibrato: lo mismo que `gestos/instrumento.js` |
| `Manos.kt` | 21 puntos → una mano, y el filtro de una euro (beta 3) |
| `Sinte.kt` | la voz (aditiva, sin aliasing) y la pista de audio |
| `Camara.kt` | CameraX + MediaPipe |
| `Lienzo.kt` | el degradé, las guías, la barra del volumen, las manos |
| `MainActivity.kt` | el cableado y los ajustes |

**Si cambia la lógica en la página, cambia acá en la misma tanda**, y al revés:
`Musica.kt` e `Instrumento.kt` son traducciones de los `.js`.

## Verificación previa

```
ANDROID_HOME=/opt/sdk ./gradlew test assembleRelease lintRelease
```

Doce pruebas (`GestosTest.kt`): la teoría, el instrumento, el suavizado y que
la voz se deslice sin escalones. El modelo de manos (`hand_landmarker.task`,
el mismo de la página) lo baja Gradle la primera vez y no se versiona.

Sólo arm64: los Redmi del ecosistema lo son, y con los cuatro procesadores la
APK pesaba 56 MB en vez de 23.

## La firma

Sin secretos cargados, cada corrida firma con una clave de depuración nueva:
**para actualizar hay que desinstalar la anterior** (se pierden sólo los
ajustes). Para instalar encima, Mauro carga en Settings → Secrets → Actions de
este repositorio `FIRMA_JKS` (el keystore en base64), `FIRMA_STORE_PASS`,
`FIRMA_KEY_PASS` y `FIRMA_ALIAS` — el mismo paso a paso que la pizarra, con un
keystore propio. El keystore no entra nunca a este repositorio.
