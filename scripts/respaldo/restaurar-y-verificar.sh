#!/bin/sh
# Cuentas Claras · restauración real y verificación de un respaldo (sprint 7, tanda 1; docs/operacion/respaldos.md).
#
# Baja el respaldo pedido (o el último) y su manifiesto, comprueba su SHA-256, lo descifra con la clave privada age, crea
# una base NUEVA en un MySQL vacío (los usuarios de 01 con claves de un solo uso), migra con el jar hasta la versión del
# manifiesto, carga los datos, aplica 02 y 03 (los triggers después de cargar: los de nacimiento rechazarían filas
# históricas), corre «java -jar cuentas-claras.jar verificar-respaldo» (cadena de la bitácora, anclas, conteos y libros)
# y arranca la aplicación en prod sobre la copia (verificador de permisos y /actuator/health/readiness). Escribe un
# informe JSON y una línea de resumen. Los datos restaurados no salen de esta máquina.
#
# Variables:
#   RESTAURAR_AGE_IDENTIDAD    archivo con la clave privada age (nunca en el servidor de la aplicación)
#   RESTAURAR_ORIGEN           «simulado» (RESPALDO_SIMULADO_DIR) o un remoto de rclone con RCLONE_CONFIG_<NOMBRE>_*
#   RESTAURAR_ARCHIVO          cc-AAAAMMDD-HHMMSS.sql.gz.age (por defecto, el último)
#   RESTAURAR_JAR              el jar de la MISMA versión (por defecto target/cuentas-claras-*.jar o ./cuentas-claras.jar)
#   Base vacía: RESTAURAR_DOCKER=si (crea un mysql:8.4 desechable en RESTAURAR_DOCKER_PUERTO, 3399, y lo borra) o
#               RESTAURAR_DB_HOST, RESTAURAR_DB_PUERTO (3306), RESTAURAR_DB_ADMIN (root), RESTAURAR_DB_ADMIN_CLAVE
#   AUDITORIA_CLAVE_HMAC       opcional: con ella se recalcula el HMAC de toda la cadena (simulacro presencial y CI)
#   RESTAURAR_ARRANCAR         si (por defecto) o no: arrancar la aplicación en prod sobre la copia
#   RESTAURAR_PUERTO_APP       18090
#   RESTAURAR_INFORME_DIR      dónde escribir restauracion-AAAAMMDD-HHMMSS.json (.)
#   RESTAURAR_AVISO_CORREO     a quién enviar el resumen (con el comando «mail»)
# Código de salida: 0 si todo pasó; 1 si algo falló (el informe dice qué).
set -eu
umask 077

aqui=$(cd "$(dirname "$0")" && pwd)
raiz=$(cd "$aqui/../.." && pwd)
scripts_mysql="${RESTAURAR_SCRIPTS_MYSQL:-$raiz/scripts/mysql}"
: "${RESTAURAR_AGE_IDENTIDAD:?Falta RESTAURAR_AGE_IDENTIDAD (archivo con la clave privada age)}"
origen="${RESTAURAR_ORIGEN:-simulado}"
carpeta_simulada="${RESPALDO_SIMULADO_DIR:-$HOME/cuentas-claras-respaldos-simulados}"
archivo="${RESTAURAR_ARCHIVO:-}"
informes="${RESTAURAR_INFORME_DIR:-.}"
puerto_app="${RESTAURAR_PUERTO_APP:-18090}"
arrancar="${RESTAURAR_ARRANCAR:-si}"
jar="${RESTAURAR_JAR:-}"
if [ -z "$jar" ]; then
	for candidato in "$raiz"/target/cuentas-claras-*.jar ./cuentas-claras.jar; do
		[ -f "$candidato" ] && jar="$candidato" && break
	done
fi
[ -n "$jar" ] && [ -f "$jar" ] || { echo "restauración: falta el jar (RESTAURAR_JAR)" >&2; exit 1; }
for comando in age java mysql gzip curl; do
	command -v "$comando" >/dev/null 2>&1 || { echo "restauración: falta $comando" >&2; exit 1; }
done

trabajo=$(mktemp -d "${TMPDIR:-/tmp}/cc-restauracion.XXXXXX")
contenedor=""
pid_app=""
limpiar() {
	[ -z "$pid_app" ] || kill "$pid_app" 2>/dev/null || true
	[ -z "$contenedor" ] || docker rm -f "$contenedor" >/dev/null 2>&1 || true
	rm -rf "$trabajo"
}
trap limpiar EXIT INT TERM

