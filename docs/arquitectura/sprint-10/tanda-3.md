# Sprint 10 · Tanda 3 · Libretas en PDF y exportación para el SIAGIE (brief)

> Léase primero. Diseño completo: `docs/arquitectura/sprint-10-notas-libretas-siagie.md` (secciones **3.13** (auditoría en
> papel, prevalece), 3.7 a 3.10, 4, 5 «V37», 6.2 y 6.3 «Tanda 3», 6.4, 6.5, 7, 8, 9.2, 12 y las dos «Implementación»).
> Rama `claude/sprint-10-notas-libretas-siagie`, PR en borrador #20: **la tanda está terminada cuando el CI del PR está en
> verde.** Parte de 31fbd8e (tanda 2 en verde: V36, 108 triggers, 16 funciones, 124 huellas).

## 1. Decisiones y desviaciones que ya rigen
1. **La libreta nunca se retiene por deuda** (3.8): se emite para toda matrícula **ACTIVA o RESERVADA** (C1);
   `LibretaConDeudaTest` lo demuestra con **3 familias** (con deuda vencida, al día y RESERVADA).
2. Decisiones 169 (PDFBox solo para escribir, lista blanca M11), 170 (foto sin guardar el PDF, con la cabecera, A3), 177
   revisada (M9), 188 (reemisión en 2 días hábiles), 189 ajustada (delegación solo a Administración) y A4, A6, B14 de la 3.13.
3. **Desviaciones de la tanda 2 que esta tanda respeta:** `CALIFICACION_TARDIA` es de `asignacion_docente` (datos
   `periodo_id` y `area_id`); las 48 horas se miden con `firma_operacion.firmada_bd`; un «sin nota» omite la clave `nivel`; la
   cadena de la bitácora serializa el final de cada escritura (decisión 191): la emisión masiva no escribe un evento por
   libreta (sección 2).

## 2. Alcance
- **`EmisionLibretas`** (`sistema.libretas`, cc_sistema, cada 5 minutos): por cada periodo CERRADO, una `libreta_emitida` por
  matrícula ACTIVA o RESERVADA de las secciones del año. Bitácora: **un** evento `LIBRETAS_EMITIDAS` por periodo y sección
  (con la cantidad y la huella de la lista), no uno por libreta (decisión 191).
- **`FotoLibreta`**: arma el contenido con las marcas de agua (`hasta_calificacion_id`, `hasta_marca_id`,
  `hasta_exoneracion_id`) y la cabecera (`alumno_nombre`, `alumno_codigo`, `tipo_codigo`, `codigo_modular`, `dre`, `ugel`,
  `tutor_nombre`, `director_nombre`); calcula la huella; la verifica al descargar.
- **`GeneradorLibretaPdf`** (único usuario de PDFBox fuera del analizador): formato de los anexos 02 a 04 (sección 3.7).
- Portal `/familia/libretas` (todas las versiones, con fecha y «qué cambió»), descarga con `entrega_libreta` PORTAL;
  `/academico/libretas` (Dirección: una o la sección; tutor: las de su sección) con `entrega_libreta` IMPRESA, y ENTREVISTA
  en Inicial; `/academico/libretas/verificar?codigo=`.
- Avisos `LIBRETA_DISPONIBLE` y `LIBRETA_CORREGIDA` (finalidad nueva `LIBRETAS`; sin consentimiento, correo); plantillas
  `cc_libreta_v1` y `cc_libreta_corregida_v1` (`plantillas-whatsapp-meta.md`: 30 a 32).
- `IntegridadLibretas` (emitidas contra matrículas ACTIVAS o RESERVADAS; huellas; plazos de reemisión) y la reemisión.
- Exportación para el SIAGIE con su hoja «Control», delegación a Administración y su pantalla.
- Demo (decisión 187): I bimestre cerrado con libretas, una versión 2 y la exportación DEMO.
- **Fuera de alcance:** situación final PRO, PER o RR (decisión 178); llenar la plantilla `.xls` del SIAGIE (decisión 181).

