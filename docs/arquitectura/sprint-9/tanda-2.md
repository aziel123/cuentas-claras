# Sprint 9 · Tanda 2 · Asistencia diaria con aviso a las familias (brief)

> Léase primero. Diseño completo: `docs/arquitectura/sprint-9-asistencia-comunicados.md` (secciones 3.4 a 3.6, 4, 5, 6
> «V33», 7.2, 7.3 «Tanda 2», 7.4, 7.5, 9, 10, 12, 13.1, 13.3 y 14 «Tanda 2»). Rama `claude/sprint-9-asistencia-comunicados`,
> PR en borrador: **la tanda está terminada cuando el CI del PR está en verde.** Plazo: 1.5 semanas.

## 1. Decisiones del usuario que cambian el diseño (21-oct-2026)
1. **La asistencia la toma el DOCENTE, solo en sus propias secciones** (decisión 144 confirmada). Rol DOCENTE con
   asignación VIGENTE en la sección (tutor, docente o auxiliar). **Dirección no registra ni corrige marcas directamente**:
   aprueba solicitudes, revierte justificaciones (con firma) y ve todo. Para cubrir a un docente ausente se propone una
   asignación a otra persona (flujo de la tanda 1). El diseño ya no da a Dirección la rama `r.rol = 'DIRECTOR'` en
   `trg_registro_asistencia_nace` ni en `v_es_personal` de `trg_marca_asistencia_nace`, y la matriz quita la X de DIR en
   `/hoy`, `corregir-hoy`, `pedir-correccion` y `pedir-registro-tardio`.
2. **Puerta abierta al marcador dactilar, sin implementarlo:** columna `fuente VARCHAR(10) NOT NULL DEFAULT 'DOCENTE'` en
   `registro_asistencia` y en `marca_asistencia`, con `CHECK (fuente IN ('DOCENTE', 'MARCADOR'))`. Los dos triggers
   rechazan toda fuente distinta de `DOCENTE` («el marcador todavia no esta habilitado»). Enum Java `FuenteAsistencia`
   con los dos valores; ningún servicio, ruta ni actor de sistema escribe `MARCADOR`. Ojo: `origen` ya existe en las dos
   tablas con otro significado (DEL_DIA/TARDIO y REGISTRO/CORRECCION_*): por eso la columna nueva se llama `fuente`.
3. **PDFBox aprobado** (decisión 157). No entra código de PDF en esta tanda (los adjuntos son de la tanda 3, bloque 2),
   pero los controles de la sección 6 de este brief quedan fijados como contrato y la tanda 3 no puede relajarlos.

## 2. Alcance
- V33: `configuracion_academica`, `registro_asistencia`, `marca_asistencia`, `consentimiento_aviso`; tipos de mensaje
  `ASISTENCIA_DIA` y `ASISTENCIA_CORREGIDA`; índice `ix_mensaje_tipo_fecha`.
- Hoja del día en el celular (todos «Presente», un toque por alumno, un POST), corrección del mismo día, reporte mensual.
- Corrección de otro día (`CORRECCION_ASISTENCIA`) y registro tardío (`REGISTRO_ASISTENCIA_TARDIO`) por la bandeja.
- Portal: `/familia/asistencia` (mes de cada hijo, justificar una FALTA de los últimos 5 días hábiles) y `/familia/avisos`
  (consentimientos por finalidad y canal). Consentimiento desde la ficha firmada (Administración o Dirección).
  Reversión de una justificación por Dirección con firma.
- `comunicacion.proceso.AvisosAsistencia` (`sistema.asistencia`): un aviso por familia y día a la hora fija (10:30) y el
  segundo envío (16:00) solo con lo que cambió; plantillas `cc_asistencia_v2` y `cc_asistencia_corregida_v2`.
- `AlertasAcademicas`: «sección sin asistencia» a Dirección a la hora de alerta (09:30), con el número, sin nombres.
- «Mis datos», «Datos con plazo vencido», `respaldar.sh` y `tablas-vigiladas.txt` con las tablas nuevas.
- **Fuera de alcance:** comunicados, PDF, SIAGIE (tanda 3); marcador dactilar; modo sin conexión; justificar tardanzas;
  exportar el reporte.

