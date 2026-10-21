# Sprint 9 · Tanda 2 · Asistencia diaria con aviso a las familias (brief)

> Léase primero. Diseño completo: `docs/arquitectura/sprint-9-asistencia-comunicados.md` (secciones 3.4 a 3.6, **3.15**,
> 4, 5, 6 «V33», 7.2, 7.3 «Tanda 2», 7.4, 7.5, 9, 10, 12, 13.1, 13.3 y 14 «Tanda 2»). Rama
> `claude/sprint-9-asistencia-comunicados`, PR en borrador: **la tanda está terminada cuando el CI del PR está en verde.**
> Incluye los 15 hallazgos de la auditoría en papel sobre `d581d0d` (A1 a A15, tabla de la sección 3.15 del diseño).

## 1. Decisiones del usuario (21-oct-2026)
1. **La asistencia la toma el DOCENTE, solo en sus secciones** (decisión 144): rol DOCENTE con asignación VIGENTE (tutor,
   docente o auxiliar). **Dirección no registra ni corrige marcas**: aprueba solicitudes, revierte justificaciones con
   firma y ve todo. Para un ausente, se propone una **cobertura** con fecha de fin (A7).
2. **Marcador dactilar, puerta abierta sin implementarlo:** columna `fuente` (`DOCENTE` | `MARCADOR`, CHECK) en
   `registro_asistencia` y `marca_asistencia`; los triggers rechazan `MARCADOR`. Se llama `fuente` porque `origen` ya
   existe con otro significado. El DTO de la hoja no recibe `fuente` y ArchUnit prohíbe `FuenteAsistencia.MARCADOR` (A15).
3. **PDFBox aprobado** (decisión 157). No entra código de PDF en esta tanda (tanda 3, bloque 2); la sección 6 es contrato.

## 2. Alcance
- V33 (sección 3). Hoja del día en el celular (todos «Presente», un toque por alumno, **un POST con una firma**),
  corrección del mismo día **hasta la hora del segundo aviso** (A2), reporte mensual.
- Corrección de otro día o posterior al cierre (`CORRECCION_ASISTENCIA`, pedida por un docente de la sección, A13) y
  registro tardío (`REGISTRO_ASISTENCIA_TARDIO`) por la bandeja.
- Portal: `/familia/asistencia` (justificar una FALTA de los últimos 5 días hábiles, con firma de sesión, A5; nunca una
  ya revertida, A3) y `/familia/avisos` (consentimientos). Consentimiento desde la ficha. Reversión por Dirección.
- Cobertura (A7): `asignacion_docente.hasta` (hasta 5 días hábiles), que `FinAsignaciones` termina sola.
- `AvisosAsistencia` (`sistema.asistencia`) con **marca de agua por colegio** (A2): 10:30 (incluye lo del día anterior
  posterior al último envío) y 16:00; el aviso incluye FALTA_JUSTIFICADA (A1). Plantillas `cc_asistencia_v2` y
  `cc_asistencia_corregida_v2`.
- `AlertasAcademicas` (09:30, Dirección): secciones sin registro **o con menos marcas iniciales que matrículas ACTIVAS**
  (A4), **con los nombres de las secciones** y sin alumnos (A7).
- Gobierno (A8): aviso si hay menos de 2 personas activas con PROMOTOR o DIRECTOR (`VerificadorConfiguracion` y puesta en
  marcha); `TipoAviso.SOLICITUDES_PENDIENTES` a Promotoría por solicitudes pendientes más de 2 días.
- «Mis datos», «Datos con plazo vencido», `respaldar.sh` y `tablas-vigiladas.txt` con las tablas nuevas.
- **Fuera de alcance:** comunicados, PDF, SIAGIE (tanda 3); marcador; modo sin conexión; justificar tardanzas.

## 3. Migración `V33__asistencia_y_consentimientos.sql`
- SQL en la sección 6 del diseño: `configuracion_academica`, `registro_asistencia`, `marca_asistencia`,
  `consentimiento_aviso`, **`aviso_asistencia_corte`** (A2), **`ALTER TABLE asignacion_docente ADD hasta`** con
  `ck_asignacion_docente_hasta` (A7; V32 ya está publicada: no se edita), tipos `ASISTENCIA_DIA` y `ASISTENCIA_CORREGIDA`.
- CHECK nuevo `ck_marca_asistencia_justificada`: `estado <> 'FALTA_JUSTIFICADA' OR origen IN ('JUSTIFICACION',
  'CORRECCION_APROBADA')` (A1).
