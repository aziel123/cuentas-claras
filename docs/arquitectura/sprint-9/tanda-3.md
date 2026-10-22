# Sprint 9 · Tanda 3 · Comunicados con confirmación de lectura, PDF saneado y nómina para el SIAGIE (brief)

> Léase primero. Diseño completo: `docs/arquitectura/sprint-9-asistencia-comunicados.md` (secciones **3.8 a 3.11**, 3.13,
> 3.14, **3.16**, 6 «V34», 7.2 y 7.3 «Tanda 3», el bloque de `trg_mensaje_nace` de 7.3, 7.4, 7.5, 8, 9, 10, 11, 12, 13.2,
> E33, E34, E36, E42 a E45 y E48, 14 «Tanda 3» y 15). Rama `claude/sprint-9-asistencia-comunicados`, PR en borrador: **la
> tanda está terminada cuando el CI del PR está en verde.** Incluye los **16 hallazgos de la auditoría en papel sobre
> `021d693`** (B1 a B16, tabla de la sección 3.16 del diseño).

## 1. Decisiones y contexto
1. **PDFBox aprobado** (decisión 157), con los controles de 3.9, A9, A10 y A14, y **en un proceso hijo** (B5, sección 6).
2. **La asistencia la toma el DOCENTE** (decisión 144); el auxiliar marca, pero **no envía comunicados** (decisión 140).
   Dirección no marca, tampoco en la demo: el paso guiado «Marca la asistencia de 3.° B» usa la cuenta `xxxx.docente`.
3. Decisiones 154 a 156 y 158 a 168 con su valor por defecto. D12 (plantilla del SIAGIE): si no llegó, perfil provisional.

## 2. Alcance
- V34, `02` y `03` de la tanda 3 con los ajustes de las secciones 3 y 4.
- **Comunicados** (3.8): Promotoría y Dirección a colegio, nivel, grado o sección; docente solo a sus secciones, sin urgente.
  Emisor y etiqueta desde la sesión, `FiltroContenido` sin excepciones (título, texto y texto del PDF), pie fijo, envío con
  firma, destinatarios congelados, retiro con firma. **Fe de erratas sin destinatarios propios: llega a TODOS los del
  original** (B1); la de un RETIRADO, solo Dirección o Promotoría (B9).
- **PDF** (3.9 y sección 6): analizado en un proceso hijo. Va por `PUBLICAR_COMUNICADO` el de un docente y **todo PDF con
  poco texto o con imágenes** (B3), con aprobación de otra persona.
- **Portal** (3.10): `/familia/comunicados`, abrir y «Confirmo que lo leí» con su sesión; estadísticas (pendientes con el
  nombre del alumno, solo con asignación VIGENTE, B16).
- **Avisos** (`AvisosComunicados`): resumen diario al contacto principal con tope mensual, correo a los demás, urgentes
  (07:00–20:00), recordatorio a las 48 h; `cc_comunicados_v2` y `cc_comunicado_urgente_v2`; consumo en `/panel/sistema`;
  la familia elige sus 2 apoderados de asistencia y el contacto principal (desviación 7 de la tanda 2).
- **SIAGIE** (3.11): sexo y código de estudiante, códigos modulares, «Datos que faltan», perfil provisional y `.xlsx` por
  sección (solo Dirección, con trigger, B14). Demo (3.14), plantillas de Meta (28 → 30), manuales, `estado-del-proyecto.md`.
- **Fuera de alcance:** antivirus, citaciones a una familia, llenar el `.xls` del SIAGIE, editar un comunicado enviado.

## 3. Migración `V34__comunicados_y_nomina_siagie.sql`
- SQL de la sección 6 del diseño, con estos cambios (B13 a B15 y la lección de V32: un CHECK que evalúa NULL pasa):
  - `ck_mensaje_tipo`: la lista de V33 tal cual más `COMUNICADOS_RESUMEN` y `COMUNICADO_URGENTE`;
  - `ck_alumno_codigo_estudiante` con `'c'` en el `REGEXP_LIKE` (B15);
  - `comunicado`: **`uk_comunicado_huella UNIQUE (colegio_id, huella_contenido)`** (B2); `ck_comunicado_alcance` con
    `nivel IS NOT NULL` y la lista de `ck_seccion_grado`; `ck_comunicado_estado` con `motivo_retiro IS NOT NULL` en RETIRADO
    y **`enviado_en IS NULL AND destinatarios IS NULL` en POR_APROBAR y RECHAZADO** (B13);
  - `comunicado_adjunto.requiere_revision BOOLEAN NOT NULL` (B3: lo calcula el analizador; sección 6);
  - `exportacion_nomina.sesion_id BIGINT NOT NULL` con FK compuesta a `sesion_usuario` (B14).
