#!/bin/sh
# Cuentas Claras · casos del job «respaldo» del CI (sprint 7, tanda 1). Cada paso del job llama a este script con un
# caso; el mismo script se corre en local contra contenedores desechables. Usa las variables del job (.github/workflows/
# ci.yml): MYSQL_ADMIN, MYSQL_RESTAURACION, RESPALDO_*, RESTAURAR_*, RCLONE_CONFIG_* y CLAVE_HMAC_PRUEBAS.
# Solo para bases DESECHABLES: borra filas como administrador para simular a un DBA deshonesto.
set -eu

aqui=$(cd "$(dirname "$0")" && pwd)
respaldar="$aqui/respaldar.sh"
restaurar="$aqui/restaurar-y-verificar.sh"
destino_ci="${CI_RESPALDOS_DIR:-/tmp/cc-respaldos-ci}"
forjados_ci="${CI_FORJADOS_DIR:-/tmp/cc-respaldos-forjados}"
simulados_ci="${CI_SIMULADOS_DIR:-/tmp/cc-respaldos-simulados}"

admin() {
	# shellcheck disable=SC2086
	$MYSQL_ADMIN -N cuentasclaras "$@"
}

ultimo_manifiesto() {
	ls "$destino_ci"/*.manifiesto.json | sort | tail -n 1
}

case "${1:-}" in
sin-credenciales)
	if RESPALDO_DESTINO=otroremoto sh "$respaldar" 2> /tmp/cc-sin-credenciales.err; then
		echo "ERROR: respaldó sin las credenciales del destino"; exit 1
	fi
	grep -q "RCLONE_CONFIG_OTROREMOTO_TYPE" /tmp/cc-sin-credenciales.err
	una=$(echo "$RESPALDO_AGE_DESTINATARIOS" | cut -d' ' -f1)
	if RESPALDO_AGE_DESTINATARIOS="$una" sh "$respaldar" 2> /tmp/cc-un-destinatario.err; then
		echo "ERROR: respaldó con una sola clave pública"; exit 1
	fi
	grep -q "hacen falta 2 claves" /tmp/cc-un-destinatario.err
	echo "OK: sin las credenciales del destino en el entorno, o sin las dos claves públicas, no se respalda"
	;;

primer-respaldo)
	sh "$respaldar" | tee /tmp/cc-respaldo-1.out
	grep -q "comparación: PRIMERO" /tmp/cc-respaldo-1.out
	test "$(ls "$destino_ci" | grep -c '\.sql\.gz\.age$')" = "1"
	test "$(admin -e "SELECT COUNT(*) FROM respaldo WHERE destino = 'cirespaldos' AND comparacion = 'PRIMERO'")" = "1"
	grep -q '"evento_auditoria": \[' "$(ultimo_manifiesto)"
	echo "OK: respaldo cifrado en el destino, con su manifiesto y registrado por cc_respaldo"
	;;

e29)
	set -- $(admin -e "SELECT numero_documento, apellido_paterno FROM alumno WHERE numero_documento REGEXP '^[0-9]{8}$'
		AND apellido_paterno REGEXP '^[A-Za-z]{4,}$' ORDER BY id LIMIT 1")
	dni="$1"
	apellido="$2"
	archivo=$(ls "$destino_ci"/*.sql.gz.age | head -n 1)
	if grep -a -q -e "$dni" -e "$apellido" "$archivo"; then
		echo "ERROR: el respaldo cifrado deja ver datos personales"; exit 1
	fi
	# Control: descifrado con la clave privada, el DNI sí está (la prueba no es vacía).
	age -d -i "$RESTAURAR_AGE_IDENTIDAD" "$archivo" | gunzip -c | grep -q "$dni"
	echo "OK (E29): el .age no contiene el DNI ni el apellido de un alumno; descifrado con la clave privada, sí"
	;;

restauracion)
	RESTAURAR_ORIGEN=cirespaldos RESTAURAR_INFORME_DIR=/tmp/cc-informes AUDITORIA_CLAVE_HMAC="$CLAVE_HMAC_PRUEBAS" \
		sh "$restaurar" | tee /tmp/cc-restauracion.out
	grep -q "\[OK\] verificar_respaldo" /tmp/cc-restauracion.out
	grep -q "\[OK\] arranque_prod" /tmp/cc-restauracion.out
	grep -q '"resultado": "OK"' /tmp/cc-informes/restauracion-*.json
	grep -q '"nombre" : "hmac"' /tmp/cc-informes/restauracion-*.json
	echo "OK: restaurado en un MySQL vacío, cadena HMAC y libros verificados, prod arrancó sobre la copia"
	;;

e31)
	# La base de la restauración anterior se descarta: la restauración solo usa un MySQL vacío.
	# shellcheck disable=SC2086
	$MYSQL_RESTAURACION -e "DROP DATABASE cuentasclaras; DROP USER cc_migrador, cc_app, cc_sistema, cc_respaldo; DROP ROLE cc_negocio"
	mkdir -p "$forjados_ci"
	original=$(ls "$destino_ci"/*.sql.gz.age | sort | head -n 1)
	nombre=$(basename "$original" .sql.gz.age)
	detalle=$(admin -e "SELECT detalle FROM evento_auditoria WHERE detalle REGEXP '^[A-Za-z0-9 ]{12,}$'
		ORDER BY secuencia LIMIT 1")
	test -n "$detalle"
	# Quien toma el servidor tiene las claves públicas: puede fabricar un respaldo y su manifiesto. Aquí se parte del
	# volcado real (con la clave privada del CI) y se cambia el detalle de un evento.
	destinatarios=""
	for clave in $RESPALDO_AGE_DESTINATARIOS; do destinatarios="$destinatarios -r $clave"; done
	forjado="$forjados_ci/$nombre.sql.gz.age"
	# shellcheck disable=SC2086
	age -d -i "$RESTAURAR_AGE_IDENTIDAD" "$original" | gunzip -c \
		| sed "/^INSERT INTO \`evento_auditoria\`/s/'$detalle'/'$detalle (cambiado)'/" | gzip -c \
		| age $destinatarios -o "$forjado"
	sed -e "s/\"sha256\": \"[0-9a-f]*\"/\"sha256\": \"$(sha256sum "$forjado" | cut -d' ' -f1)\"/" \
		-e "s/\"bytes\": [0-9]*/\"bytes\": $(wc -c < "$forjado" | tr -d ' ')/" \
		"$destino_ci/$nombre.manifiesto.json" > "$forjados_ci/$nombre.manifiesto.json"
	if RESTAURAR_ORIGEN=ciforjado RESTAURAR_INFORME_DIR=/tmp/cc-informes-forjado \
			AUDITORIA_CLAVE_HMAC="$CLAVE_HMAC_PRUEBAS" sh "$restaurar" > /tmp/cc-forjado.out 2>&1; then
		cat /tmp/cc-forjado.out
		echo "ERROR: el respaldo alterado pasó la verificación"; exit 1
	fi
	grep -q "La cadena no verifica en la secuencia" /tmp/cc-forjado.out || { cat /tmp/cc-forjado.out; exit 1; }
	echo "OK (E31): $(grep -o 'La cadena no verifica en la secuencia [0-9]*' /tmp/cc-forjado.out | head -n 1)"
	;;

e30)
	admin -e "SET FOREIGN_KEY_CHECKS = 0; DELETE FROM pago ORDER BY id LIMIT 1"
	set +e
	sh "$respaldar" > /tmp/cc-respaldo-2.out 2> /tmp/cc-respaldo-2.err
	codigo=$?
	set -e
	cat /tmp/cc-respaldo-2.out /tmp/cc-respaldo-2.err
	test "$codigo" = "3"
	grep -q "faltan filas" /tmp/cc-respaldo-2.err
	grep -q "pago (faltan 1)" /tmp/cc-respaldo-2.out
	test "$(admin -e "SELECT COUNT(*) FROM respaldo WHERE comparacion = 'FALTAN_FILAS' AND diferencias LIKE '%pago%'")" = "1"
	echo "OK (E30): el respaldo siguiente detectó el pago borrado (código 3, FALTAN_FILAS registrado)"
	;;

