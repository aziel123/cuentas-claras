# Sprint 11 · Tanda 1b · Guía de puesta en marcha (brief; solo interfaz)

> Léase primero. Diseño: `docs/arquitectura/sprint-11-ciclo-colegio.md`, **sección 3.14** (y 3.7 para los 15 pasos, 3.12 para
> la facultad del titular, 13 para las decisiones 206 y 207). Va **después de la tanda 1** (que trae los 15 pasos, el año escolar
> del colegio y la facultad) y antes de la tanda 2. Misma rama y PR en borrador: **la tanda está terminada cuando el CI del PR
> está en verde.** Skills: `sistema-diseno` (marca, componentes y accesibilidad) y `evaluacion-ux` (microcopy).

## 1. Decisiones (pendientes del usuario: se implementan los valores por defecto)
1. **Sin cambios en la base** (tampoco en F-D1 ni F-D2): sin migración, triggers, funciones ni huellas. Conteos sin cambio: **117 triggers, 19 funciones y
   136 huellas** (los de la tanda 1). Si algo pareciera pedir una columna, se calcula en Java desde lo que ya existe.
2. **Avance (206):** «Tu colegio está en preparación: faltan N de 15 pasos», con N = obligatorios pendientes; «(y 2 opcionales)»
   si faltan; la barra sobre los 13 obligatorios.
3. **Dónde (207):** banner en el inicio de cada rol del personal (no en el portal de las familias), estados vacíos en 7
   pantallas, mensaje con enlace al intentar algo bloqueado y la Puesta en marcha con responsable e «Ir».
4. **Nunca un botón que termina en 403:** el botón se muestra solo si el rol puede entrar a la ruta según `ModuloApp`; si no,
   el texto dice a quién le toca.
5. **Sin datos de familias ni montos** en la guía; el docente la ve sin conteos (decisión 141).

## 2. Alcance
- **`puestaenmarcha.model.PasoPuestaEnMarcha`** (enum nuevo): los 15 pasos de la tabla de 3.14, cada uno con `numero`,
  `titulo`, `accion` (verbo; con `{año}` cuando aplica), `roles` (quién lo hace), `ruta`, `dependeDe` (pasos), `obligatorio` y
  `quien` (`COLEGIO`, `PLATAFORMA` u `OPERADOR`). Una prueba exige que las claves coincidan con `EstadoPuestaEnMarcha.PASOS`.
- **`PuntoLista`** gana `paso` (el enum) y `bloqueadoPor` (el primer paso pendiente del que depende, o nulo), calculados en
  `EstadoPuestaEnMarcha`. `paraOperador()` los conserva sin enlaces.
- **`puestaenmarcha.service.GuiaPuestaEnMarcha`** (nuevo): `guiaPara(colegioId, roles)` → `GuiaVista(pendientes, total,
  obligatoriosHechos, siguiente, tuyo, esperandoPlataforma, conConteos)`; las reglas de `siguiente`, `tuyo` y
  `esperandoPlataforma` están en 3.14. Lee la lista una vez por pedido (sin caché entre pedidos: un paso hecho se ve al volver).
- **`seguridad.config.ModuloApp.puedeEntrar(Set<Rol>, String ruta)`**: público, el mismo cálculo que ya usa el filtro (si
  existe, se reutiliza).
- **Banner** (`fragments/guia.html :: bannerPreparacion(guia)`) en `inicio/promotor`, `director`, `administracion`, `caja` y
  `docente`; nunca en `inicio/apoderado`. `InicioController` agrega `guia` al modelo solo en PREPARACION.
- **Estados vacíos** (`componentes.html :: estadoVacioPaso(titulo, texto, punto)`) en: año escolar, secciones del año,
  pensiones, alumnos, cuenta del banco (conectores), usuarios (Dirección o Caja) y saldo inicial.