## 3. Cómo llega a la familia una corrección (también las aprobadas en la tanda 2)
1. **Versión 1 = lo informado al cierre.** Su marca de agua es la última calificación y la última marca de asistencia con
   `registrado_bd` hasta el `cerrado_en` del periodo, no «lo que haya hoy». Así una corrección aprobada en la tanda 2 (antes de
   que existieran libretas) **no se esconde dentro de la versión 1**.
2. **Versión siguiente por cada cambio aprobado:** si la matrícula tiene una `CORRECCION_APROBADA` o un `REGISTRO_TARDIO` con id
   mayor que la marca de agua de su libreta vigente, se emite la versión siguiente (motivo `CORRECCION`) con `cambios`
   («Matemática · Resuelve problemas de cantidad: B → A»; sin conclusiones). Para el atraso de la tanda 2, la primera pasada
   emite la versión 1 y la 2 de esa matrícula en la misma corrida.
3. **Un aviso por familia y periodo:** si la vigente es la 1, `LIBRETA_DISPONIBLE`; si es mayor, `LIBRETA_CORREGIDA` (también
   en el atraso: un solo mensaje, no dos). El portal muestra las dos versiones y qué cambió.
4. La alerta A5 de la tanda 2 ya avisó a Promotoría; la bitácora conserva el «de qué a qué».

## 4. Migración `V37__libretas_y_exportacion_siagie.sql`
- SQL de la sección 5 del diseño **con los cambios de la 3.13**: `libreta_emitida` con `hasta_exoneracion_id`, la cabecera
  (`alumno_nombre`, `alumno_codigo`, `tipo_codigo` en CODIGO_ESTUDIANTE, DNI, CE o PASAPORTE, `codigo_modular`, `dre`,
  `ugel`, `tutor_nombre`, `director_nombre`) y `solicitud_id`; `motivo` en CIERRE, CORRECCION o REEMISION (REEMISION con
  `solicitud_id` y `cambios`); `entrega_libreta.canal` en PORTAL, IMPRESA o ENTREVISTA (`apoderado_id` solo en PORTAL);
  `exportacion_calificaciones.huella_libretas` en lugar de `hasta_calificacion_id`; `delegacion_siagie`; finalidad `LIBRETAS`
  en `ck_consentimiento_aviso_valores` y los tipos `LIBRETA_DISPONIBLE` y `LIBRETA_CORREGIDA` en `ck_mensaje_tipo`
  (**copiar las listas completas de V33 y V34**).
- `TipoSolicitud` +1 (`REEMISION_LIBRETA`), sin migración.
- **Comprobar en H2 y en MySQL 8.4:** la FK de `libreta_emitida` consigo misma; el CHECK de la cadena (versión 1 sin
  reemplazo ni cambios; mayor que 1 con los dos); `ADD CONSTRAINT` sobre `consentimiento_aviso` y `mensaje` con filas.
- Un caso NULL por CHECK (`reemplaza_id`, `cambios`, `solicitud_id`, `apoderado_id`, `dre`); entidades de solo inserción.

## 5. MySQL: permisos, triggers y huellas
- `01-usuarios.sql`: sin cambios. `02`: bloque «Sprint 10 · tanda 3»: `INSERT` en `entrega_libreta`,
  `exportacion_calificaciones` y `delegacion_siagie` para `cc_negocio`; **`libreta_emitida` solo `INSERT` para
  `cc_sistema`** (en el bloque 2); ningún UPDATE ni DELETE.