- **Comprobar en H2 2.4.240 MODE=MySQL y en MySQL 8.4:** `TIME` en los CHECK; la FK de `marca_asistencia` consigo misma;
  `ck_mensaje_tipo` con la lista completa de V31 (copiarla de V31); `ix_mensaje_tipo_fecha` sin duplicar; `ADD COLUMN`
  y `ADD CONSTRAINT` sobre `asignacion_docente` con filas existentes.
- Lección de V32: un CHECK que evalúa NULL pasa. Un caso NULL por CHECK (`motivo`, `primera`, `reemplaza_id`, `hasta`).
- Entidades de solo inserción con `updatable = false`; `AsignacionDocente.hasta` también `updatable = false`;
  `AvisoAsistenciaCorte` solo la escribe `sistema.asistencia`.

## 4. MySQL: permisos, triggers y huellas
- `01-usuarios.sql`: sin cambios. Sin `04-una-vez`.
- `02-permisos-tablas.sql`: bloque «Sprint 9 · tanda 2» (sección 7.2): `INSERT` solo en las 3 tablas de solo inserción;
  `INSERT, UPDATE (columnas)` en `configuracion_academica`; `hasta` **fuera** del UPDATE de `asignacion_docente` (1143);
  `aviso_asistencia_corte`: `SELECT, INSERT, UPDATE (ultima_marca_id, actualizado_en, version)` solo a `cc_sistema`.
- `03-triggers.sql` (sección 7.3 «Tanda 2», con A1 a A7 y A13 ya escritos en el SQL):
  - funciones nuevas `cc_hoy_lima` y `cc_sesion_abierta`; **versión nueva** de `cc_docente_de_seccion` (ignora una
    cobertura con `hasta` vencido; definirla después de `cc_hoy_lima`);
  - triggers nuevos: `trg_registro_asistencia_nace` (firma `asistencia:{seccion}:{fecha}:REGISTRADA`),
    `trg_marca_asistencia_nace`, `trg_consentimiento_aviso_nace`, **`trg_configuracion_academica_nace`** y
    **`trg_configuracion_academica_cambio`** (A6: solo DIRECTOR, firma `configuracion_academica:{colegio}:CAMBIADA`);
  - versiones nuevas: `trg_mensaje_nace` (asistencia) y `trg_asignacion_docente_nace` (`hasta` ≤ hoy + 10 días, A7);
  - en `trg_marca_asistencia_nace`: `REGISTRO` con `creado_por = registrado_por`; `CORRECCION_DEL_DIA` con firma
    `…:CORREGIDA`, antes de `hora_segundo_aviso` y nunca sobre `JUSTIFICACION` ni `REVERSION_JUSTIFICACION`;
    `JUSTIFICACION` con firma `…:JUSTIFICADA` y nunca sobre `REVERSION_JUSTIFICACION`; `REVERSION_JUSTIFICACION` no por
    quien tiene asignación VIGENTE en la sección; `CORRECCION_APROBADA` pedida por un docente de la sección.
- **Conteo esperado:** 88 → **93 triggers** (5 nuevos; A6 son 2 porque MySQL no tiene un trigger para INSERT y UPDATE);
  funciones con huella 11 → **13** (10 de `03` + 3 de `02`; `cc_docente_de_seccion` cambia de huella, no de número);
  huellas 99 → **106**. Prod: «Huellas de los 93 triggers y las 13 funciones verificadas». Tanda 3: 97 y 110.
- `VerificadorPermisosBaseDatos`: `TRIGGERS_ESPERADOS` con los 5 nuevos; 1142 (`UPDATE registro_asistencia`,
  `DELETE FROM marca_asistencia`, `UPDATE consentimiento_aviso`; con `cc_app`, cualquier escritura en
  `aviso_asistencia_corte`); 1143 (`UPDATE asignacion_docente SET hasta = hasta`); 1644 (registro de ayer `DEL_DIA` del
  colegio 0; `configuracion_academica` del colegio 0 sin firma; `mensaje` `ASISTENCIA_DIA` con «S/ 350.00»).
- **Pruebas MySQL obligatorias (antes «comprobar»):** `a12ZonaDeVenceEnEsLima` (A12); la ventana de 1 hora del tardío;
  la hora de cierre de la corrección del día en hora de Lima; que el trigger de INSERT de `marca_asistencia` lee su
  propia tabla; `REGEXP_LIKE` con «Pagán» y «Vencesla». `cc_solicitud_firmada` ya exige `resuelto_por <> solicitado_por`.

