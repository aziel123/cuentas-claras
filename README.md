# Cuentas Claras

Plataforma de gestión escolar del **Colegio Virgen María**. Empieza por la cobranza con controles antifraude: cada pago emite su comprobante, avisa a la familia, queda en una auditoría que no se puede borrar y se concilia en el cierre de caja diario.

## Requisitos
- Java 21
- No hace falta instalar Maven: el proyecto trae `./mvnw`.
- Para desarrollo no hace falta base de datos: el perfil `dev` usa H2 en memoria.

## Uso
```bash
./mvnw -B verify          # compila, aplica migraciones y corre las pruebas
./mvnw spring-boot:run    # levanta la app en http://localhost:8080
```

Salud de la aplicación: `GET /actuator/health`.

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
Perfil `prod` con MySQL 8 (base con `utf8mb4`):
```bash
SPRING_PROFILES_ACTIVE=prod \
DB_URL="jdbc:mysql://HOST:3306/cuentas_claras" \
DB_USUARIO="..." DB_CLAVE="..." \
AUDITORIA_CLAVE_HMAC="..." \
CC_PROMOTOR_USUARIO="..." CC_PROMOTOR_NOMBRE="..." CC_PROMOTOR_CLAVE="..." \
java -jar target/cuentas-claras-*.jar
```
- `AUDITORIA_CLAVE_HMAC` (obligatoria, mínimo 32 caracteres): sella la bitácora de auditoría. Guárdala en el gestor de secretos; si se pierde, no se puede verificar la bitácora.
- `CC_PROMOTOR_*` (y `CC_COLEGIO_ID`, por defecto 1): crean al primer PROMOTOR solo si no hay ningún usuario. Debe cambiar la clave al ingresar; luego retira `CC_PROMOTOR_CLAVE` del entorno.
- Si la bitácora queda bloqueada: `docs/operacion/incidente-auditoria.md`.

## Documentación
- `docs/plan-de-desarrollo.md`: sprints, hitos y decisiones.
- `docs/prototipo/cuentas-claras.html`: prototipo navegable.
- `docs/ux/`: kit para las reuniones con el colegio.
- `CLAUDE.md` y `.claude/`: convenciones, agentes y skills del proyecto.
