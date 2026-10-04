# Instalar Cuentas Claras en tu PC (MySQL + aplicación)

La aplicación es **una sola pieza**: Spring Boot genera las pantallas (front) y la lógica (back). En tu PC corren **dos contenedores**: MySQL 8 y la aplicación. Se instalan con los mismos controles que producción: usuarios de MySQL separados, permisos mínimos y triggers. Si falta algo, la aplicación no arranca.

## 1. Requisitos
- **Docker Desktop** (Windows o Mac) o Docker Engine (Linux): https://www.docker.com/products/docker-desktop/
  - En Windows, durante la instalación, acepta usar **WSL 2**.
- **Git**: https://git-scm.com/downloads
- Unos 3 GB libres en disco.

No hace falta instalar Java, Maven ni MySQL: Docker se encarga de todo.

## 2. Descargar el proyecto
En una terminal (PowerShell en Windows):
```bash
git clone https://github.com/aziel123/cuentas-claras
cd cuentas-claras
git checkout claude/sprint-3-correcciones
```

## 3. Configurar las claves
Copia el archivo de ejemplo:
```bash
# Windows (PowerShell)
copy .env.ejemplo .env
# Mac / Linux
cp .env.ejemplo .env
```
Abre `.env` con un editor (por ejemplo, el Bloc de notas) y **cambia todas las claves**. Cada una tiene una explicación al lado. El archivo `.env` no se sube a GitHub.

## 4. Levantar todo
```bash
docker compose up --build
```
La primera vez tarda varios minutos: descarga MySQL y Java, compila la aplicación e inicializa la base. Verás estos pasos en orden:
1. `mysql`: crea la base `cuentasclaras` y los usuarios `cc_migrador` y `cc_app`.
2. `migrar`: aplica las migraciones (`Migración terminada: 12 migraciones aplicadas`).
3. `preparar-bd`: permisos por tabla y triggers (`base de datos lista`).
4. `app`: la aplicación verifica los permisos y los 28 triggers, y arranca (`Started CuentasClarasApplication`).

Abre **http://localhost:8080**.

Para dejarlo corriendo en segundo plano, usa `docker compose up -d --build`. Para ver los mensajes, usa `docker compose logs -f app`.

## 5. Primer ingreso
- Usuario: el de `CC_PROMOTOR_USUARIO` (por defecto `promotora`).
- Clave: la de `CC_PROMOTOR_CLAVE`.
- La plataforma te pide **cambiar la clave** y luego volver a ingresar.

Después de ese primer ingreso puedes borrar la línea `CC_PROMOTOR_CLAVE` del `.env`: ya no se usa.

## 6. Primeros pasos para probar el flujo completo
Muchas acciones necesitan **dos personas distintas** (quien pide no aprueba), así que crea varios usuarios:

1. **Usuarios y roles** (como promotora): crea al menos `direccion` (Dirección), `admin` (Administración) y `cajera` (Caja). Cada uno recibe una clave temporal que debe cambiar al entrar.
2. **Colegio** (como Administración): crea el año 2026 «en curso» y sus secciones (por ejemplo, 1.° Primaria A).
3. **Alumnos**: regístralos a mano o impórtalos con la plantilla de Excel. `docs/ux/ejemplo-importacion-corregido.xlsx` sirve si creaste las secciones A del año 2026.
4. **Pensiones** (como Administración): arma el plan y envíalo; **Dirección o Promotoría lo aprueba**. Se generan las cuotas.
5. **Caja** (como cajera): busca una familia, cobra, imprime la boleta y, al final del día, cierra la caja a ciegas.
6. **Aprobaciones** (como Dirección): aprueba el cierre. **Promotoría** ve el resumen y las alertas en su inicio.

Si solo quieres mirar pantallas con datos de ejemplo ya cargados, sin MySQL, usa el modo demostración: `./mvnw spring-boot:run` (necesita Java 21; usuarios en el README).

## 7. Ver las tablas (opcional)
MySQL queda disponible **solo desde tu PC** en el puerto **3307**. Con MySQL Workbench, DBeaver o similar:
- Host: `127.0.0.1`
- Puerto: `3307`
- Usuario: `cc_app`, con la clave `CC_CLAVE_APP`
- Base: `cuentasclaras`

Con `cc_app` puedes leer, pero no editar la bitácora ni borrar pagos: lo impiden los permisos y los triggers. Para administrar, usa `root` con `MYSQL_ROOT_PASSWORD`.

## 8. Comandos útiles
| Quiero… | Comando |
|---|---|
| Apagar (los datos se conservan) | `docker compose down` |
| Volver a encender | `docker compose up -d` |
| Actualizar a la última versión del código | `git pull` y luego `docker compose up -d --build` |
| Ver los mensajes de la aplicación | `docker compose logs -f app` |
| **Borrar todo y empezar de cero** (se pierden los datos) | `docker compose down -v` |

## 9. Problemas comunes
- **«port is already allocated»**: el puerto 8080 está ocupado. Cambia `PUERTO_APP=8081` en `.env` y entra a http://localhost:8081.
- **`mysql` tarda o aparece como *unhealthy*** la primera vez: espera y vuelve a ejecutar `docker compose up -d`. La inicialización solo ocurre una vez.
- **La aplicación no arranca y dice que faltan permisos o triggers**: revisa `docker compose logs preparar-bd`. Si cambiaste las claves después de la primera vez, ejecuta `docker compose down -v` y empieza de cero (las claves de MySQL se fijan al crear la base).
- **No puedo iniciar sesión desde el celular por la IP de la PC**: es esperable. Esta configuración es solo para `localhost`. Para usarla en red o internet hace falta https; eso se hace al elegir el hosting (decisión D3 del plan).

## 10. Importante
- Esta instalación es para **probar en tu PC**. Para que el colegio la use de verdad hace falta un servidor con https, respaldos diarios y claves en un gestor de secretos (`docs/operacion/`).
- El comprobante es **simulado**: no tiene valor tributario hasta conectar el proveedor de facturación electrónica (OSE).
