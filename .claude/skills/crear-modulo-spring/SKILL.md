---
name: crear-modulo-spring
description: Convenciones y pasos para crear un módulo o funcionalidad nueva en el backend Spring Boot del proyecto (migración, entidad JPA multi-colegio, repositorio, servicio con auditoría, DTO, controlador, vista Thymeleaf, permisos y pruebas). Úsala al implementar cualquier CRUD o caso de uso nuevo.
---

# Crear un módulo en Spring Boot

Stack: Spring Boot 4.1, Java 21, Spring Security 7, Spring Data JPA (Hibernate 7), Thymeleaf, Flyway, MySQL 8 (H2 en modo MySQL para desarrollo y pruebas) y Maven (`./mvnw`).

Notas de Spring Boot 4: usa `jakarta.*` (no `javax.*`); el starter web es `spring-boot-starter-webmvc`; las anotaciones de prueba están en paquetes nuevos (`org.springframework.boot.webmvc.test.autoconfigure.*`, `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`, `org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase`). Java 21 permite `record` para DTOs (Spring 7 los enlaza y Thymeleaf los lee con `th:field`).

## Estructura de paquetes (por módulo)
```
pe.edu.virgenmaria.cuentasclaras
├── colegio/             # Colegio (multi-colegio)
├── comun/               # BaseEntity, multicolegio (ContextoColegio), config (reloj, JPA), errores, inicio
├── seguridad/           # Usuario, Rol, ModuloApp (matriz de permisos), login, gestión de usuarios
├── auditoria/           # EventoAuditoria (solo inserción), AuditoriaService, bitácora e integridad
└── <modulo>/            # ej. cobranza
    ├── model/           # Entidades JPA y enums
    ├── repository/      # Interfaces Spring Data
    ├── service/         # Lógica de negocio con @Transactional
    ├── dto/             # records de entrada y salida con validaciones
    └── web/             # @Controller (Thymeleaf)
```
Las vistas van en `src/main/resources/templates/<modulo>/` y usan `fragments/layout.html` y `fragments/componentes.html`.

## Pasos
1. **Migración Flyway** `src/main/resources/db/migration/V<n>__<descripcion>.sql`:
   - Hibernate solo valida (`ddl-auto: validate`). Nunca edites una migración publicada: crea una nueva.
   - SQL compatible con MySQL 8 y con H2 en modo MySQL. Fechas en `DATETIME(6)` y dinero en `DECIMAL(10,2)`.
   - Toda tabla de negocio lleva `colegio_id BIGINT NOT NULL` (FK a `colegio`), `creado_en`, `creado_por`, `actualizado_en` y `version BIGINT NOT NULL DEFAULT 0`.
   - **FK compuestas con `colegio_id`**: cada tabla padre tiene `UNIQUE (id, colegio_id)` y cada hija la referencia con `FOREIGN KEY (padre_id, colegio_id) REFERENCES padre (id, colegio_id)`. Así la base rechaza que un registro apunte a otro colegio aunque falle el filtro de `@TenantId`.
   - **CHECK con columnas que admiten NULL**: escribe `col IS NOT NULL AND col ...`. En SQL, un CHECK que evalúa NULL **pasa**: `aprobado_por <> creado_por` no protege nada si `aprobado_por` es NULL. Ejemplo: `CHECK (estado <> 'ANULADA' OR (anulacion_aprobada_por IS NOT NULL AND anulacion_aprobada_por <> anulacion_solicitada_por))`.
   - **Agrega el GRANT de la tabla** en `scripts/mysql/02-permisos-tablas.sql`. Tablas financieras: INSERT y UPDATE, **nunca DELETE**. El job `mysql` del CI falla si falta.
   - **GRANT por columna para los campos financieros**: si un monto, una fecha de vencimiento, el alumno o la clave de idempotencia no deben cambiar, no des `UPDATE` sobre la tabla sino `GRANT UPDATE (estado, monto_pagado, ..., actualizado_en, version) ON cuentasclaras.<tabla>`. MySQL responde 1143 si alguien intenta cambiar otra columna. La lista debe coincidir **exactamente** con las columnas `updatable = true` de la entidad (incluidas `actualizado_en` y `version` de `BaseEntity`); compruébalo con una prueba (ver `InmutabilidadCuotasTest`), en `PermisosMySqlTest` (1143) y en `VerificadorPermisosBaseDatos` si la columna es crítica.
   - **Triggers de MySQL** (`scripts/mysql/03-triggers.sql`, los aplica `cc_migrador` después de migrar; H2 no los tiene): para reglas que el GRANT por columna no distingue (estados, «la suma del libro», «otra persona»).
     - **Van por tanda (por migración).** `CREATE TRIGGER` acepta un cuerpo que nombra una tabla que aún no existe, pero desde ese momento **todo UPDATE sobre la tabla del trigger falla con 1146**. Si la tabla llega en una migración posterior, publica una versión reducida del trigger y pásala a la final con esa migración (ver `docs/arquitectura/sprint-3-caja.md`, sección 6.3). El script del repositorio corresponde siempre a la última migración: nunca lo apliques antes de migrar.
     - **Compara con `<=>` o con `NOT EXISTS`, nunca con `=` a secas.** En un trigger, `IF NULL THEN` no entra (igual que un CHECK con NULL pasa): `IF NOT (NEW.estado <=> 'PENDIENTE')` o `IF NOT EXISTS (SELECT 1 FROM ... WHERE ...)` sí rechazan cuando la fila o el valor no existen.
     - Agrega una inserción imposible (colegio 0) de cada trigger nuevo a `VerificadorPermisosBaseDatos` y al paso `comprobar` del job `mysql` del CI: debe dar 1644.
   - **`saveAndFlush` antes de un INSERT que un trigger valida contra una fila que acabas de modificar.** Con IDENTITY, Hibernate hace el `INSERT` en el `save`, pero el `UPDATE` de una entidad ya gestionada espera al flush: sin el flush, el trigger ve la fila vieja y rechaza (en H2 no hay triggers, así que solo falla en MySQL). Ejemplos: la serie antes del comprobante, el descuento APROBADO antes de sus ajustes, el conteo de la caja antes del cierre y la caja CERRADA antes de su solicitud. Cúbrelo con un flujo completo en `PermisosMySqlTest`.
   - En producción la aplicación **no migra**: se despliega con `java -jar cuentas-claras.jar migrar` (usuario `cc_migrador`) y después se arranca con `cc_app`. Si faltan migraciones, la aplicación no arranca.
