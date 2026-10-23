# Rediseño de la interfaz · evaluación heurística y propuesta

Fecha: 15/10/2026 · Alcance: inicio de cada rol, caja (cobrar y cierre), aprobaciones, portal de la familia en celular y menú.
Método: evaluación heurística de Nielsen (skill `evaluacion-ux`), severidad de 0 a 4. Maqueta: `docs/ux/propuesta-rediseno/`.

## Causa raíz
Casi todos los problemas de distribución vienen de dos decisiones del armazón actual:
- `--ancho-max: 1180px` en `static/css/tokens.css` encierra **menú y contenido** en 1180 px. En un monitor de 1920 px el contenido útil queda en unos 900 px y sobran unos 700 px de bandas vacías.
- El menú se arma con la lista plana de `ModuloApp` (más de 24 entradas) sin agrupar ni ordenar por tarea.

## Hallazgos priorizados

| # | Pantalla | Heurística | Problema | Sev. | Propuesta |
|---|---|---|---|---|---|
| 1 | Inicio Promotoría y Dirección | 1 Visibilidad del estado | «Para revisar» (faltante de S/ 50.00 en la caja de Pedro Huanca) queda debajo de las cifras. Lo que puede ser fraude se ve al final. | 4 | Bloque «Para revisar» primero, con borde rojo, badge con conteo y **un botón por ítem** («Revisar el cierre»). Las cifras van después. |
| 2 | Armazón (todas) | 8 Estético y minimalista | Columna de unos 900 px en una pantalla de 1920 px. Las tarjetas se aprietan y los textos se parten. | 3 | `--ancho-max: 1440px`, rejilla `264px + 1fr`, tarjetas con `auto-fit, minmax(220px, 1fr)`. Entre 1280 y 1440 px el contenido se estira y luego se centra. |
| 3 | Menú | 6 Reconocer antes que recordar | Lista plana de más de 24 opciones. Cuesta encontrar «Aprobaciones» y no se ve qué está pendiente. | 3 | Seis grupos plegables: *Dinero de hoy, Familias y alumnos, Cobranza, Control y auditoría, Configuración, Mi cuenta*. Contadores (rojo = acción, ámbar = aviso) en Aprobaciones, Pagos en línea y Avisos. Solo se muestran los grupos con algún módulo del rol. |
| 4 | Tarjetas de cifras (inicio, panel) | 8 / 1 | El número tiene el mismo peso que la etiqueta y las notas («vs. ayer») se pierden. | 3 | Etiqueta de 14 px en gris, **cifra de 40 px en negrita** con `S/` reducido y nota de 14 px coloreada con flecha y texto («↓ S/ 320.00 menos que ayer»). Borde superior rojo en las cifras de riesgo (deuda vencida). |
| 5 | Caja · cobrar | 7 Eficiencia | Búsqueda, cuotas y cobro van uno debajo de otro. La cajera hace scroll en cada cobro. | 3 | Búsqueda arriba y luego dos columnas: cuotas con casillas a la izquierda y «Resumen del cobro» fijo a la derecha, con el medio de pago en botones grandes y el botón «Registrar pago de S/ 900.00» con el monto dentro. |
| 6 | Caja · cierre | 5 Prevención de errores | El cierre ciego funciona, pero «Cerrar mi caja» se ve igual que cualquier enlace. | 2 | Botón secundario siempre visible en el encabezado de Caja, con su propio paso de confirmación (consecuencia y conteo por denominación). |
| 7 | Aprobaciones | 6 / 1 | No se ve quién pide, cuánto y por qué sin entrar a cada ítem. | 3 | Un ítem por fila: badge del tipo + monto en negrita + «Solicita X · Motivo: …» + botón. Es el mismo componente que «Para revisar» del inicio. |
| 8 | Portal de la familia (celular) | 1 / 2 | El total pendiente compite con la información del colegio y el botón de pago no es lo primero. | 3 | Tarjeta «Lo que debes hoy» arriba: cifra grande, badge Vencido y «Pagar ahora con Yape o tarjeta» a todo el ancho (52 px). Cuotas como tarjetas y barra inferior con 4 secciones. |
| 9 | Inicio Caja, Administración y Docente | 4 Consistencia | Cada inicio usa una estructura diferente. | 2 | Misma plantilla para todos: encabezado, Para revisar, cifras y detalle. Cada rol solo cambia los datos. |
| 10 | Menú en celular | 7 | El menú lateral se apila completo antes del contenido. | 2 | En celular el menú queda plegado en un botón «Menú». El portal de la familia usa barra inferior. |
| 11 | Tablas en celular | 8 | Las columnas se cortan. | 2 | Patrón `tabla-tarjetas`: en ≤ 640 px cada fila se vuelve una tarjeta de dos columnas con el monto a la derecha. |

## Accesibilidad (revisión de la maqueta)
- Contraste: textos sobre blanco y fondos suaves con los tokens existentes (≥ 4.5:1). El celeste `#38aee6` solo se usa en bordes decorativos.
- Los estados siempre llevan texto (Faltante, Vencido, Pendiente, Pagado) y las tendencias llevan flecha y texto, no solo color.
- Objetivos de 44 px como mínimo (menú, botones, grupos plegables) y 52 px en las acciones principales.
- `:focus-visible` de 3 px en azul, enlace «Saltar al contenido» y `aria-current="page"` en el menú. Los contadores llevan `aria-label` («3 pendientes»).
- Grupos con `<details>/<summary>` nativos: funcionan con teclado y lector de pantalla, y no necesitan JS.

## Notas para trasladar a Thymeleaf
- Cero estilos y scripts en línea, compatible con la CSP. El plegado del menú en celular usa `::details-content` (Chrome/Edge 131+). En las plantillas conviene un botón con un `app.js` externo como respaldo para Safari antiguo.
- La maqueta carga Inter desde Google Fonts. En la app hay que **alojar Inter localmente** (`static/fonts/`), porque la CSP no permite orígenes externos.
- El agrupamiento del menú requiere un campo `grupo` en `ModuloApp` (enum `GrupoMenu` con orden) y un fragmento `menu-agrupado` que solo pinte los grupos con módulos visibles para el rol. Los contadores salen de los servicios que ya alimentan las alertas de Promotoría.
- Fragmentos nuevos o cambiados: `layout` (rejilla y ancho), `menu-agrupado`, `tarjeta-kpi` (cifra grande), `lista-revisar` (reutilizado en Inicio y Aprobaciones), `tabla-tarjetas`.
