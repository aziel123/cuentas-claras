# Sprint 11 · Tanda 2 · Exportación, cierre, supresión y suspensión en producción (brief)

> Léase primero. Diseño: `docs/arquitectura/sprint-11-ciclo-colegio.md`, secciones **2** (plazos legales), **3.9 a 3.11**, **3.13**
> (AP-3, AP-6, AP-7, AP-8, AP-11, AP-12 y AP-14, que prevalecen), **3.16b** (A1c-3, A1c-4 y A1c-6) y las decisiones 132, 196, 198 a
> 200 y 204. Parte de `3e1382c` (tandas 1, 1c y 1b en verde): **V40, 117 triggers, 18 funciones en `03` (21 con huella; 138
> huellas) y `reloj-pendiente.txt` en 27.** Rama `claude/sprint-11-ciclo-colegio`, PR #22 en borrador: **la tanda está terminada
> cuando el CI del PR está en verde.** Antes de codear, la auditoría del diseño en papel de esta tanda.

## 1. Decisiones (pendientes del usuario: se implementan los valores por defecto)
1. **Plazos (196, que corrige la 132; AP-7 y AP-11):** con la **autorización expresa** del titular registrada en la base,
   `conservar_hasta` = la fecha en que se aplicó la suspensión + **12 meses exactos** (unos 11 meses de consulta para las
   familias después del cierre; con los respaldos mensuales, hasta 24 meses: DS 016-2024-JUS, art. 31.2). **Sin autorización:**
   el cierre + 30 días. Una **BAJA**: + 30 días. **El proveedor no guarda 5 años:** lo tributario (5 años o la prescripción) lo
   conserva el colegio con su exportación (Código Tributario, art. 87 num. 7).
2. **Formato (198) y cifrado (199):** ZIP AES-256 con `zip4j` (Apache 2.0, dependencia nueva; abre con 7-Zip o el Explorador
   de Windows 11): un CSV por tabla, libretas y comunicados en PDF, `MANIFIESTO.csv` con SHA-256 y `LEEME.pdf`. Los comprobantes,
   en CSV (su XML y su CDR los guardan el OSE y SUNAT). Se descarga durante 30 días.
3. **Fin definitivo (200):** la instalación **dedicada** se elimina completa, con acta. En la **compartida**, el DBA borra las
   filas del colegio y **tacha** sus eventos de la bitácora (no los borra: la cadena es de todos), con un acta que aprueban dos
   operadores; la fila del colegio queda con la **ficha mínima** de AP-14.
4. **La suspensión en producción se habilita con V41 (204, AP-8):** el trigger rechaza proponer una SUSPENSION mientras no
   existe `exportacion_colegio`. Llega con esta migración, sin versión nueva del trigger.
5. **La BAJA de un colegio con datos (AP-6):** se permite con su exportación `ENTREGADA` o `VENCIDA`. En la tanda 1 se rechazaba.
6. **Sin cambios de las reglas de la 1c:** la Promotoría aplica directo; el cierre y el acta los deciden dos operadores, porque
   son del proveedor.

## 2. Alcance
- **Exportación (3.9; AP-12):**
  - `sistema.exportacion` (cc_sistema) la genera al aplicarse una SUSPENSION, antes de un CIERRE si no hay una de menos de
    30 días, o a pedido escrito del colegio (la genera un operador con su firma);
  - la genera en flujo, con un tope de 2 GB;
  - **cada consulta filtra por `colegio_id` de forma explícita o con un join al dueño**, según `exportacion/tablas.txt`, que dice
    la columna o el join de cada tabla;
  - las libretas se regeneran y se **comparan con su huella**: si una no coincide, la exportación falla y avisa;
  - CSV en UTF-8 con BOM, coma y CRLF, sin fórmulas (una celda que empieza con `=`, `+`, `-`, `@`, tabulador o retorno lleva un
    apóstrofo);
  - nunca van claves, tokens, secretos, firmas ni sesiones;
  - la clave de 24 caracteres se guarda cifrada con `CC_CLAVE_CONECTORES` hasta que se ve una vez, y **descifrarla deja
    `EXPORTACION_CLAVE_DESCIFRADA`** en las dos bitácoras;
  - la entrega va al titular: enlace de un solo uso, su DNI y un código por WhatsApp (A1c-5: atado a esa exportación), hasta 3
    descargas;
  - el correo de control recibe la SHA-256 del ZIP y del manifiesto; `LimpiezaExportaciones` borra el archivo a los 30 días.
