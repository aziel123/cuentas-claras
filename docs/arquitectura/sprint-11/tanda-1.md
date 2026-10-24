# Sprint 11 · Tanda 1 · Estados del colegio, año escolar del colegio y facultad del titular (brief)

> Léase primero. Diseño completo: `docs/arquitectura/sprint-11-ciclo-colegio.md`; **la sección 3.13 (auditoría en papel sobre
> 8dd46fb, 14 hallazgos AP-1 a AP-14) prevalece**. Además: 3.1 a 3.8, 3.12, 5 «V39», 6.2 a 6.5, 7, 8, 9 y 13. Rama
> `claude/sprint-11-ciclo-colegio`, PR en borrador: **la tanda está terminada cuando el CI del PR está en verde.**

## 1. Decisiones (pendientes del usuario: se implementan los valores por defecto)
1. **Dos operadores** para dar de baja, suspender, reactivar y bloquear la facultad (192): propone uno, aprueba otro, con firma.
2. **Suspensión:** a los 30 días de aprobada (194), con la caja revisada, los comprobantes aceptados y las libretas emitidas
   (AP-4, AP-13); sin pagos en línea desde 2 horas antes ni efectivo desde 24 horas antes. **La base la rechaza hasta que
   exista V41** (AP-8, 204): en esta tanda se construye y se prueba, pero no se usa. El titular puede **objetar** (AP-10, 205).
3. **Baja** (193, AP-6): sin comprobantes ni órdenes; **sin datos** (alumnos, apoderados, matrículas); inmediata si nadie activó
   su cuenta, si no a los 7 días. Con datos, rechazada hasta la tanda 2 (exportación).
4. **En SUSPENDIDO** nadie del personal ingresa, tampoco la Promotoría; las familias en **modo consulta** (195); a ellas solo
   les llega `ACTIVACION_CUENTA` (AP-5).
5. **El año escolar lo crean y cambian Promotoría o Dirección con firma** (197, AP-9); el alta del operador ya no lo pide;
   **pasar a producción exige el año.**
6. **Facultad del titular** (201, AP-1, AP-2): en PREPARACION da y quita; en PRODUCCION solo da, y solo sin otra persona
   directiva además del titular y del destino; **quitar en PRODUCCION, siempre `CAMBIO_ROLES`**. Cada uso con un código por
   WhatsApp al contacto del titular; la cuenta nueva con otro contacto; sin correo de control, no hay facultad.
7. **Reloj real (AP-3):** `cc_ahora_lima_real()` (con `SYSDATE()`, SIGNAL si es NULL) en `cc_hoy_lima`, `cc_firma_valida`,
   `cc_sesion_abierta`, los triggers nuevos y las versiones nuevas. El resto, en la tanda 2.
8. **«Preparaste la cuenta» (A5, decisión 211; diseño 3.15):** la regla no cambia. El titular crea primero a Dirección, y
   Dirección aprueba lo de las cuentas que creó el titular (la regla no es transitiva: `ControlParticipantes.ampliar`). **Paso 1:
   comprobar que `usuario.creado_por` de esas cuentas es el titular, no `sistema.identidad`.**
9. **La tanda 1c (3.16) ajusta después** la facultad del titular, la 201, AP-1 y A5 para la Promotoría. Esta tanda se
   implementa tal como está; la 1c cambia esas reglas en su propia migración (V40).
10. **La 202 (subdominios) NO entra.** CIERRE, exportación, `conservar_hasta` y supresión son de la tanda 2.

## 2. Alcance
- **V39** (diseño, sección 5): estados y CHECK de `colegio` (`estado_desde`, `cambio_estado_id`, `suspendido_en`,
  `facultad_bloqueada`, `facultad_cambio_id`); `cambio_estado_colegio` (con `ambito`, tipos de facultad y objeción);
  `uso_facultad_titular` (con `mensaje_id`); `anio_escolar.actualizado_por`; FK circular; `COLEGIO_SUSPENDIDO` en
  `sesion_usuario`.
- **Cambios de estado** (`plataforma`): proponer, aprobar, rechazar y anular, con firma; bandeja `/plataforma/cambios-estado`
  («Aprobar» oculto para quien propuso; objeción y «Bloqueada por: …» visibles); filtro «Dados de baja».
  `AplicacionCambiosEstado` (cada 15 min, `sistema.plataforma`): aplica los vencidos; espera órdenes en vuelo (H3), cierres y
  comprobantes (AP-4) y libretas (AP-13), con una alerta diaria por cada pendiente al correo de control, la Promotoría y los
  operadores; cierra las sesiones del personal; recordatorios a 7 días y a 1 día.