empezo=$(date -u +%Y-%m-%dT%H:%M:%SZ)
pasos=""
resultado="OK"
paso() {
	# paso <nombre> <OK|FALLA> <detalle>: se acumula para el informe (sin datos personales)
	[ -z "$pasos" ] || pasos="$pasos,"
	detalle=$(printf '%s' "$3" | tr '"\\\n\t' "'/  " | head -c 400)
	pasos="$pasos{\"paso\":\"$1\",\"resultado\":\"$2\",\"detalle\":\"$detalle\"}"
	echo "[$2] $1: $3"
	[ "$2" = "OK" ] || resultado="FALLA"
}

azar() {
	od -An -N18 -tx1 /dev/urandom | tr -d ' \n'
}

sha256() {
	if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | cut -d' ' -f1
	elif command -v shasum >/dev/null 2>&1; then shasum -a 256 "$1" | cut -d' ' -f1
	else openssl dgst -sha256 -r "$1" | cut -d' ' -f1; fi
}

campo() {
	sed -n "s/^ *\"$1\": *\"\{0,1\}\([^\",]*\)\"\{0,1\},*$/\1/p" "$2" | head -n 1
}

informe() {
	mkdir -p "$informes"
	nombre="restauracion-$(echo "${archivo:-sin-archivo}" | sed 's/^cc-//; s/\.sql\.gz\.age$//').json"
	{
		echo "{"
		echo "  \"archivo\": \"$archivo\","
		echo "  \"inicio\": \"$empezo\","
		echo "  \"fin\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\","
		echo "  \"resultado\": \"$resultado\","
		echo "  \"pasos\": [$pasos],"
		if [ -s "$trabajo/verificacion.json" ]; then
			printf '  "verificacion": '
			cat "$trabajo/verificacion.json"
			echo
		else
			echo "  \"verificacion\": null"
		fi
		echo "}"
	} > "$informes/$nombre"
	echo "Restauración de $archivo: $resultado (informe: $informes/$nombre)"
	if [ -n "${RESTAURAR_AVISO_CORREO:-}" ] && command -v mail >/dev/null 2>&1; then
		mail -s "[Cuentas Claras] Restauración de $archivo: $resultado" "$RESTAURAR_AVISO_CORREO" < "$informes/$nombre" || true
	fi
}

terminar() {
	informe
	[ "$resultado" = "OK" ] && exit 0
	exit 1
}

# --- 1. Bajar el respaldo y su manifiesto; comprobar el SHA-256 ---------------------------------------------------------
# Solo variables de entorno RCLONE_CONFIG_<NOMBRE>_*: ningún archivo de configuración de rclone.
[ "$origen" = "simulado" ] || export RCLONE_CONFIG=/dev/null
listar() {
	if [ "$origen" = "simulado" ]; then
		ls "$carpeta_simulada" 2>/dev/null | grep -E '^cc-[0-9]{8}-[0-9]{6}\.manifiesto\.json$' | sort
	else
		rclone lsf "$origen:" --include 'cc-*.manifiesto.json' | sort
	fi
}
traer() {
	if [ "$origen" = "simulado" ]; then cp "$carpeta_simulada/$1" "$2"; else rclone copyto "$origen:$1" "$2"; fi
}
manifiestos=$(listar || true)
if [ -z "$archivo" ]; then
	ultimo=$(echo "$manifiestos" | tail -n 1)
	[ -n "$ultimo" ] || { paso "bajar" "FALLA" "no hay respaldos en «$origen»"; terminar; }
	archivo=$(echo "$ultimo" | sed 's/\.manifiesto\.json$/.sql.gz.age/')
fi
echo "$archivo" | grep -Eq '^cc-[0-9]{8}-[0-9]{6}\.sql\.gz\.age$' || { paso "bajar" "FALLA" "nombre de respaldo no válido"; terminar; }
base_nombre=$(echo "$archivo" | sed 's/\.sql\.gz\.age$//')
manifiesto="$trabajo/$base_nombre.manifiesto.json"
cifrado="$trabajo/$archivo"
if ! traer "$base_nombre.manifiesto.json" "$manifiesto" || ! traer "$archivo" "$cifrado"; then
	paso "bajar" "FALLA" "no se pudo bajar $archivo o su manifiesto de «$origen»"
	terminar
fi
mkdir "$trabajo/anteriores"
for anterior in $(echo "$manifiestos" | awk -v actual="$base_nombre.manifiesto.json" '$0 < actual' | tail -n 7); do
	traer "$anterior" "$trabajo/anteriores/$anterior" || true
