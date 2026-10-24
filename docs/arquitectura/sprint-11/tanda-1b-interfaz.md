# Sprint 11 · Tanda 1b · Correcciones de interfaz (brief)

> Léase primero, junto con `tanda-1b.md` (la guía de puesta en marcha): las dos son la **tanda 1b** y van en el mismo PR en
> borrador. **La tanda está terminada cuando el CI del PR está en verde.** Diseño: `docs/arquitectura/sprint-11-ciclo-colegio.md`,
> secciones 3.14 y 13. Skills: `sistema-diseno` (tokens, componentes y accesibilidad AA) y `evaluacion-ux` (microcopy).
> Revisión visual con `disenador-ui` antes de marcar el PR listo.

## 1. Decisiones (pendientes del usuario: se implementan los valores por defecto)
1. **Sin cambios en la base:** sin migración, triggers, funciones ni huellas. Conteos sin cambio: **117, 19 y 136**. Ninguna
   regla de aprobación cambia: solo dónde se ve y cómo se cuenta (F-D5).
2. **Ancho (208):** armazón fluido hasta **1760 px**, con márgenes laterales de 24 a 32 px; el texto corrido, a unos 70
   caracteres por línea.
3. **Bandeja de Aprobaciones (209):** lista también lo que hoy se aprueba en otra pantalla, con «Revisar» que lleva a esa
   pantalla; el contador del menú lo suma con la regla de `puedeResolver`.
4. **Inicio en preparación (210):** domina la guía; las cifras y los gráficos aparecen cuando hay datos.

## 2. Pensiones (F-D1 a F-D4)
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

**F-D3. Las fechas del formulario del plan con `min` y `max` iguales a la validación.** Hoy los `<input type="date">` de
`pensiones/plan-formulario.html` no tienen límites: el calendario deja elegir fechas que el servidor rechaza después. **La
validación del servidor no cambia.**
- **Una sola fuente para los rangos:** `ConfiguracionPlan` expone `rangoMatricula(AnioEscolar)`, `rangoPensiones(AnioEscolar)` y
  `rangoCobroDesde(AnioEscolar)` (un `record RangoFechas(LocalDate desde, LocalDate hasta)`), y **`validar` usa esos mismos
  métodos** en lugar de calcular las fechas aparte. Así el HTML y el servidor no pueden separarse.
  | Campo | `min` | `max` |
  |---|---|---|
  | Vencimiento de la matrícula | 01/01 del año anterior | fin de clases |
  | Cada pensión (`vencimientos[i]`) | día 1 del mes de inicio de clases | 31/12 del año del plan |
  | «Cobrar desde» | 01/01 del año del plan | 31/12 del año del plan |
- **Plantilla:** fragmento nuevo `componentes.html :: campoFecha(nombre, etiqueta, ayuda, requerido, min, max)` (el `campoDe` de
  hoy no cambia, para no tocar a sus otros usos), con `min` y `max` en formato ISO (`aaaa-mm-dd`); las 12 casillas de pensión
  reciben los mismos `th:min` y `th:max`. El controlador agrega `rangos` al modelo en «nuevo», «editar» y al volver a mostrar
  el formulario después de un error. La ayuda de cada campo dice el rango en palabras («Entre el 01/01/2025 y el 18/12/2026»),
  porque `min` y `max` no se anuncian igual en todos los lectores de pantalla y en algunos navegadores no impiden escribir.
  «Cobrar desde» mantiene su ayuda «el día 1 de un mes» (eso lo sigue validando solo el servidor).
- **Prueba obligatoria** `PlanFormularioRangosTest` (con MockMvc, sobre el HTML):
  - en «Proponer plan» de un año con clases del 01/04 al 30/11: la matrícula trae `min` = 01/01 del año anterior y `max` = fin
    de clases; **cada una de las 12 casillas** de pensión trae `min` = día 1 de abril y `max` = 31/12; «Cobrar desde» trae
    `min` = 01/01 y `max` = 31/12;
  - lo mismo en «Editar plan» y al volver a mostrar el formulario después de un error de validación;
  - **coherencia con el servidor:** `validar` acepta una fecha en el `min` y otra en el `max` de cada campo, y rechaza un día
    antes del `min` y un día después del `max` (la matrícula, la primera pensión y «Cobrar desde» del día 1 del año siguiente).