## 5. Segregación de funciones («quien cobra no aprueba», en su versión académica)
- Quien pide una `CORRECCION_ASISTENCIA` o un `REGISTRO_ASISTENCIA_TARDIO` no la aprueba; aprueba otra persona activa de
  Dirección o Promotoría con su firma. La corrección la pide un docente de la sección (A13).
- El docente no aprueba nada ni ve la bandeja. Un director que también enseña en la sección no revierte allí (A13).
- Quien registra (docente) no revierte justificaciones (Dirección, firma `marca_asistencia:{id}:REVERTIDA`); nadie del
  personal crea una FALTA_JUSTIFICADA sin solicitud aprobada (A1).
- Las horas del aviso solo las cambia Dirección con firma, y el cambio queda resaltado (A6).
- Con menos de 2 personas activas de PROMOTOR o DIRECTOR la segregación no se cumple: aviso en el arranque y en la puesta
  en marcha; el alta de esos roles va por `CAMBIO_ROLES` aprobada por la otra persona (sprint 7) (A8).
- Caja no tiene rutas de asistencia; DOCENTE no se combina con CAJA ni ADMINISTRACION (tanda 1). INDECOPI: `academico`
  no depende de cobranza y el aviso de una familia con deuda es idéntico al de una al día.

## 6. PDFBox: controles obligatorios (contrato para la tanda 3; nada se implementa en esta tanda)
`comunicados.service.AdjuntosComunicado` es la única clase que usa `org.apache.pdfbox` (ArchUnit).
1. **Versión:** la última 3.x estable (hoy 3.0.8; nunca anterior a 3.0.7, por CVE-2026-23907), fijada en el `pom.xml`,
   en el SBOM y aprobada por el job **Dependencias** (OSV-Scanner sin CRÍTICA ni ALTA con arreglo); Dependabot la sigue.
2. **Tamaño** ≤ 2 MB antes de abrir; **bytes mágicos** `%PDF-`; rechazo de políglotos; **≤ 10 páginas**.
3. **Memoria:** `MemoryUsageSetting.setupMixed(50 MB, 100 MB)` (los dos topes, A9); `/tmp` en tmpfs con `size=`
   (`compose.prod.yml`: `size=256m`; subir a `384m` si la prueba lo pide).
4. **Tiempo** ≤ 10 s, en un **executor dedicado** de 1 o 2 hilos con cola acotada (4) y `AbortPolicy` (A10): lleno,
   «intente en unos minutos» sin abrir el PDF; **10 subidas por usuario y hora**.
5. **Rechazo** (no limpieza): cifrado o con contraseña; JavaScript (`/Names/JavaScript` y en cualquier acción);
   `OpenAction`, `AA`, anotaciones con `/A` o `/AA`; `Launch`, `SubmitForm`, `ImportData`, `ResetForm`, `Hide`, `Named`,
   `GoToR`, `GoToE`, `Rendition`, `Sound`, `Movie`, `RichMedia`; archivos embebidos; `AcroForm`/XFA (A14). **URI solo con
   lista permitida** (dominio de `CC_URL_PUBLICA` y `dominios-permitidos`).
6. **Re-guardado completo**, sin `Info`, sin XMP (`/Metadata`) y sin revisiones incrementales (A14); **SHA-256** del
   original y del saneado.
7. **Descarga:** `Content-Type: application/pdf`, `Content-Disposition: attachment; filename="comunicado-{id}.pdf"`,
   `X-Content-Type-Options: nosniff`, `Content-Security-Policy: sandbox; default-src 'none'`,
   `Cache-Control: private, no-store`.
8. **Pruebas:** PDF **malformado**, PDF **enorme** (> 2 MB, y 5,000 páginas o bomba de objetos), PDF **con JavaScript**,
   los demás de E26 y A14, cabeceras de descarga y **`diezBombasSeguidasNoDejanSinServicio`** (A10).

## 7. Archivos que toca
- **Base:** `V33__asistencia_y_consentimientos.sql`; `scripts/mysql/02-permisos-tablas.sql`; `scripts/mysql/03-triggers.sql`
  (encabezado «Versión anterior (V32): 88 triggers y 8 funciones»); `scripts/respaldo/tablas-vigiladas.txt` y
  `respaldar.sh` (las 3 tablas de solo inserción, por colegio).