- **Bloqueos:** `ColegioEnPreparacionException` (en `colegio.service`, con `AccionBloqueada`: `COBRAR`, `PAGO_EN_LINEA`,
  `RECAUDACION`, `COMUNICADO`, `RECORDATORIO`, `RENOVACION`) desde `ColegioService.exigirEnProduccion` y al traducir los
  1644 de preparación de `trg_pago_registro`, `trg_orden_pago_nace` y `trg_mensaje_nace`; `PreparacionAdvice` (409, la misma
  pantalla con la alerta y el enlace); botones deshabilitados con su motivo en caja («Cobrar»), comunicados («Enviar»),
  recaudación («Confirmar lote») y renovación.
- **Puesta en marcha** (`puesta-en-marcha/lista.html`): número, título, estado con texto e ícono, responsable, conteo, «Ir» o
  «Lo hace …», «Siguiente» marcado con texto, «Pasar a producción» deshabilitado con el motivo.
- **Dos correcciones de Pensiones (sección 2b):** la tarjeta de Secundaria desalineada (F-D1) y los vencimientos propuestos
  según el inicio de clases (F-D2).
- **Fuera de alcance:** cambios de lógica de los pasos (son de la tanda 1); avisos por WhatsApp o correo de los pasos
  pendientes (después, si el usuario lo pide); la guía para el operador (ya ve la lista como sí o no).

## 2b. Correcciones de Pensiones que van en esta tanda
**F-D1. Tarjeta de Secundaria desalineada en Pensiones.** «Sin plan aprobado» baja a una segunda línea junto a «Secundaria» y su
botón queda más abajo que los de Inicial y Primaria.
- **Corrección (CSS con los tokens de `sistema-diseno`, sin colores nuevos):** `.lista-tarjetas` en grilla con filas de la
  misma altura (`align-items: stretch`); cada `.tarjeta` como columna flexible (`display: flex; flex-direction: column`); la
  cabecera en dos filas fijas: **el título en la primera y la etiqueta en la segunda**, siempre, para cualquier largo de
  palabra (no un `flex-wrap` que depende del ancho); la etiqueta con `white-space: nowrap`; el bloque de acciones al final con
  `margin-top: auto` y botones con la misma altura mínima (44 px) y el mismo ancho en la tarjeta. Así los tres botones quedan a
  la misma altura en escritorio, y en celular (una columna) siguen en el mismo lugar de cada tarjeta.
- **Pruebas:** `PensionesTarjetasTest` (renderiza el resumen con los tres niveles: cada tarjeta tiene la misma estructura,
  `.tarjeta-titulo`, `.tarjeta-etiqueta` y `.tarjeta-acciones`, en ese orden, sin importar el estado del plan) y revisión visual
  con `disenador-ui` en 360, 768 y 1280 px con «Secundaria» y la etiqueta más larga («Sin plan aprobado»).

**F-D2. Vencimientos de las pensiones propuestos según el inicio de clases.** Hoy `ConfiguracionPlan.porDefecto(int anio, …)`
propone siempre el último día de **marzo a diciembre** (`Calendario.vencimientosPorDefecto(anio, 3, 10)`), sin mirar el año
escolar. Un colegio que empieza en abril recibe una pensión de marzo que `validarVencimientos` rechaza («vence antes del inicio
de clases»), y uno que termina en noviembre recibe una de diciembre.
- **Regla nueva:** `ConfiguracionPlan.porDefecto(AnioEscolar anio, BigDecimal matricula, BigDecimal pension)` propone el último
  día de **cada mes desde el mes del inicio de clases hasta el mes del fin de clases**, uno por mes, **como máximo 12**
  (`MAX_PENSIONES`). Si las clases empiezan a mitad de mes (por ejemplo, el 16/03), la primera pensión es la de ese mismo mes
  (31/03), que `validarVencimientos` acepta porque compara por mes. El vencimiento de la matrícula y «Cobrar desde» no cambian.
- **Se reemplaza la firma con `int`:** `ServicioPlanesPension.propuestaPorDefecto`, `PensionesDemo` y `DatosDemoPensionesDev`
  usan la nueva. Las pruebas que hoy llaman a `porDefecto(int, …)` (`ConfiguracionPlanTest`, `CalculadoraCronogramaTest`,
  `CuotasDePrueba`) pasan un `AnioEscolar` de marzo a diciembre y sus resultados esperados no cambian. `EscenarioCobranza` y
  `GeneradorCronogramaTest`, que arman sus vencimientos con `Calendario.vencimientosPorDefecto` directamente, quedan igual.