## 3. Migración `V33__asistencia_y_consentimientos.sql`
- El SQL está en la sección 6 del diseño, **ya con la columna `fuente`** y sus CHECK. No toca tablas de dinero.
- Sin migración de datos: nadie recibe WhatsApp de asistencia hasta dar su consentimiento; el correo sale salvo oposición.
- `configuracion_academica` sin fila = valores por defecto (los de la tabla).
- **Comprobar en H2 2.4.240 MODE=MySQL y en MySQL 8.4:** `TIME` y sus literales en los CHECK; la FK de `marca_asistencia`
  consigo misma (`fk_marca_asistencia_reemplaza`); `DROP CONSTRAINT ck_mensaje_tipo` con la lista completa de tipos de
  V31 (copiarla de V31, no del diseño, si difieren); que `ix_mensaje_tipo_fecha` no duplique un índice existente.
- Recordar la lección de V32: un CHECK que evalúa NULL pasa. Revisar `primera`, `reemplaza_id`, `solicitud_id` y
  `motivo` con un caso NULL en cada CHECK (por ejemplo `CORRECCION_DEL_DIA` con `motivo` NULL debe fallar).
- Entidades `RegistroAsistencia`, `MarcaAsistencia` y `ConsentimientoAviso` con todos los campos `updatable = false`.

## 4. MySQL: permisos, triggers y huellas
- `01-usuarios.sql`: sin cambios. Sin `04-una-vez`.
- `02-permisos-tablas.sql`: bloque «Sprint 9 · tanda 2» de la sección 7.2: `INSERT` solo en `registro_asistencia`,
  `marca_asistencia` y `consentimiento_aviso` (UPDATE/DELETE = 1142); `INSERT, UPDATE (columnas)` en
  `configuracion_academica`. `mensaje` sin cambios.
- `03-triggers.sql` (sección 7.3 «Tanda 2», con los cambios de la decisión 144):
  - funciones nuevas `cc_hoy_lima()` y `cc_sesion_abierta(sesion, nombre, colegio)`;
  - triggers nuevos `trg_registro_asistencia_nace`, `trg_marca_asistencia_nace`, `trg_consentimiento_aviso_nace`;
  - versión nueva de `trg_mensaje_nace` (bloque de asistencia antes de las reglas del contacto verificado; la regla de
    cobranza y el consentimiento; la plantilla `cc_asistencia%` no sirve para otro tipo).
- **Conteo esperado:** 88 → **91 triggers**; funciones con huella 11 → **13** (10 de `03` + 3 de `02`); huellas 99 →
  **104**. Arranque en prod: «Huellas de los 91 triggers y las 13 funciones verificadas».
- `VerificadorPermisosBaseDatos`: `TRIGGERS_ESPERADOS` con los 3 nuevos; casos 1142 nuevos (`UPDATE registro_asistencia`,
  `DELETE FROM marca_asistencia`, `UPDATE consentimiento_aviso`, con `WHERE 1 = 0`); inserciones imposibles nuevas (1644):
  un `registro_asistencia` del colegio 0 con fecha de ayer y `DEL_DIA`, y un `mensaje` `ASISTENCIA_DIA` de
  `sistema.asistencia` con «S/ 350.00» en los parámetros.
- Comprobar en MySQL: la ventana de 1 hora del registro tardío (`registrado_bd >= UTC − 6 h`) y la zona de
  `sesion_usuario.vence_en`; que el trigger de INSERT de `marca_asistencia` puede LEER su propia tabla; `REGEXP_LIKE` con
  `\b` e `'i'` en ICU, con «Pagán» y «Vencesla» (no deben coincidir). `cc_solicitud_firmada` ya exige
  `resuelto_por <> solicitado_por`: no tocarla.

## 5. Segregación de funciones («quien cobra no aprueba», en su versión académica)
La asistencia no mueve dinero, pero la regla se aplica igual a quien pide y quien aprueba:
- quien pide una `CORRECCION_ASISTENCIA` o un `REGISTRO_ASISTENCIA_TARDIO` no la aprueba (`cc_solicitud_firmada` ya exige
  `resuelto_por <> solicitado_por`); aprueba otra persona activa de Dirección o Promotoría con la firma de su sesión;