- **Cierre (3.10):**
  - `CIERRE` de SUSPENDIDO a CERRADO, con dos operadores, y la exportación `ENTREGADA`, o `LISTA` o `VENCIDA` con 30 días;
  - la base pone `cerrado_en` y `conservar_hasta`;
  - aviso al titular, a la Promotoría, al correo institucional y al de control, con la fecha de supresión.
- **Autorización expresa (AP-11):** el titular la firma (`colegio:{id}:CONSERVACION`) en la Puesta en marcha, como paso
  opcional, o después. La base guarda quién y cuándo.
- **Modo consulta en CERRADO:**
  - las familias entran hasta `conservar_hasta` (`trg_sesion_usuario_nace`, versión);
  - ven estado de cuenta, comprobantes, libretas y comunicados, y solo reciben `ACTIVACION_CUENTA` (AP-5, ya hecho);
  - después, nadie entra.
- **Supresión con acta (3.11; AP-14):**
  - `acta_supresion` la propone un operador y la aprueba otro;
  - el DBA la ejecuta con `scripts/fin-de-colegio/suprimir.sql`, guiado por el `inventario.txt` que escribe primero esta tanda;
  - `reaplicar.sh` vuelve a suprimir al restaurar un respaldo;
  - el tachado de `evento_auditoria` y `VerificadorIntegridadAuditoria` aceptan solo los eventos tachados con su acta y su
    `huella_eventos`;
  - `RevisionConservacion` avisa 30 días antes y el día del vencimiento.
- **Suspensión aplicada en MySQL:** las pruebas que la tanda 1 dejó en H2 (sección 6, pendientes heredados).
- **Fuera de alcance:** los subdominios (202) y cambiar de titular (115).

## 3. Migración `V41__exportacion_cierre_y_supresion.sql`
- **`colegio`:** `cerrado_en DATETIME(6)`, `conservar_hasta DATE`, `conservacion_autorizada_por VARCHAR(60)`,
  `conservacion_autorizada_en DATETIME(6)` y el CHECK exacto (AP-7, AP-11):
  ```sql
  CONSTRAINT ck_colegio_conservar CHECK (
      (estado NOT IN ('CERRADO', 'BAJA') AND conservar_hasta IS NULL)
      OR (estado = 'CERRADO' AND conservacion_autorizada_en IS NOT NULL AND conservar_hasta IS NOT NULL
          AND conservar_hasta = DATE(suspendido_en) + INTERVAL 12 MONTH)
      OR (estado = 'CERRADO' AND conservacion_autorizada_en IS NULL AND conservar_hasta IS NOT NULL
          AND conservar_hasta = DATE(cerrado_en) + INTERVAL 30 DAY)
      OR (estado = 'BAJA' AND conservar_hasta IS NOT NULL AND conservar_hasta = DATE(estado_desde) + INTERVAL 30 DAY)
      OR estado = 'SUPRIMIDO')
  ```
  Cada caso del CHECK, con su prueba con NULL: un CHECK que evalúa NULL pasa.
- **`cambio_estado_colegio`:**
  - `ck_cambio_estado_colegio_tipo` con `CIERRE` (SUSPENDIDO → CERRADO; motivos `FIN_CONTRATO`, `FALTA_PAGO`, `PEDIDO_COLEGIO`
    y `OTRO`), copiando las listas de V39;
  - `exportacion_id` (FK), obligatorio en `CIERRE`.
- **Tablas nuevas** (columnas en 5 de V41 del diseño): `exportacion_colegio` (`sha256_zip` y `sha256_manifiesto` no cambian:
  1143; `clave_cifrada` NULL después de verla; una `GENERANDO` o `LISTA` por colegio); `descarga_exportacion` (solo inserción);
  `acta_supresion` (una por colegio; quien aprueba no es quien propone; `huella_eventos`, `filas_suprimidas` en JSON y
  `respaldos_vencen_el`).
