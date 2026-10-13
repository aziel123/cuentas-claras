#!/bin/sh
# Cuentas Claras · prepara la base de Cloud SQL y la VM de una instalación (sprint 8, tanda 3; secciones 6.1 y 7.3 del
# diseño; H13 y H14). Corre en la MÁQUINA DEL RESPONSABLE TÉCNICO, autenticado con gcloud, después de crear-infra.sh:
#
#   CC_PROYECTO=<proyecto> CC_MARCA_NOMBRE="<marca>" CC_DOMINIO_APP=app.<dominio> CC_ACME_CORREO=<correo> \
#   CC_OPERADORES="usuario:correo" CORREO_REMITENTE=avisos@<dominio> SMTP_HOST=<smtp> SMTP_USUARIO=<usuario> \
#   RESPALDO_AGE_DESTINATARIOS="age1... age1..." sh deploy/gcp/preparar-base.sh compartida
#
# Hace:
#   1. Lee la IP PRIVADA de Cloud SQL y el certificado de su autoridad (para TLS con sslMode=VERIFY_CA: H14).
#   2. Copia deploy/ y scripts/ a la VM, construye la imagen de herramientas y crea el almacén PKCS12 del certificado.
#   3. Escribe /etc/cc/instalacion.env (sin secretos) y /etc/cc/caddy.env en la VM.
#   4. Crea los usuarios cc_migrador, cc_app, cc_sistema y cc_respaldo y el rol cc_negocio con 01-usuarios.sql, COMO ROOT Y
#      POR SQL (nunca desde la consola: les daría cloudsqlsuperuser y prod no arrancaría, H13). Las claves salen de Secret
#      Manager EN ESTA MÁQUINA y llegan a la VM por la entrada estándar de SSH: no quedan en la VM ni en la línea de comandos.
#   5. Instala los servicios de systemd (cargar secretos al arrancar y el respaldo de las 02:30) y carga los secretos.
# Después: deploy/desplegar.sh (migrar, 02, 03 y arrancar).
set -eu

aqui=$(cd "$(dirname "$0")" && pwd)
CC_RAIZ=$(cd "$aqui/../.." && pwd)
CC_INSTANCIA="${1:-${CC_INSTANCIA:-compartida}}"
export CC_INSTANCIA CC_RAIZ
# shellcheck source=deploy/gcp/variables.sh
. "$aqui/variables.sh"
[ "$CC_INSTANCIA" != "demo" ] || { echo "La demo no tiene base: usa deploy/desplegar.sh demo <digest>." >&2; exit 1; }

: "${CC_MARCA_NOMBRE:?Define CC_MARCA_NOMBRE (el nombre comercial; decisión 122)}"
: "${CC_DOMINIO_APP:?Define CC_DOMINIO_APP (por ejemplo app.<dominio>)}"
: "${CC_ACME_CORREO:?Define CC_ACME_CORREO (los avisos del certificado de Let’s Encrypt)}"
: "${CC_OPERADORES:?Define CC_OPERADORES (usuario:correo de los operadores de la plataforma; decisión 112)}"
: "${RESPALDO_AGE_DESTINATARIOS:?Define RESPALDO_AGE_DESTINATARIOS (las 2 claves PÚBLICAS age de los custodios; decisión 129)}"

cc_paso "1. IP privada y autoridad de TLS de $CC_SQL"
ip=$(cc_gcloud sql instances describe "$CC_SQL" --format 'value(ipAddresses[0].ipAddress)')
tipo=$(cc_gcloud sql instances describe "$CC_SQL" --format 'value(ipAddresses[0].type)')
[ "$tipo" = "PRIVATE" ] || { echo "La primera IP de $CC_SQL es $tipo: la instancia debe tener solo IP privada." >&2; exit 1; }
ca=$(mktemp)
trap 'rm -f "$ca"' EXIT INT TERM
cc_gcloud sql instances describe "$CC_SQL" --format 'value(serverCaCert.cert)' > "$ca"
grep -q "BEGIN CERTIFICATE" "$ca" || { echo "No se pudo leer el certificado de la autoridad de $CC_SQL." >&2; exit 1; }
echo "IP privada: $ip"

cc_paso "2. Archivos y herramientas en la VM $CC_VM"
cc_copiar_repo
cc_ssh "sudo docker build -q -t cc-herramientas:local $CC_DIR_VM/deploy/herramientas"
cc_ssh "sudo install -d -m 755 /etc/cc/certs && sudo tee /etc/cc/certs/server-ca.pem > /dev/null \
	&& sudo chmod 644 /etc/cc/certs/server-ca.pem" < "$ca"
truststore=$(openssl rand -hex 16)
cc_ssh "sudo rm -f /etc/cc/certs/cloudsql-ca.p12 && sudo docker run --rm -v /etc/cc/certs:/certs cc-herramientas:local \
	keytool -importcert -noprompt -alias cloudsql -file /certs/server-ca.pem -keystore /certs/cloudsql-ca.p12 \
	-storetype PKCS12 -storepass $truststore > /dev/null && sudo chmod 644 /etc/cc/certs/cloudsql-ca.p12"

