#!/bin/sh
# Cuentas Claras · respaldo diario cifrado (sprint 7, tanda 1; docs/operacion/respaldos.md).
#
# Vuelca SOLO los datos de la base (el esquema, los GRANT y los triggers se rehacen desde el jar y scripts/mysql/ de la
# misma versión), los comprime y los cifra con age para dos destinatarios en una sola tubería (nada queda sin cifrar en el
# disco), compara los conteos y el ancla de la bitácora con el manifiesto del respaldo anterior, sube el archivo y su
# manifiesto al destino y registra la fila en `respaldo` como cc_respaldo (trg_respaldo_registro exige anclas reales).
#
# Variables (ninguna credencial va en este archivo ni en el repositorio):
#   RESPALDO_DB_HOST, RESPALDO_DB_PUERTO (3306), RESPALDO_DB_USUARIO (cc_respaldo), RESPALDO_DB_CLAVE,
#   RESPALDO_DB_NOMBRE (cuentasclaras), RESPALDO_MYSQL_OPCIONES (por ejemplo --ssl-mode=REQUIRED)
#   RESPALDO_AGE_DESTINATARIOS   claves públicas age (age1...), separadas por espacios o comas: la de Promotoría y la del
#                                responsable técnico (decisión 90). El servidor NUNCA tiene las claves privadas.
#   RESPALDO_MIN_DESTINATARIOS   (2)
#   RESPALDO_DESTINO             «simulado» (por defecto: una carpeta local, solo en dev, CI y piloto; la base de prod no
#                                lo admite) o el nombre de un remoto de rclone cuyas credenciales llegan SOLO por variables
#                                de entorno RCLONE_CONFIG_<NOMBRE>_* (este script no lee ningún archivo de configuración
#                                de rclone).
#   RESPALDO_SIMULADO_DIR        carpeta del destino simulado ($HOME/cuentas-claras-respaldos-simulados)
#   RESPALDO_TRABAJO             carpeta de trabajo (${TMPDIR:-/tmp}); el archivo cifrado se borra al terminar
#   RESPALDO_AVISO_CORREO        a quién avisar si algo falla o faltan filas (con el comando «mail» del servidor)
#
# Código de salida: 0 bien; 3 respaldo hecho, pero FALTAN filas o el ancla del respaldo anterior (alerta CRÍTICA: el
# respaldo es la evidencia); 1 cualquier otra falla (no quedó registrado: «Sin respaldo» a las 26 h).
#
# Correcciones del sprint 7 (QA-S7-1): la alerta «faltan filas» NO la borra un segundo respaldo. Mientras la base diga
# que hay un respaldo con FALTAN_FILAS sin resolver (resolucion_respaldo, la escribe Promotoría con su firma), se compara
# contra la LÍNEA BASE del manifiesto anterior (el bloque «base:»: por tabla, la referencia que mostró la falta, y el
# ancla) y el registro dice FALTAN_FILAS (trg_respaldo_registro lo exige). Resuelta, la línea base vuelve a ser el
# último manifiesto. Correcciones del sprint 7 (S7-M1): el manifiesto guarda los permisos de las cuentas de la
# aplicación (permisos_objetos(), de 02) para que el simulacro semanal los compare con los de 02.
#
# Sprint 8, tanda 3 (V30, decisión 131): «faltan filas» POR COLEGIO. Las tablas vigiladas que tienen colegio_id se cuentan
# por colegio (bloque «colegios» del manifiesto: "tabla@colegio": [filas, id máximo]); las demás, como antes. El registro
# dice en colegios_faltan qué colegios tienen filas faltantes y cada diferencia lleva su colegio («pago (faltan 1)
# [colegio 2]»). Lo que no es de un colegio cliente (tablas sin colegio, el ancla de la bitácora y las filas del colegio de
# plataforma) afecta a TODOS los colegios. Mientras la Promotoría de un colegio no resuelva la suya, ese colegio sigue en
# colegios_faltan (con su línea base) y los demás comparan con el último manifiesto. En una instalación dedicada (un
# colegio) es lo mismo que en el sprint 7.
set -eu
umask 077

aqui=$(cd "$(dirname "$0")" && pwd)
tablas_vigiladas="$aqui/tablas-vigiladas.txt"