**F-D4. Textos del plan en borrador.** Al guardar un plan nuevo, `PlanPensionController` (línea 62) muestra «Listo: propusiste
el plan. Ahora debe aprobarlo otra persona de Promotoría o Dirección.», pero el plan sigue en `BORRADOR`: falta pulsar «Enviar
para aprobación» (`pensiones/plan.html`). Y la etiqueta del borrador, «En preparación», choca con el «En preparación» del
colegio que muestra la guía de esta misma tanda.
- **Mensaje al guardar:** «Guardaste el borrador. Revísalo y pulsa «Enviar para aprobación» para que otra persona de
  Promotoría o Dirección lo apruebe.» El de «Enviar para aprobación» queda (o pasa a ser): «Enviaste el plan. Ahora debe
  aprobarlo otra persona de Promotoría o Dirección.»
- **Etiquetas de `EstadoPlan`:** `BORRADOR` «Borrador» (antes «En preparación»); `ENVIADO` «Por aprobar» (antes «Enviado, por
  aprobar»), igual que la etiqueta que ya usa `pensiones/resumen.html` y que `EstadoDescuento.SOLICITADO`. `APROBADO`,
  `REEMPLAZADO` y `DESCARTADO` no cambian. Solo cambia el texto: los nombres del enum, la base y los estados no cambian.
- **Por la misma razón, `EstadoLote.BORRADOR`** (lote del saldo inicial) pasa de «En preparación» a «Borrador». Su `ENVIADO`
  («Enviado, por confirmar») queda: ahí se confirma, no se aprueba.
- **Lo que «En preparación» sigue nombrando:** solo el estado del colegio (`EstadoColegio.PREPARACION`). `TextosEstadosTest`
  recorre las etiquetas de todos los enums de estado con `etiqueta()` y falla si otra que no sea la del colegio dice «En
  preparación».
- **Pruebas que cambian:** `PensionesWebTest` (línea 99: espera «Guardaste el borrador» en el aviso de éxito) y las que comparan
  «En preparación» o «Enviado, por aprobar» de un plan o un lote. **Prueba nueva:** `PlanBorradorTextosTest` (guardar un plan
  nuevo: el aviso dice «Guardaste el borrador» y menciona «Enviar para aprobación»; la ficha del plan muestra «Borrador»; después
  de enviarlo, «Por aprobar» y el aviso de enviado).

## 3. Bandeja de Aprobaciones con todo lo pendiente (F-D5)
**El problema:** un plan enviado a aprobación no aparece en Aprobaciones («Pendientes (0)»): se aprueba en Cobranza → Pensiones →
el plan → «Aprobar plan». La bandeja y su contador (`BandejaAprobaciones.contarPorResolver`) solo leen `solicitud_cambio`.

**Lo que hoy se aprueba fuera de la bandeja** (leído en los controladores y servicios; los cierres de caja ya están en el
módulo Aprobaciones, pero en su propia lista y fuera del contador):

| # | Qué | Dónde se resuelve hoy | Quién | Pendiente cuando |
|---|---|---|---|---|
| 1 | Plan de pensiones | `/pensiones/planes/{id}` «Aprobar plan» (`ServicioPlanesPension.aprobar`) | Promotoría o Dirección, otra persona | `EstadoPlan.ENVIADO` |
| 2 | Lote del saldo inicial | `/pensiones/saldo-inicial/{id}` «Confirmar» (`ServicioSaldoInicial.confirmar`) | Promotoría o Dirección | `EstadoLote.ENVIADO` |
| 3 | Lote de recaudación bancaria | `/recaudacion/lotes/{id}/confirmar` (`ServicioRecaudacion.confirmar`) | Promotoría o Dirección | Lote enviado, por confirmar |
| 4 | Extracto de una cuenta (conciliación) | `/conciliacion/cuentas/{id}/confirmar` (`ServicioExtractos.confirmar`) | Promotoría o Dirección | Extracto cargado, por confirmar |
| 5 | Feriado propuesto | `/feriados/{id}/aprobar` (`ServicioFeriados.aprobar`) | Promotoría o Dirección | Feriado propuesto |
| 6 | Correo externo de control propuesto por el operador | `/puesta-en-marcha/correo-control/{id}/aprobar` (`CorreoDeControl.aprobar`) | Promotoría | `cambio_correo_control` PROPUESTO |
| 7 | Cierre de caja | `/aprobaciones/cajas/{id}` (`CajasAprobacionController`) | Promotoría o Dirección (comprobar) | `cierre_caja` en `POR_REVISAR` |
| 8 | Respaldo con filas faltantes | `/panel/sistema/respaldo/resolver` (`EstadoTecnicoController`) | Promotoría | Respaldo con `colegios_faltan` sin resolución del colegio |