- `03` (sección 6.3 «Tanda 3» con la 3.13; comparar siempre con `<=>`):
  - **`trg_libreta_emitida_nace`**: `cc_es_sistema()` y `creado_por = sistema.libretas`; periodo CERRADO; matrícula del alumno,
    del año y de la sección, **ACTIVA o RESERVADA**; versión siguiente y `reemplaza_id` a la de versión máxima; las marcas de
    agua no bajan; versión 1 con `hasta_calificacion_id` no mayor que la última nota registrada hasta `cerrado_en`;
    CORRECCION solo con una `CORRECCION_APROBADA` o `REGISTRO_TARDIO` de esa matrícula y periodo entre la marca anterior y la
    nueva; REEMISION con su solicitud `REEMISION_LIBRETA` firmada, aprobada por PROMOTOR activo y pedida por DIRECTOR;
  - **`trg_entrega_libreta_nace`**: PORTAL, la cuenta del apoderado activo de la familia del alumno con su sesión; IMPRESA,
    DIRECTOR o el **TUTOR VIGENTE de la sección de la libreta** con su sesión; ENTREVISTA, igual que IMPRESA y solo en Inicial;
  - **`trg_exportacion_calificaciones_nace`**: DIRECTOR, o una `delegacion_siagie` con `cc_hoy_lima()` entre `desde` y
    `hasta`; con `cc_sesion_abierta`; periodo CERRADO; sección del año;
  - **`trg_delegacion_siagie_nace`**: DIRECTOR con la firma `delegacion_siagie:{usuario}:{desde}:DELEGADA`;
    `hasta` hasta 15 días después de `desde`; `desde` desde hoy; la cuenta delegada activa, sin apoderado, **con
    ADMINISTRACION y sin CAJA ni DOCENTE** (decisión 189 ajustada);
  - **`trg_mensaje_nace` (versión nueva)**: `LIBRETA_*` solo por `sistema.libretas` con `cc_sistema`, a un apoderado, con su
    plantilla, sin cobranza (regla clara sobre lo que sigue al nombre del colegio) y, por WhatsApp, con el último
    consentimiento `LIBRETAS` otorgado.
  - Encabezado: «Versión anterior (V36, sprint 10, tanda 2): 108 triggers y 13 funciones (16 con huella; 124 huellas)».
- **Conteo esperado:** 108 → **112 triggers** (4 nuevos y 1 con versión nueva); funciones con huella **16** (sin cambio);
  huellas 124 → **128**. Prod: «Huellas de los 112 triggers y las 16 funciones verificadas».
- `VerificadorPermisosBaseDatos`: `TRIGGERS_ESPERADOS` con los 4; 1142 en **cualquier escritura de `cc_app` en
  `libreta_emitida`**, `UPDATE entrega_libreta`, `UPDATE exportacion_calificaciones` y `UPDATE delegacion_siagie`; 1644 en
  una `libreta_emitida` del colegio 0 versión 2 sin corrección, con `cc_sistema`. `HuellasObjetosBd` = 128.
- **Pruebas MySQL obligatorias (antes «comprobar»):** la marca de agua de la versión 1 con `cerrado_en` en hora de Lima; que
  el trigger lee su propia tabla (versión máxima); `huella_libretas` se calcula en Java y la base solo la guarda.

## 6. La libreta, la verificación y la reemisión (A3, A4, M9)
- **Cabecera en la foto y en la huella:** si mañana cambia el nombre del alumno, el tutor o la UGEL, la libreta emitida no
  cambia; la versión siguiente (si la hay) toma los datos nuevos.
- **Código:** `LIB-{id}-{16 primeros caracteres de la huella}` (A4), impreso con «Emitida el … · versión N».
- **`/academico/libretas/verificar?codigo=`** (Dirección): si el código existe, si es la versión vigente y **los niveles de esa
  versión por competencia** (sin conclusiones), para compararlos con el papel. Cada consulta, en la bitácora
  (`LIBRETA_VERIFICADA`) y en `acceso_dato_personal` (`LIBRETA`).