- **Un caso NULL por CHECK nuevo.** Comprobar en H2 y en MySQL 8.4 el `DATE(...) + INTERVAL 12 MONTH` dentro de un CHECK (si
  H2 no lo acepta, el trigger lo exige y H2 lo prueba en la aplicación: desviación anotada).

## 4. MySQL: funciones, triggers, huellas y reloj
- `02-permisos-tablas.sql`:
  - cc_sistema escribe `exportacion_colegio`, `descarga_exportacion` y `acta_supresion`; cc_negocio solo lee lo que muestra;
  - nadie tiene GRANT sobre `conservar_hasta` ni `cerrado_en`: los pone el trigger (como `aplica_desde`, A1c-6);
  - `UPDATE (conservacion_autorizada_por, conservacion_autorizada_en)` de `colegio` para cc_negocio.
- `03-triggers.sql`:
  - **función nueva** `cc_supresion_en_curso(colegio)`: con `cc_ahora_lima_real()`, la conexión `cc_migrador`, `@cc_acta`
    igual a un acta `EJECUTANDO` de dos operadores y el plazo vencido;
  - **triggers nuevos (7):** `trg_exportacion_colegio_nace` y `_cambio`, `trg_descarga_exportacion_nace`,
    `trg_acta_supresion_nace` y `_estado`, `trg_evento_auditoria_tachado` (BEFORE UPDATE: solo el tachado exacto con
    `cc_supresion_en_curso`) y `trg_evento_auditoria_sin_borrado` (BEFORE DELETE: siempre rechaza);
  - **versiones nuevas:**
    - `trg_cambio_estado_colegio_nace` (CIERRE; BAJA con datos solo con la exportación `ENTREGADA` o `VENCIDA`, AP-6) y
      `_estado`;
    - `trg_colegio_cambio` (CERRADO y SUPRIMIDO; `cerrado_en` y `conservar_hasta` de la base; la autorización con la firma del
      titular);
    - `trg_sesion_usuario_nace` (CERRADO solo hasta `conservar_hasta`);
    - los triggers de UPDATE y DELETE de las tablas del inventario, que dejan pasar solo con `cc_supresion_en_curso`.
- **Reloj real hasta 0 (AP-3):** los **27 usos que quedan** (`UTC_TIMESTAMP`, `NOW(`, `CURDATE`, `CURRENT_TIMESTAMP` y
  `CURRENT_DATE` fuera de comentarios) pasan a `cc_ahora_lima_real()` o a `cc_hoy_lima()`. Están en 18 triggers:
  con 2 usos, `trg_periodo_evaluacion_cambio`, `trg_matricula_estado`, `trg_marca_asistencia_nace`, `trg_lote_calificacion_nace`,
  `trg_llamada_control_registro`, `trg_comunicado_nace`, `trg_comunicado_destinatario_cambio` y `trg_calificacion_nace`; con 1,
  `trg_semilla_muestreo_registro`, `trg_resumen_diario_registro`, `trg_respaldo_registro`, `trg_registro_asistencia_nace`,
  `trg_muestra_llamada_registro`, `trg_libreta_emitida_nace`, `trg_feriado_registro`, `trg_exportacion_calificaciones_nace`,
  `trg_entrega_libreta_nace` y `trg_delegacion_llamada_registro`.
  `reloj-pendiente.txt` queda en **0** y el paso `reloj-real` del CI pasa a exigir 0. Las ventanas «hace 5 minutos» de estos
  triggers no cambian de duración, solo de reloj.
- **Conteo esperado:** 117 → **124 triggers**; funciones de `03` 18 → **19**, con huella 21 → **22**; huellas 138 → **146**.
  Encabezado: «Versión anterior (V40, sprint 11, tanda 1c): 117 triggers y 18 funciones (21 con huella; 138 huellas)». Prod:
  «Huellas de los 124 triggers y las 22 funciones verificadas».
- `VerificadorPermisosBaseDatos`: `TRIGGERS_ESPERADOS` con los 7; 1142 (`DELETE FROM evento_auditoria`, `UPDATE
  exportacion_colegio SET sha256_zip` con cc_app); 1143 (`conservar_hasta` con cc_app y cc_sistema); 1644 (un tachado sin acta; un
  acta aprobada por quien la propuso); `HuellasObjetosBd` = 146.