- el docente no aprueba nada ni ve la bandeja; un director que también es DOCENTE no aprueba lo que él pidió;
- quien registra (el docente) no es quien revierte justificaciones (Dirección, con firma `marca_asistencia:{id}:REVERTIDA`);
- Caja y Administración no tienen ninguna ruta de asistencia (salvo Administración: consentimiento desde la ficha) y
  DOCENTE no se combina con CAJA ni ADMINISTRACION (tanda 1, `trg_usuario_rol_alta`);
- INDECOPI: `academico` no depende de cobranza; el aviso de una familia con deuda es idéntico al de una al día.

## 6. PDFBox: controles obligatorios (contrato para la tanda 3; nada se implementa en esta tanda)
Decisión 157 confirmada. `comunicados.service.AdjuntosComunicado` es la única clase que usa `org.apache.pdfbox` (ArchUnit).
1. **Versión:** la última 3.x estable (hoy 3.0.8; nunca anterior a 3.0.7, por CVE-2026-23907), fijada en el `pom.xml`.
   El job **Dependencias** del CI (SBOM CycloneDX + OSV-Scanner) debe listarla en el SBOM y pasar sin CRÍTICA ni ALTA
   con arreglo; Dependabot la sigue. Confirmar la versión en pdfbox.apache.org el día que se agregue.
2. **Tamaño máximo** 2 MB, validado antes de abrir el archivo (además del límite de subida de Spring).
3. **Bytes mágicos:** debe empezar con `%PDF-`; rechazo de políglotos (ZIP, HTML o JavaScript en el primer kilobyte).
4. **Límite de páginas:** 10.
5. **Memoria acotada:** `Loader.loadPDF(..., () -> MemoryUsageSetting.setupMixed(...))` con tope de unos 50 MB y archivo
   temporal en `/tmp` (tmpfs en el contenedor); nunca `setupMainMemoryOnly()` sin tope.
6. **Tiempo límite** de 10 segundos en un hilo aparte que se cancela; al vencer, rechazo y evento.
7. **Rechazo** (no limpieza) de PDF cifrados o con contraseña y de JavaScript o acciones embebidas: `OpenAction`, `AA`,
   `Launch`, `SubmitForm`, `ImportData`, `GoToR`, `GoToE`, archivos embebidos, `RichMedia`, `AcroForm`/XFA y `URI` ajenas.
8. **SHA-256** del original y del saneado (re-guardado completo con PDFBox), en `comunicado_adjunto` y en el evento.
9. **Descarga** con `Content-Type: application/pdf`, `Content-Disposition: attachment; filename="comunicado-{id}.pdf"`,
   `X-Content-Type-Options: nosniff`, `Content-Security-Policy: sandbox; default-src 'none'`, `Cache-Control: private,
   no-store`.
10. **Pruebas obligatorias** (`AdjuntoComunicadoTest`, archivos en `src/test/resources/hostiles/pdf/`): como mínimo un
    **PDF malformado**, un **PDF enorme** (más de 2 MB, y otro de 5,000 páginas o bomba de objetos que debe cortar por
    páginas, memoria o tiempo sin tumbar la JVM) y un **PDF con JavaScript**; más los demás de E26 (cifrado, `Launch`,
    embebido, XFA, políglotos, extensión falsa) y una prueba web de las cabeceras.

## 7. Archivos que toca
- **Base:** `src/main/resources/db/migration/V33__asistencia_y_consentimientos.sql`; `scripts/mysql/02-permisos-tablas.sql`;
  `scripts/mysql/03-triggers.sql` (encabezado con «Versión anterior (V32): 88 triggers y 8 funciones»);
  `scripts/respaldo/tablas-vigiladas.txt` y `scripts/respaldo/respaldar.sh` (conteo por colegio de las 3 tablas nuevas).
- **`academico`:** `model` (`RegistroAsistencia`, `MarcaAsistencia`, `EstadoAsistencia`, `OrigenMarca`, `FuenteAsistencia`,
  `MotivoJustificacion`, `ConfiguracionAcademica`, `ConsentimientoAviso`, `Finalidad`, `Canal`); `repository` sin `delete*`
  ni `save` de existentes; `service` (`RegistroAsistencia` o `HojaAsistencia` si choca el nombre con la entidad,
  `ManejadorCorreccionAsistencia`, `ManejadorRegistroTardio`, `JustificacionesFamilia`, `ReportesAsistencia`,
  `AlertasAcademicas`, `ConsentimientosAviso`, `ConfiguracionAcademicaServicio`); `api` (`InasistenciasDelDia`,
  `Consentimientos`); `dto` sin campos de documento, contacto, apoderado ni dinero; `web` (`AsistenciaController`,
  `PortalAsistenciaController`, `ConfiguracionAcademicaController`).