: "${RESPALDO_DB_HOST:?Falta RESPALDO_DB_HOST}"
: "${RESPALDO_DB_CLAVE:?Falta RESPALDO_DB_CLAVE (la clave de cc_respaldo, desde el gestor de secretos)}"
: "${RESPALDO_AGE_DESTINATARIOS:?Faltan RESPALDO_AGE_DESTINATARIOS (las claves públicas age)}"
puerto="${RESPALDO_DB_PUERTO:-3306}"
usuario="${RESPALDO_DB_USUARIO:-cc_respaldo}"
base="${RESPALDO_DB_NOMBRE:-cuentasclaras}"
opciones_mysql="${RESPALDO_MYSQL_OPCIONES:-}"
minimo="${RESPALDO_MIN_DESTINATARIOS:-2}"
destino="${RESPALDO_DESTINO:-simulado}"
carpeta_simulada="${RESPALDO_SIMULADO_DIR:-$HOME/cuentas-claras-respaldos-simulados}"
trabajo_base="${RESPALDO_TRABAJO:-${TMPDIR:-/tmp}}"

avisar() {
	echo "cuentas-claras respaldo: $1. $2" >&2
	if [ -n "${RESPALDO_AVISO_CORREO:-}" ] && command -v mail >/dev/null 2>&1; then
		printf '%s\n\nServidor: %s\n' "$2" "$(hostname)" | mail -s "[Cuentas Claras] $1" "$RESPALDO_AVISO_CORREO" || true
	fi
}

fallar() {
	avisar "el respaldo FALLÓ" "$1"
	exit 1
}

# --- Destino y destinatarios, antes de tocar la base -------------------------------------------------------------------
echo "$destino" | grep -Eq '^[A-Za-z0-9_-]{1,60}$' || fallar "RESPALDO_DESTINO debe ser «simulado» o el nombre de un remoto de rclone"
if [ "$destino" != "simulado" ]; then
	variable="RCLONE_CONFIG_$(echo "$destino" | tr '[:lower:]-' '[:upper:]_')_TYPE"
	eval "tipo=\${$variable:-}"
	[ -n "$tipo" ] || fallar "el destino «$destino» no tiene credenciales: define $variable y las demás RCLONE_CONFIG_* en el entorno"
	command -v rclone >/dev/null 2>&1 || fallar "falta rclone para subir al destino «$destino»"
	# Solo variables de entorno: ningún archivo de configuración de rclone (con credenciales) en el servidor.
	export RCLONE_CONFIG=/dev/null
fi
command -v age >/dev/null 2>&1 || fallar "falta age (paquete «age» de la distribución)"
command -v mysqldump >/dev/null 2>&1 || fallar "falta mysqldump (cliente de MySQL)"
destinatarios=""
cantidad=0
for clave in $(echo "$RESPALDO_AGE_DESTINATARIOS" | tr ',' ' '); do
	echo "$clave" | grep -Eq '^age1[0-9a-z]{58}$' || fallar "RESPALDO_AGE_DESTINATARIOS tiene un valor que no es una clave pública age"
	destinatarios="$destinatarios -r $clave"
	cantidad=$((cantidad + 1))
done
[ "$cantidad" -ge "$minimo" ] || fallar "hacen falta $minimo claves públicas age (Promotoría y responsable técnico) y hay $cantidad"

# --- Carpeta de trabajo y credenciales de la base en un archivo de opciones con permisos 600 ------------------------------
trabajo=$(mktemp -d "$trabajo_base/cc-respaldo.XXXXXX")
trap 'rm -rf "$trabajo"' EXIT INT TERM
opciones="$trabajo/mysql.cnf"
printf '[client]\nhost=%s\nport=%s\nuser=%s\npassword=%s\ndefault-character-set=utf8mb4\n' \
	"$RESPALDO_DB_HOST" "$puerto" "$usuario" "$RESPALDO_DB_CLAVE" > "$opciones"

sql() {
	# shellcheck disable=SC2086
	mysql --defaults-extra-file="$opciones" $opciones_mysql -N -B "$base" -e "$1" < /dev/null
}

sha256() {
	if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | cut -d' ' -f1
	elif command -v shasum >/dev/null 2>&1; then shasum -a 256 "$1" | cut -d' ' -f1
	else openssl dgst -sha256 -r "$1" | cut -d' ' -f1; fi
}

