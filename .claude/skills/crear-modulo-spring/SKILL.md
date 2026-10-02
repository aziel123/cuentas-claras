---
name: crear-modulo-spring
description: Convenciones y pasos para crear un módulo o funcionalidad nueva en el backend Spring Boot del proyecto (entidad JPA, repositorio, servicio, DTO, controlador, vista Thymeleaf y pruebas). Úsala al implementar cualquier CRUD o caso de uso nuevo.
---

# Crear un módulo en Spring Boot

Stack: Spring Boot 4.1, Java 21, Spring Data JPA, Thymeleaf, Flyway, MySQL 8 (H2 en modo MySQL para desarrollo y pruebas) y Maven (`./mvnw`).

Notas de Spring Boot 4: usa `jakarta.*` (no `javax.*`); el starter web es `spring-boot-starter-webmvc`; las anotaciones de prueba están en paquetes nuevos (`org.springframework.boot.webmvc.test.autoconfigure.*`, `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`). Java 21 permite `record` para DTOs.

## Estructura de paquetes (por módulo)
```
pe.edu.virgenmaria.cuentasclaras
├── colegio/             # Colegio (multi-colegio)
├── comun/               # BaseEntity, excepciones, utilidades de dinero y fechas
├── seguridad/           # Usuario, Rol, configuración de Spring Security
├── auditoria/           # EventoAuditoria (solo inserción) y AuditoriaService
└── <modulo>/            # ej. cobranza
    ├── model/           # Entidades JPA y enums
    ├── repository/      # Interfaces Spring Data
    ├── service/         # Lógica de negocio con @Transactional
    ├── dto/             # Objetos de entrada y salida con validaciones
    └── web/             # @Controller (Thymeleaf) o @RestController
```
Las vistas van en `src/main/resources/templates/<modulo>/` y los fragmentos compartidos en `templates/fragments/`.

## Pasos
1. **Migración Flyway** en `src/main/resources/db/migration/V<n>__<descripcion>.sql`. El esquema lo define Flyway; Hibernate solo valida (`ddl-auto: validate`). Nunca edites una migración ya publicada: crea una nueva. Escribe SQL compatible con MySQL 8 y con H2 en modo MySQL.
2. **Entidad** en `model/`:
   - Extiende `BaseEntity` (id, `colegioId`, `creadoEn`, `creadoPor`, `actualizadoEn`).
   - Dinero: `@Column(precision = 10, scale = 2) private BigDecimal monto;`
   - Estados como `@Enumerated(EnumType.STRING)`.
   - Relaciones `LAZY` por defecto.
3. **Repositorio**: `JpaRepository<Entidad, Long>`. Métodos siempre con `colegioId`, por ejemplo `findByColegioIdAndAlumnoId(...)`.
4. **DTOs** (preferible `record`): `XxxRequest` (con `@NotNull`, `@Positive`, `@Size`) y `XxxResponse`. Mapeo manual o con un mapper simple.
5. **Servicio**:
   - Contiene toda la lógica. Usa `@Transactional` en los métodos que escriben.
   - Si la operación es financiera, llama a `AuditoriaService.registrar(...)` y **nunca** borra; cambia el estado a `ANULADO` con motivo y aprobador.
   - Lanza excepciones de negocio (`ReglaNegocioException`) con mensajes en español claros para el usuario.
6. **Controlador**:
   - Sin lógica. Valida con `@Valid`, delega al servicio y devuelve la vista o el DTO.
   - Restringe por rol con `@PreAuthorize("hasRole('...')")` cuando exista Spring Security.
7. **Vista** (si aplica): usa los fragmentos y tokens de la skill `sistema-diseno`. Usa `th:text`, nunca `th:utext` con datos de usuario.
8. **Pruebas**:
   - `service`: JUnit 5 + Mockito.
   - `web`: `@WebMvcTest`.
   - `repository` (si hay consultas propias): `@DataJpaTest`.
9. **Verifica**: `./mvnw -B verify` (compila, aplica las migraciones sobre H2 y corre todas las pruebas).

## Lista de control antes de terminar
- [ ] Ningún `double` o `float` para dinero.
- [ ] Toda consulta filtra por `colegioId`.
- [ ] Ningún borrado físico de datos financieros.
- [ ] Toda operación financiera queda auditada.
- [ ] Ninguna entidad se expone directamente en la API.
- [ ] Hay pruebas, pasan, y la salida real queda reportada.