cc_paso "3. /etc/cc/instalacion.env y /etc/cc/caddy.env (sin secretos)"
{
	echo "# Cuentas Claras · instalación «$CC_INSTANCIA» (sin secretos). Lo escribió deploy/gcp/preparar-base.sh."
	echo "CC_SECRETOS=$CC_SECRETOS"
	echo "CC_SQL_IP=$ip"
	echo "CC_TRUSTSTORE_CLAVE=$truststore"
	echo "CC_MARCA_NOMBRE=\"$CC_MARCA_NOMBRE\""
	echo "CC_MARCA_EMPRESA=\"${CC_MARCA_EMPRESA:-}\""
	echo "CC_MARCA_RUC=${CC_MARCA_RUC:-}"
	echo "CC_MARCA_SOPORTE_CORREO=${CC_MARCA_SOPORTE_CORREO:-}"
	echo "CC_MARCA_SOPORTE_WHATSAPP=${CC_MARCA_SOPORTE_WHATSAPP:-}"
	echo "CC_INSTALACION=${CC_INSTALACION:-COMPARTIDA}"
	echo "CC_OPERADORES=\"$CC_OPERADORES\""
	echo "CC_OPERADOR_CORREO=${CC_OPERADOR_CORREO:-}"
	echo "CC_DOMINIO_APP=$CC_DOMINIO_APP"
	echo "MENSAJERIA_WHATSAPP_PROVEEDOR=${MENSAJERIA_WHATSAPP_PROVEEDOR:-NINGUNO}"
	echo "WHATSAPP_NUMERO_ID=${WHATSAPP_NUMERO_ID:-}"
	echo "MENSAJERIA_CORREO_PROVEEDOR=${MENSAJERIA_CORREO_PROVEEDOR:-SMTP}"
	echo "CORREO_REMITENTE=${CORREO_REMITENTE:-}"
	echo "SMTP_HOST=${SMTP_HOST:-}"
	echo "SMTP_PUERTO=${SMTP_PUERTO:-587}"
	echo "SMTP_USUARIO=${SMTP_USUARIO:-}"
	echo "RESPALDO_AGE_DESTINATARIOS=\"$RESPALDO_AGE_DESTINATARIOS\""
	echo "RESPALDO_DESTINO=${RESPALDO_DESTINO:-respaldos}"
} | cc_ssh "sudo tee /etc/cc/instalacion.env > /dev/null && sudo chmod 600 /etc/cc/instalacion.env"
printf 'CC_DOMINIO_APP=%s\nCC_ACME_CORREO=%s\n' "$CC_DOMINIO_APP" "$CC_ACME_CORREO" \
	| cc_ssh "sudo tee /etc/cc/caddy.env > /dev/null && sudo chmod 600 /etc/cc/caddy.env"

cc_paso "4. Usuarios de MySQL con 01-usuarios.sql, como root y por SQL (H13)"
# SQL como root en Cloud SQL, desde el contenedor de herramientas de la VM. La entrada estándar trae la clave de root en la
# primera línea y después el SQL: el contenedor la escribe en un archivo de opciones suyo (se borra con él).
mysql_root() {
	cc_ssh "sudo docker run -i --rm -v /etc/cc/certs:/certs:ro cc-herramientas:local sh -c 'read -r clave; \
printf \"[client]\nuser=root\npassword=%s\n\" \"\$clave\" > /tmp/root.cnf; \
exec mysql --defaults-extra-file=/tmp/root.cnf --ssl-mode=VERIFY_CA --ssl-ca=/certs/server-ca.pem -h $ip -N'"
}
{
	cc_secreto db-root
	echo
	sed -e "s/__CLAVE_MIGRADOR__/$(cc_secreto db-migrador)/" -e "s/__CLAVE_APP__/$(cc_secreto db-app)/" \
		-e "s/__CLAVE_SISTEMA__/$(cc_secreto db-sistema)/" -e "s/__CLAVE_RESPALDO__/$(cc_secreto db-respaldo)/" \
		"$CC_RAIZ/scripts/mysql/01-usuarios.sql"
} | mysql_root
# Comprobación: ningún usuario de la aplicación tiene otro rol que cc_negocio (por ejemplo cloudsqlsuperuser, si alguien
# lo creó desde la consola: H13 y S7-M1).
anidados=$({
	cc_secreto db-root
	echo
	echo "SELECT CONCAT(TO_USER, ' tiene ', FROM_USER) FROM mysql.role_edges WHERE TO_USER LIKE 'cc%' AND FROM_USER <> 'cc_negocio';"
} | mysql_root)
[ -z "$anidados" ] || { echo "ERROR: $anidados. Bórralo y créalo con 01 (nunca desde la consola)." >&2; exit 1; }
echo "Usuarios creados por SQL; ninguno tiene un rol de más."

cc_paso "5. Servicios de systemd y secretos en la VM"
cc_ssh "sudo install -m 644 $CC_DIR_VM/deploy/systemd/cc-secretos.service $CC_DIR_VM/deploy/systemd/cc-respaldo.service \
	$CC_DIR_VM/deploy/systemd/cc-respaldo.timer /etc/systemd/system/ && sudo systemctl daemon-reload \
	&& sudo systemctl enable cc-secretos.service cc-respaldo.timer && sudo systemctl start cc-secretos.service \
	&& sudo systemctl start cc-respaldo.timer && sudo ls -l /etc/cc"
cc_paso "Listo. Siguiente: sh deploy/desplegar.sh $CC_INSTANCIA <digest de la imagen>"