- Una prueba con el NULL de cada CHECK (`nivel`, `grado`, `motivo_retiro`, `corrige_id`, `enviado_en`, `destinatarios`).
- **Comprobar en H2 2.4.240 MODE=MySQL y MySQL 8.4:** varios NULL en `uk_alumno_codigo_estudiante`; `MEDIUMBLOB` de 2 MB;
  la FK de `confirmacion_lectura` a `(comunicado_id, familia_id)`; la FK de `comunicado` consigo misma; `ADD COLUMN` y
  `ADD CONSTRAINT` sobre `alumno` con filas.
- Entidades de solo inserción (`ComunicadoAdjunto`, `ConfirmacionLectura`, `ExportacionNomina`) con `updatable = false`,
  `@PreUpdate` y `@PreRemove` que fallan; en `Comunicado`, solo las columnas del GRANT; `@TenantId` en todas.

## 4. MySQL: permisos, triggers y huellas
- `01` sin cambios, sin `04-una-vez`. `02`: bloque «Sprint 9 · tanda 3» (7.2); **título, texto, alcance, emisor, urgente,
  `requiere_confirmacion`, `corrige_id`, `adjunto_id`, `sesion_id` y `huella_contenido` fuera del UPDATE** (1143).
- `trg_comunicado_nace` (7.3 y ajustes):
  - **recalcula la huella** (B2): `SHA2(CONCAT_WS(CHAR(10), alcance, IFNULL(nivel,''), IFNULL(grado,''),
    IFNULL(seccion_id,''), titulo, cuerpo, IFNULL(sha256 del adjunto,'')), 256)` y la compara con `huella_contenido`. Java
    (`HuellaComunicado`) arma la misma cadena en UTF-8, con `CRLF` → `LF` y título sin saltos de línea; una prueba compara;
  - **rechaza los caracteres de formato** (`REGEXP_LIKE(CONCAT(titulo, cuerpo), '\\p{Cf}')`, ancho cero, B4) y aplica la
    expresión de cobranza a título y texto (segunda capa de `FiltroContenido`);
  - firma `comunicado:{huella40}:ENVIAR` **también en POR_APROBAR**; el adjunto lo subió quien envía;
    **`enviado_en` a ±5 minutos** de `UTC_TIMESTAMP(6) - INTERVAL 5 HOUR` (B13);
  - **adjunto con `requiere_revision` ⇒ POR_APROBAR**, para cualquier emisor (B3); POR_APROBAR de Dirección o Promotoría solo
    en ese caso; el docente con adjunto, siempre POR_APROBAR;
  - fe de erratas: original ENVIADO (mismo emisor, Dirección o Promotoría) o **RETIRADO solo por Dirección o Promotoría** (B9).
- `trg_comunicado_estado`: aprobación por **el otro órgano** (B3): de un docente, PROMOTOR o DIRECTOR; de Dirección, PROMOTOR;
  de Promotoría, DIRECTOR; siempre otra persona (`cc_solicitud_firmada`) y `enviado_en` a ±5 min; **un retiro no se
  reescribe**; **`destinatarios` se escribe una vez y debe ser igual a las filas de `comunicado_destinatario`** y, en una fe
  de erratas, **a los destinatarios del original** (B1).
- `trg_comunicado_destinatario_nace`: además, nada nace si `comunicado.destinatarios` ya está escrito (lista cerrada).
  `trg_confirmacion_lectura_nace`, como en el diseño.