No entran: el aviso de privacidad (un paso propio de Dirección, que ya muestra la guía), la confirmación de una importación de
alumnos (la confirma quien la sube), la partida sugerida de la conciliación (es parte del mismo extracto, punto 4), la
confirmación de lectura de un comunicado (es de la familia) y los cambios de estado del colegio (son de los operadores). **El
paso 1 de la tanda los comprueba uno por uno** (estado exacto de cada pendiente y roles de 3, 4 y 7) y corrige esta tabla.

**Diseño:**
- **Puerto nuevo `aprobaciones.api.PendientesExternos`:**
  `List<PendienteExterno> pendientes(String usuario)` y `long contarPorResolver(String usuario)`. Lo implementa **cada módulo
  dueño** (`cobranza`: planes y saldo inicial; `recaudacion`; `conciliacion`; `colegio`: feriados; `puestaenmarcha`: correo de
  control; `caja`: cierres; `operacion`: respaldo). `aprobaciones` no depende de ellos: solo recibe la lista de beans. ArchUnit
  lo exige, y cobranza sigue sin depender de académico.
- **`PendienteExterno(tipo, titulo, detalle, solicitadoPor, desde, enlace, puedeResolver)`:**
  - el título es corto («Plan de pensiones de Primaria 2026»);
  - el detalle va sin datos de familias ni montos de alumnos (por ejemplo, «Enviado por adm.lopez el 24/10»);
  - **el enlace es la pantalla donde se resuelve**.
- **La regla de `puedeResolver`, la misma de la bandeja:** quien pidió o participó no lo ve como suyo para resolver. Por
  ejemplo, quien envió el plan lo ve en «Enviados por ti, esperando a otra persona», no en «Pendientes». Cada módulo dice quién
  participó con los campos que ya tiene (`enviado_por`, `propuesto_por`, `creado_por` o el cajero del cierre).
- **Bandeja (`aprobaciones/bandeja.html`):**
  - «Pendientes (N)» mezcla las solicitudes y los pendientes externos, ordenados por fecha;
  - cada externo lleva la etiqueta «Se revisa en su pantalla» y el botón «Revisar» (secundario, con `aria-label="Revisar: {título}"`);
  - aprobar y rechazar siguen en la pantalla del módulo: **no se duplica ninguna lógica de aprobación**;
  - en celular (`aprobaciones/movil.html`), lo mismo como tarjetas.
- **Contador del menú (`ContadorAprobaciones`):** `bandeja.contarPorResolver()` más la suma de `contarPorResolver(usuario)` de los
  puertos; «N por aprobar». `contarPendientes` (el resumen diario de `sistema.panel`) suma también los externos sin el filtro de
  participantes.
- **Seguridad:** cada puerto filtra por el colegio de la sesión (como sus pantallas) y devuelve solo lo que el rol puede abrir;
  el botón «Revisar» aparece solo si `ModuloApp.puedeEntrar` lo permite (de `tanda-1b.md`).

**Pruebas:**
- `BandejaPendientesExternosTest`:
  - un plan enviado por Administración aparece en la bandeja de Promotoría con «Revisar», que lleva a `/pensiones/planes/{id}`, y
    el contador da 1;
  - para quien lo envió, no cuenta y aparece en «Enviados por ti»;
  - después de aprobarlo, desaparece y el contador baja;
  - **un caso por cada uno de los 8 tipos**;
  - con dos colegios, ninguno ve los pendientes del otro.