2. **Entidad** en `model/`:
   - Extiende `BaseEntity`: id, `colegioId` con `@TenantId`, `creadoEn`, `creadoPor`, `actualizadoEn` y `@Version version`.
   - Hibernate asigna `colegioId` al guardar y **filtra por él toda consulta JPA** (`findById`, consultas derivadas y JPQL). No pongas setter de `colegioId`.
   - Dinero: `@Column(precision = 10, scale = 2) BigDecimal`. Nunca `double` ni `float` (regla ArchUnit).
   - Estados con `@Enumerated(EnumType.STRING)`. Relaciones `LAZY` por defecto. Sin setters públicos: métodos con intención (`anular(motivo, aprobador)`).
3. **Repositorio**: `JpaRepository<Entidad, Long>`.
   - **Nada de SQL nativo**: Hibernate no filtra las consultas nativas por colegio (regla ArchUnit). Usa consultas derivadas o JPQL.
   - Nada de `delete*`: lo financiero se anula y los usuarios se desactivan (regla ArchUnit).
   - Para contadores o saldos que cambian en paralelo: `@Lock(PESSIMISTIC_WRITE)` en una consulta JPQL.
   - Tablas financieras: extiende `Repository<Entidad, Long>` y declara solo lo que usas (así no hereda `delete*`), sin `@Modifying` ni JPQL `update` (reglas ArchUnit de `cobranza`).
4. **DTOs** (`record`): `XxxRequest` (`@NotNull`, `@Positive`, `@Size`, con mensajes en español) y `XxxResponse` o `XxxVista`. **Nunca** expongas una entidad en un controlador (regla ArchUnit).
5. **Servicio**:
   - Toda la lógica, con `@Transactional` en los métodos que escriben.
   - Protege por rol también aquí: `@PreAuthorize("hasAnyRole(...)")`. La matriz de URL no basta.
   - Si es una operación financiera o sensible, llama a `AuditoriaService.registrar(accion, entidad, id, anterior, nuevo, detalle)` dentro de la misma transacción. Si la operación falla, el evento tampoco se guarda. Nunca pases claves, hashes ni datos sensibles. Agrega la acción a `AccionAuditoria` con su descripción en lenguaje claro.
   - Nunca borres: cambia el estado a `ANULADO` con motivo (de 10 a 500 caracteres) y aprobador. **Quien cobra no aprueba.**
   - Errores de negocio: `ReglaNegocioException` (mensaje claro para el usuario). Recurso inexistente o de otro colegio: `RecursoNoEncontradoException` (404).
   - **Fechas**: siempre `LocalDateTime.now(reloj)` con el `Clock` inyectado (hora de Lima). Nunca `now()` sin reloj (regla ArchUnit). Se guardan tal cual en la base (`java_time_use_direct_jdbc`).
