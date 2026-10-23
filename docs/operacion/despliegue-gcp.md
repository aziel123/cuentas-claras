# Despliegue en Google Cloud (región Santiago)

> Sprint 8, tanda 3 (decisiones 110, 111, 133 a 135; secciones 6.6 y 7 de `docs/arquitectura/sprint-8-lista-para-vender.md`).
> Guía del **responsable técnico**. Todo lo que dice aquí está en scripts del repositorio (`deploy/`): nada se hace a mano en
> la consola, salvo lo marcado como «en la consola».
>
> **Estado:** los scripts se escribieron y se validaron sin una cuenta de Google Cloud (sintaxis con `sh -n` y shellcheck,
> los compose con `docker compose config`, el Caddyfile con `caddy validate`, el job `mysql` y el respaldo con
> `correr-job.py` en contenedores locales). **Ningún comando de `gcloud` se ejecutó.** Las opciones de `gcloud` salen de la
> documentación (sección 7.3 del diseño): si alguna cambió, `gcloud ... --help` lo dice. Por eso el primer paso es el
> **paso 0** contra una instancia de prueba.

## 1. Qué se despliega

```
Internet ── 443 ──> VM e2-small (Ubuntu 24.04 LTS, Docker Compose, IP estática, SSH solo por IAP)
                      ├─ caddy (TLS automático con Let's Encrypt; publica 80 y 443)
                      └─ app   (imagen de GHCR por digest; 8080 sin publicar; solo lectura, sin capacidades)
                               │  TLS (sslMode=VERIFY_CA), IP privada de la VPC
                               v
                    Cloud SQL para MySQL 8.4 Enterprise, db-g1-small, zonal, SSD 10 GB con crecimiento
                    (respaldos diarios a las 02:00 de Lima + PITR de 7 días; protección contra borrado)
Respaldo propio (sprint 7): temporizador de systemd en la VM, 02:30 ──> bucket compatible con S3 con bloqueo de objetos
Vigilante externo (sprint 7): GitHub Actions cada 15 minutos ──> /actuator/health y /salud/respaldo
Demo: OTRA VM e2-small, misma imagen, perfil «demo», red de Docker sin salida, sin base ni secretos
```

| Archivo | Para qué | Dónde corre |
|---|---|---|
| `deploy/gcp/variables.sh` | Nombres y valores por defecto (proyecto, región, VPC, Cloud SQL, VM, prefijo de los secretos) | Tu máquina |
| `deploy/gcp/crear-infra.sh` | APIs, VPC y subred, cortafuegos (SSH solo por IAP, 80 y 443), acceso privado a servicios, Cloud SQL, IP estática, cuenta de servicio, secretos y la VM. Idempotente | Tu máquina |
| `deploy/gcp/preparar-base.sh` | Usuarios `cc_*` con `01` **por SQL**, certificado de la autoridad de Cloud SQL, `/etc/cc` y los servicios de systemd | Tu máquina → VM |
| `deploy/desplegar.sh` | Respaldo previo, migrar, `02`, `03`, arrancar y leer el verificador; o la demo | Tu máquina → VM |
| `deploy/gcp/paso-0.sh` | El job `mysql` del CI completo contra una instancia de **prueba** | Cloud Shell o tu máquina |
| `deploy/compose.prod.yml`, `deploy/compose.demo.yml`, `deploy/Caddyfile` | Los contenedores de la VM | VM |
| `deploy/herramientas/Dockerfile` | Cliente mysql, age, rclone, JDK 21 y socat (solo paquetes de Ubuntu) | VM y tu máquina |
| `deploy/vm/*.sh`, `deploy/systemd/*` | Arranque de la VM, secretos al arrancar, pasos del despliegue, respaldo de las 02:30 y reinicio de la demo | VM |
| `scripts/ci/correr-job.py` | Corre un job del CI fuera de GitHub (paso 0 y reproducciones locales) | Contenedor de herramientas |

## 2. Costo estimado (US$ al mes, `southamerica-west1`)

