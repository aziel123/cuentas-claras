#!/bin/sh
# Cuentas Claras · «PASO 0» (sección 6.6 del diseño): ¿acepta Cloud SQL para MySQL 8.4 los 82 triggers, las 9 funciones,
# el rol cc_negocio, permisos_objetos() (lee mysql.*), el TLS sin proxy y el arranque de prod? Corre el job «mysql» del CI
# COMPLETO contra una instancia de PRUEBA, más las comprobaciones a mano de la sección 6.6 y un respaldo con su
# restauración. Si algo falla, se cambia de proveedor ANTES de desplegar (AWS RDS en sa-east-1).
#
#   CC_PROYECTO=<proyecto> sh deploy/gcp/paso-0.sh crear     # la instancia de prueba (pocos dólares si se borra el mismo día)
#   CC_PROYECTO=<proyecto> sh deploy/gcp/paso-0.sh probar    # el job mysql, las comprobaciones, el respaldo y la restauración
#   CC_PROYECTO=<proyecto> sh deploy/gcp/paso-0.sh borrar    # borra la instancia (no tiene protección contra borrado)
#
# Dónde correrlo: en Cloud Shell (ya trae gcloud y Docker) o en una máquina con gcloud, Docker y salida a internet.
# La instancia de prueba tiene IP PÚBLICA autorizada SOLO para la IP de esta máquina (PASO0_IP_AUTORIZADA) y empieza
# admitiendo conexiones sin TLS (el job del CI las usa en sus pruebas, como el MySQL del CI); al final de «probar» pasa a
# TLS obligatorio (ENCRYPTED_ONLY) y se comprueba que sin TLS no conecta y que prod arranca con sslMode=VERIFY_CA.
# Por IP privada (la de producción) lo comprueba el primer despliegue (desplegar.sh lee las mismas líneas del verificador).
#
# Prueba local (sin Google Cloud): PASO0_SQL_IP=<ip de un MySQL 8.4> PASO0_ROOT_CLAVE=<clave> sh deploy/gcp/paso-0.sh probar
# Los resultados (registros y el resumen para el acta) quedan en $PASO0_DIR, FUERA del repositorio. Ninguna clave se sube.
set -eu

aqui=$(cd "$(dirname "$0")" && pwd)
CC_RAIZ=$(cd "$aqui/../.." && pwd)
accion="${1:-}"
CC_INSTANCIA=paso0
CC_PROYECTO="${CC_PROYECTO:-local}"
export CC_INSTANCIA CC_RAIZ CC_PROYECTO
# shellcheck source=deploy/gcp/variables.sh
. "$aqui/variables.sh"
CC_SQL="${PASO0_SQL:-cc-paso0}"
PASO0_DIR="${PASO0_DIR:-$HOME/cc-paso0}"
herr="cc-paso0-herr"
rest="cc-paso0-restauracion"
mkdir -p "$PASO0_DIR"
chmod 700 "$PASO0_DIR"
clave_root_archivo="$PASO0_DIR/root.clave"

ip_sql() {
	if [ -n "${PASO0_SQL_IP:-}" ]; then echo "$PASO0_SQL_IP"; return; fi
	cc_gcloud sql instances describe "$CC_SQL" --format 'value(ipAddresses[0].ipAddress)'
}

clave_root() {
	if [ -n "${PASO0_ROOT_CLAVE:-}" ]; then echo "$PASO0_ROOT_CLAVE"; return; fi
	cat "$clave_root_archivo"
}

case "$accion" in
crear)
	ip="${PASO0_IP_AUTORIZADA:-$(curl -fsS https://checkip.amazonaws.com | tr -d '[:space:]')}"
	echo "$ip" | grep -Eq '^[0-9]{1,3}(\.[0-9]{1,3}){3}$' || { echo "No se pudo saber la IP pública de esta máquina: define PASO0_IP_AUTORIZADA." >&2; exit 1; }
	[ -s "$clave_root_archivo" ] || { openssl rand -hex 24 > "$clave_root_archivo"; chmod 600 "$clave_root_archivo"; }
	cc_paso "Instancia de prueba $CC_SQL (MySQL 8.4, $CC_REGION), autorizada solo para $ip"
	cc_gcloud services enable sqladmin.googleapis.com
	cc_gcloud sql instances create "$CC_SQL" \
		--database-version "$CC_SQL_VERSION" --edition ENTERPRISE --tier "$CC_SQL_TIER" \
		--region "$CC_REGION" --availability-type ZONAL \
		--assign-ip --authorized-networks "$ip/32" --ssl-mode ALLOW_UNENCRYPTED_AND_ENCRYPTED \
		--database-flags log_bin_trust_function_creators=on \
		--storage-type SSD --storage-size 10GB --backup-start-time 07:00 --enable-bin-log \
		--no-deletion-protection --root-password "$(cat "$clave_root_archivo")"
	cc_gcloud sql instances describe "$CC_SQL" --format 'value(serverCaCert.cert)' > "$PASO0_DIR/server-ca.pem"
	echo "Lista. IP: $(ip_sql). Siguiente: sh deploy/gcp/paso-0.sh probar"
	;;