ahora_lima() {
	sql "SELECT DATE_FORMAT(UTC_TIMESTAMP(6) - INTERVAL 5 HOUR, '%Y-%m-%d %H:%i:%s.%f')"
}

# --- 1. Ancla «antes», versión del esquema y conteos de las tablas de solo inserción --------------------------------------
inicio=$(ahora_lima) || fallar "no se pudo conectar a la base como $usuario"
# El nombre es por segundo (ck_respaldo_archivo, V24) y único (uk_respaldo_archivo). Dos respaldos seguidos pueden caer en
# el mismo segundo (una base chica se respalda en menos de uno): si el nombre ya está registrado o en el destino simulado,
# se espera al segundo siguiente. La clave única sigue siendo el control final (dos respaldos a la vez: el segundo falla).
intentos=0
while :; do
	sello=$(sql "SELECT DATE_FORMAT(UTC_TIMESTAMP() - INTERVAL 5 HOUR, '%Y%m%d-%H%i%s')")
	archivo="cc-$sello.sql.gz.age"
	ocupado=$(sql "SELECT COUNT(*) FROM respaldo WHERE archivo = '$archivo'")
	if [ "$ocupado" = "0" ] && ! { [ "$destino" = "simulado" ] && [ -e "$carpeta_simulada/$archivo" ]; }; then
		break
	fi
	intentos=$((intentos + 1))
	[ "$intentos" -lt 5 ] || fallar "el nombre $archivo ya existe y no se liberó en 5 segundos (¿otro respaldo corriendo a la vez?)"
	sleep 1
done
manifiesto_nombre="cc-$sello.manifiesto.json"
version=$(sql "SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success = 1")
set -- $(sql "SELECT ultima_secuencia, ultimo_hash FROM auditoria_cadena WHERE id = 1")
secuencia_antes="$1"
hash_antes="$2"

lista=$(grep -Ev '^[[:space:]]*(#|$)' "$tablas_vigiladas" | grep -E '^[a-z][a-z0-9_]*$' | sed "s/.*/'&'/" | paste -sd, -)
existentes=$(sql "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ($lista) ORDER BY TABLE_NAME")
consulta=""
for tabla in $existentes; do
	[ -z "$consulta" ] || consulta="$consulta UNION ALL "
	consulta="${consulta}SELECT '$tabla', COUNT(*), COALESCE(MAX(id), 0) FROM \`$tabla\`"
done
sql "$consulta" > "$trabajo/conteos.tsv"

# Sprint 8, tanda 3: las tablas vigiladas con colegio_id, contadas por colegio; y qué colegios son clientes (COLEGIO) y
# cuál es la plataforma (sus filas afectan a todos).
con_colegio=$(sql "SELECT TABLE_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
	AND COLUMN_NAME = 'colegio_id' AND TABLE_NAME IN ($lista) ORDER BY TABLE_NAME")
consulta=""
for tabla in $con_colegio; do
	[ -z "$consulta" ] || consulta="$consulta UNION ALL "
	consulta="${consulta}SELECT '$tabla', colegio_id, COUNT(*), COALESCE(MAX(id), 0) FROM \`$tabla\` GROUP BY colegio_id"
done
: > "$trabajo/conteos-colegio.tsv"
[ -z "$consulta" ] || sql "$consulta" > "$trabajo/conteos-colegio.tsv"
colegios_clientes=$(sql "SELECT id FROM colegio WHERE tipo = 'COLEGIO' ORDER BY id" | tr '\n' ' ')
plataforma=$(sql "SELECT COALESCE(MAX(id), 0) FROM colegio WHERE tipo = 'PLATAFORMA'")

# S7-M1: los permisos de las cuentas (arreglo JSON de líneas canónicas, sin hosts ni claves); «[]» si 02 todavía no
# crea la función.
permisos=$(sql "SELECT permisos_objetos()" 2>/dev/null | tr -d '\\' || true)
case "$permisos" in
	\[*\]) ;;
	*) permisos="[]" ;;
esac

