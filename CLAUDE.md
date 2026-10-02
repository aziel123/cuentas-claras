# Cuentas Claras · Colegio Virgen María

Plataforma de gestión escolar para el **Colegio Virgen María** (colegio privado del Perú). El primer módulo es **cobranza con controles antifraude**: el colegio perdió más de S/ 70,000 por falta de control en los cobros. Después vienen el módulo académico (notas, asistencia, SIAGIE) y el de comunicación con los padres.

- Plan de trabajo por sprints: `docs/plan-de-desarrollo.md`
- Prototipo navegable (referencia visual y de flujos): `docs/prototipo/cuentas-claras.html`
- Kit de descubrimiento con el colegio: `docs/ux/`
- Marca: azul `#1b4f9c` y celeste `#38aee6` (ver la skill `sistema-diseno`)

## Stack
Spring Boot 4.1 · Java 21 · Spring Data JPA · Thymeleaf · Flyway · MySQL 8 (H2 en modo MySQL en desarrollo y pruebas) · Maven (`./mvnw`).
Paquete base: `pe.edu.virgenmaria.cuentasclaras`.

## Comandos
- Compilar y probar todo: `./mvnw -B verify`
- Ejecutar en local (perfil `dev`, H2 en memoria): `./mvnw spring-boot:run` → http://localhost:8080
- Producción: perfil `prod` con las variables `DB_URL`, `DB_USUARIO` y `DB_CLAVE`.

## Base de datos
- El esquema se define **solo** con migraciones Flyway en `src/main/resources/db/migration/` (`V<n>__descripcion.sql`).
- Hibernate valida el esquema (`ddl-auto: validate`) y no lo modifica.
- Nunca edites una migración ya publicada: crea una nueva.

## Skills del proyecto (`.claude/skills/`)
- `contexto-colegio`: dominio, actores, glosario, **reglas antifraude obligatorias** y normativa. Cárgala siempre.
- `crear-modulo-spring`: convenciones de backend (paquetes, capas, dinero, auditoría, migraciones, pruebas).
- `sistema-diseno`: marca azul y celeste, tokens, componentes, patrones de pantalla y accesibilidad.
- `evaluacion-ux`: entrevistas, personas, journey maps, heurísticas, pruebas de usabilidad y métricas.

## Agentes del proyecto (`.claude/agents/`)
| Agente | Cuándo usarlo |
|---|---|
| `arquitecto-software` | Antes de codear un módulo: modelo de datos y plan |
| `backend-spring` | Implementar migraciones, entidades, servicios, controladores y pruebas |
| `qa-tester` | Diseñar y escribir los casos de prueba |
| `auditor-seguridad-antifraude` | Revisar todo cambio que toque dinero, roles o datos de alumnos |
| `disenador-ui` | Diseñar o revisar pantallas y plantillas Thymeleaf |
| `investigador-ux` | Entrevistas, personas, flujos, usabilidad y microcopy |

Flujo sugerido para una funcionalidad: `investigador-ux` → `arquitecto-software` → `disenador-ui` → `backend-spring` → `qa-tester` → `auditor-seguridad-antifraude`.

## Reglas no negociables
- Dinero siempre en `BigDecimal` (escala 2). Nunca `double`.
- Los pagos y las cuotas no se borran ni se editan: se anulan con motivo y aprobación.
- Toda operación financiera queda en la auditoría inmutable.
- Toda entidad de negocio lleva `colegioId` y toda consulta filtra por él.
- Quien cobra no aprueba (segregación de funciones).
- Nada de credenciales en el código: van en variables de entorno.