done
esperado=$(campo sha256 "$manifiesto")
bytes_esperados=$(campo bytes "$manifiesto")
version=$(campo version_esquema "$manifiesto")
if [ "$(sha256 "$cifrado")" != "$esperado" ] || [ "$(wc -c < "$cifrado" | tr -d ' ')" != "$bytes_esperados" ]; then
	paso "sha256" "FALLA" "el archivo no coincide con su manifiesto (SHA-256 o tamaño)"
	terminar
fi
paso "sha256" "OK" "$archivo coincide con su manifiesto ($bytes_esperados bytes, esquema V$version)"

# --- 2. Un MySQL vacío (desechable o el que se indique) ------------------------------------------------------------------
if [ "${RESTAURAR_DOCKER:-no}" = "si" ]; then
	host="${RESTAURAR_DOCKER_HOST:-127.0.0.1}"
	puerto="${RESTAURAR_DOCKER_PUERTO:-3399}"
	admin=root
	clave_admin=$(azar)
	contenedor="cc-restauracion-$$"
	docker run -d --rm --name "$contenedor" -e MYSQL_ROOT_PASSWORD="$clave_admin" -p "127.0.0.1:$puerto:3306" \
		"${RESTAURAR_DOCKER_IMAGEN:-mysql:8.4}" --log-bin-trust-function-creators=1 >/dev/null
else
	: "${RESTAURAR_DB_HOST:?Falta RESTAURAR_DB_HOST (o RESTAURAR_DOCKER=si)}"
	: "${RESTAURAR_DB_ADMIN_CLAVE:?Falta RESTAURAR_DB_ADMIN_CLAVE}"
	host="$RESTAURAR_DB_HOST"
	puerto="${RESTAURAR_DB_PUERTO:-3306}"
	admin="${RESTAURAR_DB_ADMIN:-root}"
	clave_admin="$RESTAURAR_DB_ADMIN_CLAVE"
fi
opciones_admin="$trabajo/admin.cnf"
printf '[client]\nhost=%s\nport=%s\nuser=%s\npassword=%s\nprotocol=TCP\ndefault-character-set=utf8mb4\n' \
	"$host" "$puerto" "$admin" "$clave_admin" > "$opciones_admin"
admin_sql() {
	mysql --defaults-extra-file="$opciones_admin" -N -B "$@"
}
listo=no
for _ in $(seq 1 90); do
	if admin_sql -e "SELECT 1" >/dev/null 2>&1 < /dev/null; then listo=si; break; fi
	sleep 2
done
[ "$listo" = "si" ] || { paso "base" "FALLA" "el MySQL de la restauración no responde en $host:$puerto"; terminar; }
if [ "$(admin_sql -e "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = 'cuentasclaras'" < /dev/null)" != "0" ]; then
	paso "base" "FALLA" "$host:$puerto ya tiene una base cuentasclaras: la restauración solo usa un MySQL vacío"
	terminar
fi

# --- 3. Usuarios con claves de un solo uso, migración hasta la versión del manifiesto ------------------------------------
clave_migrador=$(azar)
clave_app=$(azar)
clave_sistema=$(azar)
clave_respaldo=$(azar)
sed -e "s/__CLAVE_MIGRADOR__/$clave_migrador/" -e "s/__CLAVE_APP__/$clave_app/" -e "s/__CLAVE_RESPALDO__/$clave_respaldo/" \
	-e "s/__CLAVE_SISTEMA__/$clave_sistema/" "$scripts_mysql/01-usuarios.sql" | admin_sql
admin_sql -e "SET PERSIST log_bin_trust_function_creators = 1" < /dev/null
url="jdbc:mysql://$host:$puerto/cuentasclaras?allowPublicKeyRetrieval=true&useSSL=false"
if DB_URL="$url" DB_MIGRADOR_USUARIO=cc_migrador DB_MIGRADOR_CLAVE="$clave_migrador" DB_MIGRAR_HASTA="$version" \
		java -jar "$jar" migrar > "$trabajo/migrar.log" 2>&1; then
	paso "migrar" "OK" "esquema hasta V$version con el jar $(basename "$jar")"
else
	paso "migrar" "FALLA" "$(tail -n 3 "$trabajo/migrar.log")"
	terminar
fi