- **`comunicacion`:** `model.TipoMensaje` (+2), `model.PlantillaMensaje` (+2), `proceso.AvisosAsistencia`;
  `ConsumoMensajes` solo si cabe (si no, tanda 3).
- **Transversales:** `aprobaciones.model.TipoSolicitud` (+2), `comun.sistema.ActorSistema` (`ASISTENCIA`),
  `comun.alertas.TipoAviso` (`SECCION_SIN_ASISTENCIA`, destinatario Dirección), `auditoria.model.AccionAuditoria`
  (eventos de la sección 12), `auditoria.service.VerificadorPermisosBaseDatos`, `seguridad.config.ModuloApp`
  (rutas de asistencia solo DOCENTE; `/academico/configuracion` y revertir solo DIRECTOR; `/familia/asistencia/**` y
  `/familia/avisos` APODERADO), `privacidad` (tipo `REPORTE_ASISTENCIA`; «Mis datos»; plazo vencido de la nota),
  `application.yml` (`cuentasclaras.academico.*` de la sección 8; sin variables de entorno nuevas).
- **Plantillas Thymeleaf:** hoja del día (celular), reporte, configuración, portal asistencia y avisos (skill `sistema-diseno`).
- **Docs:** `docs/entrega/plantillas-whatsapp-meta.md` (26 → 28; `PlantillasMetaDocTest`), `docs/manuales/docente.md`
  (asistencia en un minuto), sección «Tanda 2 · Implementación» del diseño.
- **CI:** `.github/workflows/ci.yml`, paso nuevo del job `mysql` «Sprint 9, tanda 2» y el caso del job `respaldo`.

## 8. Pruebas obligatorias
**H2 (`./mvnw -B verify`, también con `-DargLine=-Duser.timezone=America/Los_Angeles`):**
- `RegistroAsistenciaTest`: `soloElDiaDeHoy` (E1), `seccionAjenaResponde404` (E2), `direccionNoRegistra` (E49, 403),
  guardar 30 alumnos en un POST; `MarcaVigenteTest.laVigenteEsLaUltimaDeLaCadena` (E8).
- `CorreccionAsistenciaTest`: `despuesDelAvisoLaFamiliaRecibeLaCorreccion` y `quedaEnLaListaDeDireccion` (E5),
  `otroDiaExigeAprobacion` (E6), registro tardío por la bandeja (E10), quien pide no aprueba.
- `JustificacionTest`: `otraFamiliaResponde404` (E11), `elDocenteNoRevierte` (E13), `fueraDePlazoSeRechaza` (E14),
  `elDocenteQueNoEsTutorNoVeLaNota` (E35). `ConsentimientosTest`: `desdeFichaExigeEvidencia`, `laFamiliaLoRevoca` (E38).
- `AvisosAsistenciaTest`: `unoPorFamiliaYDia` (dos hermanos, un mensaje) y `respetaElHorario` (E41),
  `sinConsentimientoVaPorCorreo` (E37). `AsistenciaSinDeudaTest` (E39). `AlertasAcademicasTest` (E47).
  `ConfiguracionAcademicaTest.cambiarHorasQuedaResaltado` (E15).
- `PlantillasMensajeTest` y `PlantillasConColegioTest` (las 2 nuevas sin palabras de cobranza; misma lista que el
  trigger vía `ContenidoSinCobranza`), `PlantillasMetaDocTest`, `InmutabilidadAcademicoTest` (las 3 entidades nuevas),
  `DtoDocenteSinDatosTest` (DTO nuevos), `LogsSinDatosPersonalesTest` (guardar y justificar, E36),
  `ReglasArquitecturaTest` (academico sin cobranza ni comunicacion; `AvisosAsistencia` solo lee `academico.api`),
  `RutasIdorTest` + `CatalogoRutasConId` con las rutas nuevas con id (3 colegios y otra familia).
