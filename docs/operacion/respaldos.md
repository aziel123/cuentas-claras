# Respaldos diarios cifrados y restauración probada

> Sprint 7, tanda 1 (diseño: `docs/arquitectura/sprint-7-endurecimiento.md`, sección 9). Para el responsable técnico.
> Ninguna clave va en este documento, en los scripts ni en el repositorio: todas llegan por variables de entorno.

## Qué se respalda, cuándo y dónde
- **Qué:** solo los datos (`mysqldump --no-create-info --skip-triggers --single-transaction`, sin `flyway_schema_history`).
  El esquema, los permisos y los triggers se rehacen con el jar y `scripts/mysql/` de **la misma versión**.
- **Cuándo:** todos los días a las 02:30 y antes de cada despliegue (decisión 88). Pérdida máxima: 24 horas.
- **Dónde:** un almacenamiento de objetos compatible con S3 (decisión 89; por ejemplo Backblaze B2 o Cloudflare R2), con
  **bloqueo de objetos de 35 días** y una **clave de aplicación sin permiso de borrar** en el servidor: quien tome el
  servidor no puede borrar los respaldos. Ciclo de vida: 35 diarios y el del día 1 de cada mes durante 12 meses.
- **Cifrado:** `age` para **dos destinatarios**, la clave pública de Promotoría y la del responsable técnico (decisión
  90). El servidor solo tiene las públicas. Las privadas se guardan fuera de línea, como la clave HMAC
  (`custodia-clave-auditoria.md`). `age` se instala desde los paquetes de la distribución (`apt-get install age`).

## Las claves age (una vez)
En la máquina de cada persona (nunca en el servidor):
```bash
age-keygen -o promotoria.txt          # imprime la clave pública: age1...
age-keygen -y promotoria.txt          # la vuelve a mostrar
```
Promotoría guarda `promotoria.txt` en su sobre o gestor personal; el responsable técnico, la suya. En el servidor solo
van las dos claves públicas en `RESPALDO_AGE_DESTINATARIOS`.

## El respaldo: `scripts/respaldo/respaldar.sh`
Variables (por ejemplo en un archivo de entorno del servicio con permisos 600, fuera del repositorio):

| Variable | Valor |
|---|---|
| `RESPALDO_DB_HOST`, `RESPALDO_DB_PUERTO` | La base de producción |
| `RESPALDO_DB_USUARIO`, `RESPALDO_DB_CLAVE` | `cc_respaldo` y su clave ([mysql-usuarios.md](mysql-usuarios.md)) |
| `RESPALDO_MYSQL_OPCIONES` | Por ejemplo `--ssl-mode=REQUIRED` en el hosting |
| `RESPALDO_AGE_DESTINATARIOS` | Las dos claves públicas `age1...`, separadas por un espacio |
| `RESPALDO_DESTINO` | El nombre del remoto de rclone (por ejemplo `b2colegio`). Por defecto `simulado` (ver abajo) |
| `RCLONE_CONFIG_<NOMBRE>_*` | Las credenciales del almacenamiento, **solo** por variables (el script ignora cualquier `rclone.conf`). Ejemplo S3: `RCLONE_CONFIG_B2COLEGIO_TYPE=s3`, `..._PROVIDER`, `..._ACCESS_KEY_ID`, `..._SECRET_ACCESS_KEY`, `..._ENDPOINT` |
| `RESPALDO_AVISO_CORREO` | Quién recibe el aviso si falla o faltan filas (usa el comando `mail` del servidor) |

Lo que hace, en orden (sale con error en el primer paso que falle):
1. Lee el **ancla «antes»** (última secuencia y hash de la bitácora), la versión del esquema y los **conteos** de las
   tablas de `tablas-vigiladas.txt` (filas e id máximo).
2. **Compara con el manifiesto anterior** (el del destino, que no se puede alterar): las filas con id hasta el máximo
   anterior deben ser al menos tantas como antes, y el evento del ancla anterior debe tener el mismo hash. Si no:
   aviso CRÍTICO, «Faltan filas» en el panel de Promotoría y **sigue respaldando** (el respaldo es la evidencia).
3. Vuelca, comprime y cifra en una sola tubería: nada queda sin cifrar en el disco.
4. Lee el **ancla «después»** y escribe el manifiesto JSON (archivo, SHA-256 del cifrado, bytes, versión, anclas,
   comparación y conteos).
5. Sube el archivo y el manifiesto (`rclone copy`) y los comprueba (`rclone check`).
6. Registra la fila en `respaldo` como `cc_respaldo`. El trigger exige anclas reales y que se registre al terminar; si
   el ancla del respaldo anterior ya no está, exige que diga `FALTAN_FILAS`.

Código de salida: `0` bien; `3` respaldo hecho pero **faltan filas** (alerta crítica); `1` falló (no quedó registrado:
a las 26 horas, «Sin respaldo»).

### Destino simulado (dev, CI y piloto)
`RESPALDO_DESTINO=simulado` copia a `RESPALDO_SIMULADO_DIR` (una carpeta del mismo equipo). **No sirve en producción** y
no se puede usar ahí:
- la base solo lo registra si el DBA escribió `('respaldo_simulado', 'PERMITIDA')` en `configuracion_bd`;
- en prod la aplicación **no arranca** con esa fila, y `/panel/sistema` y las alertas no cuentan un respaldo simulado.