# QA-S7-1 y sprint 8, tanda 3: los colegios con una falta sin resolver (la de un respaldo que los nombra, o una anterior a
# V30, de toda la base, sin ninguna resolución posterior) y el primer respaldo que sigue abierto (para el mensaje).
abiertos="r.comparacion = 'FALTAN_FILAS' AND ((r.colegios_faltan IS NULL AND NOT EXISTS (SELECT 1 FROM resolucion_respaldo s
	WHERE s.respaldo_id >= r.id)) OR (JSON_CONTAINS(r.colegios_faltan, CAST(k.id AS JSON)) AND NOT EXISTS (SELECT 1
	FROM resolucion_respaldo s WHERE s.respaldo_id >= r.id AND s.colegio_id = k.id)))"
pendientes=$(sql "SELECT k.id FROM colegio k WHERE k.tipo = 'COLEGIO' AND EXISTS (SELECT 1 FROM respaldo r WHERE $abiertos)
	ORDER BY k.id" | tr '\n' ' ') || fallar "no se pudo leer si hay alertas de filas faltantes sin resolver"
pendientes=$(echo "$pendientes" | sed 's/ *$//')
pendiente=""
if [ -n "$pendientes" ]; then
	pendiente=$(sql "SELECT r.archivo FROM respaldo r JOIN colegio k ON k.tipo = 'COLEGIO' WHERE $abiertos
		ORDER BY r.id LIMIT 1")
fi
es_pendiente() {
	case " $pendientes " in *" $1 "*) return 0 ;; esac
	return 1
}

# --- 2. Comparación con el manifiesto anterior (el del destino, que no se puede alterar) --------------------------------
anterior="$trabajo/anterior.json"
if [ "$destino" = "simulado" ]; then
	mkdir -p "$carpeta_simulada"
	ultimo=$(ls "$carpeta_simulada" 2>/dev/null | grep -E '^cc-[0-9]{8}-[0-9]{6}\.manifiesto\.json$' | sort | tail -n 1 || true)
	[ -z "$ultimo" ] || cp "$carpeta_simulada/$ultimo" "$anterior"
else
	ultimo=$(rclone lsf "$destino:" --include 'cc-*.manifiesto.json' 2>/dev/null | sort | tail -n 1 || true)
	[ -z "$ultimo" ] || rclone copyto "$destino:$ultimo" "$anterior" || fallar "no se pudo bajar el manifiesto anterior $ultimo"
fi
comparacion="PRIMERO"
diferencias=""
afectados=""
base_secuencia=""
base_hash=""
: > "$trabajo/base-faltantes.txt"
: > "$trabajo/base-faltantes-colegio.txt"

# Una diferencia más; $2 es el colegio afectado («todos» o un id; las filas de la plataforma afectan a todos).
anotar() {
	[ -z "$diferencias" ] || diferencias="$diferencias, "
	diferencias="$diferencias$1"
	if [ "$2" = "todos" ] || [ "$2" = "$plataforma" ]; then
		afectados="$afectados $colegios_clientes"
	else
		afectados="$afectados $2"
	fi
}

if [ -s "$anterior" ]; then
	comparacion="IGUAL"
	sec_anterior=$(sed -n 's/^ *"secuencia_despues": *\([0-9][0-9]*\),*$/\1/p' "$anterior")
	hash_anterior=$(sed -n 's/^ *"hash_despues": *"\([0-9a-f]*\)",*$/\1/p' "$anterior")
	sed -n 's/^ *"\([a-z][a-z0-9_]*\)": *\[ *\([0-9][0-9]*\), *\([0-9][0-9]*\) *\],*$/\1 \2 \3/p' "$anterior" > "$trabajo/anteriores.txt"
	# Sprint 8, tanda 3: por colegio («tabla@colegio»). Un manifiesto anterior a V30 no trae el bloque: entonces todas las
	# tablas se comparan como antes y una falta en una tabla con colegio afecta a todos.
	sed -n 's/^ *"\([a-z][a-z0-9_]*\)@\([0-9][0-9]*\)": *\[ *\([0-9][0-9]*\), *\([0-9][0-9]*\) *\],*$/\1 \2 \3 \4/p' \
		"$anterior" > "$trabajo/anteriores-colegio.txt"
	# QA-S7-1: con una alerta sin resolver, la referencia es la línea base del manifiesto anterior (si la trae): el ancla y
	# las tablas sin colegio, si queda algún colegio pendiente; por colegio, solo para los colegios pendientes (y la
	# plataforma, que afecta a todos).
	if [ -n "$pendientes" ] && grep -q '"base:' "$anterior"; then
		sec_anterior=$(sed -n 's/^ *"base_secuencia": *\([0-9][0-9]*\),*$/\1/p' "$anterior")
		hash_anterior=$(sed -n 's/^ *"base_hash": *"\([0-9a-f]*\)",*$/\1/p' "$anterior")
		sed -n 's/^ *"base:\([a-z][a-z0-9_]*\)": *\[ *\([0-9][0-9]*\), *\([0-9][0-9]*\) *\],*$/\1 \2 \3/p' "$anterior" > "$trabajo/anteriores.txt"
		sed -n 's/^ *"base:\([a-z][a-z0-9_]*\)@\([0-9][0-9]*\)": *\[ *\([0-9][0-9]*\), *\([0-9][0-9]*\) *\],*$/\1 \2 \3 \4/p' \
			"$anterior" > "$trabajo/base-anterior-colegio.txt"
		: > "$trabajo/mezcla.txt"
		while read -r tabla colegio filas maximo; do
			if es_pendiente "$colegio" || [ "$colegio" = "$plataforma" ]; then
				grep "^$tabla $colegio " "$trabajo/base-anterior-colegio.txt" >> "$trabajo/mezcla.txt" \
					|| echo "$tabla $colegio $filas $maximo" >> "$trabajo/mezcla.txt"
			else
				echo "$tabla $colegio $filas $maximo" >> "$trabajo/mezcla.txt"
			fi
		done < "$trabajo/anteriores-colegio.txt"
		mv "$trabajo/mezcla.txt" "$trabajo/anteriores-colegio.txt"
	fi
	if [ -n "$sec_anterior" ] && [ "$sec_anterior" -gt 0 ]; then
		hash_actual=$(sql "SELECT hash FROM evento_auditoria WHERE secuencia = $sec_anterior")
		if [ "$hash_actual" != "$hash_anterior" ]; then
			anotar "ancla de la bitácora (evento $sec_anterior)" todos
			base_secuencia="$sec_anterior"
			base_hash="$hash_anterior"
		fi
	fi
	por_colegio="no"
	[ ! -s "$trabajo/anteriores-colegio.txt" ] || por_colegio="si"
	while read -r tabla filas maximo; do
		echo "$existentes" | grep -qx "$tabla" || continue
		if [ "$por_colegio" = "si" ] && echo "$con_colegio" | grep -qx "$tabla"; then
			continue
		fi
		quedan=$(sql "SELECT COUNT(*) FROM \`$tabla\` WHERE id <= $maximo")
		if [ "$quedan" -lt "$filas" ]; then
			anotar "$tabla (faltan $((filas - quedan)))" todos
			echo "$tabla $filas $maximo" >> "$trabajo/base-faltantes.txt"
		fi
	done < "$trabajo/anteriores.txt"
	# Por colegio: una consulta por tabla con el id máximo de cada colegio en el respaldo anterior.
	for tabla in $(cut -d' ' -f1 "$trabajo/anteriores-colegio.txt" | sort -u); do
		echo "$con_colegio" | grep -qx "$tabla" || continue
		condicion=$(awk -v t="$tabla" '$1 == t { printf "%s(colegio_id = %s AND id <= %s)", (n++ ? " OR " : ""), $2, $4 }' \
			"$trabajo/anteriores-colegio.txt")
		sql "SELECT colegio_id, COUNT(*) FROM \`$tabla\` WHERE $condicion GROUP BY colegio_id" > "$trabajo/quedan.tsv"
		awk -v t="$tabla" '$1 == t { print $2, $3, $4 }' "$trabajo/anteriores-colegio.txt" > "$trabajo/de-la-tabla.txt"
		while read -r colegio filas maximo; do
			quedan=$(awk -v c="$colegio" '$1 == c { print $2 }' "$trabajo/quedan.tsv")
			quedan="${quedan:-0}"
			if [ "$quedan" -lt "$filas" ]; then
				if [ "$colegio" = "$plataforma" ]; then marca="[plataforma]"; else marca="[colegio $colegio]"; fi
				anotar "$tabla (faltan $((filas - quedan))) $marca" "$colegio"
				echo "$tabla $colegio $filas $maximo" >> "$trabajo/base-faltantes-colegio.txt"
			fi
		done < "$trabajo/de-la-tabla.txt"
	done
fi
if [ -n "$pendientes" ]; then
	# La base todavía tiene alertas sin resolver de esos colegios (trg_respaldo_registro exige FALTAN_FILAS): se repiten.
	for colegio in $pendientes; do
		case " $afectados " in
			*" $colegio "*) ;;
			*) anotar "alerta sin resolver desde el respaldo $pendiente [colegio $colegio]" "$colegio" ;;
		esac
	done