Precios de agregadores de terceros (sección 7.5 del diseño), **a confirmar con la calculadora de Google Cloud** antes de
contratar. Sin impuestos.

| Rubro | Instalación compartida | Notas |
|---|---|---|
| VM e2-small (2 vCPU compartidas, 2 GB) | ~17.5 | e2-medium (4 GB), ~35, si la memoria no alcanza |
| Disco de la VM, 20 GB pd-balanced | ~2-3 | |
| IP externa estática | ~3-4 | |
| Cloud SQL `db-g1-small` Enterprise, zonal | ~36 | Solo cómputo |
| Almacenamiento SSD de Cloud SQL (10 GB), respaldos y binlog | ~3-5 | Crece con el uso |
| Secret Manager, egreso y registros | ~2-4 | |
| **Total por instalación** | **~US$ 65-75** (unos S/ 245-280) | Con alta disponibilidad regional, Cloud SQL cuesta el doble (decisión 111) |
| VM de la demo (e2-small, sin base) + su IP | ~20-22 | Aparte |
| Bucket de respaldos (Cloudflare R2 o Backblaze B2, con bloqueo de objetos) | 0-1 | Capa gratuita para pocos GB |
| Dominio | ~1 | Menos de US$ 15 al año para `.com`; el precio de `.pe` no se verificó |
| **Paso 0** (instancia de prueba unas horas) | ~1-3 | Si se borra el mismo día |

Plataforma completa: compartida + demo ≈ **US$ 85-100 al mes**. Con el Colegio Virgen María en una instalación dedicada
(decisión 109), una instalación más: ≈ **US$ 150-170 al mes**.

## 3. Antes de empezar (una vez)

1. **En la consola:** una cuenta de Google Cloud con facturación, un proyecto (`CC_PROYECTO`) y un presupuesto con alerta
   (por ejemplo, US$ 120 al mes).
2. En tu máquina: `gcloud` (o usa **Cloud Shell**, que ya lo trae con Docker), `openssl` y Docker.
   `gcloud auth login` y `gcloud config set project <CC_PROYECTO>`.
3. El **nombre comercial** (decisión 122): todavía es provisional y sale de `CC_MARCA_NOMBRE`. Con la marca elegida se
   compra el dominio (decisión 133); mientras tanto sirve un dominio provisional.
4. Las **2 claves age de los custodios** de la plataforma (decisión 129): cada custodio corre `age-keygen -o custodio.txt`
   en SU máquina y te entrega solo la línea `age1...` (la pública). La privada nunca va a la nube.
5. El **bucket de respaldos** con bloqueo de objetos y una clave de aplicación **sin permiso de borrar** (sprint 7,
   `docs/operacion/respaldos.md`). Sus credenciales van como líneas `RCLONE_CONFIG_RESPALDOS_*` en el secreto
   `<prefijo>-rclone-respaldo` (paso 5.4).
6. La imagen: cada merge a `main` publica `ghcr.io/<org>/cuentas-claras:<sha>` (job `imagen` del CI) y el resumen del job
   dice su **digest** (`ghcr.io/<org>/cuentas-claras@sha256:...`). Se despliega SIEMPRE por digest. Si el paquete de GHCR es
   privado, crea un token de GitHub de **solo lectura** (`read:packages`) y guárdalo en el secreto `<prefijo>-ghcr-lectura`
   como `usuario:token`.

## 4. Paso 0: ¿Cloud SQL acepta los 99 triggers? (antes de todo, medio día)

Es el riesgo técnico más grande (sección 6.6): si Cloud SQL no acepta los triggers, las funciones `DEFINER`, el rol
`cc_negocio`, la lectura de `mysql.*` de `permisos_objetos()` o el TLS sin proxy, se cambia de proveedor **antes** de
desplegar (AWS RDS para MySQL en `sa-east-1`, con `log_bin_trust_function_creators=1` en un grupo de parámetros).

