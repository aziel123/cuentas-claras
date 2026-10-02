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
   - **Agrega el GRANT de la tabla** en `scripts/mysql/02-permisos-tablas.sql`. Tablas financieras: INSERT y UPDATE, **nunca DELETE**. El job `mysql` del CI falla si falta.
2. **Entidad** en `model/`:
   - Extiende `BaseEntity`: id, `colegioId` con `@TenantId`, `creadoEn`, `creadoPor`, `actualizadoEn` y `@Version version`.
   - Hibernate asigna `colegioId` al guardar y **filtra por él toda consulta JPA** (`findById`, consultas derivadas y JPQL). No pongas setter de `colegioId`.
   - Dinero: `@Column(precision = 10, scale = 2) BigDecimal`. Nunca `double` ni `float` (regla ArchUnit).
   - Estados con `@Enumerated(EnumType.STRING)`. Relaciones `LAZY` por defecto. Sin setters públicos: métodos con intención (`anular(motivo, aprobador)`).
3. **Repositorio**: `JpaRepository<Entidad, Long>`.
   - **Nada de SQL nativo**: Hibernate no filtra las consultas nativas por colegio (regla ArchUnit). Usa consultas derivadas o JPQL.
   - Nada de `delete*`: lo financiero se anula y los usuarios se desactivan (regla ArchUnit).
   - Para contadores o saldos que cambian en paralelo: `@Lock(PESSIMISTIC_WRITE)` en una consulta JPQL.
4. **DTOs** (`record`): `XxxRequest` (`@NotNull`, `@Positive`, `@Size`, con mensajes en español) y `XxxResponse` o `XxxVista`. **Nunca** expongas una entidad en un controlador (regla ArchUnit).
5. **Servicio**:
   - Toda la lógica, con `@Transactional` en los métodos que escriben.
   - Protege por rol también aquí: `@PreAuthorize("hasAnyRole(...)")`. La matriz de URL no basta.
   - Si es una operación financiera o sensible, llama a `AuditoriaService.registrar(accion, entidad, id, anterior, nuevo, detalle)` dentro de la misma transacción. Si la operación falla, el evento tampoco se guarda. Nunca pases claves, hashes ni datos sensibles. Agrega la acción a `AccionAuditoria` con su descripción en lenguaje claro.
   - Nunca borres: cambia el estado a `ANULADO` con motivo (de 10 a 500 caracteres) y aprobador. **Quien cobra no aprueba.**
   - Errores de negocio: `ReglaNegocioException` (mensaje claro para el usuario). Recurso inexistente o de otro colegio: `RecursoNoEncontradoException` (404).
   - **Fechas**: siempre `LocalDateTime.now(reloj)` con el `Clock` inyectado (hora de Lima). Nunca `now()` sin reloj (regla ArchUnit). Se guardan tal cual en la base (`java_time_use_direct_jdbc`).
6. **Procesos sin usuario** (tareas programadas, arranque, listeners de login): no hay colegio en sesión, así que el contexto queda en NINGUNO y no se ve nada. Usa `ContextoColegio.en(colegioId, () -> transaccion.execute(...))`: primero el colegio y **después** la transacción, porque cambiar de colegio con una transacción abierta lanza excepción. `ContextoColegio.comoSistema(...)` ve todos los colegios y solo lo usan las clases autorizadas en `ReglasArquitecturaTest`.
7. **Controlador**: sin lógica.
   - Valida con `@Valid`, delega al servicio y devuelve la vista. Para errores de negocio, `try/catch (ReglaNegocioException)` y mostrar el mensaje.
   - El usuario en sesión llega con `@AuthenticationPrincipal UsuarioAutenticado`.
   - **Permisos**: agrega o activa el módulo en `ModuloApp` (rutas y roles). De ahí salen las reglas de URL y el menú. Toda ruta fuera de la matriz se niega.
8. **Vista**: usa `fragments/layout :: pagina(titulo, ~{::main})` y los componentes (`campo`, `boton`, `badge`, `alerta`, `estadoVacio`, `modalConfirmacion` con motivo obligatorio). Usa `th:text`, nunca `th:utext`. Sin `style=`, `<style>`, `<script>` en línea ni `onclick` (CSP; lo revisa `InterfazBaseTest`). CSS solo con variables de `tokens.css`.
9. **Pruebas** (siempre con el perfil `test`):
   - Servicio puro: JUnit 5 + Mockito.
   - JPA: `@PruebaJpa`. Si la prueba usa `ContextoColegio.en(...)`, ponle `@Transactional(propagation = NOT_SUPPORTED)` y limpia con `LimpiezaBaseDatos`.
   - Web o integración: `@PruebaIntegracion` (aplicación completa con MockMvc) con `@ComoUsuario(roles = ..., colegioId = ...)` o `UsuariosDePrueba.como(...)`. No uses `@WithMockUser`: deja el colegio en NINGUNO.
   - Siempre: una prueba de **aislamiento** (el colegio B no ve ni toca datos del A, y recibe 404) y una de **auditoría** (el evento queda con su valor anterior y nuevo).
   - Si la funcionalidad depende de MySQL (permisos o tipos), cúbrela en `PermisosMySqlTest` (job `mysql` del CI).
10. **Verifica**: `./mvnw -B verify` (compila, aplica las migraciones sobre H2, corre las pruebas y las reglas ArchUnit).

## Lista de control antes de terminar
- [ ] Ningún `double` o `float` para dinero.
- [ ] La entidad extiende `BaseEntity` y no hay SQL nativo.
- [ ] Ningún borrado físico de datos financieros; la anulación tiene motivo y aprobador distinto de quien cobra.
- [ ] Toda operación financiera o sensible queda auditada.
- [ ] El módulo está en `ModuloApp` y el servicio tiene `@PreAuthorize`.
- [ ] El GRANT de cada tabla nueva está en `scripts/mysql/02-permisos-tablas.sql`.
- [ ] Ninguna entidad se expone en un controlador.
- [ ] Hay pruebas de aislamiento y de auditoría, pasan, y la salida real queda reportada.