e32)
	ancla=$(sed -n 's/^ *"secuencia_despues": *\([0-9]*\),$/\1/p' "$(ultimo_manifiesto)")
	nueva=$((ancla - 3))
	admin -e "DELETE FROM evento_auditoria WHERE secuencia > $nueva;
		UPDATE auditoria_cadena SET ultima_secuencia = $nueva,
			ultimo_hash = (SELECT h FROM (SELECT hash AS h FROM evento_auditoria WHERE secuencia = $nueva) x) WHERE id = 1"
	set +e
	sh "$respaldar" > /tmp/cc-respaldo-3.out 2> /tmp/cc-respaldo-3.err
	codigo=$?
	set -e
	cat /tmp/cc-respaldo-3.out /tmp/cc-respaldo-3.err
	test "$codigo" = "3"
	grep -q "ancla de la bitácora (evento $ancla)" /tmp/cc-respaldo-3.out
	grep -q "evento_auditoria (faltan" /tmp/cc-respaldo-3.out
	echo "OK (E32): el recorte del final de la bitácora (con el eslabón retrocedido) se detectó por el ancla"
	;;

restauracion-sin-clave)
	# El simulacro semanal (sin la clave HMAC) sobre el último respaldo, el de después de E30 y E32: las anclas de los
	# respaldos anteriores y los libros delatan el recorte y el pago borrado.
	# shellcheck disable=SC2086
	$MYSQL_RESTAURACION -e "DROP DATABASE IF EXISTS cuentasclaras; DROP USER IF EXISTS cc_migrador, cc_app, cc_sistema, cc_respaldo; DROP ROLE IF EXISTS cc_negocio"
	if RESTAURAR_ORIGEN=cirespaldos RESTAURAR_INFORME_DIR=/tmp/cc-informes-semanal RESTAURAR_ARRANCAR=no 			sh "$restaurar" > /tmp/cc-semanal.out 2>&1; then
		cat /tmp/cc-semanal.out
		echo "ERROR: el simulacro sin clave no detectó el recorte"; exit 1
	fi
	grep -q "El ancla del respaldo anterior" /tmp/cc-semanal.out || { cat /tmp/cc-semanal.out; exit 1; }
	grep -q "aplicaciones_con_pago_y_cuota" /tmp/cc-semanal.out || { cat /tmp/cc-semanal.out; exit 1; }
	echo "OK: sin la clave HMAC, la restauración del último respaldo detecta el recorte (anclas) y el pago borrado (libros)"
	;;