```sh
export CC_PROYECTO=<proyecto>
sh deploy/gcp/paso-0.sh crear     # instancia de prueba cc-paso0: MySQL 8.4, la marca de las funciones, IP pública solo para tu IP
sh deploy/gcp/paso-0.sh probar    # 20-40 minutos
sh deploy/gcp/paso-0.sh borrar    # el mismo día
```

`probar` hace, desde el contenedor de herramientas (la instancia se ve en `127.0.0.1:3306` a través de `socat`: el TLS es
de punta a punta con Cloud SQL):
1. **El job `mysql` del CI completo** (`scripts/ci/correr-job.py mysql`): usuarios con `01`, `migrar`, `02`, `03` (82
   triggers y 9 funciones), las pruebas con los permisos mínimos (fase 2, sprints 6 a 8), el paso `comprobar`, el arranque
   de prod con su verificador, M2, M3, M4 y la rotación de la clave de los conectores. La única línea que se quita es
   `SET PERSIST log_bin_trust_function_creators` (en Cloud SQL esa marca va en la instancia: H13).
2. Las comprobaciones a mano de la sección 6.6: los privilegios del administrador del servicio (`ROLE_ADMIN`, `CREATE ROLE`,
   `DROP ROLE`, `CREATE USER`; sin `SUPER`), `huellas_objetos()` = 91, `permisos_objetos()` sin error, ningún `cc_*` con un rol
   de más, la marca de las funciones, `general_log` apagado y `Ssl_cipher` con `VERIFY_CA`.
3. Un respaldo con `respaldar.sh` como `cc_respaldo` y su restauración real en un MySQL desechable.
4. Pasa la instancia a TLS obligatorio (`ENCRYPTED_ONLY`): sin TLS no conecta y prod arranca con `sslMode=VERIFY_CA`
   («Huellas de los 99 triggers y las 14 funciones verificadas» y «Conexiones a MySQL cifradas (TLS) verificadas»).

Los registros quedan en `~/cc-paso0` (fuera del repositorio). Copia el resultado a
`docs/operacion/acta-paso-0-cloud-sql.md`. Lo que el paso 0 **no** prueba (lo prueba el primer despliegue, que lee las mismas
líneas del verificador): la conexión por **IP privada** desde la VM.

## 5. La instalación compartida

### 5.1 Infraestructura
```sh
export CC_PROYECTO=<proyecto>
sh deploy/gcp/crear-infra.sh compartida
```
Crea la VPC `cc-vpc`, el cortafuegos, el acceso privado a servicios, la instancia `cc-compartida` (unos 10 minutos), la IP
`cc-ip-compartida`, la cuenta de servicio `cc-vm-compartida` (solo lee los secretos `cc-compartida-*` de la aplicación y
del respaldo, **nunca** `db-root` ni `db-migrador`), los secretos (las claves de MySQL, la clave HMAC y la de los conectores
se generan al azar; WhatsApp, SMTP y el bucket quedan vacíos) y la VM `cc-vm-compartida` con Docker.

### 5.2 Dominio y TLS
**En el proveedor del dominio:** un registro `A` de `app.<dominio>` a la IP que imprimió `crear-infra.sh` (y `demo.<dominio>`
a la de la demo). Caddy obtiene y renueva solo el certificado de Let's Encrypt cuando el DNS apunta a la VM. El vigilante
externo avisa 14 días antes del vencimiento (sprint 7).

### 5.3 Base y VM
```sh
CC_MARCA_NOMBRE="<marca>" CC_DOMINIO_APP=app.<dominio> CC_ACME_CORREO=ti@<dominio> \
CC_OPERADORES="operador1:operador1@<dominio>,operador2:operador2@<dominio>" \
CORREO_REMITENTE=avisos@<dominio> SMTP_HOST=<smtp> SMTP_USUARIO=<usuario> CC_OPERADOR_CORREO=ti@<dominio> \
RESPALDO_AGE_DESTINATARIOS="age1...custodio1 age1...custodio2" \
sh deploy/gcp/preparar-base.sh compartida
```
- Lee la **IP privada** de Cloud SQL y el certificado de su autoridad; en la VM crea el almacén PKCS12 para `VERIFY_CA` (H14).
- Crea `cc_migrador`, `cc_app`, `cc_sistema`, `cc_respaldo` y el rol `cc_negocio` con **`01-usuarios.sql`, como root y por
  SQL**. Nunca desde la consola: un usuario creado ahí recibe `cloudsqlsuperuser` y prod no arranca (H13). El script lo
  comprueba al final.