6. **Procesos sin usuario** (tareas programadas, arranque, listeners de login): no hay colegio en sesión, así que el contexto queda en NINGUNO y no se ve nada. Usa `ContextoColegio.en(colegioId, () -> transaccion.execute(...))`: primero el colegio y **después** la transacción, porque cambiar de colegio con una transacción abierta lanza excepción. `ContextoColegio.comoSistema(...)` ve todos los colegios y solo lo usan las clases autorizadas en `ReglasArquitecturaTest`.
   - **Dinero que entra sin una persona** (sprint 4): lo registra un **actor de sistema** (`ActorSistema`: `sistema.pasarela`, `sistema.recaudacion`, `sistema.conciliacion`), nunca una persona. Corre con `EjecucionComoSistema.como(actor, colegioId, ...)` desde una clase del paquete `..proceso..` (regla ArchUnit) y su servicio exige `hasRole('SISTEMA_...')` con `Propagation.MANDATORY`. Ninguna persona puede llamarse `sistema...` (CHECK y validación).
   - **Consulta a la fuente antes de registrar dinero**: un aviso (webhook) es solo un aviso; el pago se registra cuando lo confirma la consulta a la pasarela con su llave secreta.
   - **Confirmación a ciegas de todo archivo que mueve dinero** (recaudación, extracto): lo sube una persona y otra escribe, sin verlo en pantalla, el total o el saldo que muestra su app del banco; nunca quien lo subió (o preparó su cuenta). Los intentos fallidos se cuentan y al máximo el archivo queda RECHAZADO.
   - **Archivo original con SHA-256** (`RegistroArchivos`, tabla `archivo_cargado`, solo inserción): la vista previa vive en la sesión de quien subió y el registro vuelve a leer el archivo y compara su huella.
   - **Avisos a las familias = outbox en la MISMA transacción** (sprint 5): lo que mueve dinero (pago, anulación, descuento) publica un evento y `comunicacion` lo escucha con `@EventListener` **síncrono** (no `AFTER_COMMIT`) y crea el `mensaje` con `CreadorMensajes` (`Propagation.MANDATORY`, clave idempotente y `saveAndFlush`). Si el mensaje no se puede crear, la operación tampoco se guarda. El envío es aparte (`DespachoMensajes`, como `sistema.mensajeria`). Ninguna persona escribe texto libre a un apoderado: las plantillas son el enum `PlantillaMensaje` (sin mencionar evaluaciones ni amenazar; lo revisa `PlantillasMensajeTest`).
   - **El destino es siempre el contacto REGISTRADO** del destinatario (nunca un número escrito en pantalla); en MySQL lo exige `trg_mensaje_nace`. Cambiar el celular o el correo de un apoderado requiere su solicitud aprobada por otra persona.
   - **Los secretos se generan en el envío**: un enlace o token de un solo uso lo genera el proceso de envío (`EnlacesActivacion.generarParaMensaje`, solo desde `DespachoMensajes`), se guarda solo su hash y nunca vuelve a la pantalla de quien dio el acceso.
   - **Día hábil = `CalendarioHabil`** (sprint 5, tanda 3): inyecta el bean (`DiasHabiles`) para todo «día hábil siguiente/anterior», ventanas y alertas. Combina lunes a viernes, los 16 feriados nacionales (`FeriadosNacionales`, en el código) y los días no laborables del colegio (`feriado`, solo a futuro y solo Promotoría o Dirección). Los estáticos `Calendario.siguienteDiaHabil/anteriorDiaHabil` están `@Deprecated` y ArchUnit los prohíbe fuera de `comun.fecha`; en pruebas puras usa `DiasHabiles.LUNES_A_VIERNES` o `NACIONALES`. Para mensajes a familias: `admiteMensajes` (lunes a sábado sin feriados) y la ventana de 08:00 a 20:00 del despacho.
   - **Muestreo al azar = semilla secreta guardada** (`SemillasMuestreo.de(ambito, fecha)`, tabla `semilla_muestreo` de solo inserción), nunca `new Random(fecha)`.
