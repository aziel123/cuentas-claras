# Sprint 11 · Tanda 1 · Estados del colegio, año escolar del colegio y facultad del titular (brief)

> Léase primero. Diseño completo: `docs/arquitectura/sprint-11-ciclo-colegio.md` (secciones **3.1** (hallazgos), 3.2 a 3.8,
> **3.12**, 4, 5 «V39», 6.2 a 6.5, 7, 8, 9, 11 y 13). Rama `claude/sprint-11-ciclo-colegio`, PR en borrador: **la tanda está
> terminada cuando el CI del PR está en verde.** Antes de codear, la auditoría del diseño en papel
> (`auditor-seguridad-antifraude`); sus hallazgos críticos y altos se corrigen en el diseño y en este brief.

## 1. Decisiones (pendientes del usuario: se implementan los valores por defecto)
1. **Dos operadores** para dar de baja, suspender y reactivar (decisión 192): propone uno, aprueba otro, cada uno con su firma.
2. **Suspensión a los 30 días** de aprobada, fijada por la base (194); sin pagos en línea desde 2 horas antes (H3).
3. **Baja:** inmediata si el titular nunca activó su cuenta; a los 7 días si la activó (193). Sin comprobantes ni órdenes.
4. **En SUSPENDIDO** nadie del personal ingresa, tampoco la Promotoría; las familias en **modo consulta** (195).
5. **El año escolar lo crea Promotoría o Dirección con firma**, sin segunda aprobación; Administración ya no (197). El alta del
   operador ya no lo pide. **Pasar a producción exige el año.**
6. **Facultad del titular** (201): el titular (cuenta de su `primera_promotoria`) da y quita Promotoría y Dirección sin segunda
   aprobación, con avisos; en PRODUCCION solo mientras no haya otra persona activa de Promotoría o Dirección.
7. **H9 (hora que la conexión puede mover con `SET timestamp`):** los controles de fecha nuevos usan `cc_ahora_lima_real()`;
   el resto de los triggers queda para la auditoría del diseño (diseño, 3.1).
8. **La 202 (subdominios) NO entra en esta tanda.** CIERRE, exportación y supresión son de la tanda 2.

## 2. Alcance
- **V39** (diseño, sección 5): estados nuevos y CHECK de `colegio` (H5), `estado_desde`, `cambio_estado_id`,
  `cambio_estado_colegio`, `uso_facultad_titular`, FK circular, motivo `COLEGIO_SUSPENDIDO` en `sesion_usuario`.
- **Cambios de estado** (`plataforma`): proponer, aprobar, rechazar y anular desde `/plataforma`, con la firma de cada
  operador; bandeja `/plataforma/cambios-estado` («Aprobar» oculto para quien propuso); filtro «Dados de baja»; la lista
  principal sin BAJA. `AplicacionCambiosEstado` (cada 15 min, `sistema.plataforma`): aplica los vencidos, espera órdenes de
  pago `CREADA` sin vencer (H3), cierra las sesiones del personal (`COLEGIO_SUSPENDIDO`), avisa y registra; recordatorios a
  7 días y a 1 día. BAJA inmediata y REACTIVACION se aplican en la transacción de la aprobación.
- **Personal:** franja ámbar «El acceso del personal se suspenderá el {fecha}» desde la aprobación; en SUSPENDIDO,
  `ProveedorAutenticacion` rechaza con el mensaje genérico y el evento `INGRESO_RECHAZADO_COLEGIO_SUSPENDIDO`, y el filtro de
  sesión corta en cada pedido.
- **Familias (modo consulta, 3.8):** estado de cuenta, comprobantes, libretas y comunicados; sin «Pagar», Yape, justificar,
  confirmar lecturas ni editar «Mis datos»; `POST` del portal 403 salvo `/salir` y la clave; la activación del portal sí.
- **`RecorridoColegios`** (H2): `activos()` = PREPARACION y PRODUCCION; `conBitacora()` para `HuellaDiaria` y el resumen al
  correo de control.
- **Año escolar al colegio (3.7):** alta sin la sección «Año escolar inicial»; `crearAnio` para PROMOTOR y DIRECTOR con firma
  `anio_escolar:{anio}:CREADO`; Puesta en marcha con **15 pasos** (3 «Año escolar y fechas de clases», 4 «Secciones»);
  pensiones, alumnos y saldo inicial dicen «Primero configure el año escolar» mientras falte.