- **Huella que no coincide al descargar** (decisión 188): no se entrega ese PDF; evento `LIBRETA_HUELLA_NO_COINCIDE`; alerta
  `INTEGRIDAD_LIBRETAS` a Promotoría; la familia ve «Su libreta está en revisión. El colegio se la entregará a más tardar el
  {fecha}» (2 días hábiles con los feriados del colegio); Dirección pide `REEMISION_LIBRETA` con motivo, Promotoría la aprueba
  y `sistema.libretas` emite la versión siguiente (motivo REEMISION, «Reemitida tras una revisión de integridad») con
  `LIBRETA_CORREGIDA`. Vencido el plazo sin reemisión, la alerta se repite cada día.
- **Código de estudiante** (M9): `alumno_codigo` es `alumno.codigo_estudiante`; si no hay, el documento con su tipo.
- **Impresión:** Dirección (una o la sección) y el tutor (las de su sección), hasta 45 por archivo y una a la vez por colegio;
  cada libreta impresa deja su `entrega_libreta` IMPRESA a nombre de quien imprime; en Inicial, ENTREVISTA.

## 7. PDFBox: lista blanca (M11)
`pdfboxEscribeSoloLaLibreta` (ArchUnit) permite en `academico.libreta.pdf` solo: `PDDocument` (constructor sin argumentos,
`addPage`, `save`, `close`, `getDocumentInformation`), `PDPage`, `PDRectangle`, `PDPageContentStream`,
`PDType0Font.load(PDDocument, InputStream, boolean)`, `PDFont`, `LosslessFactory.createFromImage`, `PDImageXObject` (solo como
tipo), `PDDocumentInformation`, `MemoryUsageSetting` y `RandomAccessStreamCache`. **Prohibidos expresamente:**
`PDImageXObject.createFrom*`, `PDFMergerUtility`, `importPage`, `PDAcroForm`, los paquetes `interactive.action` e
`interactive.annotation` y `Loader`. Las pruebas, excluidas de forma explícita (`ImportOption.DoNotIncludeTests`). Controles
de la sección 3.9: texto saneado (`TextoSeguroPdf`), Liberation Sans del jar, executor de 2 hilos con cola de 8, 6 páginas,
`setupMixed(32 MB, 64 MB)`, cabeceras de descarga del sprint 9.

## 8. SIAGIE (A6, M12, decisión 189)
- Sale de **la foto de la libreta vigente de cada matrícula**; `huella_libretas` = SHA-256 de los «id:huella» ordenados. **Se
  rechaza** si alguna matrícula de la sección tiene una corrección o nota tardía aprobada sin su libreta nueva («Espere a que
  se emitan las libretas corregidas»). Hoja **«Control»**: por competencia, cuántos AD, A, B, C, sin nota y EXO, para que
  Dirección la compare con el SIAGIE después de la carga.
- Perfil `SIAGIE_CALIFICACIONES_PROVISIONAL`, una hoja por área y una de transversales, con «PROVISIONAL», el usuario y la
  fecha en la primera fila y en el nombre del archivo.
- **Delegación:** Dirección delega con su firma a **una persona de Administración** (sin Caja ni Docente) por hasta 15 días;
  la delegada exporta **con su sesión**, cada descarga en `acceso_dato_personal` (`CALIFICACIONES_SIAGIE`, con la cantidad) y
  en la bitácora. Alerta a Promotoría con más de 10 secciones por persona y día.

## 9. Segregación de funciones e INDECOPI
- Emite solo `sistema.libretas` (sin GRANT para `cc_app`); la reemisión la pide Dirección y la aprueba Promotoría; verifica
  solo Dirección; exporta Dirección o la delegada de Administración; Caja no ve nada académico.
- **INDECOPI:** `PortalSinFiltroDeDeudaTest` **completo (B14)**: interceptores, filtros, `@ControllerAdvice`,
  `@ModelAttribute`, los fragmentos del layout de familias y la redirección después del login.

## 10. Archivos que toca
- **Base:** `V37__libretas_y_exportacion_siagie.sql`; `02-permisos-tablas.sql`; `03-triggers.sql`; `tablas-vigiladas.txt` y
  `respaldar.sh` (+4).