fi
if [ -n "$diferencias" ]; then
	comparacion="FALTAN_FILAS"
	avisar "CRÍTICA: faltan filas que existían en el respaldo anterior" \
		"Comparado con ${ultimo:-el respaldo anterior} faltan: $diferencias. El respaldo de hoy se hace igual (es la evidencia). Preserva todo y sigue docs/operacion/incidente-auditoria.md."
fi
# Los colegios afectados, sin repetir y en orden, como arreglo JSON (solo colegios clientes).
colegios_faltan=""
for colegio in $(echo "$afectados" | tr ' ' '\n' | grep -E '^[0-9]+$' | sort -n -u); do
	case " $colegios_clientes " in *" $colegio "*) colegios_faltan="${colegios_faltan:+$colegios_faltan,}$colegio" ;; esac
done
[ -z "$colegios_faltan" ] || colegios_faltan="[$colegios_faltan]"

# --- 3. Volcado, compresión y cifrado en una sola tubería ----------------------------------------------------------------
cifrado="$trabajo/$archivo"
estado="$trabajo/estado"
: > "$estado"
# shellcheck disable=SC2086
{ mysqldump --defaults-extra-file="$opciones" $opciones_mysql --single-transaction --no-create-info --skip-triggers \
	--no-tablespaces --hex-blob --set-gtid-purged=OFF --complete-insert --default-character-set=utf8mb4 \
	--ignore-table="$base.flyway_schema_history" "$base" 2>"$trabajo/mysqldump.err" || echo "mysqldump" >> "$estado"; } \
	| { gzip -c || echo "gzip" >> "$estado"; } \
	| { age $destinatarios -o "$cifrado" || echo "age" >> "$estado"; }