- **Facultad del titular (3.12):** «Crear cuenta de Promotoría o Dirección» y «Dar o quitar Promotoría o Dirección» en
  «Usuarios», solo para el titular; firma `facultad_titular:{colegio}:{n}`; evento `FACULTAD_TITULAR_USADA`; avisos en la misma
  transacción al correo de control, a toda la Promotoría y a los operadores.
- **Avisos:** `AvisoPlataforma.Tipo` +7 e indicador `operadores`; plantillas con `{{1}}` = colegio (y en `plantillas-whatsapp-meta.md`).
- **Fuera de alcance:** CIERRE, exportación, acta y supresión (tanda 2); subdominios (202); cambiar de titular (115).

## 3. Migración `V39__ciclo_de_vida_y_titular.sql`
- SQL en la sección 5 del diseño. **Nada de `||`** (en MySQL es OR). Copiar las listas de `ck_sesion_usuario_cierre` de V25 y
  agregar `COLEGIO_SUSPENDIDO`.
- **Comprobar en H2 2.4.240 MODE=MySQL y en MySQL 8.4 antes de seguir:** `ALTER TABLE … MODIFY COLUMN estado_desde … NOT NULL`
  (si H2 no lo acepta, queda nulo y lo exige la aplicación: anotarlo como desviación); `DROP CONSTRAINT` y `ADD CONSTRAINT` de
  `ck_colegio_estado` y `ck_colegio_produccion` con filas existentes (la PLATAFORMA y los colegios de V1 y de la demo); la FK
  circular `fk_colegio_cambio_estado (cambio_estado_id, id) → cambio_estado_colegio (id, colegio_id)`; que el CHECK se evalúe
  después del BEFORE trigger (el trigger pone `pendiente = NULL`; la aplicación también lo pone).
- Lección de V32 a V34: un CHECK que evalúa NULL pasa. **Un caso NULL por CHECK:** `pendiente`, `referencia_aviso`,
  `aprobado_por`, `efectiva_desde`, `aplicado_en`, `resuelto_por`, `cambio_estado_id` y `cuenta_nueva`.
- Entidades: `CambioEstadoColegio` (`tipo`, `colegioId`, `plataformaId`, `motivoTipo`, `motivo`, `referenciaAviso`,
  `estadoOrigen`, `estadoDestino`, `propuestoPor` y `operadorId` con `updatable = false`; `aprobadoEn`, `efectivaDesde` y
  `aplicadoEn` con `insertable = false` y releídos después de guardar, porque los pone la base); `UsoFacultadTitular`
  (`@Immutable`, repositorio `Repository` sin `delete*`; `creadoEn` lo pone la base); `Colegio` con `estadoDesde` y
  `cambioEstadoId`; `EstadoColegio` con 4 valores, `opera()` y `consultaFamilias()`; `MotivoCierreSesion.COLEGIO_SUSPENDIDO`.
- **H2 no tiene triggers:** la aplicación repite cada regla y la prueba en H2; la base, en el job `mysql`.

## 4. MySQL: permisos, triggers y huellas
- `01-usuarios.sql` sin cambios; sin `04-una-vez`. `02-permisos-tablas.sql`, bloque «Sprint 11 · tanda 1» (sección 6.2): `SELECT` de `cambio_estado_colegio` y de
  `uso_facultad_titular` para `cc_negocio`; `INSERT` y `UPDATE (columnas)` de `cambio_estado_colegio` y `INSERT` de
  `uso_facultad_titular` para `cc_sistema`; `UPDATE (estado, estado_desde, cambio_estado_id)` de `colegio` para `cc_sistema`
  (H6) y `UPDATE (estado_desde)` para `cc_negocio`. `permisos_objetos()` y `PermisosEsperados` con las filas nuevas.