- **`academico.libreta` (nuevo):** `model` (`LibretaEmitida`, `EntregaLibreta`, `MotivoLibreta`, `CanalEntrega`), `service`
  (`FotoLibreta`, `ContenidoLibreta`, `IntegridadLibretas`, `VerificacionLibreta`, `ReemisionLibretas`,
  `ManejadorReemisionLibreta`), `proceso.EmisionLibretas`, `pdf` (`GeneradorLibretaPdf`, `TextoSeguroPdf`, el executor),
  `api.LibretasPorAvisar`, `web` (`PortalLibretasController`, `LibretasController`).
- **`academico.siagie`:** `CalificacionesSiagie`, `PerfilCalificaciones`, `CampoCalificaciones`, `DelegacionesSiagie`;
  `siagie/perfiles/SIAGIE_CALIFICACIONES_PROVISIONAL.json`; `SiagieController`.
- **`comunicacion`:** `TipoMensaje` (+2), `PlantillaMensaje` (+2), `proceso.AvisosLibretas`.
- **Transversales:** `ActorSistema.LIBRETAS`, `TipoSolicitud` (+1), `Finalidad.LIBRETAS` y la pantalla de consentimientos,
  `TipoAviso.INTEGRIDAD_LIBRETAS`, `AccionAuditoria`, `ClaveFirma` (+1), `ModuloApp`, `ReglasArquitecturaTest`
  (lista blanca), `VerificadorPermisosBaseDatos`, `HuellasObjetosBd`, `DatosDemoColegio`, `application.yaml`
  (`cuentasclaras.libretas.*` y `siagie.*`).
- **Plantillas Thymeleaf**, `docs/entrega/plantillas-whatsapp-meta.md` (32), manuales de familias, docente y Dirección,
  sección «Tanda 3 · Implementación», `ci.yml` (V1–V37, 128 y 112/16), `paso-0.sh`, `en-la-vm.sh`, `despliegue-gcp.md`, el
  acta del paso 0 y `mysql-usuarios.md`.

## 11. Pruebas obligatorias
**H2 (`./mvnw -B verify`, también con la zona de Los Ángeles):**
- **`LibretaConDeudaTest`** (E27): 3 familias de la misma sección, **con deuda vencida, al día y con matrícula RESERVADA**;
  se cierra el periodo; las 3 tienen libreta, el mismo aviso y la descargan con 200; el texto del PDF y la huella dependen
  solo de sus notas.
- `EmisionLibretasTest`: una por matrícula ACTIVA o RESERVADA (C1); **un evento por sección** (decisión 191);
  `correccionDeLaTanda2DaVersion1Y2YUnSoloAviso` (sección 3); `IntegridadLibretasTest` (E29).
- `FotoLibretaTest`: `cabeceraEnLaHuella`, `huellaDistintaDaPlazoYReemision` (A3, E40), exoneración desde su periodo.
- `VerificacionLibretaTest`: `muestraLosNiveles`, `codigoDe16`, versión no vigente (A4, E16).
- `LibretaPdfTest` y `GeneradorLibretaPdfTest`: `codigoAntesQueDni` (M9); emoji, derecha a izquierda, `\u0000` y 1000
  caracteres (E37); `LibretaSinCobranzaTest` (E31); 45 libretas en menos de 10 s; `ColaDeLibretasTest` (E38).
- `EntregaLibretaTest`: el tutor imprime solo su sección (M9); otra familia o colegio, 404 (E32); ENTREVISTA solo en Inicial.
- `CalificacionesSiagieTest`: `saleDeLaLibretaVigente`, `correccionSinLibretaRechaza`, `hojaDeControl` (A6, E17);
  `DelegacionSiagieTest`: solo Administración, nunca Caja ni una cuenta con Caja (M12, decisión 189); acceso registrado.