## 5. Pendientes heredados de las tandas 1, 1c y 1b
1. **Reloj real hasta 0** (sección 4).
2. **`th:if` (o `th:unless`) junto a `th:replace` en el mismo elemento** (hallazgo de la 1b): el `th:replace` se evalúa
   igual. Son **19 usos en 8 plantillas**:
   - `familia/comunicados.html` (5), `comunicados/detalle.html` (3), `comunicados/lista.html` (3);
   - `comunicados/nuevo.html`, `familia/comunicado.html` y `familia/mis-datos.html` (2 cada una);
   - `aprobaciones/cajas.html` y `avisos-familias/bandeja.html` (1 cada una).
   Cada uno pasa a `<th:block th:if="…"><div th:replace="…"></div></th:block>`. `PlantillasIfReplaceTest` recorre todas las
   plantillas y falla si un elemento tiene los dos atributos.
3. **Tarjeta de cambio de cuenta bancaria (F-D7, de la 1b):**
   - el ⚠ con su tooltip «Confirma el número con el banco antes de aprobar»;
   - titular, prueba de conexión y motivo en «Ver detalle» (`<details>`);
   - **número de cuenta y CCI completos siguen visibles** fuera del `<details>` (`ControlesVisiblesTest`).
4. **El resumen diario cuenta las aprobaciones externas (F-D5):** hoy `contarPendientes` (`sistema.panel`) no las suma, porque
   los puertos filtran por el rol de la sesión. Cada puerto gana `contarPendientesDelColegio(colegioId)`, sin filtro de rol ni de
   participantes, que solo llama el resumen. `ResumenDiarioTest`: un plan enviado y un feriado propuesto suman 2.
5. **La suspensión aplicada, en MySQL:** las pruebas que la tanda 1 dejó en H2 porque sin V41 no se podía proponer:
   `a4SuspensionConCierrePorRevisarFalla`, `a4SuspensionConComprobantePendienteFalla`, `a4EfectivoEnLasUltimas24HorasFalla`,
   `h3OrdenDentroDeLas2HorasFalla`, `f10SesionDelPersonalEnSuspendidoFalla`, `a5ActivacionDeApoderadoEnSuspendidoPasa`,
   `a5RecordatorioEnSuspendidoFalla` y `f1SuspensionAntesDe30DiasFalla` (la espera de 30 días sale de la base; se prueba con
   `espera_directa_horas` y la suya, como en la 1c).
6. **La alerta a los operadores de A1c-4** (quien cambió la cuenta bancaria intenta confirmar la conciliación), que la 1c no
   implementó.

## 6. Archivos que toca
- **Base:** `V41__exportacion_cierre_y_supresion.sql`; `scripts/mysql/02` y `03`; `reloj-pendiente.txt` (0);
  `scripts/ci/reloj-real.sh` (exige 0); `scripts/fin-de-colegio/inventario.txt`, `suprimir.sql` y `reaplicar.sh` (nuevos);
  `scripts/respaldo/restaurar-y-verificar.sh` (llama a `reaplicar.sh`).
- **Java:** `exportacion` (nuevo; depende de `comun`, `colegio`, `auditoria` y de repositorios de lectura: `Exportador`,
  `EscritorCsv`, `ZipCifrado`, `ManifiestoExportacion`, `EntregaExportacion`, `LimpiezaExportaciones`); `plataforma` (CIERRE,
  actas, `RevisionConservacion`); `VerificadorIntegridadAuditoria` (tachados); `ModoConsultaFamilias` (CERRADO);
  `puestaenmarcha` (autorización); los 7 adaptadores de `PendientesExternos`; `pom.xml` (zip4j).
- **Plantillas:** la entrega (`/exportacion/{token}`), las actas del operador, la tarjeta de la cuenta bancaria y las 8 plantillas
  del punto 2 de la sección 5.
- **Documentos:** `docs/operacion/fin-de-un-colegio.md` (nuevo: procedimiento, acta y respaldos); las cláusulas 8 de los
  términos y 9 del encargo (CSV y PDF, 12 meses con autorización, supresión con acta; para el abogado); los manuales de
  operador y de Promotoría; `ci.yml` (V1–V41, 146 y 124/22).