7. **Controlador**: sin lógica.
   - Valida con `@Valid`, delega al servicio y devuelve la vista. Para errores de negocio, `try/catch (ReglaNegocioException)` y mostrar el mensaje.
   - El usuario en sesión llega con `@AuthenticationPrincipal UsuarioAutenticado`.
   - **Permisos**: agrega o activa el módulo en `ModuloApp` (rutas y roles). De ahí salen las reglas de URL y el menú. Toda ruta fuera de la matriz se niega.
8. **Vista**: usa `fragments/layout :: pagina(titulo, ~{::main})` y los componentes (`campo`, `boton`, `badge`, `alerta`, `estadoVacio`, `modalConfirmacion` con motivo obligatorio). Usa `th:text`, nunca `th:utext`. Sin `style=`, `<style>`, `<script>` en línea ni `onclick` (CSP; lo revisa `InterfazBaseTest`). CSS solo con variables de `tokens.css`.
9. **Pruebas** (siempre con el perfil `test`; no hay perfil por defecto):
   - Servicio puro: JUnit 5 + Mockito.
   - JPA: `@PruebaJpa`. Si la prueba usa `ContextoColegio.en(...)`, ponle `@Transactional(propagation = NOT_SUPPORTED)` y limpia con `LimpiezaBaseDatos`.
   - Web o integración: `@PruebaIntegracion` (aplicación completa con MockMvc) con `@ComoUsuario(roles = ..., colegioId = ...)` o `UsuariosDePrueba.como(...)`. No uses `@WithMockUser`: deja el colegio en NINGUNO.
   - Siempre: una prueba de **aislamiento** (el colegio B no ve ni toca datos del A, y recibe 404) y una de **auditoría** (el evento queda con su valor anterior y nuevo).
   - Si la funcionalidad depende de MySQL (permisos o tipos), cúbrela en `PermisosMySqlTest` (job `mysql` del CI).
   - Hora y fechas: usa el `Clock` inyectado o `@Import(ConfiguracionRelojAjustable.class)` con `RelojAjustable`, nunca `LocalDateTime.now()`. La suite debe pasar con `-DargLine=-Duser.timezone=America/Los_Angeles`.
   - Si llamas a un servicio con `UsuariosDePrueba.iniciarSesion(...)` en una prueba con MockMvc, limpia el contexto después: MockMvc lo reutiliza.
   - Cada corrección de seguridad lleva una prueba que falle sin ella.
10. **Verifica**: `./mvnw -B verify` (compila, aplica las migraciones sobre H2, corre las pruebas y las reglas ArchUnit).

## Lista de control antes de terminar
- [ ] Ningún `double` o `float` para dinero.
- [ ] La entidad extiende `BaseEntity` y no hay SQL nativo.
- [ ] Ningún borrado físico de datos financieros; la anulación tiene motivo y aprobador distinto de quien cobra.
- [ ] Toda operación financiera o sensible queda auditada.
- [ ] El módulo está en `ModuloApp` y el servicio tiene `@PreAuthorize`.
- [ ] El GRANT de cada tabla nueva está en `scripts/mysql/02-permisos-tablas.sql`; los campos financieros inmutables usan GRANT por columna.
- [ ] Las FK a tablas de negocio son compuestas con `colegio_id`, y los CHECK sobre columnas que admiten NULL dicen `col IS NOT NULL AND ...`.
- [ ] Los triggers nuevos van en la tanda de su migración (nunca nombran una tabla que aún no existe), comparan con `<=>` o `NOT EXISTS` y tienen su inserción imposible en el verificador de prod y en el CI.
- [ ] Antes de cada INSERT que un trigger valida contra una fila modificada en la misma transacción hay un `saveAndFlush`, y un flujo completo en `PermisosMySqlTest` lo prueba con los permisos mínimos.
- [ ] Lo que registra el sistema lo hace un actor de sistema desde `..proceso..`; lo que viene de un archivo del banco se confirma a ciegas por otra persona y guarda su original con SHA-256.
- [ ] Ninguna entidad se expone en un controlador.
- [ ] Hay pruebas de aislamiento y de auditoría, pasan, y la salida real queda reportada.