- **`academico`:** `model` (`RegistroAsistencia`, `MarcaAsistencia`, `EstadoAsistencia`, `OrigenMarca`, `FuenteAsistencia`,
  `MotivoJustificacion`, `ConfiguracionAcademica`, `ConsentimientoAviso`, `Finalidad`, `Canal`, `AvisoAsistenciaCorte`;
  `AsignacionDocente.hasta`); repositorios sin `delete*`; `service` (hoja y guardado con firma, correcciones,
  `ManejadorCorreccionAsistencia`, `ManejadorRegistroTardio`, `JustificacionesFamilia`, `ReportesAsistencia`,
  `AlertasAcademicas`, `ConsentimientosAviso`, configuración con firma, cobertura en `Asignaciones`); `proceso.FinAsignaciones`
  (coberturas vencidas); `api` (`InasistenciasDelDia` con FALTA_JUSTIFICADA y marca de agua, `Consentimientos`); `dto`
  (`MarcaPedida` **sin `fuente`**); `web` (`AsistenciaController`, `PortalAsistenciaController`,
  `ConfiguracionAcademicaController`).
- **`comunicacion`:** `TipoMensaje` (+2), `PlantillaMensaje` (+2), `proceso.AvisosAsistencia` (marca de agua).
- **Transversales:** `TipoSolicitud` (+2), `ActorSistema.ASISTENCIA`, `TipoAviso` (`SECCION_SIN_ASISTENCIA` a Dirección y
  `SOLICITUDES_PENDIENTES` a Promotoría), `AccionAuditoria`, `VerificadorPermisosBaseDatos`, `VerificadorConfiguracion` y
  la lista de puesta en marcha (A8), `ModuloApp` (asistencia solo DOCENTE; configuración y revertir solo DIRECTOR;
  `/familia/asistencia/**` y `/familia/avisos` APODERADO), `privacidad`, `application.yml` (`cuentasclaras.academico.*`).
- **Plantillas Thymeleaf** (skill `sistema-diseno`), `docs/entrega/plantillas-whatsapp-meta.md` (26 → 28),
  `docs/manuales/docente.md`, sección «Tanda 2 · Implementación» del diseño, `.github/workflows/ci.yml` (paso «Sprint 9,
  tanda 2» del job `mysql` y el caso del job `respaldo`).

## 8. Pruebas obligatorias
**H2 (`./mvnw -B verify`, también con `-DargLine=-Duser.timezone=America/Los_Angeles`):**
- `RegistroAsistenciaTest`: `soloElDiaDeHoy` (E1), `seccionAjenaResponde404` (E2), `direccionNoRegistra` (E49, 403),
  30 alumnos en un POST con una firma; `MarcaVigenteTest.laVigenteEsLaUltimaDeLaCadena` (E8).
- `CorreccionAsistenciaTest`: `despuesDelAvisoLaFamiliaRecibeLaCorreccion`, `quedaEnLaListaDeDireccion` (E5),
  `otroDiaExigeAprobacion` (E6), `despuesDelCierreVaPorSolicitud` (E52), registro tardío (E10), quien pide no aprueba.
- `JustificacionTest`: `otraFamiliaResponde404` (E11), `elDocenteNoRevierte` (E13), `fueraDePlazoSeRechaza` (E14),
  `loRevertidoNoSeJustifica` (E53), `elDocenteQueNoEsTutorNoVeLaNota` (E35). `ConsentimientosTest` (E38).
- `AvisosAsistenciaTest`: `unoPorFamiliaYDia`, `respetaElHorario` (E41), `sinConsentimientoVaPorCorreo` (E37),
  `laFaltaJustificadaTambienSeAvisa` (E51), `loDeDespuesDelUltimoEnvioSaleAlDiaSiguiente` (E52). `AsistenciaSinDeudaTest`
  (E39). `AlertasAcademicasTest`: sin registro y **registro incompleto** (A4), con los nombres de las secciones (E47).
- `AsignacionesTest.coberturaTerminaSola` y `coberturaDeMasDeCincoDiasHabilesSeRechaza` (E56).
  `ConfiguracionAcademicaTest.cambiarHorasQuedaResaltado` y `soloDireccionConFirma` (E15, E55).
  `VerificadorConfiguracionTest.avisaConMenosDeDosPersonasDeGobierno` y la alerta de solicitudes pendientes (A8).