- **Nuevo `trg_exportacion_nomina_nace`** (B14): `creado_por` con DIRECTOR activo y `cc_sesion_abierta(sesion_id, …)`.
- **Versión nueva de `trg_mensaje_nace`:** partir del bloque REAL de la tanda 2 (con el respaldo de `sistema.mensajeria`):
  - suma los 2 tipos de comunicados (`sistema.comunicados`, APODERADO, `cc\_comunicado%`, finalidad `COMUNICADOS`); el
    `ELSEIF` cubre `cc\_asistencia%` y `cc\_comunicado%`;
  - **B6, para los 4 tipos académicos:** `parametros` empieza con `CONCAT(c.nombre, CHAR(10))` (comparación binaria con el
    nombre del colegio) y la regla de cobranza corre **solo sobre el resto**
    (`SUBSTRING(parametros, CHAR_LENGTH(c.nombre) + 2)`). Si el nombre de una persona calza con la regla, la etiqueta pasa a
    genérica («su hijo(a)», «su docente de 3.° B»); el alta y el cambio de nombre del colegio avisan si el nombre calza;
  - **expresión nueva (B7)**, igual en `03` y en `ContenidoSinCobranza`: «S/» solo como `(^|[^a-z])s/\.?\s*[0-9]`, y los
    decimales sin las horas (`7.45 a.m.` pasa); copiar con `\\` en `03` (desviación 5 de la tanda 2);
  - **el pie fijo no va en los parámetros** (dice «pagos»): va en la plantilla.
- **Conteo:** 93 → **98 triggers** (4 de comunicados + `trg_exportacion_nomina_nace`); funciones **13** sin cambio; huellas
  106 → **111**. Prod: «Huellas de los 98 triggers y las 13 funciones verificadas». Encabezado de `03`: «Versión de la
  migración V34 (sprint 9, tanda 3): 98 triggers y 10 funciones (13 con huella; 111 huellas)».
- `VerificadorPermisosBaseDatos`: los 5 triggers; 1142 (`UPDATE` de `comunicado_adjunto`, `confirmacion_lectura`,
  `exportacion_nomina`; `DELETE FROM comunicado`); 1143 (`titulo`, `cuerpo`, `adjunto_id`, `huella_contenido`,
  `comunicado_destinatario.familia_id`); 1644 (comunicado de Dirección del colegio 0; nómina sin sesión; con `cc_sistema`, un
  resumen con «S/ 350.00»).

## 5. Segregación de funciones
- **Quien escribe no aprueba:** PDF de docente o PDF sin texto: lo aprueba otra persona; el de Dirección, Promotoría, y el de
  Promotoría, Dirección (B3). Con un solo órgano activo, ese PDF no sale (exportarlo con texto o sin adjunto).
- **Nadie se hace pasar por Dirección** (E16, E17); **quien envía no confirma** (E23 a E25); **retirar no borra** (E28).
- **Quien vigila no es quien actúa** (B10): las alertas de nómina masiva y de `COMUNICADO_RETIRADO` van a **Promotoría**.
- **El docente sin asignación VIGENTE** no ve sus estadísticas por alumno (B16). **Nómina solo Dirección**, con su sesión
  (B14). **INDECOPI:** `comunicados` no depende de cobranza; deuda o no, el mismo trato (E39); nunca a una familia sola (E29).

## 6. PDF: proceso hijo y controles (decisión 157, A9, A10, A14, B3, B5, B8 y B12)
- **Analizador aparte** (B5): `comunicados.analizador.AnalizadorPdf` (un `main` sin Spring) es lo único que usa
  `org.apache.pdfbox` (ArchUnit: ese paquete no depende de nada de la aplicación). `AdjuntosComunicado` (padre) no abre PDF.
- **Lanzamiento** (`LanzadorAnalizador`, `ProcessBuilder`): `java -Xmx128m -Xss512k -XX:MaxMetaspaceSize=64m
  -XX:ReservedCodeCacheSize=32m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError
  -Djava.io.tmpdir={dir} -Djava.security.manager=allow -cp /app/app.jar -Dloader.main=…AnalizadorPdf
  org.springframework.boot.loader.launch.PropertiesLauncher {dir}`. Fuera del jar (pruebas, `dev`), `-cp` es el
  `java.class.path` del padre. **Entorno vacío** (`environment().clear()`: sin `DB_*`, sin `JAVA_TOOL_OPTIONS`).
- **Intercambio por archivos** en `/tmp/cc-pdf/{uuid}/` (tmpfs, permisos 700): `entrada.pdf`; el hijo escribe
  `saneado.pdf`, `texto.txt` (tope 200 KB) y `resultado.properties` (páginas, motivo de rechazo, caracteres por página,
  imágenes); el padre lee con topes, **recalcula los SHA-256** y borra el directorio en `finally` (al arrancar, borra restos).
