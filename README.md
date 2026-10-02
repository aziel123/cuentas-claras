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

## Producción
Perfil `prod` con MySQL 8 (base con `utf8mb4`):
```bash
SPRING_PROFILES_ACTIVE=prod \
DB_URL="jdbc:mysql://HOST:3306/cuentas_claras" \
DB_USUARIO="..." DB_CLAVE="..." \
java -jar target/cuentas-claras-*.jar
```

## Documentación
- `docs/plan-de-desarrollo.md`: sprints, hitos y decisiones.
- `docs/prototipo/cuentas-claras.html`: prototipo navegable.
- `docs/ux/`: kit para las reuniones con el colegio.
- `CLAUDE.md` y `.claude/`: convenciones, agentes y skills del proyecto.