- **Prueba obligatoria** `PropuestaVencimientosSegunInicioTest`: la propuesta por defecto **pasa `validar(anio)`** y trae estos
  vencimientos:
  - clases del 01/03 al 18/12: 10 pensiones, de marzo a diciembre;
  - clases del 01/04 al 30/11: 8 pensiones, de abril a noviembre (ninguna en marzo ni en diciembre);
  - clases del 16/03 al 18/12 (a mitad de mes): 10 pensiones, la primera el 31/03;
  - un año de enero a diciembre: 12 pensiones (el tope);
  - y lo mismo por el servicio (`propuestaPorDefecto(anioId, nivel)` con su año escolar), para el año en curso y el siguiente.

## 3. Textos (microcopy; revisar con `investigador-ux` antes de cerrar)
- Banner, le toca: «Tu colegio está en preparación: faltan 6 de 15 pasos.» · «Siguiente: Crear las secciones del año 2026 — lo
  hace Administración o Dirección.» · botón «Crear las secciones».
- Banner, no le toca: «Este paso lo hace Administración o Dirección.» · «Lo que puedes hacer tú: Aprobar el aviso de
  privacidad.» Si no hay nada para su rol: «Por ahora no tienes pasos pendientes. Te avisaremos aquí cuando haya uno.»
- Banner, esperando a la plataforma: «Estamos activando WhatsApp y el correo de tu colegio. Si lleva más de 2 días, escribe a
  {soporte}.»
- Estado vacío con dependencia: «Este año todavía no tiene secciones. Primero: Configurar el año escolar (lo hace Promotoría o
  Dirección).»
- Bloqueo en caja: «Tu colegio está en preparación: todavía no se registran pagos. Cuando la Promotoría lo pase a producción,
  podrás cobrar. Faltan 6 pasos.» · enlace «Ver la Puesta en marcha».
- Portal en preparación (familias): «El colegio todavía no recibe pagos por aquí.»
- Segunda persona, verbo de acción, sin jerga («segregación» no aparece en pantalla: el paso 2 se llama «Equipo del colegio»).

## 4. Diseño visual y accesibilidad (skill `sistema-diseno`, WCAG 2.1 AA)
- **Banner:** superficie blanca (`--color-superficie`), borde superior de 4 px celeste (`--color-celeste`, decorativo), título en
  `--color-azul-profundo`, texto en `--color-texto`; botón primario (`--color-primario`); si el inicio ya tiene un primario, el
  del banner es secundario (**un primario por pantalla**). Sin modo oscuro.
- **Barra:** `<progress>` nativo con `<label for>` visible; el color del relleno es `--color-primario` y el texto da la cifra:
  la información no depende del color.
- **Estados de los pasos:** `badge` con texto («Hecho», «Pendiente», «Opcional», «Bloqueado», «Lo hace la plataforma») e ícono
  con `aria-hidden="true"`; los colores de estado de los tokens (`--color-exito-suave`, `--color-alerta-suave`,
  `--color-info-suave`) con su texto oscuro, que ya cumplen AA.
- **Semántica:** el banner es `<section aria-labelledby="guia-titulo">` (no `role="alert"`); la alerta de bloqueo sí lleva
  `role="alert"`; la lista de pasos es `<ol>` (el orden importa); cada «Ir» con `aria-label="Ir: {acción}"`; un botón
  deshabilitado lleva `aria-disabled="true"` y `aria-describedby` al motivo, y sigue siendo enfocable para leer el motivo.
- **Teclado y táctil:** orden lógico (avance, siguiente, botón, «Ver los 15 pasos»); foco visible con `:focus-visible`;
  objetivos de 44 × 44 px; en celular (360 px) el texto y el botón van apilados y la tabla de pasos pasa a tarjetas.
- **CSS:** clases nuevas en `static/css/` con los tokens de `tokens.css`; ningún color nuevo (si hiciera falta, validar su
  contraste y agregarlo a la skill).