- **Sin red:** el hijo instala una `Policy` propia y un `SecurityManager` (Java 21) que solo permite leer el jar y
  `java.home` y escribir en `{dir}`: ningún `SocketPermission`. Con el entorno vacío, el hijo no tiene credenciales.
- **Tiempo:** `waitFor(10, SECONDS)`; al vencer, `descendants()` y el propio `destroyForcibly()` **por su PID** (nunca por
  nombre), con el PID en el log. Salidas: 0 aceptado, 2 rechazado, 3 sin memoria, otra «no se pudo revisar».
- **Concurrencia y memoria:** 1 hijo a la vez (executor de 1 hilo, cola de 4, `AbortPolicy`: «intente en unos minutos»),
  **como máximo 1 tarea por usuario y 2 por colegio** en cola o en curso. `setupMixed(48 MB, 64 MB)` dentro del hijo;
  tmpfs `size=256m`. `JAVA_TOOL_OPTIONS` de la aplicación de 75 % a **55 %** de `mem_limit` (1400m) para dejar lugar al hijo.
- **Límite de subidas desde la bitácora** (B12): evento `ADJUNTO_REVISADO` (resultado y SHA-256, sin texto) por intento;
  más de **10 por usuario o 30 por colegio** en la última hora: rechazo sin lanzar el hijo.
- **Controles del hijo:** versión **3.0.8** (nunca anterior a la 3.0.7; SBOM, OSV-Scanner, Dependabot, `VersionPdfBoxTest`);
  ≤ 2 MB, `%PDF-` en el byte 0, sin políglotos (ZIP, `<html`, `<script`, `<svg` en el primer KB); ≤ 10 páginas; cifrado o
  dañado: rechazo; **lista de rechazo completa** (A14: JavaScript, `OpenAction`, `AA`, `/A` y `/AA` en anotaciones,
  `Launch`, `SubmitForm`, `ImportData`, `ResetForm`, `Hide`, `Named`, `GoToR`, `GoToE`, `Rendition`, `Sound`, `Movie`,
  `RichMedia`, embebidos, `AcroForm` y XFA, URI fuera de la lista permitida) y además **toda anotación que no sea `Link` y
  las capas OCG** (B3); captura `StackOverflowError`; **re-guardado completo sin `Info`, sin XMP y sin revisiones**, que se
  vuelve a abrir y revisar. **`requiere_revision`** si tiene menos de 50 caracteres extraíbles por página o imágenes (B3).
- **Descarga:** `application/pdf`, **`Content-Disposition: attachment; filename="comunicado-{id}.pdf"`**, **`nosniff`**,
  `Content-Security-Policy: sandbox; default-src 'none'`, `Cache-Control: private, no-store`. **De un comunicado que no
  está ENVIADO, 404** (B8), salvo para quien puede aprobarlo desde la bandeja. Demo: solo el PDF de ejemplo.

## 7. Filtro de contenido (B4 y B7)
`FiltroContenido` normaliza antes de revisar: **NFKC**, quita `\p{Cf}` y los acentos combinados (`\p{M}` tras NFD), mapea
los caracteres parecidos a latinos (cirílico y griego: а→a, е→e, о→o, р→p, с→c…) y pasa los dígitos a ASCII; luego, sus
reglas y `ContenidoSinCobranza`, que devuelve un **motivo claro** («menciona un monto en soles», «menciona una deuda», …).

## 8. Archivos que toca
- **Base:** V34, `02`, `03` (encabezado) y `tablas-vigiladas.txt` (+`comunicado`, `comunicado_adjunto`,
  `comunicado_destinatario`, `confirmacion_lectura`, `exportacion_nomina`). **`pom.xml`:** `pdfbox` 3.0.8.
- **`comunicados`:** `model`, repositorios sin `delete*`, `analizador` (`AnalizadorPdf`, su `Policy`), `service`
  (`FiltroContenido`, `HuellaComunicado`, `AdjuntosComunicado`, `LanzadorAnalizador`, `EnvioComunicados`,
  `ManejadorPublicarComunicado`, `EtiquetaEmisor`, `LecturaFamilia`, `EstadisticasComunicado`, `IntegridadComunicados`, que
  además **compara los destinatarios con las matrículas ACTIVAS del alcance y alerta si no coinciden**, B11), `api`, `dto`,
  `web`. **`comunicacion`:** `TipoMensaje` y `PlantillaMensaje` (+2), `AvisosComunicados`, `ConsumoMensajes`.
