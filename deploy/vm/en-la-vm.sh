#!/bin/sh
# Cuentas Claras · los pasos del despliegue que corren EN LA VM, como root (sprint 8, tanda 3; sección 7.4 del diseño). Los
# llama deploy/desplegar.sh por SSH (IAP). Las claves de root y de cc_migrador NUNCA están en la VM: llegan por la entrada
# estándar en el momento y solo viven dentro del contenedor desechable que las usa.
#
#   en-la-vm.sh respaldo-previo           respaldo «antes de desplegar» (si la base ya tiene datos)
#   en-la-vm.sh detener                   detiene la aplicación (la base no queda a medias con prod arriba)
#   en-la-vm.sh migrar <imagen>           java -jar … migrar con cc_migrador          (entrada: la clave de cc_migrador)
#   en-la-vm.sh sql-root <archivo>        scripts/mysql/<archivo> como root            (entrada: la clave de root)
#   en-la-vm.sh sql-migrador <archivo>    scripts/mysql/<archivo> como cc_migrador     (entrada: la clave de cc_migrador)
#   en-la-vm.sh arrancar <imagen>         secretos, docker compose up y verificación del arranque
#   en-la-vm.sh demo <imagen>             la demo (sin base ni secretos)
set -eu
umask 077

dir=/opt/cuentas-claras
herramientas=cc-herramientas:local

instalacion() {
	# shellcheck disable=SC1091
	. /etc/cc/instalacion.env
}

url_jdbc() {
	instalacion
	printf 'jdbc:mysql://%s:3306/cuentasclaras?sslMode=VERIFY_CA&trustCertificateKeyStoreUrl=file:/certs/cloudsql-ca.p12&trustCertificateKeyStoreType=PKCS12&trustCertificateKeyStorePassword=%s' \
		"$CC_SQL_IP" "$CC_TRUSTSTORE_CLAVE"
}

imagen_valida() {
	echo "$1" | grep -Eq '^[a-z0-9./_-]+@sha256:[0-9a-f]{64}$' \
		|| { echo "La imagen va por digest (…@sha256:<64 hex>), nunca por etiqueta: $1" >&2; exit 1; }
}

mysql_como() {
	# $1 usuario; la clave llega por la primera línea de la entrada estándar y el SQL, del archivo $2 (montado).
	instalacion
	docker run -i --rm -v /etc/cc/certs:/certs:ro -v "$dir/scripts/mysql:/sql:ro" "$herramientas" sh -c "read -r clave; \
		printf '[client]\nuser=%s\npassword=%s\n' '$1' \"\$clave\" > /tmp/c.cnf; \
		exec mysql --defaults-extra-file=/tmp/c.cnf --ssl-mode=VERIFY_CA --ssl-ca=/certs/server-ca.pem -h '$CC_SQL_IP' \
		cuentasclaras < /sql/$2"
}

esperar_listo() {
	# $1 archivo compose. La aplicación no publica su puerto: se pregunta desde el contenedor de Caddy.
	for _ in $(seq 1 90); do
		if docker compose -f "$1" --env-file /etc/cc/imagen.env exec -T caddy \
				wget -qO- http://app:8080/actuator/health/readiness 2> /dev/null | grep -q '"UP"'; then
			return 0
		fi
		sleep 2
	done
	return 1
}

case "${1:-}" in
respaldo-previo)
	instalacion
	hay=$(docker run --rm --env-file /etc/cc/respaldo.env -v /etc/cc/certs:/certs:ro "$herramientas" sh -c \
		'printf "[client]\nuser=%s\npassword=%s\n" "$RESPALDO_DB_USUARIO" "$RESPALDO_DB_CLAVE" > /tmp/c.cnf; mysql --defaults-extra-file=/tmp/c.cnf $RESPALDO_MYSQL_OPCIONES -h "$RESPALDO_DB_HOST" -N -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = '"'"'cuentasclaras'"'"' AND TABLE_NAME = '"'"'flyway_schema_history'"'"'"' 2> /dev/null || echo 0)
	if [ "$hay" = "0" ]; then
		echo "Base sin migrar (primer despliegue): no hay nada que respaldar."
	else
		systemctl start cc-respaldo.service
		echo "Respaldo «antes de desplegar» hecho."
	fi
	;;

detener)
	if [ -f /etc/cc/imagen.env ] && [ -f "$dir/deploy/compose.prod.yml" ]; then
		docker compose -f "$dir/deploy/compose.prod.yml" --env-file /etc/cc/imagen.env stop app || true
	fi
	;;

migrar)
	imagen="${2:?Falta la imagen}"
	imagen_valida "$imagen"
	docker pull -q "$imagen" > /dev/null
	url=$(url_jdbc)
	docker run -i --rm --read-only --tmpfs /tmp -v /etc/cc/certs:/certs:ro -e DB_URL="$url" \
		-e DB_MIGRADOR_USUARIO=cc_migrador --entrypoint sh "$imagen" -c \
		'read -r clave; DB_MIGRADOR_CLAVE="$clave" exec java -jar /app/app.jar migrar'
	;;