- `ContadorAprobacionesTest`: el contador igual a la cantidad de «Pendientes» de la bandeja para cada rol.
- `ReglasArquitecturaTest`: `aprobaciones` no depende de los módulos dueños.
- `MatrizPermisosTest`: «Revisar» nunca lleva a una ruta con 403.

## 4. Ancho de pantalla y tableros (F-D6)
**El problema:** en 1920 px el contenido se ve angosto y centrado, con mucho vacío a los lados: `tokens.css` define
`--ancho-max: 1440px`, que usan el armazón y el encabezado (`app.css`, líneas 60 y 98). En el inicio de Promotoría de un colegio
en preparación aparecen tarjetas en S/ 0.00 y gráficos vacíos.

**Armazón:**
- `--ancho-max: 1760px` y un margen lateral fluido `--margen-lateral: clamp(24px, 2vw, 32px)`, usado por `.armazon` y por el
  encabezado: los dos se alinean al mismo contenedor.
- Debajo de 1760 px el contenido es fluido; en celular, el margen de hoy.
- La impresión (`@media print`) no cambia.

**Texto corrido:**
- Clase `.prosa` y regla para `p`, `.secundario` y las ayudas: `max-width: 70ch`. Reemplaza el `72ch` de `.cabecera > div >
  .secundario`, para tener un solo valor.
- Los párrafos no se estiran con el armazón.
- Los formularios mantienen sus anchos (`.formulario-angosto`, 520 px).

**Tableros de Promotoría y Dirección** (`inicio/promotor.html`, `inicio/director.html`, `fragments/graficos.html`):
- **Cifras:** `.kpis` en grilla `repeat(auto-fit, minmax(220px, 1fr))`. **Desde 1600 px, las 4 cifras en una sola fila**
  (`repeat(4, 1fr)`).
- **Gráficos:** `.graficos` en una columna hasta 1599 px. **Desde 1600 px, en 2 columnas:**
  - primera fila: el gráfico de los últimos 30 días al lado de «Para revisar»;
  - segunda fila: la morosidad y los medios de pago.
- **Tablas:** usan todo el ancho del contenedor (`width: 100%`), con los montos alineados a la derecha como siempre.
- Ningún tamaño de letra cambia; los gráficos SVG escalan con su `viewBox`.

**Inicio de Promotoría (y de Dirección) en preparación:**
- Si el colegio está en PREPARACION, **no se muestran las cifras ni los gráficos**. Domina la guía de `tanda-1b.md`, en su
  variante ampliada: el avance, el siguiente paso y los 3 pasos que siguen, cada uno con quién lo hace.
- En PRODUCCION, un gráfico sin datos (por ejemplo, el primer día) muestra su estado vacío («Todavía no hay pagos en estos 30
  días»), no ejes vacíos. Una cifra sin movimientos muestra «S/ 0.00» con la nota «Sin pagos todavía».
- El controlador (`InicioController`) ya sabe el estado del colegio: **no se calcula el tablero en preparación** (ahorra las
  consultas).

**Accesibilidad:**
- El orden de lectura no cambia al pasar a 2 columnas: el DOM queda en el orden lógico y solo cambia la grilla.
- Sin desborde horizontal en ningún ancho.
- Objetivos de 44 px y contraste según los tokens.

**Pruebas:**
- `InicioPreparacionTest`: en PREPARACION, el inicio de Promotoría y el de Dirección traen la guía ampliada y **no** traen
  `.kpis` ni `.graficos`; en PRODUCCION sin pagos, cada gráfico trae su estado vacío.
- `TokensCssTest`: `--ancho-max` vale 1760 px y existe `--margen-lateral`.
- `PlantillasSinAnchoFijoTest`: ninguna plantilla del inicio pone un `style` con ancho en px.
- **Revisión visual con `disenador-ui`** en **390, 1280, 1600 y 1920 px**, sin desbordes:
  - el inicio de Promotoría en preparación y en producción;
  - el de Dirección;
  - una tabla larga (pagos del día);
  - Pensiones con las tres tarjetas;
  - un formulario;
  - la bandeja de Aprobaciones.
  Las capturas quedan en la sección «Tanda 1b · Implementación» del diseño.