if [ -s "$estado" ]; then
	fallar "falló el volcado cifrado ($(tr '\n' ' ' < "$estado")): $(head -c 500 "$trabajo/mysqldump.err")"
fi

# --- 4. Ancla «después», huella del archivo cifrado y manifiesto ---------------------------------------------------------
set -- $(sql "SELECT ultima_secuencia, ultimo_hash FROM auditoria_cadena WHERE id = 1")
secuencia_despues="$1"
hash_despues="$2"
huella=$(sha256 "$cifrado")
bytes=$(wc -c < "$cifrado" | tr -d ' ')
fin=$(ahora_lima)

conteos_json=$(awk 'BEGIN { ORS = "" } { if (NR > 1) print ","; printf "\"%s\":[%s,%s]", $1, $2, $3 }' "$trabajo/conteos.tsv")
manifiesto="$trabajo/$manifiesto_nombre"
{
	echo "{"
	echo "  \"formato\": \"cuentas-claras-respaldo/1\","
	echo "  \"archivo\": \"$archivo\","
	echo "  \"sha256\": \"$huella\","
	echo "  \"bytes\": $bytes,"
	echo "  \"cifrado\": \"age\","
	echo "  \"destinatarios\": $cantidad,"
	echo "  \"version_esquema\": \"$version\","
	echo "  \"version_aplicacion\": \"${RESPALDO_VERSION_APLICACION:-}\","
	echo "  \"inicio\": \"$inicio\","
	echo "  \"fin\": \"$fin\","
	echo "  \"destino\": \"$destino\","
	echo "  \"secuencia_antes\": $secuencia_antes,"
	echo "  \"hash_antes\": \"$hash_antes\","
	echo "  \"secuencia_despues\": $secuencia_despues,"
	echo "  \"hash_despues\": \"$hash_despues\","
	echo "  \"comparacion\": \"$comparacion\","
	echo "  \"diferencias\": \"$diferencias\","
	echo "  \"colegios_faltan\": ${colegios_faltan:-null},"
	echo "  \"permisos\": $permisos,"
	# QA-S7-1: la línea base para el respaldo siguiente: lo que mostró la falta se conserva (tabla y ancla); lo demás, lo
	# de hoy. Solo se usa mientras la alerta siga sin resolver.
	echo "  \"base_secuencia\": ${base_secuencia:-$secuencia_despues},"
	echo "  \"base_hash\": \"${base_hash:-$hash_despues}\","
	echo "  \"base\": {"
	awk 'FILENAME == ARGV[1] { falta[$1] = $2 " " $3; next }
		{ split(($1 in falta) ? falta[$1] : $2 " " $3, v, " ");
		  printf "%s    \"base:%s\": [%s, %s]", (FNR > 1 ? ",\n" : ""), $1, v[1], v[2] } END { print "" }' \
		"$trabajo/base-faltantes.txt" "$trabajo/conteos.tsv"
	echo "  },"
	# Sprint 8, tanda 3: la línea base y los conteos por colegio («tabla@colegio»).
	echo "  \"base_colegios\": {"
	awk 'FILENAME == ARGV[1] { falta[$1 "@" $2] = $3 " " $4; next }
		{ k = $1 "@" $2; split((k in falta) ? falta[k] : $3 " " $4, v, " ");
		  printf "%s    \"base:%s\": [%s, %s]", (FNR > 1 ? ",\n" : ""), k, v[1], v[2] } END { print "" }' \
		"$trabajo/base-faltantes-colegio.txt" "$trabajo/conteos-colegio.tsv"
	echo "  },"
	echo "  \"colegios\": {"
	awk '{ printf "%s    \"%s@%s\": [%s, %s]", (NR > 1 ? ",\n" : ""), $1, $2, $3, $4 } END { print "" }' "$trabajo/conteos-colegio.tsv"
	echo "  },"
	echo "  \"conteos\": {"
	awk '{ printf "%s    \"%s\": [%s, %s]", (NR > 1 ? ",\n" : ""), $1, $2, $3 } END { print "" }' "$trabajo/conteos.tsv"
	echo "  }"
	echo "}"
} > "$manifiesto"