- `03-triggers.sql` (sección 6.3, SQL completo en el diseño):
  - **funciones nuevas** `cc_colegio_en_preparacion(colegio)` (H1), `cc_es_titular(colegio, nombre)` y `cc_ahora_lima_real()`
    (H9: `SYSDATE()`, que `SET timestamp` no mueve; en los controles de fecha nuevos);
  - **triggers nuevos** `trg_cambio_estado_colegio_nace` (operador activo con firma `cambio_estado_colegio:{colegio}:{n}`,
    origen = estado actual, BAJA sin comprobantes ni órdenes), `trg_cambio_estado_colegio_estado` (APROBADO por OTRO
    operador con `:APROBADO`; la base pone `aprobado_en` y `efectiva_desde`; APLICADO con la fecha vencida y el colegio ya en
    el destino con este cambio; RECHAZADO por otro; ANULADO antes de aplicar), `trg_anio_escolar_nace` (PROMOTOR o DIRECTOR
    activo con `anio_escolar:{anio}:CREADO`; nunca cc_sistema) y `trg_uso_facultad_titular_nace` (cc_sistema; el titular con
    su firma; otra cuenta del personal; numerada sin huecos; en PRODUCCION sin otra persona directiva; `creado_en` de la base);
  - **versiones nuevas** de `trg_colegio_cambio` (bloques a a e: RUC y ficha congelados fuera de preparación, cc_app solo a
    PRODUCCION, cc_sistema solo con SU cambio aprobado y vencido, BAJA sin comprobantes, **producción con año escolar**,
    `estado_desde` de la base), `trg_sesion_usuario_nace` (personal fuera en BAJA, SUSPENDIDO, CERRADO y SUPRIMIDO; apoderado
    solo en SUSPENDIDO y CERRADO), `trg_orden_pago_nace` (H3), `trg_configuracion_colegio_registro` y `_cambio` (H1),
    `trg_usuario_rol_alta` (excepción por SU uso `DAR` de hace 5 minutos), `trg_usuario_rol_baja` (por SU uso `QUITAR`; el
    PROMOTOR del titular no se quita) y `trg_usuario_identidad` (la cuenta del titular no se desactiva).
  - **Comparar siempre con `<=>`.** Encabezado: «Versión anterior (V38, correcciones del sprint 10): 112 triggers y 13
    funciones (16 con huella; 128 huellas)».
- **Conteo esperado:** 112 → **116 triggers** (4 nuevos y 8 con versión nueva); funciones de `03` 13 → **16**, con huella
  16 → **19**; huellas 128 → **135**. Prod: «Huellas de los 116 triggers y las 19 funciones verificadas». Tanda 2: 123, 20 y 143.
- `VerificadorPermisosBaseDatos`: `TRIGGERS_ESPERADOS` con los 4; 1142 (`INSERT INTO cambio_estado_colegio` con cc_app,
  `UPDATE` y `DELETE` en `uso_facultad_titular`); 1143 (`UPDATE cambio_estado_colegio SET tipo = tipo`, `SET motivo = motivo`;
  `UPDATE colegio SET cambio_estado_id = cambio_estado_id` con cc_app); 1644 (un cambio que nace APROBADO; un uso de la
  facultad del colegio 0; un `anio_escolar` del colegio 0 con cc_sistema). `HuellasObjetosBd` = 135.

## 5. Segregación de funciones
- **Cambiar el estado:** propone un operador y aprueba OTRO (base: `ck_cambio_estado_colegio_otro` y trigger). Nadie del
  colegio cambia a un estado nuevo; la Promotoría solo pasa a producción. Lo aplica `sistema.plataforma` en su fecha.
- **El año escolar:** Promotoría o Dirección con firma. Ni el operador, ni Administración, ni Caja, ni el docente.
- **Facultad del titular:** solo el titular, nunca sobre sí mismo; las combinaciones prohibidas («quien cobra no aprueba») siguen;
  Administración, Caja y Docente sin cambios. En PRODUCCION con otra persona directiva, `CAMBIO_ROLES` como hoy.
- **A8 del sprint 9, ajustado** (diseño, 3.12): el aviso de «menos de 2 personas de Promotoría o Dirección» sigue; el texto de
  la puesta en marcha dice «Como titular, puedes crear las cuentas de Promotoría y Dirección».

## 6. Archivos que toca
- **Base:** `V39__ciclo_de_vida_y_titular.sql`; `scripts/mysql/02-permisos-tablas.sql`; `scripts/mysql/03-triggers.sql`;
  `scripts/respaldo/tablas-vigiladas.txt` y `respaldar.sh` (`uso_facultad_titular`, solo inserción, por colegio).