- **Objeción del titular** (AP-10): con firma, en PROPUESTO o APROBADO; la resuelve otro operador con respuesta; sin
  resolver no se aplica.
- **Bloqueo de la facultad** (AP-2): `BLOQUEO_FACULTAD` y `DESBLOQUEO_FACULTAD`, dos operadores, inmediatos.
- **Personal:** franja ámbar con la fecha; en SUSPENDIDO, ingreso rechazado (mensaje genérico,
  `INGRESO_RECHAZADO_COLEGIO_SUSPENDIDO`) y el filtro de sesión corta en cada pedido.
- **Familias (modo consulta, 3.8):** estado de cuenta, comprobantes, libretas y comunicados; sin pagar ni editar; `POST` 403
  salvo `/salir` y la clave; activar el portal y restablecer la clave, sí.
- **`RecorridoColegios`** (H2): `activos()` = PREPARACION y PRODUCCION; `conBitacora()` con SUSPENDIDO.
- **Año escolar al colegio (3.7):** alta sin «Año escolar inicial»; crear y cambiar con firma (Promotoría o Dirección);
  Puesta en marcha de **15 pasos** (3 «Año escolar y fechas de clases», 4 «Secciones»).
- **Facultad del titular (3.12, AP-2):** pantallas en «Usuarios»; pedir el código, escribirlo y firmar; evento
  `FACULTAD_TITULAR_USADA` y avisos en la misma transacción al correo de control, a toda la Promotoría y a los operadores.
- **CI `reloj-real`** (AP-3) con `scripts/mysql/reloj-pendiente.txt`.
- **Fuera de alcance:** CIERRE, exportación, acta y supresión (tanda 2); subdominios (202); cambiar de titular (115).

## 3. Migración `V39__ciclo_de_vida_y_titular.sql`
- SQL en la sección 5 del diseño. **Nada de `||`.** Copiar la lista de `ck_sesion_usuario_cierre` de V25 y agregar el motivo.
- **Paso 1, comprobar en H2 2.4.240 MODE=MySQL y en MySQL 8.4 antes de seguir:** `MODIFY COLUMN … NOT NULL` (si H2 no lo
  acepta, desviación anotada); `DROP` y `ADD CONSTRAINT` de los CHECK de `colegio` con filas existentes; la FK circular; que el
  CHECK se evalúe después del BEFORE trigger; **que `cc_app` y `cc_sistema` pueden hacer `SET timestamp` y que `SYSDATE()` no
  lo obedece** (AP-3); los nombres de columnas de `mensaje` y `configuracion_colegio` usados en los triggers; quién llama hoy a
  `AnioEscolar.iniciar()` y `cerrar()` (AP-9).
- **Un caso NULL por CHECK** (lección de V32 a V34): `pendiente`, `referencia_aviso`, `aprobado_por`, `efectiva_desde`,
  `aplicado_en`, `resuelto_por`, `objecion`, `objecion_resuelta_por`, `cambio_estado_id`, `suspendido_en` y `cuenta_nueva`.
- Entidades: `CambioEstadoColegio` (lo fijo con `updatable = false`; `aprobadoEn`, `efectivaDesde` y `aplicadoEn` con
  `insertable = false` y releídos: los pone la base); `UsoFacultadTitular` (`@Immutable`, `Repository` sin `delete*`);
  `Colegio` y `AnioEscolar` con las columnas nuevas; `EstadoColegio` (+4, `opera()`, `consultaFamilias()`);
  `MotivoCierreSesion.COLEGIO_SUSPENDIDO`; `TipoMensaje.CODIGO_FACULTAD` (guarda solo la SHA-256 del código).
- **H2 no tiene triggers:** la aplicación repite cada regla y la prueba en H2; la base, en el job `mysql`.