- **`PortalSinFiltroDeDeudaTest` completo (B14)**, `ConfiguracionSinDeudaTest`, `ReglasArquitecturaTest`
  (`pdfboxEscribeSoloLaLibreta` con una clase de prueba por prohibición, `pdfboxParseaSoloElAnalizador`, sin cobranza),
  `PlantillasMensajeTest`, `PlantillasConColegioTest`, `PlantillasMetaDocTest`, `ConsentimientosTest`, `MatrizPermisosTest`,
  `RutasIdorTest` y `CatalogoRutasConId`, `LogsSinDatosPersonalesTest`, `VersionPdfBoxTest`; conteos: **37 migraciones, 112
  triggers, 128 huellas**.

**MySQL real (`mysql/AuditoriaSprint10LibretasMySqlTest`). 1644 salvo que se diga otra cosa:**
- `e14LibretaConCcAppFalla` (1142), `libretaVersion2SinCorreccionFalla`, `libretaDeMatriculaRetiradaFalla`,
  **`c1LibretaDeMatriculaReservadaPasa`**, `version1ConMarcaPosteriorAlCierreFalla`, `a3ReemisionSinSolicitudFalla`,
  `reemisionAprobadaPorDireccionFalla`.
- `e32EntregaAOtraFamiliaFalla`, `m9TutorImprimeSoloSuSeccion`, `entrevistaFueraDeInicialFalla`.
- `m12SinDelegacionFalla`, `m12DelegacionVencidaFalla`, `delegacionACajaFalla`, `delegacionACuentaConCajaFalla`,
  `delegacionDeMasDe15DiasFalla`, `exportacionDePeriodoAbiertoFalla`.
- `e30AvisoDeLibretaConCobranzaFalla`, `avisoSinConsentimientoLibretasPorWhatsappFalla`; `checksConNull`.
- **`LibretaConDeudaTest` en MySQL** (las 3 familias, con `cc_app` y `cc_sistema`) y `flujoLibretasConPermisosMinimos`.
- `comprobar`; `huellas_objetos()` = 128; prod con 112 y 16; respaldo: una `libreta_emitida` borrada como DBA en el
  colegio A marca solo a A; carga: 300 libretas emitidas al cerrar en menos de 60 s.

## 12. Riesgos
1. **Bitácora que serializa** (decisión 191): emitir 300 libretas con un evento cada una alargaría el cierre y frenaría a los
   demás; por eso, un evento por sección. Medir la emisión de un colegio de 300 alumnos en el CI.
2. **Maquetación propia con PDFBox** (cortes de tabla, conclusiones largas, Secundaria con 31 competencias): revisión de
   `disenador-ui` con PDF reales antes de marcar la tanda lista.
3. **La Liberation Sans del jar** puede no tener algún carácter («°», «·», comillas tipográficas): `TextoSeguroPdf` lo pasa a
   «?»; comprobarlo con la prueba de caracteres.
4. **Plantilla del SIAGIE sin confirmar (D12):** la exportación sale PROVISIONAL; la hoja «Control» permite comparar después.
5. **El tutor imprime libretas con el DNI** de quien no tiene código de estudiante (M9 lo acepta); pedir al colegio que
   complete los códigos (la pantalla del SIAGIE del sprint 9 ya los lista).

## 13. Terminado cuando
Al cerrar un periodo, cada familia, también la que debe y la que tiene la matrícula RESERVADA, recibe su aviso y descarga su
libreta en PDF; una corrección aprobada (también las de la tanda 2) llega como versión nueva con el aviso de qué cambió; el
tutor imprime las de su sección y queda la constancia; un papel adulterado se detecta con el código y los niveles; una huella
que no coincide nunca deja a la familia sin libreta; la exportación para el SIAGIE sale de las libretas vigentes con su hoja
de control y la puede sacar la persona de Administración delegada; y el CI del PR en borrador está en verde (112 triggers,
16 funciones, 128 huellas).