- **`academico`:** `CodigoModular`, `ExportacionNomina`, `siagie.*` (el archivo imprime **usuario y fecha junto a
  «PROVISIONAL»**, B14), `SiagieController`. **Transversales:** `TipoSolicitud`, `ActorSistema`, `AccionAuditoria`
  (+`ADJUNTO_REVISADO`), `ClaveFirma`, `ModuloApp`, `alumnos`, `privacidad`, `colegio` (aviso de B6), los verificadores,
  `ContenidoSinCobranza` (B7), `demo`.
- **Docs y CI:** plantillas Thymeleaf, `plantillas-whatsapp-meta.md` (30), manuales (`direccion.md`: la nómina es
  provisional y se copia en la plantilla del SIAGIE, B14), `compose.prod.yml` y `compose.demo.yml` (55 %), despliegue con
  **98 y 13**, «Tanda 3 · Implementación» del diseño y `ci.yml` (paso «Sprint 9, tanda 3», V1–V34, 111).

## 9. Pruebas obligatorias
**H2 (`./mvnw -B verify`, también con `-DargLine=-Duser.timezone=America/Los_Angeles`):**
- `FiltroContenidoTest` (30 casos más los de B4: ancho cero, guion suave, NFKC de letras de ancho completo, cirílico
  `реnsión`, dígitos árabes-índicos, acentos combinados) y `ContenidoSinCobranzaTest` (la de `03`; «padres/madres» y
  «7.45 a.m.» pasan; «S/ 350», «s/.350» y «350.00» no; el motivo). `HuellaComunicadoTest` (la misma cadena que el trigger).
- `ComunicadosTest`: `elDocenteNoEscribeComoDireccion`, `seccionAjenaResponde404`, `elAuxiliarNoEnvia`,
  `cobranzaSeRechazaYQuedaEnLaBitacora`, `pdfDeDocenteQuedaPorAprobar`, **`pdfDeImagenDeDireccionLoApruebaPromotoria`**,
  `todoComunicadoLlevaElPie`, `laFeDeErratasNoTocaElOriginal`, **`laFeDeErratasLlegaATodos`**, `retirarNoBorra`,
  `elMismoContenidoNoSaleDosVeces`. `IntegridadComunicadosTest` (E19 y **destinatarios que no coinciden**, B11).
- **`AdjuntoComunicadoTest`** (con el proceso hijo): los 10 hostiles (malformado, enorme por bytes y por páginas, bomba de
  objetos o de Flate, con JavaScript, `OpenAction`, `Launch`, embebido, XFA, cifrado, políglotos, extensión falsa), los de
  A14, **anotación que no es Link, capa OCG, recursión profunda** (B3, B5); sin `Info`, XMP ni revisiones; los dos SHA-256;
  el texto pasa por el filtro; `requiere_revision` con poco texto o con imágenes.
- **`AnalizadorPdfTest`**: `alVencerElTiempoSeMataPorPid`, `sinMemoriaTerminaConCodigo3`, `elHijoNoAbreConexiones`,
  `elHijoNoVeLasVariablesDelPadre`, `noQuedanArchivosEnTmp`.
- **`diezBombasSeguidasNoDejanSinServicio`** (las páginas responden en menos de 2 s y ninguna subida pasa de 10 s),
  `colaLlenaPideIntentarMasTarde`, `unaTareaPorUsuarioYDosPorColegio`, **`limiteDeSubidasDesdeLaBitacora`** (B12),
  `cabecerasDeDescarga`, **`adjuntoNoEnviadoResponde404`** (B8), `VersionPdfBoxTest`, `TmpfsConTamanoTest`.
- `PortalComunicadosTest` (`otraFamiliaResponde404`, `confirmarExigeSesionYCsrf`, `retiradoMuestraLaConstancia`),
  **`EstadisticasComunicadoTest.sinAsignacionVigenteNoVePendientes`** (B16), `AvisosComunicadosTest` (resumen, tope, 48 h,
  urgente a las 7, `elPieNoVaEnLosParametros`, **`nombreQueCalzaUsaEtiquetaGenerica`**, B6), `AsistenciaSinDeudaTest`.
