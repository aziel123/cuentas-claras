# Video de presentación · Cuentas Claras

Fuente del video animado de presentación (3:30, 1080p). Las escenas son HTML y un motor de animación propio las renderiza **cuadro por cuadro**. Aplica los principios de la [Motion Design Skill](https://github.com/lottiefiles/motion-design-skill): personalidad corporativa-premium, entradas escalonadas, foco celeste y capa ambiental.

## Qué hay aquí
| Archivo | Para qué sirve |
|---|---|
| `escenas/animado.html` | Las 23 escenas y el motor de animación (`render(id, ms)`). Ábrelo con `?e=portada&t=2000` para ver un instante. |
| `escenas/fuentes/inter-400.woff2` | Fuente Inter, con licencia SIL Open Font License. |
| `pantallas/*.png` | Capturas reales de la plataforma, tomadas en el perfil `dev`. |
| `cajas.json` | Coordenadas del recuadro celeste que señala lo importante en cada captura. |
| `capturar4.js` | Vuelve a tomar las capturas y las coordenadas. Necesita la app corriendo en `dev` en el puerto 8090. |
| `animar.js` | Renderiza el video: `node animar.js video` crea un clip por escena en `clips2/`. Con `SOLO=portada,cierre` renderiza solo esas escenas. |
| `unir2.py` | Une los clips en `Cuentas-Claras-presentacion-animada.mp4` y genera el guion y los subtítulos. |
| `eventos.js` y `efectos.py` | Extraen el instante de cada animación y arman la pista de efectos de sonido sincronizada. |
| `plan.json` | Texto de la narración de cada escena. |
| `Guion-narracion.md` y `Cuentas-Claras-presentacion.srt` | Guion con tiempos para la voz en off, y subtítulos. |
| `efectos-cues.json` | Los 66 efectos de sonido, con su segundo exacto y su volumen. |

## Cómo volver a generarlo
Necesitas Node 18 o superior, Python 3 con numpy, y ffmpeg.

```bash
cd docs/video
npm install playwright && npx playwright install chromium
node animar.js video                      # unos 7 minutos con 3 navegadores en paralelo
python unir2.py                           # crea el video sin sonido, el guion y los subtítulos
node eventos.js                           # tiempos de cada animación
python efectos.py <carpeta-de-efectos>    # crea efectos.f32 (la pista de efectos)
ffmpeg -f f32le -ar 48000 -ac 2 -i efectos.f32 -c:a pcm_s16le Efectos-sonido.wav
ffmpeg -i Cuentas-Claras-presentacion-animada.mp4 -i Efectos-sonido.wav -map 0:v -map 1:a -c:v copy -c:a aac -shortest Cuentas-Claras-presentacion-con-efectos.mp4
```

La carpeta de efectos sale de [HyperFrames](https://github.com/heygen-com/hyperframes), en `skills/media-use/audio/assets/sfx/`. Son 21 sonidos de Pixabay que se pueden usar en proyectos comerciales sin dar crédito.

Los videos generados (`*.mp4`), los cuadros y los clips no se suben al repositorio.