## 7. Pruebas obligatorias
**H2 (también con la zona de Los Ángeles):**
- **`ExportacionDosColegiosTest` (AP-12):** con dos colegios cargados, **cada fila de cada CSV** del ZIP de A tiene su `colegio_id`
  (o su dueño) en A, y ningún valor único de B aparece en el ZIP de A.
- `ExportacionTest`: manifiesto y huellas; las libretas iguales a su huella; una libreta alterada hace fallar la exportación.
- `ExportacionSinSecretosTest`, `CsvSinFormulasTest` y `ExportacionGrandeTest` (50,000 filas, en flujo).
- `EntregaExportacionTest`: DNI, código atado, 3 descargas; la clave se ve una vez y deja `EXPORTACION_CLAVE_DESCIFRADA`.
- `CierreTest`: sin exportación, rechazado; con ella, CERRADO, con `conservar_hasta` exacto con autorización y sin ella.
  `ModoConsultaCerradoTest` (hasta `conservar_hasta`).
- `BajaConDatosTest` (AP-6); `SupresionFichaMinimaTest` (AP-14, columna por columna); `VerificadorCadenaTachadaTest` (con acta
  pasa; sin acta o con otra huella falla); `RevisionConservacionTest`.
- Los pendientes de la sección 5: `PlantillasIfReplaceTest`, `ControlesVisiblesTest` (cuenta bancaria) y `ResumenDiarioTest`.
- Las que cuentan: **41 migraciones, 124 triggers, 146 huellas**.

**MySQL real (`mysql/AuditoriaSprint11FinColegioMySqlTest`). 1644 salvo aviso:**
- `exportacionHuellaNoCambia` (1143), `descargaCuartaFalla`, `cierreSinExportacionFalla`, `cierrePorElMismoOperadorFalla`;
- `conservarHastaManipuladoFalla` (1143 con cc_app y cc_sistema; 1644 como DBA), `conservarHastaExacto` (las 3 fórmulas);
- `bajaConDatosSinExportacionFalla`, `bajaConExportacionEntregadaPasa`;
- `sesionEnCerradoTrasConservarHastaFalla`, `apoderadoEnCerradoAntesPasa`;
- `tachadoSinActaFalla`, `tachadoDeOtraColumnaFalla`, `borradoDeBitacoraFalla` (también como DBA), `actaAprobadaPorQuienLaPropusoFalla`,
  `supresionConPlazoSinVencerFalla`;
- `suprimirSqlDeUnColegioNoTocaAOtro` (`suprimir.sql` con dos colegios: B queda igual fila por fila y su cadena verifica);
- el paso `reloj-real` en 0; las 8 de la suspensión aplicada (sección 5, punto 5); `huellas_objetos()` = 146 y arranque prod con
  124 y 22.

## 8. Riesgos
- **El tachado toca el corazón de la bitácora:** solo con `cc_supresion_en_curso` y la `huella_eventos` del acta, con la prueba
  de dos colegios.
- **Plazos de fuentes secundarias:** el abogado revisa la sección 2 y las cláusulas antes del primer cierre real.
- **Cambiar 27 usos del reloj** en 18 triggers de sprints anteriores: sus pruebas de MySQL deben seguir verdes sin cambios.
- **Tamaño de la exportación:** el tope es de 2 GB y se genera en flujo; si un colegio lo supera, se parte (se decide cuando pase).
- **H2 y los CHECK con fechas:** si H2 no acepta el CHECK de `conservar_hasta`, lo exige el trigger y la aplicación lo repite.

## 9. Terminado cuando
Una suspensión se propone y se aplica en producción y entrega al titular un ZIP cifrado cuya huella llega al correo de control,
sin una fila de otro colegio. El cierre deja `conservar_hasta` exacto y las familias consultan hasta esa fecha. La supresión con
acta borra y tacha lo de un solo colegio, y la cadena sigue verificando. `reloj-pendiente.txt` está en 0, ninguna plantilla usa
`th:if` con `th:replace`, la tarjeta de la cuenta bancaria tiene su ⚠ y «Ver detalle» y el resumen diario cuenta las
aprobaciones externas. El CI del PR en borrador está en verde (124 triggers, 22 funciones, 146 huellas).