- Las claves de root y de los usuarios se leen de Secret Manager **en tu máquina** y llegan a la VM por la entrada estándar de
  SSH: la VM no guarda las de root ni de `cc_migrador`.
- Escribe `/etc/cc/instalacion.env` (sin secretos) y `/etc/cc/caddy.env`, e instala `cc-secretos.service` (arma
  `/etc/cc/app.env` y `/etc/cc/respaldo.env` con Secret Manager en cada arranque, permisos 600) y `cc-respaldo.timer`
  (02:30 de Lima).

### 5.4 Secretos que pones tú
```sh
P=cc-compartida
printf '%s' '<token de WhatsApp>'   | gcloud secrets versions add $P-whatsapp-token --data-file=-
printf '%s' '<secreto de la app>'   | gcloud secrets versions add $P-whatsapp-secreto-app --data-file=-
printf '%s' '<token de verificación>' | gcloud secrets versions add $P-whatsapp-token-verificacion --data-file=-
printf '%s' '<clave SMTP>'          | gcloud secrets versions add $P-smtp-clave --data-file=-
printf 'RCLONE_CONFIG_RESPALDOS_TYPE=s3\nRCLONE_CONFIG_RESPALDOS_PROVIDER=Cloudflare\nRCLONE_CONFIG_RESPALDOS_ACCESS_KEY_ID=...\nRCLONE_CONFIG_RESPALDOS_SECRET_ACCESS_KEY=...\nRCLONE_CONFIG_RESPALDOS_ENDPOINT=...\n' \
  | gcloud secrets versions add $P-rclone-respaldo --data-file=-
```
Mientras WhatsApp siga en trámite, la aplicación arranca con correo (decisión 40): `MENSAJERIA_WHATSAPP_PROVEEDOR=NINGUNO`
en `instalacion.env`. Con las plantillas aprobadas (`docs/entrega/plantillas-whatsapp-meta.md`), `WHATSAPP_CLOUD` y
`WHATSAPP_NUMERO_ID`.

### 5.5 Desplegar (y cada actualización)
```sh
sh deploy/desplegar.sh compartida ghcr.io/<org>/cuentas-claras@sha256:<digest>
```
1. Respaldo «antes de desplegar» (si la base ya tiene datos).
2. Detiene la aplicación.
3. `java -jar … migrar` con `cc_migrador` (la imagen nueva).
4. `02-permisos-tablas.sql` como root y `03-triggers.sql` como `cc_migrador`.
5. Arranca con el digest nuevo.
6. Espera `/actuator/health/readiness` y busca en el log del verificador «Huellas de los 99 triggers y las 14 funciones
   verificadas», «Privilegios verificados», «Conexiones a MySQL cifradas (TLS) verificadas», «Plataforma verificada» y
   «Triggers verificados». Si falta una, **detiene la aplicación**: prod no queda arriba con la base a medias.
7. Anota el digest en `/etc/cc/desplegado.txt` de la VM.

Ventana (decisión 88): después de las 21:00 o en fin de semana; unos 2 minutos sin servicio.

### 5.6 Después del primer despliegue
1. Cada operador de `CC_OPERADORES` recibe su enlace de activación por correo; con él entra a `/plataforma` y da de alta el
   primer colegio (`docs/operacion/alta-de-un-colegio.md`).
2. Fuerza el primer respaldo y su restauración de prueba: `sudo systemctl start cc-respaldo.service` en la VM y, en la máquina
   de un custodio, `scripts/respaldo/restaurar-y-verificar.sh` (`docs/operacion/respaldos.md`). `/panel/sistema` debe decir
   «Último respaldo: hoy …, verificado en el destino» y «Custodios del respaldo: la plataforma».