probar)
	ip=$(ip_sql)
	root=$(clave_root)
	cc_paso "Contenedor de herramientas y MySQL desechable para la restauración"
	docker build -q -t cc-herramientas:local "$CC_RAIZ/deploy/herramientas" > /dev/null
	docker rm -f "$rest" "$herr" > /dev/null 2>&1 || true
	docker run -d --name "$herr" -v "$CC_RAIZ:/repo" -v "$PASO0_DIR:/resultados" -v cc-paso0-m2:/root/.m2 -w /repo \
		cc-herramientas:local sleep infinity > /dev/null
	docker run -d --name "$rest" --network "container:$herr" -e MYSQL_ROOT_PASSWORD=root-restauracion-ci mysql:8.4 \
		--port=3308 --mysqlx=OFF > /dev/null
	# 127.0.0.1:3306 del contenedor = la instancia (socat solo reenvía TCP: el TLS es de punta a punta con Cloud SQL).
	docker exec -d "$herr" socat TCP-LISTEN:3306,fork,reuseaddr "TCP:$ip:3306"
	admin="mysql --protocol=TCP -h 127.0.0.1 -P 3306 -uroot -p$root"
	en_herr() {
		docker exec -i -e MYSQL_ADMIN="$admin" -e PASO0_ROOT="$root" "$herr" bash -eo pipefail -c "$1"
	}

	cc_paso "1. El job «mysql» del CI completo (SET PERSIST no se usa: la marca es de la instancia)"
	en_herr "sed -i 's/\r\$//' mvnw && ./mvnw -B -q -DskipTests package \
		&& python3 scripts/ci/correr-job.py mysql --env \"MYSQL_ADMIN=\$MYSQL_ADMIN\" \
			--quitar 'SET PERSIST log_bin_trust_function_creators'" 2>&1 | tee "$PASO0_DIR/job-mysql.log"

	cc_paso "2. Comprobaciones a mano (sección 6.6, punto 4)"
	en_herr '
		a() { $MYSQL_ADMIN -N cuentasclaras -e "$1"; }
		echo "-- Privilegios del administrador del servicio:"; $MYSQL_ADMIN -N -e "SHOW GRANTS" | tr "," "\n" \
			| grep -E "ROLE_ADMIN|CREATE ROLE|DROP ROLE|CREATE USER|SUPER" || true
		echo "-- huellas_objetos(): $(a "SELECT JSON_LENGTH(huellas_objetos())") objetos (esperados: 91)"
		echo "-- permisos_objetos(): $(a "SELECT JSON_LENGTH(permisos_objetos())") líneas (sin error)"
		echo "-- Usuarios cc_* con un rol que no es cc_negocio: $(a "SELECT COUNT(*) FROM mysql.role_edges WHERE TO_USER LIKE \"cc%\" AND FROM_USER <> \"cc_negocio\"")"
		echo "-- log_bin_trust_function_creators: $(a "SELECT @@GLOBAL.log_bin_trust_function_creators")"
		echo "-- general_log (debe ser 0): $(a "SELECT @@GLOBAL.general_log")"
		echo "-- TLS con la autoridad del servidor (VERIFY_CA):"
		mysql --protocol=TCP -h 127.0.0.1 -P 3306 -uroot -p"$PASO0_ROOT" --ssl-mode=VERIFY_CA --ssl-ca=/resultados/server-ca.pem \
			-N -e "SHOW SESSION STATUS LIKE \"Ssl_cipher\"" || echo "(sin server-ca.pem: corre «crear» o bájalo de la consola)"
	' 2>&1 | tee "$PASO0_DIR/comprobaciones.log"

	cc_paso "3. Respaldo (respaldar.sh como cc_respaldo, destino simulado de prueba) y restauración en el MySQL desechable"
	en_herr '
		$MYSQL_ADMIN cuentasclaras -e "INSERT INTO configuracion_bd (clave, valor, creado_en) SELECT \"respaldo_simulado\", \"PERMITIDA\", NOW(6) FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM configuracion_bd WHERE clave = \"respaldo_simulado\")"
		mkdir -p /tmp/claves && age-keygen -o /tmp/claves/a.txt 2>/dev/null && age-keygen -o /tmp/claves/b.txt 2>/dev/null
		export RESPALDO_DB_HOST=127.0.0.1 RESPALDO_DB_CLAVE=respaldo-solo-ci-1 RESPALDO_DESTINO=simulado \
			RESPALDO_SIMULADO_DIR=/tmp/respaldos RESPALDO_MYSQL_OPCIONES=--ssl-mode=REQUIRED \
			RESPALDO_AGE_DESTINATARIOS="$(age-keygen -y /tmp/claves/a.txt) $(age-keygen -y /tmp/claves/b.txt)"
		sh scripts/respaldo/respaldar.sh
		RESTAURAR_ORIGEN=simulado RESTAURAR_AGE_IDENTIDAD=/tmp/claves/b.txt \
			RESTAURAR_DB_HOST=127.0.0.1 RESTAURAR_DB_PUERTO=3308 RESTAURAR_DB_ADMIN=root \
			RESTAURAR_DB_ADMIN_CLAVE=root-restauracion-ci RESTAURAR_INFORME_DIR=/resultados/restauracion \
			RESTAURAR_ARRANCAR=no sh scripts/respaldo/restaurar-y-verificar.sh
	' 2>&1 | tee "$PASO0_DIR/respaldo.log"

	if [ -z "${PASO0_SQL_IP:-}" ]; then
		cc_paso "4. TLS obligatorio (ENCRYPTED_ONLY): sin TLS no conecta; prod arranca con sslMode=VERIFY_CA"
		cc_gcloud sql instances patch "$CC_SQL" --ssl-mode ENCRYPTED_ONLY
		en_herr '
			if mysql --protocol=TCP -h 127.0.0.1 -P 3306 -ucc_app -papp-solo-ci-1 --ssl-mode=DISABLED -e "SELECT 1" 2>/dev/null; then
				echo "ERROR: con ENCRYPTED_ONLY conectó sin TLS"; exit 1
			fi
			echo "OK: sin TLS no conecta"
			keytool -importcert -noprompt -alias cloudsql -file /resultados/server-ca.pem -keystore /tmp/ca.p12 \
				-storetype PKCS12 -storepass paso0-almacen > /dev/null
			SPRING_PROFILES_ACTIVE=prod DB_USUARIO=cc_app DB_CLAVE=app-solo-ci-1 DB_SISTEMA_USUARIO=cc_sistema \
				DB_SISTEMA_CLAVE=sistema-solo-ci-1 AUDITORIA_CLAVE_HMAC=clave-hmac-del-paso-0-de-cloud-sql-0001 \
				DB_URL="jdbc:mysql://127.0.0.1:3306/cuentasclaras?sslMode=VERIFY_CA&trustCertificateKeyStoreUrl=file:/tmp/ca.p12&trustCertificateKeyStoreType=PKCS12&trustCertificateKeyStorePassword=paso0-almacen" \
				MENSAJERIA_CORREO_PROVEEDOR=SMTP CORREO_REMITENTE=avisos@paso0.example SPRING_MAIL_HOST=127.0.0.1 \
				CC_MARCA_NOMBRE="Paso 0" CC_OPERADORES=operador.ci:operador.ci@ci.cuentasclaras.pe \
				CC_CLAVE_CONECTORES=Y2ktY29uZWN0b3Jlcy1zb2xvLWRlbC1qb2ItbXlzcWw= \
				java -jar target/cuentas-claras-*.jar --server.port=18099 > /resultados/prod-tls.log 2>&1 &
			for i in $(seq 1 90); do curl -sf http://127.0.0.1:18099/actuator/health | grep -q UP && break; sleep 2; done
			kill %1 || true
			for linea in "Huellas de los 82 triggers y las 9 funciones verificadas" "Conexiones a MySQL cifradas (TLS) verificadas" \
					"Privilegios verificados"; do
				grep -q "$linea" /resultados/prod-tls.log || { echo "ERROR: falta «$linea» (ver prod-tls.log)"; exit 1; }
				echo "OK: $linea"
			done
		' 2>&1 | tee "$PASO0_DIR/tls.log"
	fi

	docker rm -f "$rest" "$herr" > /dev/null 2>&1 || true
	cc_paso "Listo. Resultados en $PASO0_DIR: copia el resumen al acta (docs/operacion/acta-paso-0-cloud-sql.md)"
	grep -E "^\[[0-9]+\] " "$PASO0_DIR/job-mysql.log" | tail -n 40 || true
	;;

borrar)
	cc_gcloud sql instances delete "$CC_SQL"
	docker volume rm cc-paso0-m2 > /dev/null 2>&1 || true
	rm -f "$clave_root_archivo"
	echo "Instancia $CC_SQL borrada. Los registros siguen en $PASO0_DIR."
	;;

*)
	echo "Uso: CC_PROYECTO=<proyecto> sh deploy/gcp/paso-0.sh crear|probar|borrar" >&2
	exit 2
	;;
esac