## 5. Archivos que toca
- **Pensiones (F-D1 a F-D4):**
  - `templates/pensiones/resumen.html`, `plan-formulario.html` y `plan.html`, y su CSS;
  - `cobranza.model.ConfiguracionPlan` (`porDefecto(AnioEscolar, …)`, rangos y `validar`), `EstadoPlan` y `EstadoLote`;
  - `ServicioPlanesPension.propuestaPorDefecto` y `PlanPensionController` (rangos y avisos);
  - `PensionesDemo` y `DatosDemoPensionesDev`;
  - `fragments/componentes.html` (`campoFecha`);
  - las pruebas que usan la firma vieja o los textos viejos.
- **Aprobaciones (F-D5):**
  - `aprobaciones.api.PendientesExternos` y `PendienteExterno` (nuevos), `BandejaAprobaciones`, `ContadorAprobaciones`;
  - `aprobaciones/bandeja.html` y `movil.html`;
  - una implementación del puerto en `cobranza`, `recaudacion`, `conciliacion`, `colegio`, `puestaenmarcha`, `caja` y `operacion`.
- **Ancho y tableros (F-D6):**
  - `static/css/tokens.css` y `app.css` (y `rediseno.css` si repite el ancho);
  - `fragments/layout.html` (el encabezado), `fragments/graficos.html`, `inicio/promotor.html` e `inicio/director.html`;
  - `comun.web.InicioController`.
- **Documentos:** la skill `sistema-diseno` con el ancho de 1760 px, el margen lateral y la regla de 70 caracteres (es la
  fuente de los tokens); `docs/manuales/promotoria.md` y `direccion.md` (el inicio en preparación); la sección «Tanda 1b ·
  Implementación» del diseño.

## 6. Orden de implementación (commits por bloque, push frecuente)
1. F-D4 (textos) y F-D1 (tarjetas): pequeñas, con sus pruebas.
2. F-D2 (vencimientos según el inicio de clases) y F-D3 (`min` y `max`), con una sola fuente de rangos.
3. F-D5: primero el paso 1 (comprobar la tabla de los 8 tipos); después el puerto, los 7 adaptadores, la bandeja y el contador.
4. F-D6: tokens y armazón; tableros; inicio en preparación.
5. Revisión visual en los 4 anchos, accesibilidad, manuales y CI en verde.

## 7. Riesgos
- **F-D5 toca el contador del menú, que se calcula en cada página:** 8 consultas de conteo más. Cada puerto cuenta con una
  consulta indexada por colegio y estado; si se nota, se cachea por pedido, nunca entre pedidos.
- **Que la bandeja deje de ser el único lugar de aprobación** puede tentar a aprobar desde la bandeja: está prohibido en este
  diseño. «Revisar» solo enlaza; la aprobación sigue donde están sus controles (llamadas, totales vistos, firma).
- **El ancho mayor** puede estirar formularios o párrafos que hoy dependían del 1440: la revisión en 1920 px lo detecta.
- **Cambiar la firma de `porDefecto`** obliga a tocar pruebas de cobranza: sus resultados esperados no cambian (año de marzo a
  diciembre).

## 8. Terminado cuando
- Las tres tarjetas de Pensiones quedan alineadas.
- La propuesta de vencimientos pasa la validación con clases desde marzo, desde abril o desde mitad de mes.
- El calendario del plan no deja elegir fechas que el servidor rechazaría.
- Un plan guardado dice «Borrador» y cómo enviarlo.
- Aprobaciones muestra y cuenta todo lo que la persona puede resolver, con «Revisar» a su pantalla.
- En 1920 px el contenido aprovecha el ancho, con las 4 cifras en una fila desde 1600 px, sin desbordes en 390 px.
- El inicio de un colegio en preparación muestra la guía, no ceros.
- La revisión visual de `disenador-ui` pasa en los 4 anchos.
- El CI del PR en borrador está en verde, con 117, 19 y 136 sin cambios.