simulado)
	export RESPALDO_DESTINO=simulado RESPALDO_SIMULADO_DIR="$simulados_ci"
	if sh "$respaldar" > /tmp/cc-simulado-1.out 2>&1; then
		echo "ERROR: se registró un respaldo simulado sin la fila del DBA"; exit 1
	fi
	grep -q "no admite el destino simulado" /tmp/cc-simulado-1.out || { cat /tmp/cc-simulado-1.out; exit 1; }
	admin -e "INSERT INTO configuracion_bd (clave, valor, creado_en) VALUES ('respaldo_simulado', 'PERMITIDA', NOW(6))"
	rm -rf "$simulados_ci"
	set +e
	sh "$respaldar" | tee /tmp/cc-simulado-2.out
	set -e
	grep -q "en «simulado»" /tmp/cc-simulado-2.out
	test "$(ls "$simulados_ci" | grep -c '\.sql\.gz\.age$')" = "1"
	admin -e "DELETE FROM configuracion_bd WHERE clave = 'respaldo_simulado'"
	echo "OK: el destino simulado solo con la fila del DBA (en prod el verificador no arranca con ella)"
	;;

*)
	echo "Uso: $0 sin-credenciales|primer-respaldo|e29|restauracion|e31|e30|e32|restauracion-sin-clave|simulado" >&2
	exit 2
	;;
esac