## 4. MySQL: permisos, triggers y huellas
- `01-usuarios.sql` sin cambios; sin `04-una-vez`. `02-permisos-tablas.sql`, bloque «Sprint 11 · tanda 1» (6.2): `SELECT` de
  `cambio_estado_colegio` y `uso_facultad_titular` para `cc_negocio`; `INSERT` y `UPDATE (columnas, también las de la
  objeción)` de `cambio_estado_colegio` e `INSERT` de `uso_facultad_titular` para `cc_sistema`; `UPDATE (estado, estado_desde,
  cambio_estado_id, suspendido_en, facultad_bloqueada, facultad_cambio_id)` de `colegio` para `cc_sistema` (H6), `UPDATE
  (estado_desde, suspendido_en)` para `cc_negocio` y `UPDATE (actualizado_por)` de `anio_escolar` para `cc_negocio`.
- `03-triggers.sql` (6.3 y 3.13; SQL en el diseño). **Toda hora con `cc_ahora_lima_real()`, comparar siempre con `<=>`:**
  - **funciones nuevas:** `cc_colegio_en_preparacion` (H1), `cc_es_titular` (con `NOT facultad_bloqueada`) y
    `cc_ahora_lima_real` (AP-3, con cuerpo y SIGNAL si es NULL); **versiones nuevas:** `cc_hoy_lima`, `cc_firma_valida`,
    `cc_sesion_abierta`;
  - **triggers nuevos (5):** `trg_cambio_estado_colegio_nace` (operador con firma; origen = estado actual; BAJA sin
    comprobantes, órdenes ni datos; SUSPENSION solo si existe `exportacion_colegio`), `trg_cambio_estado_colegio_estado`
    (APROBADO por OTRO operador; la base pone `aprobado_en` y `efectiva_desde`; objeción del titular y su resolución por otro
    operador; APLICADO en su fecha, con el colegio ya en el destino y sin objeción abierta; RECHAZADO por otro; ANULADO antes de
    aplicar; el DBA conserva su camino), `trg_anio_escolar_nace`, `trg_anio_escolar_cambio` (AP-9) y
    `trg_uso_facultad_titular_nace` (titular con firma y código; QUITAR en PRODUCCION rechazado; DAR en PRODUCCION sin otra
    persona directiva fuera del titular y del destino; correo de control; contacto distinto; `creado_en` de la base);
  - **versiones nuevas (11):** `trg_colegio_cambio` (bloques a a e de 6.3 más AP-4, `suspendido_en`, la facultad bloqueada y el
    año para producción), `trg_sesion_usuario_nace`, `trg_firma_operacion_nace` (AP-3), `trg_orden_pago_nace` (H3),
    `trg_pago_registro` (AP-4: sin efectivo 24 horas antes), `trg_mensaje_nace` (AP-5 y `CODIGO_FACULTAD`),
    `trg_configuracion_colegio_registro` y `_cambio` (H1), `trg_usuario_rol_alta` y `_baja` (facultad; el PROMOTOR del titular
    no se quita) y `trg_usuario_identidad` (la cuenta del titular no se desactiva).
  - Encabezado: «Versión anterior (V38, correcciones del sprint 10): 112 triggers y 13 funciones (16 con huella; 128 huellas)».
- **Conteo esperado:** 112 → **117 triggers**; funciones de `03` 13 → **16**, con huella 16 → **19**; huellas 128 → **136**.
  Prod: «Huellas de los 117 triggers y las 19 funciones verificadas». Tanda 2: **124, 22 y 146**.
- **CI `reloj-real`** (AP-3): cuenta `UTC_TIMESTAMP`, `NOW(`, `CURDATE`, `CURRENT_TIMESTAMP` y `CURRENT_DATE` fuera de
  comentarios en `03` y falla si supera `reloj-pendiente.txt` (el conteo que queda después de esta tanda; en la tanda 2, 0).
- `VerificadorPermisosBaseDatos`: `TRIGGERS_ESPERADOS` con los 5; exige `@@global.sysdate_is_now = 0`; 1142 (`INSERT INTO
  cambio_estado_colegio` con cc_app; `UPDATE` y `DELETE` en `uso_facultad_titular`); 1143 (`SET tipo = tipo`, `SET motivo =
  motivo`; `UPDATE colegio SET cambio_estado_id = cambio_estado_id` con cc_app); 1644 (un cambio que nace APROBADO; un uso de
  la facultad del colegio 0; un `anio_escolar` del colegio 0 con cc_sistema). `HuellasObjetosBd` = 136.

## 5. Segregación de funciones
- **Estado y facultad:** propone un operador, aprueba OTRO (base). El colegio solo pasa a producción. Aplica
  `sistema.plataforma` en su fecha. El titular objeta; resuelve un operador que no propuso.