sql-root)
	mysql_como root "${2:?Falta el archivo de scripts/mysql}"
	;;

sql-migrador)
	mysql_como cc_migrador "${2:?Falta el archivo de scripts/mysql}"
	;;

arrancar)
	imagen="${2:?Falta la imagen}"
	imagen_valida "$imagen"
	printf 'CC_IMAGEN=%s\n' "$imagen" > /etc/cc/imagen.env
	sh "$dir/deploy/vm/cargar-secretos.sh"
	compose="$dir/deploy/compose.prod.yml"
	docker compose -f "$compose" --env-file /etc/cc/imagen.env up -d
	if ! esperar_listo "$compose"; then
		docker compose -f "$compose" --env-file /etc/cc/imagen.env logs --tail 200 app >&2 || true
		docker compose -f "$compose" --env-file /etc/cc/imagen.env stop app
		echo "ERROR: la aplicación no quedó lista: se detuvo (prod no queda arriba con la base a medias)." >&2
		exit 1
	fi
	logs=$(docker compose -f "$compose" --env-file /etc/cc/imagen.env logs app 2>&1)
	for linea in "Huellas de los 99 triggers y las 14 funciones verificadas" "Privilegios verificados" \
			"Conexiones a MySQL cifradas (TLS) verificadas" "Plataforma verificada" "Triggers verificados"; do
		if ! echo "$logs" | grep -q "$linea"; then
			docker compose -f "$compose" --env-file /etc/cc/imagen.env stop app
			echo "ERROR: falta en el log del arranque «$linea»: se detuvo la aplicación." >&2
			exit 1
		fi
	done
	printf '%s %s\n' "$(date '+%Y-%m-%d %H:%M:%S %z')" "$imagen" >> /etc/cc/desplegado.txt
	echo "Desplegada $imagen: el verificador de prod dio sus líneas (huellas, privilegios, TLS y plataforma)."
	;;

demo)
	imagen="${2:?Falta la imagen}"
	imagen_valida "$imagen"
	[ -f /etc/cc/demo.env ] || { echo "Falta /etc/cc/demo.env (lo escribe desplegar.sh demo)" >&2; exit 1; }
	if grep -Eq '^(DB_|AUDITORIA_|CC_CLAVE_|NUBEFACT_|WHATSAPP_TOKEN|PASARELA_LLAVE|SPRING_MAIL|RESPALDO_)' /etc/cc/demo.env; then
		echo "ERROR: /etc/cc/demo.env tiene una variable secreta: la demo no lleva ninguna." >&2
		exit 1
	fi
	printf 'CC_IMAGEN=%s\n' "$imagen" > /etc/cc/imagen.env
	compose="$dir/deploy/compose.demo.yml"
	docker compose -f "$compose" --env-file /etc/cc/imagen.env up -d
	esperar_listo "$compose" || { docker compose -f "$compose" --env-file /etc/cc/imagen.env logs --tail 200 app >&2; exit 1; }
	install -m 644 "$dir/deploy/systemd/cc-demo-reinicio.service" "$dir/deploy/systemd/cc-demo-reinicio.timer" \
		/etc/systemd/system/
	systemctl daemon-reload
	systemctl enable --now cc-demo-reinicio.timer
	# Sin salida: desde el contenedor de la aplicación, internet no responde (la red es internal).
	# Correcciones del sprint 8 (S8-B1): con bash (el sh de la imagen es dash, que no tiene /dev/tcp: el chequeo nunca
	# fallaba). Primero el control positivo: la misma prueba SÍ conecta con la propia aplicación (127.0.0.1:8080); si no,
	# la prueba no sirve (por ejemplo, la imagen ya no trae bash) y el despliegue se detiene.
	if ! docker compose -f "$compose" --env-file /etc/cc/imagen.env exec -T app \
			bash -c 'timeout 10 bash -c "exec 3<>/dev/tcp/127.0.0.1/8080"' 2> /dev/null; then
		echo "ERROR: la prueba de salida no funciona en el contenedor (¿falta bash?): no se puede confirmar que la demo no sale a internet." >&2
		exit 1
	fi
	if docker compose -f "$compose" --env-file /etc/cc/imagen.env exec -T app \
			bash -c 'timeout 10 bash -c "exec 3<>/dev/tcp/142.250.0.1/443"' 2> /dev/null; then
		echo "ERROR: la aplicación de la demo tiene salida a internet." >&2
		exit 1
	fi
	printf '%s %s\n' "$(date '+%Y-%m-%d %H:%M:%S %z')" "$imagen" >> /etc/cc/desplegado.txt
	echo "Demo desplegada ($imagen), sin salida a internet y con su reinicio de las 03:00."
	;;

*)
	echo "Uso: $0 respaldo-previo|detener|migrar <imagen>|sql-root <archivo>|sql-migrador <archivo>|arrancar <imagen>|demo <imagen>" >&2
	exit 2
	;;
esac