- **`colegio`:** `model.Colegio`, `model.EstadoColegio`; `service.AvisoPlataforma` (+7 tipos, `operadores`),
  `service.EstadoDelColegio` (nuevo); `service.ServicioEstructura.crearAnio`; plantilla `colegio/anio.html` (firma).
- **`plataforma`:** `model.CambioEstadoColegio`, `TipoCambioEstado`, `MotivoCambioEstado`, `EstadoCambioEstado` y su
  repositorio; `service.CambiosEstadoColegio`; `proceso.AplicacionCambiosEstado` y `proceso.RevisionEstadosColegio`;
  `service.AltaColegio` y `dto.AltaColegioRequest` (sin año); `service.ColegiosDeLaPlataforma` (filtro); `web.PlataformaController`;
  plantillas `plataforma/nuevo.html` (sin «Año escolar inicial»), `plataforma/lista.html`, `plataforma/colegio.html` y
  `plataforma/cambios-estado.html` (nueva).
- **`seguridad`:** `model.UsoFacultadTitular` y su repositorio; `service.FacultadTitular` (nuevo); `service.ServicioUsuarios`
  (delega); `service.ProveedorAutenticacion`; `service.ServicioCierreSesion`; `model.MotivoCierreSesion`; el filtro de sesión;
  `config.ModuloApp` (Promotoría en `/estructura/anios/**`; rutas nuevas); plantillas de «Usuarios».
- **`familias`:** `service.ModoConsultaFamilias` (nuevo) y el interceptor; plantillas del portal (aviso y botones ocultos).
- **`puestaenmarcha`:** `service.EstadoPuestaEnMarcha` (15 pasos), `service.PasoAProduccion`; plantilla de la lista.
- **`comun.sistema.RecorridoColegios`** (H2) y sus usuarios (`HuellaDiaria`, resumen al correo de control).
- **`comunicacion`:** plantillas de los avisos nuevos (`PlantillaMensaje`) y el despacho a operadores.
- **Demo y pruebas:** `demo.service.DemoColegios`, `alumnos.inicial.FamiliasDemo` (el año lo crea la demo como su Dirección),
  `test/.../comun/prueba/EscenarioPlataforma` (el año lo crea la Promotoría del escenario con su firma; sigue exponiendo `anio()`).
- **Transversales:** `AccionAuditoria` (+11), `ClaveFirma` (+`ANIO_ESCOLAR_CREADO`, `FACULTAD_TITULAR`, `CAMBIO_ESTADO_*`),
  `VerificadorPermisosBaseDatos`, `HuellasObjetosBd`, `PermisosEsperados`, `application.yml` (`cuentasclaras.ciclo.*`).
- **Documentos:** `docs/operacion/alta-de-un-colegio.md` (el alta sin año; la facultad del titular), `suscripcion.md` (la
  suspensión ya está en el sistema), `docs/manuales/promotoria.md` (año escolar y equipo directivo), `plantillas-whatsapp-meta.md`,
  sección «Tanda 1 · Implementación» del diseño, `.github/workflows/ci.yml` (paso «Sprint 11, tanda 1» del job `mysql`, V1–V39,
  135 y 116/19), `paso-0.sh`, `en-la-vm.sh`, `despliegue-gcp.md` y `mysql-usuarios.md` con los conteos nuevos.

## 7. Pruebas obligatorias
**H2 (`./mvnw -B verify`, también con `-DargLine=-Duser.timezone=America/Los_Angeles`):**
- `CambioEstadoColegioTest`: proponer con motivo corto rechazado; el mismo operador no aprueba (F2) ni rechaza; aprobar fija
  `efectiva_desde` a +30 días (SUSPENSION), al instante (REACTIVACION) y a 0 o 7 días (BAJA, según la activación del titular);
  anular antes de aplicar; un pendiente por colegio; la bandeja oculta «Aprobar» a quien propuso.
- `AplicacionCambiosEstadoTest`: aplica en su fecha en hora de Lima (también con la zona de Los Ángeles); no aplica antes;
  espera una orden `CREADA` sin vencer y aplica cuando vence (`AplicacionEsperaOrdenesTest`, F9); cierra las sesiones del
  personal con `COLEGIO_SUSPENDIDO` y no las de las familias (`SesionAbiertaAlSuspenderTest`, F10).
