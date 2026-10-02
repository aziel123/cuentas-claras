# Sprint 2 · Correcciones tras la auditoría antifraude

> Contexto: la auditoría reprodujo ataques en los que **una sola persona reduce lo que una familia debe** (el patrón del
> robo de S/ 70,000). Cada corrección lleva una prueba que reproduce el ataque y falla sin ella.
> Migración nueva: **`V8__correcciones_antifraude.sql`** (V5–V7 no se tocan). Triggers de MySQL en
> `scripts/mysql/03-triggers.sql` (fuera de Flyway: H2 no los soporta).

## 1. Decisiones

### C1. Saldo inicial que bloquea pensiones futuras (crítico)
- La línea PENSION y MATRICULA guarda **el año real de la deuda** (`anio_deuda`) y, si es pensión, su mes. La
  obligación (`PEN-2026-09`, `MAT-2026`) se calcula con ellos, nunca con el año del lote.
- El año de la deuda **debe ser el año del lote** (una deuda de 2025 va en un lote de 2025).
- La deuda **vence en o antes de la fecha de corte** del lote, y la fecha de corte nunca es futura. Una pensión no puede
  ser de un mes posterior al corte, y su vencimiento cae dentro de su propio mes.
- Al **aprobar un plan**, si el año tiene lotes confirmados, el plan debe cobrar desde (`cobro_desde`) **después** de la
  fecha de corte más reciente.
- Cada cuota que el generador **omite** porque la deuda ya existía queda en la bitácora por alumno y **resaltada**
  (`CUOTA_OMITIDA_DEUDA_EXISTENTE`).
- Antes de confirmar un lote, la pantalla muestra las **cuotas futuras que quedarían bloqueadas** (la deuda coincide con
  una cuota que el plan vigente generaría o ya generó). Si hay alguna, **no se puede confirmar**.

### A1. Cebo y cambio
- Plan: BORRADOR → **ENVIADO** (bloqueado) → APROBADO, o devuelto a BORRADOR con motivo. Solo se edita en BORRADOR.
- Aprobar, devolver y confirmar envían en el formulario la **versión** (`@Version`) que vio el aprobador. Si cambió:
  «El plan cambió desde que lo abriste; revísalo de nuevo» (y lo mismo con «El lote…»).

### A2. Saldo inicial: total del contador y deudas inventadas
- (a) Quien confirma escribe **a ciegas** el total del informe del contador (la pantalla no le muestra el total
  declarado ni la suma). Debe coincidir; si no, se rechaza y queda auditado. La base guarda `total_confirmado` y un CHECK
  exige que sea igual al declarado en un lote CONFIRMADO.
- (b) El concepto OTRO solo admite una **lista corta configurable** (`cuentasclaras.saldo-inicial.conceptos-otros`, por
  defecto: Taller de verano, Uniforme escolar, Materiales educativos, Excursión). Una descripción que parezca pensión o
  matrícula (o un mes) se rechaza.
- (c) No se aceptan líneas de alumnos retirados ni sin matrícula activa en el año de la deuda.
- (d) La pantalla del lote muestra, por línea, el cronograma del alumno en ese año (cuotas y saldo) y marca duplicados
  (en el lote o ya en su cronograma).

### A3. Fecha de matrícula tardía
- La fecha por defecto es el **inicio de clases**. Con esa fecha (o antes) el cronograma es completo.
- Una fecha **posterior al inicio de clases** (ingreso tardío, que recorta pensiones) crea una **solicitud
  `FECHA_MATRICULA`**. Opción elegida (la más segura): la matrícula se registra con el inicio de clases y **cronograma
  completo**; si se aprueba la solicitud, se cambia la fecha y se **anulan** las pensiones anteriores al ingreso (nunca
  la matrícula), con solicitante y aprobador distintos y auditado.
- Desviación documentada: una fecha **anterior** al inicio de clases no recorta nada y se registra sin solicitud.
- Reporte **«Ingresos tardíos»** en `/aprobaciones`.

### A4. Desvío de avisos
- Cambiar el celular o el correo de un apoderado existente, o el responsable de pago de un alumno, crea una
  **solicitud** que aprueba otra persona de Promotoría o Dirección. Los demás datos (nombres, parentesco) se corrigen al
  momento.
- La importación ya **no** cambia contactos ni responsables existentes: los muestra en la revisión como «Requiere
  solicitud» y no los aplica. Un apoderado **nuevo** sí trae su contacto.
- **Sprint 4:** al aprobarse un cambio de contacto se avisará también al contacto anterior.

### A5. Doble control con una segunda cuenta
- Solo **Promotoría** crea usuarios con ADMINISTRACION o CAJA (además de PROMOTOR y DIRECTOR), les asigna esos roles o les
  restablece la clave. Dirección ya no.