## 5. Archivos que toca
- **`puestaenmarcha`:** `model.PasoPuestaEnMarcha` (nuevo), `dto.PuntoLista`, `dto.GuiaVista` (nuevo),
  `service.EstadoPuestaEnMarcha`, `service.GuiaPuestaEnMarcha` (nuevo), `web.PuestaEnMarchaController`,
  `templates/puesta-en-marcha/lista.html`.
- **`comun.web.InicioController`** (modelo `guia`); **`seguridad.config.ModuloApp`** (`puedeEntrar`).
- **`colegio.service`:** `ColegioService.exigirEnProduccion`, `ColegioEnPreparacionException` y `AccionBloqueada` (nuevos);
  **`comun.web.PreparacionAdvice`** (nuevo) y la traducción de los 1644 de preparación donde hoy se traducen los demás.
- **Plantillas:** `fragments/guia.html` (nuevo), `fragments/componentes.html` (`estadoVacioPaso`), `inicio/promotor`,
  `director`, `administracion`, `caja` y `docente`; las 7 pantallas de estados vacíos (`colegio/anio`, la de secciones,
  `pensiones/…`, `alumnos/…`, `conectores/…`, `usuarios/…`, el saldo inicial); `caja/hoy` y `caja/familia` («Cobrar»
  deshabilitado), `comunicados/…`, `recaudacion/lote` y la campaña de renovación; `familia/…` (texto del portal en preparación).
- **CSS:** `static/css/` (banner, barra, estado de paso).
- **F-D1:** `templates/pensiones/resumen.html` y su CSS. **F-D2:** `cobranza.model.ConfiguracionPlan` (`porDefecto(AnioEscolar,
  …)`), `ServicioPlanesPension.propuestaPorDefecto`, `PensionesDemo`, `DatosDemoPensionesDev` y las pruebas que usan la firma vieja.
- **Documentos:** `docs/manuales/promotoria.md`, `direccion.md`, `administracion.md`, `caja.md` y `docente.md` («Qué ves mientras tu colegio
  está en preparación»); sección «Tanda 1b · Implementación» del diseño.

## 6. Orden de implementación (commits por bloque, push frecuente)
1. `PasoPuestaEnMarcha`, `PuntoLista` con `paso` y `bloqueadoPor`, y `GuiaPuestaEnMarcha`, con sus pruebas (sin pantallas).
2. `ModuloApp.puedeEntrar` y la prueba que recorre los 15 pasos por rol.
3. Banner en los 5 inicios y su CSS.
4. La Puesta en marcha con responsable, estado e «Ir».
5. `ColegioEnPreparacionException`, `PreparacionAdvice`, traducción de los 1644 y botones deshabilitados.
6. Los 7 estados vacíos.
7. F-D1 (tarjetas de Pensiones) y F-D2 (vencimientos según el inicio de clases, con su prueba).
8. Accesibilidad, microcopy con `investigador-ux`, revisión en celular con `disenador-ui`, manuales y CI en verde.

## 7. Pruebas obligatorias
**H2 (`./mvnw -B verify`, también con `-DargLine=-Duser.timezone=America/Los_Angeles`). Sin pruebas de MySQL nuevas: la tanda no
toca la base; el job `mysql` del CI debe seguir en verde igual.**
- `PasoPuestaEnMarchaTest`: 15 pasos, mismas claves y orden que `EstadoPuestaEnMarcha.PASOS`; toda `ruta` existe en
  `CatalogoRutasConId` o en `ModuloApp`; las dependencias no forman ciclos.
- `GuiaPuestaEnMarchaTest`:
  - colegio recién creado: siguiente = paso 1 o 3 según lo hecho; con el año hecho y sin secciones, el siguiente es el paso 4
    con «del año {año}»;
  - un paso con su dependencia pendiente nunca es el `siguiente` ni el `tuyo`;
  - por rol: a Caja no le toca nada en preparación (texto «Por ahora no tienes pasos pendientes»); a Dirección, `tuyo` = aviso
    de privacidad cuando el siguiente es de Administración;
  - 10, 11 y 13 van en `esperandoPlataforma`, nunca en `siguiente`;
  - N y la barra (13 obligatorios) con opcionales pendientes y sin ellos;
  - el docente recibe la guía sin conteos (`conConteos = false`).
