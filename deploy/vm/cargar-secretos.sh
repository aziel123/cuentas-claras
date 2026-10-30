#!/bin/sh
# Cuentas Claras · arma /etc/cc/app.env y /etc/cc/respaldo.env EN LA VM, como root, con Secret Manager (sprint 8, tanda
# 3; sección 7.4 del diseño). Lo corre cc-secretos.service en cada arranque y desplegar.sh antes de levantar la
# aplicación. La cuenta de servicio de la VM solo puede leer los secretos de SU instalación (crear-infra.sh), y nunca los
# de root ni de cc_migrador.
#
# Entrada: /etc/cc/instalacion.env (sin secretos: la IP privada de Cloud SQL, el dominio, la marca, los operadores, el
#          remitente de correo, las claves PÚBLICAS age de los custodios...). Lo escribe preparar-base.sh.
# Salida:  /etc/cc/app.env y /etc/cc/respaldo.env con permisos 600 y dueño root. No se imprime ningún valor.
set -eu
umask 077

instalacion=/etc/cc/instalacion.env
[ -r "$instalacion" ] || { echo "Falta $instalacion (lo escribe deploy/gcp/preparar-base.sh)" >&2; exit 1; }
# shellcheck disable=SC1090
. "$instalacion"
: "${CC_SECRETOS:?Falta CC_SECRETOS en $instalacion}"
: "${CC_SQL_IP:?Falta CC_SQL_IP en $instalacion}"
: "${CC_TRUSTSTORE_CLAVE:?Falta CC_TRUSTSTORE_CLAVE en $instalacion}"

gcloud_vm() {
	if command -v gcloud > /dev/null 2>&1; then gcloud "$@"; else /snap/bin/gcloud "$@"; fi
}

secreto() {
	# El valor del secreto, o vacío si no tiene versiones (por ejemplo, WhatsApp todavía en trámite).
	gcloud_vm secrets versions access latest --secret "$CC_SECRETOS-$1" --quiet 2> /dev/null || true
}

linea() {
	# Una variable por línea; un valor con salto de línea se rechaza (rompería el archivo).
	case "$2" in
		*"
"*) echo "El valor de $1 tiene saltos de línea" >&2; exit 1 ;;
	esac
	printf '%s=%s\n' "$1" "$2"
}