- **Año escolar:** Promotoría o Dirección con firma, al crear y al cambiar. Ni operador, ni Administración, ni Caja, ni docente.
- **Facultad:** solo el titular, nunca sobre sí mismo; «quien cobra no aprueba» sigue; Administración, Caja y Docente sin
  cambios. **A8 del sprint 9, ajustado** (3.12): el aviso de «menos de 2 personas directivas» sigue.

## 6. Archivos que toca
- **Base:** `V39__ciclo_de_vida_y_titular.sql`; `scripts/mysql/02-permisos-tablas.sql`, `03-triggers.sql` y
  `reloj-pendiente.txt` (nuevo); `scripts/respaldo/tablas-vigiladas.txt` y `respaldar.sh` (`uso_facultad_titular`).
- **`colegio`:** `Colegio`, `EstadoColegio`, `AnioEscolar`; `AvisoPlataforma` (+7 tipos e indicador `operadores`);
  `EstadoDelColegio` (nuevo); `ServicioEstructura` (crear y cambiar el año con firma); `colegio/anio.html`.
- **`plataforma`:** `CambioEstadoColegio` y sus enums y repositorio; `CambiosEstadoColegio` (con `objetar`, `resolverObjecion`,
  `bloquearFacultad`, `suspensionDisponible()`); `AplicacionCambiosEstado`; `RevisionEstadosColegio`; `AltaColegio` y
  `AltaColegioRequest` (sin año); `ColegiosDeLaPlataforma`; `PlataformaController`; plantillas `nuevo`, `lista`, `colegio` y
  `cambios-estado` (nueva).
- **`seguridad`:** `UsoFacultadTitular`; `FacultadTitular` (nuevo: código, contacto distinto, correo de control);
  `ServicioUsuarios`; `ProveedorAutenticacion`; `ServicioCierreSesion`; `MotivoCierreSesion`; filtro de sesión; `ModuloApp`;
  plantillas de «Usuarios» y de la objeción del titular.
- **`familias`:** `ModoConsultaFamilias` y el interceptor; plantillas del portal. **`puestaenmarcha`:** 15 pasos,
  `PasoAProduccion`. **`comun.sistema.RecorridoColegios`** y sus usuarios. **`comunicacion`:** `TipoMensaje.CODIGO_FACULTAD`,
  plantillas nuevas y despacho a operadores.
- **Demo y pruebas:** `DemoColegios`, `FamiliasDemo` (el año, como su Dirección), `EscenarioPlataforma` (el año lo crea su
  Promotoría con firma; sigue exponiendo `anio()`, así las 20 clases que lo usan no cambian).
- **Transversales:** `AccionAuditoria` (+11, más `CAMBIO_ESTADO_OBJETADO`, `OBJECION_RESUELTA`, `FACULTAD_BLOQUEADA` y
  `FACULTAD_DESBLOQUEADA`), `ClaveFirma`, `VerificadorPermisosBaseDatos`, `HuellasObjetosBd`, `PermisosEsperados`,
  `application.yml` (`cuentasclaras.ciclo.*`).
- **Documentos y CI:** `alta-de-un-colegio.md`, `suscripcion.md`, `docs/manuales/promotoria.md`, `plantillas-whatsapp-meta.md`;
  «Tanda 1 · Implementación» del diseño; `ci.yml` (paso «Sprint 11, tanda 1» del job `mysql`, V1–V39, 136 y 117/19, y
  `reloj-real`); `paso-0.sh`, `en-la-vm.sh`, `despliegue-gcp.md` y `mysql-usuarios.md` con los conteos nuevos.

## 7. Pruebas obligatorias
**H2 (`./mvnw -B verify`, también con `-DargLine=-Duser.timezone=America/Los_Angeles`):**
- `CambioEstadoColegioTest`: motivo corto rechazado; el mismo operador no aprueba ni rechaza (F2); `efectiva_desde` a +30 días
  (SUSPENSION), al instante (REACTIVACION, facultad) y a 0 o 7 días (BAJA); anular; un pendiente por colegio y ámbito; la
  bandeja oculta «Aprobar» a quien propuso; `suspensionDisponible()` falso sin la exportación (AP-8).