- `SuspensionAvisosTest` (F1 y F3): propuesta, aprobación, recordatorios y aplicación avisan al titular, a la Promotoría, al
  correo institucional y al de control; si no se puede avisar, no se guarda.
- `IngresoEnSuspendidoTest`: el personal (también la Promotoría) no ingresa; el apoderado sí, en modo consulta; en BAJA nadie.
- `ModoConsultaFamiliasTest`: se ven estado de cuenta, comprobantes, libretas y comunicados; «Pagar» y Yape ocultos; `POST`
  del portal 403; `LibretaEnSuspensionTest` (la libreta de un alumno **con deuda** se descarga en SUSPENDIDO: L8).
- `BajaColegioTest`: BAJA con comprobante rechazada (F12); BAJA sale de la lista principal y aparece en «Dados de baja».
- `RecorridoColegiosTest` (H2): `activos()` sin SUSPENDIDO ni BAJA; `conBitacora()` con SUSPENDIDO; `HuellaDiaria` sigue en
  SUSPENDIDO; `AperturaPeriodos` y `EmisionLibretas` no corren en SUSPENDIDO.
- `RevisionEstadosColegioTest` (F7): un colegio SUSPENDIDO sin su cambio aplicado genera la alerta CRÍTICA.
- **Año escolar** (`AltaSinAnioTest`, `AnioEscolarDelColegioTest`): el alta crea el colegio sin año y el formulario ya no tiene
  la sección; el paso 3 está pendiente y pensiones, alumnos y saldo inicial dicen «Primero configure el año escolar»; Promotoría
  y Dirección crean el año con firma; Administración recibe 403 (`EstructuraControllerTest` invertida); `PasoAProduccion` sin año
  rechazado; `ColegioSinAnioNoRompeTest` recorre las pantallas y procesos que leen `EN_CURSO` con un colegio sin año (H7);
  `PuestaEnMarchaTest` con 15 pasos; la demo crea su colegio con año (`DemoColegiosTest`).
- **Facultad del titular** (`FacultadTitularTest`, `FacultadTitularAvisosTest`): el titular crea una cuenta de Dirección y otra
  de Promotoría en PREPARACION sin aprobación; da y quita Dirección a una cuenta existente; otra Promotoría no puede (F13); sobre
  sí mismo, no (F14); Dirección a una cuenta con Caja, no (F15); en PRODUCCION con otra Dirección activa, va a `CAMBIO_ROLES`
  (F16); en PRODUCCION sin otra persona directiva, sí; cada uso deja `FACULTAD_TITULAR_USADA` y avisa al correo de control, a
  toda la Promotoría y a los operadores (F17); Administración, Caja y Docente siguen como hoy (`ServicioUsuariosTest` sin cambios).
- `ReglasArquitecturaTest` (nadie fuera de `plataforma` escribe `CambioEstadoColegio`; nadie fuera de `seguridad.service`
  escribe `UsoFacultadTitular`; `plataforma` sin módulos de familias), `RutasIdorTest`, `CatalogoRutasConId`,
  `MatrizPermisosTest`, `MarcaUnicaTest` (plantillas nuevas) y las pruebas que cuentan: **39 migraciones, 116 triggers, 135 huellas**.

**MySQL real (`mysql/AuditoriaSprint11CicloMySqlTest`, job `mysql`; correr local lo tocado antes del push). Todas 1644 salvo que
se diga otra cosa:**
- La fecha de aplicación se simula **como DBA** (`UPDATE … SET efectiva_desde` con root: el DBA conserva su camino), nunca
  con `SET timestamp`.
- `f1SuspensionAntesDe30DiasFalla` (aplicar con `efectiva_desde` futuro, con la zona de Los Ángeles en la aplicación);
  `h9SetTimestampNoAdelantaLaSuspension` (`SET timestamp` a +31 días en la conexión de cc_sistema y aplicar: 1644);
  `verificadorExigeSysdateIsNowApagado`;
  `efectivaDesdeLaPoneLaBase` (la aplicación manda otra fecha y la base la reemplaza).