- `NominaSiagieTest` (`soloDireccionExporta`, `cadaDescargaQuedaRegistrada`, `elDniConservaSusCeros`, `sinFormulas`,
  **`imprimeUsuarioYFecha`**), **`CampoNominaSinContactosNiCobranzaTest`** (B14), `ColegioNombreConCobranzaAvisaTest` (B6),
  `DemoQaSprint9Test`, `ReglasArquitecturaTest`, `DtoDocenteSinDatosTest`, las 3 de plantillas (30),
  `LogsSinDatosPersonalesTest`, `RutasIdorTest`. Conteos: **34 migraciones, 98 triggers, 111 huellas**.

**MySQL real (`mysql/AuditoriaSprint9ComunicadosMySqlTest`, job `mysql`; 1644 salvo que se diga otra cosa):**
- E16 (3, una 3819), E17, E18, `e19ElComunicadoNoSeEdita` (1143), E20 (2), E23 (1452 y 1644), E24, E28 (sin firma y
  `e28RetiroNoSeReescribe`), E29 (3819), E43 (1452), E45; `e17PorAprobarSinFirmaFalla`, `e22AdjuntoDeOtraPersonaFalla`,
  `e21ComunicadoConDeudaFallaEnLaBase`, `docenteNoApruebaSuPdf`, los CHECK con NULL (3819).
- Auditoría en papel: `b1FeDeErratasConMenosDestinatariosFalla`, `b1DestinatarioDespuesDeCerrarFalla`,
  `b2HuellaQueNoCoincideFalla`, `b2MismaHuellaFalla` (1062), `b3PdfConRevisionEnviadoDirectoFalla`,
  `b3DireccionApruebaSuPropioOrganoFalla`, `b4AnchoCeroFalla`, `b6ParametrosSinElColegioFallan`,
  `b6ColegioConPalabraDeCobranzaPasa`, `b7PadresMadresYHoraPasan`, `b9FeDeErratasDeRetiradoPorDocenteFalla`,
  `b13EnviadoEnFueraDeHoraFalla`, `b13PorAprobarConEnviadoEnFalla` (3819), `b14NominaSinDireccionOSinSesionFalla`,
  `b15CodigoEnMinusculasFalla` (3819); `e40ResumenConMontoFalla`, `e37ComunicadosSinConsentimientoFalla`.
- `PermisosMySqlTest.flujoComunicadosConPermisosMinimos` (envío, docente con PDF aprobado, PDF de imagen de Dirección
  aprobado por Promotoría, fe de erratas, retiro, confirmar, resumen con `cc_sistema`, nómina con sesión). Paso `comprobar`;
  `huellas_objetos()` = 111; prod por TLS con 98 y 13; job `demo` (el PDF de ejemplo pasa por el hijo **dentro de la
  imagen**: prueba el `PropertiesLauncher`) y `dependencias` (PDFBox sin CRÍTICA ni ALTA).

## 10. Riesgos
- **`SecurityManager` desaparece en Java 24:** al subir de Java, el hijo pasa a un contenedor aparte con `network_mode: none`.
- **Memoria de la VM:** aplicación al 55 % + 1 hijo (unos 250 MB) + tmpfs; la prueba de las 10 bombas corre en el job `demo`.
- **Arranque del hijo** (≈1 s por PDF) y **un solo órgano activo** (el PDF sin texto no sale): aceptados.
- **PDF sin antivirus**; el filtro se burla con creatividad; Meta puede clasificar `cc_comunicados_v2` como marketing.
- **`requiere_revision` lo calcula la aplicación** (con `cc_app` se podría insertar en falso; quedan el GRANT y la bitácora).
- **`abierto_en`, `avisado_mensaje_id` y `recordado_mensaje_id` los vigila la aplicación**; SIAGIE sin confirmar (D12).

## 11. Terminado cuando
Dirección ve cuántas familias confirmaron; un comunicado que menciona una deuda o un Yape no sale, ni con la clave de
`cc_app`; un PDF con JavaScript se rechaza, un PDF de imagen espera la aprobación del otro órgano y diez bombas seguidas no
dejan la aplicación sin servicio; un hijo vencido muere por su PID; la fe de erratas llega a todos; la base tiene 98
triggers y 111 huellas; y el CI del PR en borrador está en verde.
