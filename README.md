# Cuentas Claras

Plataforma de gestión escolar del **Colegio Virgen María**. Empieza por la cobranza con controles antifraude: cada pago emite su comprobante, avisa a la familia, queda en una auditoría que no se puede borrar y se concilia en el cierre de caja diario.

## En tu PC con MySQL (Docker)
La forma más simple de probarla completa, con MySQL y los mismos controles que producción:
```bash
cp .env.ejemplo .env      # y cambia las claves
docker compose up --build # luego abre http://localhost:8080
```
Guía paso a paso (Windows, Mac y Linux): `docs/operacion/instalacion-local.md`.

## Requisitos (desarrollo sin Docker)
- Java 21
- No hace falta instalar Maven: el proyecto trae `./mvnw`.
- Para desarrollo no hace falta base de datos: el perfil `dev` usa H2 en memoria.

## Uso
```bash
./mvnw -B verify          # compila, aplica migraciones y corre las pruebas
./mvnw spring-boot:run    # levanta la app en http://localhost:8080 con el perfil dev
```

Salud de la aplicación: `GET /actuator/health`.

No hay perfil por defecto: `./mvnw spring-boot:run` ya usa `dev`, pero el jar necesita el perfil explícito (`SPRING_PROFILES_ACTIVE=dev java -jar ...` en local). Sin perfil, o en producción con la clave HMAC de desarrollo, la aplicación no arranca.

### Usuarios de demostración (solo perfil `dev`)
Al arrancar con H2 en memoria y sin usuarios se crean estos usuarios, todos con la clave `demo-cuentas-claras-2026` (se cambia con la variable `CC_DEMO_CLAVE`):

| Usuario | Rol | Colegio |
|---|---|---|
| `promotor` | Promotoría | Colegio Virgen María |
| `director` | Dirección | Colegio Virgen María |
| `administracion` | Administración | Colegio Virgen María |
| `caja` | Caja | Colegio Virgen María |
| `docente` | Docente | Colegio Virgen María |
| `apoderado` | Apoderado | Colegio Virgen María |
| `promotor.b` | Promotoría | Colegio de Prueba B (para ver el aislamiento entre colegios) |

## Producción
Perfil `prod` con MySQL 8 (base `cuentasclaras`, `utf8mb4`). La primera vez, crea los usuarios `cc_migrador` y `cc_app` con los scripts de `scripts/mysql/` (`docs/operacion/mysql-usuarios.md`). En cada despliegue hay dos pasos separados, y la aplicación nunca recibe la clave del migrador:
```bash
# 1. Migrar (Flyway con cc_migrador) y terminar, sin servidor web
DB_URL="jdbc:mysql://HOST:3306/cuentasclaras" DB_MIGRADOR_USUARIO="cc_migrador" DB_MIGRADOR_CLAVE="..." \
java -jar target/cuentas-claras-*.jar migrar

# 2. Arrancar la aplicación con cc_app
SPRING_PROFILES_ACTIVE=prod \
DB_URL="jdbc:mysql://HOST:3306/cuentasclaras" DB_USUARIO="cc_app" DB_CLAVE="..." \
AUDITORIA_CLAVE_HMAC="..." \
CC_PROMOTOR_USUARIO="..." CC_PROMOTOR_NOMBRE="..." CC_PROMOTOR_CLAVE="..." \
java -jar target/cuentas-claras-*.jar
```

| Variable | Obligatoria | Para qué |
|---|---|---|
| `DB_URL` | Sí | Conexión JDBC a MySQL |
| `DB_USUARIO`, `DB_CLAVE` | Sí | Usuario de la aplicación (`cc_app`): sin UPDATE ni DELETE sobre la bitácora. Si los tiene, la aplicación no arranca |
| `DB_MIGRADOR_USUARIO`, `DB_MIGRADOR_CLAVE` | Solo para `migrar` | Usuario de Flyway (`cc_migrador`), el único que crea o cambia tablas. **No** van en el entorno de la aplicación |
| `AUDITORIA_CLAVE_HMAC` | Sí | Sella la bitácora (32 caracteres o más). Custodia: `docs/operacion/custodia-clave-auditoria.md` |
| `CC_PROXIES_INTERNOS` | No | Expresión regular con las IP de tus proxies inversos; solo de ellos se acepta X-Forwarded-For (por defecto: loopback y redes privadas) |
| `CC_PROMOTOR_USUARIO`, `CC_PROMOTOR_NOMBRE`, `CC_PROMOTOR_CLAVE` | Solo si no hay usuarios | Crean al primer PROMOTOR, que debe cambiar la clave al ingresar. Luego retira `CC_PROMOTOR_CLAVE` |
| `CC_COLEGIO_ID` | No (1) | Colegio del primer PROMOTOR |

- Si la bitácora queda bloqueada, sigue `docs/operacion/incidente-auditoria.md`.
- El CI tiene un job `mysql` que aplica las migraciones y corre pruebas contra MySQL 8 real con estos mismos permisos.

## Documentación
- `docs/plan-de-desarrollo.md`: sprints, hitos y decisiones.
- `docs/prototipo/cuentas-claras.html`: prototipo navegable.
- `docs/ux/`: kit para las reuniones con el colegio.
- `CLAUDE.md` y `.claude/`: convenciones, agentes y skills del proyecto.