tmp=$(mktemp /etc/cc/.app.env.XXXXXX)
trap 'rm -f "$tmp" "$tmp.r"' EXIT INT TERM
{
	echo "# Generado por cargar-secretos.sh el $(date '+%Y-%m-%d %H:%M'). No editar: se vuelve a generar en cada arranque."
	linea DB_URL "jdbc:mysql://$CC_SQL_IP:3306/cuentasclaras?sslMode=VERIFY_CA&trustCertificateKeyStoreUrl=file:/certs/cloudsql-ca.p12&trustCertificateKeyStoreType=PKCS12&trustCertificateKeyStorePassword=$CC_TRUSTSTORE_CLAVE"
	linea DB_USUARIO cc_app
	linea DB_CLAVE "$(secreto db-app)"
	linea DB_SISTEMA_USUARIO cc_sistema
	linea DB_SISTEMA_CLAVE "$(secreto db-sistema)"
	linea AUDITORIA_CLAVE_HMAC "$(secreto auditoria-hmac)"
	linea CC_CLAVE_CONECTORES "$(secreto clave-conectores)"
	linea CC_CLAVE_CONECTORES_VERSION "${CC_CLAVE_CONECTORES_VERSION:-1}"
	# Correcciones del sprint 11 (A1 y B3): las rutas DENTRO del contenedor (el compose monta /var/lib/cuentasclaras).
	linea CC_DIR_EXPORTACIONES "${CC_DIR_EXPORTACIONES:-/var/lib/cuentasclaras/exportaciones}"
	linea CC_DIR_ACTAS "${CC_DIR_ACTAS:-/var/lib/cuentasclaras/actas}"
	linea CC_MARCA_NOMBRE "${CC_MARCA_NOMBRE:?Falta CC_MARCA_NOMBRE en $instalacion}"
	linea CC_MARCA_EMPRESA "${CC_MARCA_EMPRESA:-}"
	linea CC_MARCA_RUC "${CC_MARCA_RUC:-}"
	linea CC_MARCA_SOPORTE_CORREO "${CC_MARCA_SOPORTE_CORREO:-}"
	linea CC_MARCA_SOPORTE_WHATSAPP "${CC_MARCA_SOPORTE_WHATSAPP:-}"
	linea CC_INSTALACION "${CC_INSTALACION:-COMPARTIDA}"
	linea CC_OPERADORES "${CC_OPERADORES:?Falta CC_OPERADORES en $instalacion}"
	linea CC_OPERADOR_CORREO "${CC_OPERADOR_CORREO:-}"
	linea CC_URL_PUBLICA "https://${CC_DOMINIO_APP:?Falta CC_DOMINIO_APP en $instalacion}"
	# Mensajería (decisiones 120 y 121): WhatsApp de la plataforma y correo SMTP con el dominio de la marca.
	linea MENSAJERIA_WHATSAPP_PROVEEDOR "${MENSAJERIA_WHATSAPP_PROVEEDOR:-NINGUNO}"
	linea WHATSAPP_NUMERO_ID "${WHATSAPP_NUMERO_ID:-}"
	linea WHATSAPP_TOKEN "$(secreto whatsapp-token)"
	linea WHATSAPP_SECRETO_APP "$(secreto whatsapp-secreto-app)"
	linea WHATSAPP_TOKEN_VERIFICACION "$(secreto whatsapp-token-verificacion)"
	linea MENSAJERIA_CORREO_PROVEEDOR "${MENSAJERIA_CORREO_PROVEEDOR:-SMTP}"
	linea CORREO_REMITENTE "${CORREO_REMITENTE:-}"
	linea SPRING_MAIL_HOST "${SMTP_HOST:-}"
	linea SPRING_MAIL_PORT "${SMTP_PUERTO:-587}"
	linea SPRING_MAIL_USERNAME "${SMTP_USUARIO:-}"
	linea SPRING_MAIL_PASSWORD "$(secreto smtp-clave)"
	linea SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE true
	linea SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_REQUIRED true
} > "$tmp"
chmod 600 "$tmp"
mv "$tmp" /etc/cc/app.env

{
	echo "# Generado por cargar-secretos.sh. Lo usa cc-respaldo.service (respaldar.sh como cc_respaldo)."
	linea RESPALDO_DB_HOST "$CC_SQL_IP"
	linea RESPALDO_DB_PUERTO 3306
	linea RESPALDO_DB_USUARIO cc_respaldo
	linea RESPALDO_DB_CLAVE "$(secreto db-respaldo)"
	linea RESPALDO_MYSQL_OPCIONES "--ssl-mode=VERIFY_CA --ssl-ca=/certs/server-ca.pem"
	linea RESPALDO_AGE_DESTINATARIOS "${RESPALDO_AGE_DESTINATARIOS:?Faltan las claves públicas age de los custodios en $instalacion}"
	linea RESPALDO_DESTINO "${RESPALDO_DESTINO:-respaldos}"
	linea RESPALDO_AVISO_CORREO "${CC_OPERADOR_CORREO:-}"
	# Las credenciales del destino (bucket con bloqueo de objetos, clave SIN permiso de borrar): líneas RCLONE_CONFIG_*.
	secreto rclone-respaldo | grep -E '^RCLONE_CONFIG_[A-Z0-9_]+=' || true
} > "$tmp.r"
chmod 600 "$tmp.r"
mv "$tmp.r" /etc/cc/respaldo.env
echo "Listo: /etc/cc/app.env y /etc/cc/respaldo.env (600, root)."