- Pruebas que cuentan migraciones, triggers o la versión del esquema en el manifiesto del respaldo: actualizar a 33/91.

**MySQL real (`mysql/AuditoriaSprint9MySqlTest`, job `mysql`; correr local lo que se tocó antes del push):**
- `e1RegistroDeAyerSinSolicitudFalla`, `e1RegistroDeMananaFalla`, `e2SeccionAjenaFalla`, `e3AsignacionNoVigenteNoMarca`
  (3 casos), `e6CorreccionDeOtroDiaSinSolicitudFalla`, `e6ConLaSolicitudDeOtraMarcaFalla`, `e6ConOtroEstadoQueElAprobadoFalla`,
  `e9ElDiaLoDecideLaBase`, `e10RegistroTardioExigeSuSolicitud` (3), `e11JustificarAlumnoAjenoFalla`,
  `e12JustificacionSinLaSesionDelApoderadoFalla`, `e13ReversionSinFirmaFalla`, `e14JustificacionDeHaceUnMesFalla`,
  `e37WhatsAppSinConsentimientoFalla`, `e37CorreoConOposicionFalla`, `e37ContactoSinVerificarFalla`,
  `e40AvisoDeAsistenciaConMontoFalla`, `e40RecordatorioConPlantillaDeAsistenciaFalla`,
  **`e49DireccionSinAsignacionNoRegistraNiCorrige`** y **`e50FuenteMarcadorSeRechaza`** (2): todos 1644.
- `e7LaAsistenciaNoSeEditaNiSeBorra` (1142), `e8DosSucesorasDeLaMismaMarcaFallan` (1062),
  `e15HoraDeAvisoFueraDeRangoFalla` (3819); la ventana de 1 hora del tardío; «Pagán» y «Vencesla» no disparan la regex.
- `PermisosMySqlTest`: flujo con permisos mínimos (registrar el día, corregir el mismo día, corregir otro día con
  solicitud, registro tardío, justificar desde el portal, dar el consentimiento, crear el aviso como `cc_sistema`).
- Paso `comprobar` con los casos 1142 y 1644 nuevos; `huellas_objetos()` = 104; arranque prod por TLS con 91 y 13.
- Job `respaldo`: borrar como DBA una `marca_asistencia` del colegio A marca solo a A en «faltan filas».
- Carga: 40 secciones de 30 alumnos guardando en el mismo minuto; cada guardado < 1 s en MySQL real.

## 9. Riesgos
- **Docente ausente sin reemplazo asignado:** la sección queda sin asistencia (Dirección ya no la cubre). Mitigan la
  alerta de las 09:30 y la asignación de un AUXILIAR de respaldo; si el colegio lo pide, se reabre la decisión 144.
- **Colegio con un solo directivo:** las correcciones y los tardíos necesitan a otra persona (Promotoría) que apruebe.
- **Un docente marca «presente» a quien faltó antes del aviso:** es su palabra; lo ve la familia en el portal (residual).
- **Regex de cobranza** con nombres reales: un falso positivo deja el aviso sin crear; reintento con «su hijo(a)».
- **Rendimiento:** 30 triggers por guardado con subconsultas a `asignacion_docente`, `matricula` y `sesion_usuario`; si
  la carga no pasa, agrupar el INSERT de marcas (sin quitar ningún control del trigger).
- **H2 frente a MySQL:** los triggers solo existen en MySQL; todo control de la base necesita su prueba en el job `mysql`.
- **Consentimiento desde ficha** registrado sin firma real de la familia: evidencia, alerta por volumen y revocación.
- **Meta** puede reclasificar `cc_asistencia_v2` como marketing (costo); se mide en el piloto.
- **PDFBox** (tanda 3): un fallo de la librería o del visor del celular no se descarta sin antivirus; la versión al día
  y el job Dependencias son el control.

## 10. Terminado cuando
El docente registra su sección desde el celular en menos de 1 minuto; Dirección no puede hacerlo ni por la aplicación ni
con la clave de `cc_app`; el apoderado con consentimiento recibe el aviso ese día a la hora fija con sus hijos juntos;
una corrección posterior le llega también; una falta de ayer no cambia sin la firma de otra persona de Dirección o
Promotoría; una familia con deuda recibe el mismo aviso que una al día; y el CI del PR en borrador está en verde.