- Es **participante** (no puede aprobar) quien **creó o restableció la clave** de la cuenta de un autor en los últimos 30
  días (`ControlParticipantes`, con `creado_por` y `clave_restablecida_por` del usuario). Se aplica a planes, lotes y
  solicitudes.

### A6. Retiro sin aprobación
- El retiro de un alumno es una **solicitud `RETIRO_ALUMNO`** que aprueba otra persona. La fecha de retiro no puede ser
  anterior a la fecha de matrícula del año.
- Alerta **«Matrícula retirada sin cuotas»** en la tarjeta *Para revisar* de Promotoría (puerto `comun.alertas`).

### Módulo `aprobaciones` (genérico y pequeño)
- `SolicitudCambio`: tipo, entidad y su id, resumen legible, datos (JSON pequeño), motivo, estado
  PENDIENTE/APROBADA/RECHAZADA, solicitante, quien resolvió, comentario. Una sola pendiente por tipo y entidad (UNIQUE con
  `pendiente` TRUE/NULL). CHECK: quien resuelve ≠ solicitante; solicitante = `creado_por`; rechazo con comentario.
- `RegistroSolicitudes` (sin rol propio: lo usan los servicios que ya lo exigieron) crea y audita.
- `ManejadorSolicitud` por tipo, implementado en el módulo dueño del dato (`alumnos`, `cobranza`): valida de nuevo y
  aplica el cambio al aprobar, en la misma transacción. `aprobaciones` no depende de `alumnos` ni de `cobranza` (ArchUnit).
- `BandejaAprobaciones` (`/aprobaciones`, Promotoría y Dirección): aprobar o rechazar con motivo; autoaprobación auditada
  y rechazada (`noRollbackFor`). El sprint 3 agrega anulaciones de pago y cierres de caja con un manejador nuevo cada uno.
- La solicitud de anulación de cuota existente pasa a ser `ANULACION_CUOTA` en esta bandeja.

### M1. Todos los editores
- `plan_pension.editores` guarda `,usuario1,usuario2,` (todos los que editaron alguna vez). Nadie de esa lista ni quien
  lo envió aprueba. CHECK: `editores NOT LIKE CONCAT('%,', aprobado_por, ',%')`.

### M2. Permisos de MySQL
- GRANT de UPDATE **por columna** también en `plan_pension`, `lote_saldo_inicial`, `linea_saldo_inicial` y
  `solicitud_cambio` (las mismas columnas `updatable = true` de cada entidad; lo comprueba una prueba).
- `scripts/mysql/03-triggers.sql` (lo aplica `cc_migrador` después de migrar; requiere una vez
  `SET PERSIST log_bin_trust_function_creators = 1` como administrador):
  - plan no BORRADOR: no cambian montos, fechas, configuración ni editores; un plan cerrado no cambia de estado;
  - un plan y un lote nacen en BORRADOR;
  - líneas: no se agregan ni se quitan si su lote no está en BORRADOR;
  - lote CONFIRMADO o DESCARTADO no cambia de estado.
- `VerificadorPermisosBaseDatos` (prod) no arranca si las columnas de esas tablas están abiertas (1143 esperado) o si
  faltan los triggers. Como `cc_app` no puede leer `information_schema.TRIGGERS` (exige el privilegio TRIGGER), la
  comprobación es por comportamiento: un INSERT imposible que el trigger rechaza con 1644 antes de tocar la fila.
- Pendiente para el sprint 3: la transición de una cuota a PAGADA sin pago (la controlará el módulo de pagos).

### Bajo
- B1. Promotoría (solo lectura) ve DNI, celular y correo **enmascarados** en la ficha y la familia.
- B2. Textos libres (nombre de familia, descripciones, referencia del informe, motivos y comentarios) no empiezan con
  `=`, `+`, `-` ni `@` (`TextoSeguro`).
- B3. Una sola importación en proceso por usuario, y como máximo `cuentasclaras.excel.max-textos-compartidos` (20,000)
  textos compartidos en el archivo.

## 2. Migración `V8__correcciones_antifraude.sql`
- `linea_saldo_inicial.anio_deuda` (+ CHECK por concepto; se completa con el año del lote en filas existentes).
- `lote_saldo_inicial.total_confirmado` (+ CHECK en CONFIRMADO).
- `plan_pension`: `enviado_por/en`, `devuelto_por/en`, `motivo_devolucion`, `editores`; CHECK de estado con ENVIADO,
  de envío y de aprobación (editores, enviado_por).
- `solicitud_cambio` con sus CHECK, UNIQUE de pendiente y FK a `colegio`.

## 3. Riesgos aceptados
- `log_bin_trust_function_creators` relaja una protección del binlog para funciones; la alternativa es aplicar
  `03-triggers.sql` como administrador.
- La aprobación de una fecha de matrícula tardía no puede mover el vencimiento de la cuota de matrícula (es inmutable):
  solo se anulan pensiones.