- `BannerPreparacionTest`: aparece en los 5 inicios del personal en PREPARACION; no en el del apoderado; no en PRODUCCION, ni en
  SUSPENDIDO; **por cada rol y cada uno de los 15 pasos, el botón solo aparece si `ModuloApp.puedeEntrar` es verdadero y la
  ruta responde 200 a ese rol** (sin 403).
- `EstadosVaciosPasoTest`: las 7 pantallas vacías muestran qué falta, quién lo hace y el botón si el rol puede («Agregar sección»
  para Administración y Dirección; texto «Pídeselo a …» para Caja); con la dependencia pendiente, «Primero: …»; en PRODUCCION, el
  estado vacío sin la mención a la puesta en marcha.
- `PreparacionAdviceTest`: cobrar en caja, pago en línea, confirmar un lote de recaudación y enviar un comunicado en PREPARACION
  responden 409 con el texto y el enlace a `/puesta-en-marcha`; el portal de la familia, sin enlace ni pasos; un 1644 de
  preparación traducido da el mismo mensaje.
- `PuestaEnMarchaVistaTest`: los 15 con estado (texto), responsable e «Ir» o «Lo hace …»; «Siguiente» en el correcto;
  «Pasar a producción» deshabilitado con el motivo hasta que la lista está completa, y solo para Promotoría.
- **Accesibilidad** (en `AccesibilidadPlantillasTest` si existe, o una nueva que renderiza las plantillas): `<progress>` con
  `<label>`; un solo `.btn-primario` por pantalla; `aria-label` en cada «Ir»; botones deshabilitados con `aria-describedby`; los
  badges de estado con texto; `lang="es"`.
- `MatrizPermisosTest` y `RutasIdorTest` sin rutas nuevas con id (la guía no agrega rutas); `ReglasArquitecturaTest`:
  `puestaenmarcha` sigue sin depender de módulos con datos de familias más allá de los conteos que ya lee.
- **Revisión manual** con `disenador-ui` en celular (360 px) y escritorio antes de marcar el PR listo: banner, 2 estados vacíos y
  la Puesta en marcha, con lector de pantalla (NVDA o VoiceOver) en el banner.

## 8. Riesgos
- **Rutas que cambian:** si una pantalla cambia de ruta, el botón del paso apuntaría mal; `PasoPuestaEnMarchaTest` lo detecta.
- **Rendimiento:** el inicio calcula la lista en cada carga (unas 15 consultas de conteo). Con colegios de hasta 8,000
  alumnos es aceptable; si se nota, se cachea por pedido, nunca entre pedidos.
- **Un botón que no termina en 403 pero sí en una regla** (por ejemplo, Administración que propone pensiones sin año): lo
  evita `bloqueadoPor`, que muestra «Primero: …» en lugar del botón.
- **Mensajes de la base:** la traducción de los 1644 de preparación depende del texto del trigger; una prueba fija los tres
  textos para que un cambio en `03` la rompa.

## 9. Terminado cuando
Mientras el colegio está en preparación, cada persona del personal ve en su inicio cuántos pasos faltan, cuál es el siguiente
y quién lo hace, con un botón que la lleva a la pantalla correcta solo si puede usarla; las 7 pantallas vacías dicen qué falta y
cómo seguir; cobrar o enviar algo bloqueado explica por qué y enlaza a la Puesta en marcha; la Puesta en marcha muestra estado,
responsable e «Ir» de los 15 pasos; las familias no ven nada de esto; las tres tarjetas de Pensiones quedan
alineadas y la propuesta de vencimientos pasa la validación con clases desde marzo, desde abril o desde mitad de mes; la revisión de accesibilidad pasa; y el CI del PR en
borrador está en verde, con los conteos de la tanda 1 sin cambios (117 triggers, 19 funciones, 136 huellas).