- `f2MismoOperadorNoApruebaFalla`, `rechazoPorElMismoOperadorFalla`, `aprobacionSinFirmaFalla`, `cambioQueNaceAprobadoFalla`,
  `cambioDesdeOtroEstadoFalla`, `cambioResueltoNoCambia`, `cambioConCcAppFalla` (1142), `tipoYMotivoNoCambian` (1143).
- `f5PromotoriaNoCambiaAEstadosNuevos`, `f6EstadoSinCambioAprobadoFalla`, `f6CambioDeOtroColegioFalla`,
  `cambioEstadoIdSinEstadoFalla`, `fichaEnSuspendidoNoCambia`, `rucEnSuspendidoNoCambia`.
- `f10SesionDelPersonalEnSuspendidoFalla`, `sesionDeApoderadoEnSuspendidoPasa`, `sesionEnBajaFalla`.
- `h1CorreoDeControlEnSuspendidoFalla` (registro y cambio, con la firma del operador), `h3OrdenDentroDeLas2HorasFalla`,
  `f12BajaConComprobanteFalla`, `pagoEnSuspendidoFalla` (la regla de hoy, con el estado nuevo).
- `f18AnioPorOperadorFalla` (cc_sistema), `anioSinFirmaFalla`, `anioPorAdministracionFalla`, `anioEnColegioSuspendidoFalla`,
  `f18ProduccionSinAnioFalla`.
- `f13FacultadDeQuienNoEsTitularFalla`, `facultadSinFirmaFalla`, `facultadConNumeroSaltadoFalla`, `f14TitularSobreSiMismoFalla`
  (3819, el CHECK), `f14PromotoriaDelTitularNoSeQuita` (también con `CAMBIO_ROLES` aprobada), `cuentaDelTitularNoSeDesactiva`,
  `f15FacultadNoSaltaQuienCobraNoAprueba`, `f16FacultadEnProduccionConOtraDireccionFalla`, `rolDirectivoConUsoViejoFalla` (más de
  5 minutos), `rolDirectivoConUsoDeOtraCuentaFalla`, `usoFacultadSinUpdate` (1142).
- `checksConNull` (los casos de la sección 3); `reactivacionEnUnaTransaccion` y `bajaInmediataEnUnaTransaccion` (pasan).
- `PermisosMySqlTest`: el flujo con permisos mínimos (alta sin año, el titular activa, crea el año con firma, crea su Dirección
  con la facultad, pasa a producción; dos operadores suspenden; se aplica en su fecha simulada; se reactiva).
- Paso `comprobar` con los casos nuevos; `huellas_objetos()` = 135; arranque prod por TLS con 116 y 19. Job `respaldo`: un
  `uso_facultad_titular` borrado como DBA en el colegio A marca solo a A.

## 8. Riesgos
- **Tocar `trg_colegio_cambio`, `trg_usuario_rol_*` y `trg_usuario_identidad`**, que son del corazón de la identidad: cada
  bloque nuevo va con su prueba en MySQL y las pruebas de los sprints 7 y 8 sobre esos triggers deben seguir verdes sin
  cambios (salvo las listadas en 3.7 del diseño).
- **La facultad del titular en preparación es poder sin segunda firma.** Lo compensan los avisos al correo de control, a la
  Promotoría y a los operadores, y que en preparación no hay dinero. Si la auditoría del diseño pide más, la opción es exigir
  que la cuenta nueva se active con un enlace a un contacto distinto del titular.
- **H9 parcial:** solo los controles nuevos usan la hora real; los anteriores siguen con `UTC_TIMESTAMP()` hasta que se decida.
- **`EscenarioPlataforma` lo usan 20 clases:** mantener `anio()` y crear el año dentro, para no tocarlas.

## 9. Terminado cuando
Un colegio nace sin año escolar y su titular lo crea con su firma; el titular arma su equipo de Promotoría y Dirección sin
esperar a nadie y cada uso avisa al correo de control, a la Promotoría y a los operadores; el colegio no pasa a producción sin
año; dos operadores distintos dan de baja un colegio en preparación (que sale de la lista principal) y suspenden uno en
producción, que se aplica solo 30 días después de aprobado, ni con `SET timestamp` antes; en suspensión el personal no ingresa
ni cobra y las familias consultan, también su libreta con deuda; la reactivación devuelve todo; y el CI del PR en borrador
está en verde (116 triggers, 19 funciones, 135 huellas).