- `PlantillasMensajeTest`, `PlantillasConColegioTest`, `PlantillasMetaDocTest`, `InmutabilidadAcademicoTest`,
  `DtoDocenteSinDatosTest`, **`MarcaPedidaSinFuenteTest`** (A15), `LogsSinDatosPersonalesTest` (E36),
  `ReglasArquitecturaTest` (sin cobranza; `AvisosAsistencia` solo lee `academico.api`; **nadie usa
  `FuenteAsistencia.MARCADOR`**, A15), `RutasIdorTest` + `CatalogoRutasConId` con las rutas nuevas **y los parámetros
  `?seccion=` y `?alumno=`** (A11).
- Pruebas que cuentan migraciones, triggers o la versión del esquema: 33 migraciones, 93 triggers, 106 huellas.

**MySQL real (`mysql/AuditoriaSprint9MySqlTest`, job `mysql`; correr local lo tocado antes del push). Todas 1644 salvo
que se diga otra cosa:**
- E1 (`e1RegistroDeAyerSinSolicitudFalla`, `e1RegistroDeMananaFalla`), E2, E3 (3), E6 (3), E9, E10 (3), E11, E12, E13,
  E14, E37 (3), E40 (2), E49, E50 (2).
- Auditoría en papel: `e51DocenteNoJustificaNiTocaLaJustificacion` (3: 3819 y 1644), `e52CorreccionDelDiaDespuesDelCierreFalla`,
  `e53NoSeRejustificaLoRevertido`, `e54SinFirmaNoSeRegistraNiCorrigeNiJustifica` (3), `e54MarcaInicialDeOtroFalla`,
  `e55ConfiguracionSinDireccionNiFirmaFalla` (INSERT y UPDATE), `e56CoberturaLargaFalla`, `e56CoberturaVencidaNoMarca`,
  `e57DirectorDocenteNoRevierteEnSuSeccion`, `e57CorreccionPedidaPorAjenoFalla`, **`a12ZonaDeVenceEnEsLima`**.
- `e7LaAsistenciaNoSeEditaNiSeBorra` (1142), `e8DosSucesorasDeLaMismaMarcaFallan` (1062),
  `e15HoraDeAvisoFueraDeRangoFalla` (3819); `aviso_asistencia_corte` con `cc_app` (1142); `hasta` con `cc_app` (1143).
- `PermisosMySqlTest`: flujo con permisos mínimos (registrar con firma, corregir antes del cierre, solicitud después,
  tardío, justificar con firma, consentimiento, configuración por Dirección, cobertura, aviso con marca de agua como
  `cc_sistema`).
- Paso `comprobar` con los casos nuevos; `huellas_objetos()` = 106; arranque prod por TLS con 93 y 13.
- Job `respaldo`: una `marca_asistencia` borrada como DBA en el colegio A marca solo a A.
- Carga: 40 secciones de 30 alumnos en el mismo minuto; cada guardado < 1 s en MySQL real (ahora con firma y
  `creado_por = registrado_por` en cada marca).

## 9. Riesgos
- **Docente ausente:** sin cobertura asignada, la sección queda sin asistencia; la alerta con el nombre de la sección
  (A7) le dice a Dirección qué cubrir. La cobertura necesita aprobación de otra persona: no es inmediata.
- **Menos de 2 personas de gobierno:** la segregación no se cumple; el sistema avisa (A8) pero no impide operar.
- **Marca de agua solo protegida por el GRANT** (`cc_sistema`); como el resto del outbox (residual aceptado).
- **Cierre a las 16:00:** un error visto tarde ya necesita solicitud; más carga para Dirección (medir en el piloto).
- **Rendimiento:** más subconsultas por marca (firma, configuración, registro); si no alcanza, agrupar los INSERT sin
  quitar controles.
- **H2 frente a MySQL:** los triggers solo existen en MySQL; cada control de la base necesita su prueba en el job `mysql`.
- **Un docente marca «presente» a quien faltó antes del aviso:** su palabra; la familia lo ve en el portal (residual).
- **Regex de cobranza:** un falso positivo deja el aviso sin crear; reintento con «su hijo(a)».
- **Meta** puede reclasificar `cc_asistencia_v2` como marketing; **PDFBox** (tanda 3) sin antivirus.

## 10. Terminado cuando
El docente registra su sección desde el celular en menos de 1 minuto; Dirección no puede hacerlo ni con la clave de
`cc_app`; el apoderado con consentimiento recibe el aviso a la hora fija (con las faltas justificadas incluidas); lo que
cambie después del último envío le llega al día siguiente; una falta de ayer no cambia sin la firma de otra persona; una
familia con deuda recibe el mismo aviso que una al día; y el CI del PR en borrador está en verde.