3. El vigilante externo (`.github/workflows/vigilancia.yml`) con la URL `https://app.<dominio>`.
4. **En la consola:** los registros de auditoría de acceso a datos de Cloud SQL (IAM y Admin, «Data Access») si se quiere el
   rastro del DBA (riesgo de la sección 15; su costo no se verificó).

## 6. La demo
```sh
export CC_PROYECTO=<proyecto>
sh deploy/gcp/crear-infra.sh demo
# DNS: demo.<dominio> → la IP que imprimió
CC_MARCA_NOMBRE="<marca>" CC_DOMINIO_APP=demo.<dominio> CC_ACME_CORREO=ti@<dominio> \
  sh deploy/desplegar.sh demo ghcr.io/<org>/cuentas-claras@sha256:<digest>
```
Sin base, sin secretos y sin acceso a Secret Manager (su cuenta de servicio solo escribe registros). La aplicación vive en
una red de Docker `internal: true` (sin salida); `desplegar.sh` comprueba que no sale a internet y programa el reinicio de las
03:00 (`cc-demo-reinicio.timer`, que borra todo). Si `/etc/cc/demo.env` llevara una variable secreta, no arranca.
Para mostrar el alta a un prospecto, `CC_OPERADORES` con el equipo de ventas: su enlace de activación sale en el log de la
demo (`docker compose -f /opt/cuentas-claras/deploy/compose.demo.yml logs app | grep "Enlace de activación"`), porque la demo
no tiene correo real.

## 7. Operación

| Tarea | Cómo |
|---|---|
| Ver el estado | `/panel/sistema` (Promotoría) y el vigilante externo |
| Registros | `gcloud compute ssh cc-vm-compartida --tunnel-through-iap` y `sudo docker compose -f /opt/cuentas-claras/deploy/compose.prod.yml logs --tail 200 app` |
| Respaldo a mano | `sudo systemctl start cc-respaldo.service` |
| Volver a la versión anterior | `sh deploy/desplegar.sh compartida <digest anterior>` (está en `/etc/cc/desplegado.txt`). Si la versión nueva traía una migración, primero restaurar el respaldo «antes de desplegar» |
| Rotar una clave de MySQL | Nueva versión del secreto, `ALTER USER ... IDENTIFIED BY` como root (con `en-la-vm.sh sql-root`) y `sudo systemctl restart cc-secretos.service` + `docker compose up -d` |
| Rotar la clave de los conectores | `docs/operacion/mysql-usuarios.md` (`recifrar-conectores` con `cc_migrador`) |
| Agregar o quitar un operador | Cambiar `CC_OPERADORES` en `/etc/cc/instalacion.env` y volver a desplegar (queda en la bitácora) |
| Desastre (se perdió la instancia) | PITR de Cloud SQL a un minuto (`gcloud sql instances clone --point-in-time`) o, si el proveedor no responde, restaurar el último respaldo cifrado en otra instancia con `restaurar-y-verificar.sh` (RTO ~2 h) |

## 8. Lo que tiene que hacer el responsable técnico en Google Cloud (lista)
1. Crear la cuenta, el proyecto, la facturación y un presupuesto con alerta (en la consola).
2. `paso-0.sh crear`, `probar` y `borrar`; llenar el acta. Si algo falla, parar y revisar el proveedor.
3. Elegir la marca y comprar el dominio (o usar uno provisional).
4. `crear-infra.sh compartida`; DNS de `app.<dominio>`.
5. `preparar-base.sh compartida` con la marca, el dominio, los operadores, el SMTP y las 2 claves públicas age.
6. Cargar los secretos de WhatsApp, SMTP y el bucket (5.4); si el paquete de GHCR es privado, `ghcr-lectura`.
7. `desplegar.sh compartida <digest>`; primer respaldo y restauración de prueba; el vigilante.
8. `crear-infra.sh demo`; DNS de `demo.<dominio>`; `desplegar.sh demo <digest>`.
9. Opcional: los registros de acceso a datos de Cloud SQL.