# --- 4. Cargar los datos (las filas que sembraron las migraciones se reemplazan por las del respaldo) ---------------------
tablas=$(admin_sql -e "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'cuentasclaras'
	AND TABLE_TYPE = 'BASE TABLE' AND TABLE_NAME <> 'flyway_schema_history'" < /dev/null)
{
	echo "SET FOREIGN_KEY_CHECKS = 0;"
	for tabla in $tablas; do echo "DELETE FROM \`$tabla\`;"; done
} | admin_sql cuentasclaras
estado="$trabajo/estado"
: > "$estado"
{ age -d -i "$RESTAURAR_AGE_IDENTIDAD" "$cifrado" 2>"$trabajo/age.err" || echo "age" >> "$estado"; } \
	| { gzip -dc || echo "gzip" >> "$estado"; } \
	| { admin_sql cuentasclaras 2>"$trabajo/carga.err" || echo "carga" >> "$estado"; }
if [ -s "$estado" ]; then
	paso "cargar" "FALLA" "$(tr '\n' ' ' < "$estado"): $(head -c 300 "$trabajo/age.err") $(head -c 300 "$trabajo/carga.err")"
	terminar
fi
paso "cargar" "OK" "datos descifrados y cargados sin pasar por el disco"

# --- 5. Permisos y triggers de la misma versión ---------------------------------------------------------------------------
opciones_migrador="$trabajo/migrador.cnf"
printf '[client]\nhost=%s\nport=%s\nuser=cc_migrador\npassword=%s\nprotocol=TCP\n' "$host" "$puerto" "$clave_migrador" \
	> "$opciones_migrador"
if admin_sql < "$scripts_mysql/02-permisos-tablas.sql" 2>"$trabajo/02.err" \
		&& mysql --defaults-extra-file="$opciones_migrador" cuentasclaras < "$scripts_mysql/03-triggers.sql" 2>"$trabajo/03.err"; then
	paso "permisos_y_triggers" "OK" "02-permisos-tablas.sql y 03-triggers.sql aplicados después de cargar"
else
	paso "permisos_y_triggers" "FALLA" "$(cat "$trabajo/02.err" "$trabajo/03.err" | grep -v Warning | head -c 300)"
	terminar
fi

# --- 5b. Sprint 7, tanda 2 (H7): huellas de los triggers y funciones leídas como ADMINISTRADOR directamente de
#         information_schema (sin pasar por huellas_objetos(), que un DBA podría reemplazar). verificar-respaldo las compara
#         con las del jar. Misma normalización que huellas_objetos() y HuellasObjetosBd.
admin_sql > "$trabajo/huellas-admin.tsv" <<'SQL'
SELECT t.TRIGGER_NAME, SHA2(CONCAT_WS('|', t.ACTION_TIMING, t.EVENT_MANIPULATION, t.EVENT_OBJECT_TABLE,
    TRIM(REGEXP_REPLACE(REGEXP_REPLACE(t.ACTION_STATEMENT, CONCAT('-', '-[^\n]*'), ''), '[[:space:]]+', ' '))), 256)
  FROM information_schema.TRIGGERS t WHERE t.TRIGGER_SCHEMA = 'cuentasclaras'
UNION ALL
SELECT r.ROUTINE_NAME, SHA2(CONCAT_WS('|', r.ROUTINE_TYPE,
    TRIM(REGEXP_REPLACE(REGEXP_REPLACE(r.ROUTINE_DEFINITION, CONCAT('-', '-[^\n]*'), ''), '[[:space:]]+', ' '))), 256)
  FROM information_schema.ROUTINES r WHERE r.ROUTINE_SCHEMA = 'cuentasclaras';
SQL

# --- 5c. Correcciones del sprint 7 (S7-M1): los GRANT de prod, releídos. El manifiesto trae los permisos de las cuentas
#         de la aplicación tal como estaban en prod al respaldar (permisos_objetos(), leída por cc_respaldo); la copia
#         tiene los que da 02 de esta versión. Un GRANT dado a mano o un rol anidado en cc_negocio en prod aparece aquí
#         aunque nadie haya reiniciado la aplicación (el verificador de prod solo mira al arrancar).
lineas_permisos() {
	# ["ROL a A b", "GLOBAL c ..."]: una línea por elemento, ordenadas (POSIX: el separador «", "» pasa a un salto).
	sed -e 's/^ *\[//' -e 's/\] *,* *$//' -e 's/", *"/"@"/g' | tr '@' '\n' | tr -d '"' | sed '/^ *$/d' | sort
}
sed -n 's/^ *"permisos": *\(\[.*\]\),*$/\1/p' "$manifiesto" | head -n 1 | lineas_permisos > "$trabajo/permisos-prod.txt"
admin_sql cuentasclaras -e "SELECT permisos_objetos()" < /dev/null 2>"$trabajo/permisos.err" | lineas_permisos \
	> "$trabajo/permisos-02.txt" || true
if ! grep -q '"permisos":' "$manifiesto"; then
	paso "permisos" "OK" "el manifiesto no trae los permisos (respaldo anterior a las correcciones del sprint 7)"
elif [ ! -s "$trabajo/permisos-02.txt" ]; then
	paso "permisos" "FALLA" "no se pudieron leer los permisos que da 02 en la copia: $(head -c 200 "$trabajo/permisos.err")"
else
	de_mas=$(comm -23 "$trabajo/permisos-prod.txt" "$trabajo/permisos-02.txt" | paste -sd'|' -)
	faltan=$(comm -13 "$trabajo/permisos-prod.txt" "$trabajo/permisos-02.txt" | paste -sd'|' -)
	if [ -z "$de_mas" ] && [ -z "$faltan" ]; then
		paso "permisos" "OK" "los permisos de prod son los de 02-permisos-tablas.sql ($(wc -l < "$trabajo/permisos-02.txt" | tr -d ' ') líneas, sin roles anidados)"
	else
		paso "permisos" "FALLA" "los permisos de prod no son los de 02. De más en prod: ${de_mas:-ninguno}. Faltan en prod: ${faltan:-ninguno}"
	fi
fi

# --- 6. Verificación de la copia (cadena, anclas, conteos, libros y objetos de la base) ----------------------------------
if DB_URL="$url" DB_USUARIO=cc_app DB_CLAVE="$clave_app" RESPALDO_MANIFIESTO="$manifiesto" \
		RESPALDO_HUELLAS_ADMIN="$trabajo/huellas-admin.tsv" \
		RESPALDO_MANIFIESTOS_ANTERIORES="$trabajo/anteriores" RESPALDO_INFORME="$trabajo/verificacion.json" \
		java -jar "$jar" verificar-respaldo > "$trabajo/verificar.log" 2>&1; then
	paso "verificar_respaldo" "OK" "$(grep -c '^.*OK ' "$trabajo/verificar.log" || true) comprobaciones pasaron$( [ -n "${AUDITORIA_CLAVE_HMAC:-}" ] && echo ', con el HMAC de toda la cadena')"
else
	paso "verificar_respaldo" "FALLA" "$(grep 'FALLA' "$trabajo/verificar.log" | sed 's/.*FALLA //' | head -n 5 | tr '\n' ' ')"
	cat "$trabajo/verificar.log" >&2
	terminar
fi

# --- 7. La aplicación arranca en prod sobre la copia (verificador de permisos y readiness) --------------------------------
if [ "$arrancar" = "si" ]; then
	# Clave HMAC DESECHABLE: el arranque solo comprueba permisos, triggers y readiness, y lo que selle va a una copia que
	# se borra. Nunca la clave real (en prod no se acepta la de pruebas y la real no debe salir de su custodia).
	clave_hmac="restauracion-$(azar)$(azar)"
	# Sprint 7, tanda 2: con sus dos usuarios (cc_app y cc_sistema). La copia está en un MySQL desechable local: sin TLS.
	SPRING_PROFILES_ACTIVE=prod DB_URL="$url" DB_USUARIO=cc_app DB_CLAVE="$clave_app" AUDITORIA_CLAVE_HMAC="$clave_hmac" \
		DB_SISTEMA_USUARIO=cc_sistema DB_SISTEMA_CLAVE="$clave_sistema" CC_EXIGIR_TLS_BD=false \
		MENSAJERIA_CORREO_PROVEEDOR=SMTP CORREO_REMITENTE="restauracion@cuentasclaras.invalid" SPRING_MAIL_HOST=127.0.0.1 \
		CUENTASCLARAS_TAREAS_ACTIVAS=false java -jar "$jar" --server.port="$puerto_app" > "$trabajo/app.log" 2>&1 &
	pid_app=$!
	arranco=no
	for _ in $(seq 1 90); do
		if curl -sf "http://127.0.0.1:$puerto_app/actuator/health/readiness" 2>/dev/null | grep -q '"UP"'; then
			arranco=si
			break
		fi
		kill -0 "$pid_app" 2>/dev/null || break
		sleep 2
	done
	if [ "$arranco" = "si" ] && grep -q "Triggers verificados" "$trabajo/app.log"; then
		paso "arranque_prod" "OK" "la aplicación arrancó en prod sobre la copia: verificador de permisos y readiness UP"
	else
		paso "arranque_prod" "FALLA" "la aplicación no arrancó en prod sobre la copia"
		tail -n 40 "$trabajo/app.log" >&2
	fi
	kill "$pid_app" 2>/dev/null || true
	wait "$pid_app" 2>/dev/null || true
	pid_app=""
fi

terminar