- `AplicacionCambiosEstadoTest`: aplica en su fecha en hora de Lima; espera órdenes en vuelo (F9), cierres `POR_REVISAR` u
  `OBSERVADO` y comprobantes no aceptados (AP-4), y libretas sin emitir (`a13SuspensionEsperaLasLibretas`), con la alerta diaria
  por cada pendiente; cierra las sesiones del personal y no las de las familias (F10).
- `ObjecionTitularTest` (AP-10): `a10ObjecionSinResolverImpideAplicar`, `a10ObjecionLaResuelveOtroOperador`,
  `a10ObjecionDeQuienNoEsTitularFalla`; avisos al correo de control y a los operadores.
- `SuspensionAvisosTest` (F1, F3); `IngresoEnSuspendidoTest`; `ModoConsultaFamiliasTest` con `LibretaEnSuspensionTest` (libreta
  de un alumno **con deuda**: L8); `BajaColegioTest` (con comprobante o con datos, rechazada; inmediata solo sin cuentas
  activadas; sale de la lista principal); `RecorridoColegiosTest` (H2); `RevisionEstadosColegioTest` (F7).
- **Año escolar:** `AltaSinAnioTest`; `AnioEscolarDelColegioTest` (paso 3 pendiente; Promotoría y Dirección crean y cambian con
  firma; Administración 403 en `EstructuraControllerTest`); `PasoAProduccion` sin año rechazado; `ColegioSinAnioNoRompeTest`
  (H7); `PuestaEnMarchaTest` (15 pasos); `DemoColegiosTest`.
- **Facultad:** `FacultadTitularTest` (crea Dirección y Promotoría en PREPARACION; otra Promotoría no puede (F13); sobre sí
  mismo no (F14); con Caja no (F15); en PRODUCCION con otra Dirección va a `CAMBIO_ROLES` (F16);
  **`f16bQuitarAlUnicoDirectivoEnProduccionFalla`** (AP-1); sin código, con el código de otro mensaje o vencido, rechazada;
  cuenta nueva con el contacto del titular, rechazada; sin correo de control, rechazada; bloqueada por dos operadores,
  rechazada) y `FacultadTitularAvisosTest` (F17).
- **`AltaACobroTest`** (diseño 3.15, 10 pasos): alta sin año → el titular activa, crea el año y, con la facultad, Dirección,
  Administración y Caja → Administración propone el plan → **el titular no puede aprobarlo (A5)** → **Dirección lo aprueba** →
  cuenta bancaria (la propone el titular y la aprueba Dirección) y producción → Administración registra un alumno → Caja cobra
  (comprobante y aviso) → Caja cierra a ciegas → **Dirección aprueba el cierre y el titular no puede**.
  `ParticipantesNoTransitivosTest`: una Dirección creada por el titular aprueba lo de una Administración también creada por él.
- `ReglasArquitecturaTest`, `RutasIdorTest`, `CatalogoRutasConId`, `MatrizPermisosTest`, `MarcaUnicaTest` y las que cuentan:
  **39 migraciones, 117 triggers, 136 huellas**.

**MySQL real (`mysql/AuditoriaSprint11CicloMySqlTest`, job `mysql`; correr local lo tocado antes del push). 1644 salvo aviso.
Las fechas se simulan como DBA (`UPDATE … SET efectiva_desde` con root), nunca con `SET timestamp`:**
- `f1SuspensionAntesDe30DiasFalla`, `efectivaDesdeLaPoneLaBase`, `a8SuspensionSinV41Falla`, **`h9SetTimestampNoMueveElReloj`**
  (con `SET timestamp` a +31 días: no aplica la suspensión, no alarga una firma ni una sesión), `relojNuloLanzaSignal`,
  `verificadorExigeSysdateIsNowApagado`.
- `f2MismoOperadorNoApruebaFalla`, `rechazoPorElMismoOperadorFalla`, `aprobacionSinFirmaFalla`, `cambioQueNaceAprobadoFalla`,
  `cambioDesdeOtroEstadoFalla`, `cambioResueltoNoCambia`, `cambioConCcAppFalla` (1142), `tipoYMotivoNoCambian` (1143),
  `a10AplicarConObjecionAbiertaFalla`, `a10ObjecionResueltaPorQuienPropusoFalla` (3819).