### Programarlo (systemd, ejemplo)
```ini
# /etc/systemd/system/cuentas-claras-respaldo.service
[Unit]
Description=Respaldo cifrado de Cuentas Claras
[Service]
Type=oneshot
EnvironmentFile=/etc/cuentas-claras/respaldo.env
ExecStart=/bin/sh /opt/cuentas-claras/scripts/respaldo/respaldar.sh

# /etc/systemd/system/cuentas-claras-respaldo.timer
[Unit]
Description=Respaldo diario 02:30 (hora de Lima)
[Timer]
OnCalendar=*-*-* 02:30:00 America/Lima
Persistent=true
[Install]
WantedBy=timers.target
```
`systemctl enable --now cuentas-claras-respaldo.timer`. El archivo de entorno con permisos 600 y dueño root.

## La restauración: `scripts/respaldo/restaurar-y-verificar.sh`
Restaura en un **MySQL vacío y desechable** y comprueba todo, sin tocar producción:
1. Baja el respaldo pedido (`RESTAURAR_ARCHIVO`, o el último) y su manifiesto; comprueba el SHA-256 y el tamaño.
2. Crea la base con `01` (claves de un solo uso), migra con el jar hasta la versión del manifiesto (`DB_MIGRAR_HASTA`),
   carga los datos descifrados en una tubería y aplica `02` y `03` (los triggers después de cargar).
3. Corre `java -jar cuentas-claras.jar verificar-respaldo`: versión del esquema, secuencias seguidas desde 1, eslabón en
   el último evento, anclas del manifiesto y de los 7 anteriores, huellas diarias y por hora, conteos y la consistencia
   de los libros (`comprobaciones.sql`). Con `AUDITORIA_CLAVE_HMAC`, además, el HMAC de toda la cadena.
4. Arranca la aplicación en **prod** sobre la copia, con las tareas programadas apagadas (no sale ningún mensaje a las
   familias): el verificador de permisos y `/actuator/health/readiness` deben pasar.
5. Escribe `restauracion-AAAAMMDD-HHMMSS.json` y una línea de resumen; lo envía a `RESTAURAR_AVISO_CORREO`.

En la máquina del responsable técnico (con Docker, `age`, el cliente `mysql`, Java 21 y `curl`):
```bash
RESTAURAR_DOCKER=si RESTAURAR_AGE_IDENTIDAD=~/claves/responsable.txt RESTAURAR_ORIGEN=b2colegio \
RESTAURAR_JAR=./cuentas-claras.jar sh scripts/respaldo/restaurar-y-verificar.sh
```
Con `RESTAURAR_DOCKER=si` crea un `mysql:8.4` desechable en `127.0.0.1:3399` y lo borra al terminar (también si falla).
Sin Docker: `RESTAURAR_DB_HOST`, `RESTAURAR_DB_PUERTO`, `RESTAURAR_DB_ADMIN` y `RESTAURAR_DB_ADMIN_CLAVE` de un MySQL
vacío (el script se niega si ya tiene una base `cuentasclaras`).

## Simulacros
- **Semanal y automático** (decisión 91): domingo 04:00 en la máquina del responsable técnico, sin la clave HMAC.
  Revisar el informe cada lunes.
- **Mensual y presencial** (primer lunes, 20 minutos, con Promotoría): Promotoría escribe la clave HMAC en la máquina del
  simulacro (`AUDITORIA_CLAVE_HMAC`) para la verificación completa de la cadena; se compara la huella del último resumen
  que recibió por WhatsApp con la de la copia; se prueba que la clave del servidor **no puede borrar** un respaldo
  (E33); y se firma el [acta](acta-simulacro-restauracion.md).
- Los datos restaurados no salen de esa máquina: el contenedor se elimina al terminar y el disco debe estar cifrado.

## El CI (job `respaldo`)
En cada PR: respalda la base que dejan las pruebas de MySQL, la restaura en un segundo MySQL, verifica la cadena con la
clave HMAC de pruebas y arranca prod sobre la copia. Además, los ataques (`scripts/respaldo/pruebas-ci.sh`):
- **E29:** el `.age` no contiene ningún DNI ni apellido de la base (descifrado, sí).
- **E30:** un DBA borra un pago: el respaldo siguiente sale con código 3 y registra `FALTAN_FILAS` (`pago (faltan 1)`).
- **E31:** un respaldo fabricado con un evento cambiado (y su manifiesto): «La cadena no verifica en la secuencia N».
- **E32:** un DBA recorta el final de la bitácora y retrocede el eslabón: el respaldo siguiente lo detecta por el ancla.
- El destino simulado sin la fila del DBA falla (1644), y sin credenciales del destino o con una sola clave pública el
  respaldo no empieza.

## Despliegue (orden nuevo desde el sprint 7)
1. **Respaldo** (`respaldar.sh`): si falla, no se despliega.
2. Detener la aplicación.
3. `java -jar cuentas-claras.jar migrar` (cc_migrador).
4. `04-una-vez-sprint-7.sql` (solo la primera vez), `02-permisos-tablas.sql` y `03-triggers.sql`.
5. Arrancar: el verificador comprueba permisos, triggers y la configuración del respaldo.
Son unos 2 minutos sin servicio, después de las 21:00 o en fin de semana (decisión 88).

## Desastre (base perdida o alterada)
1. Preservar la evidencia ([incidente-auditoria.md](incidente-auditoria.md)): no borrar nada, copiar los logs.
2. Restaurar el **último respaldo verificado** en una base nueva con `restaurar-y-verificar.sh` (sin `RESTAURAR_DOCKER`,
   apuntando al MySQL nuevo, y `RESTAURAR_ARRANCAR=no`).
3. Cambiar las claves de `cc_app`, `cc_respaldo` y del migrador.
4. Apuntar `DB_URL` a la base nueva y arrancar.
5. Promotoría compara el resumen y la huella de los días entre el respaldo y la caída con lo que recibió por WhatsApp;
   los pagos de ese tramo se reconstruyen con las boletas y el banco.
6. Si hubo exposición de datos personales, notificar en 48 horas (sección 8.5 del diseño).