# --- 5. Subida al destino y comprobación ---------------------------------------------------------------------------------
if [ "$destino" = "simulado" ]; then
	cp "$cifrado" "$manifiesto" "$carpeta_simulada/"
	[ "$(sha256 "$carpeta_simulada/$archivo")" = "$huella" ] || fallar "la copia en $carpeta_simulada no coincide"
else
	mkdir "$trabajo/subir"
	cp "$cifrado" "$manifiesto" "$trabajo/subir/"
	rclone copy "$trabajo/subir" "$destino:" || fallar "no se pudo subir el respaldo a «$destino»"
	rclone check "$trabajo/subir" "$destino:" --one-way || fallar "el respaldo subido a «$destino» no coincide"
fi

# --- 6. Registro en la base (solo cc_respaldo; el trigger exige que las anclas sean eventos reales y que se registre al
# terminar: «fin» es la hora de la base al registrar, después de subir) ------------------------------------------------
fin_registro=$(ahora_lima)
if [ -n "$diferencias" ]; then
	valor_diferencias="'$(echo "$diferencias" | head -c 1000 | sed "s/'//g")'"
else
	valor_diferencias="NULL"
fi
valor_colegios="NULL"
[ -z "$colegios_faltan" ] || valor_colegios="'$colegios_faltan'"
sql "INSERT INTO respaldo (inicio, fin, archivo, sha256, bytes, version_esquema, secuencia_antes, hash_antes,
	secuencia_despues, hash_despues, conteos, destino, comparacion, diferencias, colegios_faltan, creado_en, creado_por)
	VALUES ('$inicio', '$fin_registro', '$archivo', '$huella', $bytes, '$version', $secuencia_antes, '$hash_antes',
	$secuencia_despues, '$hash_despues', '{$conteos_json}', '$destino', '$comparacion', $valor_diferencias,
	$valor_colegios, UTC_TIMESTAMP(6) - INTERVAL 5 HOUR, 'cc_respaldo')" 2> "$trabajo/registro.err" \
	|| fallar "el respaldo $archivo se subió pero no se pudo registrar: $(cat "$trabajo/registro.err")"

echo "Respaldo $archivo ($bytes bytes, sha256 $huella) en «$destino»; bitácora $secuencia_antes→$secuencia_despues; comparación: $comparacion${diferencias:+ ($diferencias)}${colegios_faltan:+; colegios: $colegios_faltan}"
if [ "$comparacion" = "FALTAN_FILAS" ]; then
	exit 3
fi