- `f5PromotoriaNoCambiaAEstadosNuevos`, `f6EstadoSinCambioAprobadoFalla`, `f6CambioDeOtroColegioFalla`,
  `fichaEnSuspendidoNoCambia`, `suspendidoEnLoPoneLaBase`, `a4SuspensionConCierrePorRevisarFalla`,
  `a4SuspensionConComprobantePendienteFalla`, `a4EfectivoEnLasUltimas24HorasFalla`, `h3OrdenDentroDeLas2HorasFalla`.
- `f10SesionDelPersonalEnSuspendidoFalla`, `sesionDeApoderadoEnSuspendidoPasa`, `a5ActivacionDeApoderadoEnSuspendidoPasa`,
  `a5RecordatorioEnSuspendidoFalla`, `a5ComunicadoEnSuspendidoFalla`, `h1CorreoDeControlEnSuspendidoFalla`.
- `f12BajaConComprobanteFalla`, `a6BajaConDatosFalla`, `a6BajaConCuentaActivadaEsA7Dias`.
- `f18AnioPorOperadorFalla`, `anioSinFirmaFalla`, `anioPorAdministracionFalla`, `f18ProduccionSinAnioFalla`,
  `a9AnioCambiaSinFirmaFalla`, `a9AnioDeOtroColegioFalla` (colegio_id o anio cambiados).
- `f13FacultadDeQuienNoEsTitularFalla`, `facultadSinCodigoFalla`, `facultadConCodigoUsadoFalla` (UNIQUE),
  `facultadConNumeroSaltadoFalla`, `f14TitularSobreSiMismoFalla` (3819), `f14PromotoriaDelTitularNoSeQuita`,
  `cuentaDelTitularNoSeDesactiva`, `f15FacultadNoSaltaQuienCobraNoAprueba`, `f16FacultadEnProduccionConOtraDireccionFalla`,
  **`f16bQuitarAlUnicoDirectivoEnProduccionFalla`**, `a2CuentaNuevaConContactoDelTitularFalla`, `a2FacultadSinCorreoDeControlFalla`,
  `a2FacultadBloqueadaFalla`, `a2BloqueoDeUnSoloOperadorFalla`, `rolDirectivoConUsoViejoFalla`, `usoFacultadSinUpdate` (1142).
- `checksConNull`; `reactivacionEnUnaTransaccion` y `bajaInmediataEnUnaTransaccion` (pasan).
- **`AltaACobroMySqlTest`:** el mismo escenario con permisos mínimos; lo que dependa de procesos externos, con los dobles de
  `PermisosMySqlTest` (si un paso no se puede en MySQL, lo cubre H2, y MySQL cubre año, facultad, plan, pago y cierre).
- `PermisosMySqlTest`: alta sin año; el titular activa, crea el año con firma, pide el código y crea su Dirección; pasa a
  producción; dos operadores proponen una suspensión (rechazada: AP-8) y bloquean la facultad.
- Paso `comprobar`; `huellas_objetos()` = 136; arranque prod por TLS con 117 y 19; paso `reloj-real`. Job `respaldo`: un
  `uso_facultad_titular` borrado como DBA en el colegio A marca solo a A.

## 8. Riesgos
- **Se tocan funciones y triggers del corazón** (`cc_firma_valida`, `cc_sesion_abierta`, `trg_firma_operacion_nace`, identidad):
  las pruebas de los sprints 7 a 10 sobre ellos deben seguir verdes sin cambios (salvo las de 3.7 del diseño).
- **`SYSDATE()` y `CONVERT_TZ`** con la zona de la sesión: probar con `time_zone` en `SYSTEM`, `+00:00` y `-05:00`.
- **La facultad en preparación** sigue siendo poder sin segunda firma: lo compensan el código al teléfono del titular, los
  avisos y el bloqueo de dos operadores.
- **El flujo de suspensión no se puede usar hasta la tanda 2** (AP-8): la pantalla lo dice; las pruebas lo cubren igual.

## 9. Terminado cuando
Un colegio nace sin año y su titular lo crea con firma; el titular arma su equipo directivo con un código en su teléfono y
avisos a todos, y en producción no puede quitar solo al único contrapeso; sin año no hay producción; dos operadores dan de baja
un colegio sin datos y bloquean la facultad; la suspensión está construida, espera caja, comprobantes y libretas, no se mueve
con `SET timestamp` y la base la rechaza hasta V41; y el CI del PR en borrador está en verde (117 triggers, 19 funciones, 136
huellas).
