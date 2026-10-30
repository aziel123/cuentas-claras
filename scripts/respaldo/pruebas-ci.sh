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
	# Sprint 8, tanda 3 (V30): el registro dice de qué colegio era el pago.
	colegio=$(admin -e "SELECT colegios_faltan FROM respaldo ORDER BY id DESC LIMIT 1")
	echo "$colegio" | grep -Eq '^\[[0-9]+\]$' || { echo "ERROR: colegios_faltan = $colegio"; exit 1; }
	echo "OK (E30): el respaldo siguiente detectó el pago borrado (código 3, FALTAN_FILAS registrado; colegios $colegio)"
	;;

e32)
	ancla=$(sed -n 's/^ *"secuencia_despues": *\([0-9]*\),$/\1/p' "$(ultimo_manifiesto)")
	nueva=$((ancla - 3))
	# Sprint 11, tanda 2: la bitácora ya no se borra ni con el DBA (trg_evento_auditoria_sin_borrado). El DBA deshonesto
	# quita primero el trigger (el arranque lo delataría también); el respaldo siguiente igual lo detecta por el ancla.
	admin -e "DROP TRIGGER IF EXISTS trg_evento_auditoria_sin_borrado; DELETE FROM evento_auditoria WHERE secuencia > $nueva;
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
	# Sprint 8, tanda 3 (V30): el ancla de la bitácora es de toda la instalación: nombra a todos los colegios.
	todos=$(admin -e "SELECT CONCAT('[', GROUP_CONCAT(id ORDER BY id SEPARATOR ','), ']') FROM colegio WHERE tipo = 'COLEGIO'")
	test "$(admin -e "SELECT colegios_faltan FROM respaldo ORDER BY id DESC LIMIT 1")" = "$todos" 		|| { echo "ERROR: el ancla no nombró a todos los colegios ($todos)"; exit 1; }
	echo "OK (E32): el recorte del final de la bitácora (con el eslabón retrocedido) se detectó por el ancla (colegios $todos)"
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

faltan-filas-sigue)
	# Correcciones del sprint 7 (QA-S7-1): un respaldo más, sin borrar nada nuevo. Antes decía IGUAL (comparaba con el
	# manifiesto anterior, que ya no tenía el pago) y borraba la alerta. Ahora compara con la línea base y la base exige
	# FALTAN_FILAS mientras nadie la resuelva.
	set +e
	sh "$respaldar" > /tmp/cc-respaldo-4.out 2> /tmp/cc-respaldo-4.err
	codigo=$?
	set -e
	cat /tmp/cc-respaldo-4.out /tmp/cc-respaldo-4.err
	test "$codigo" = "3"
	grep -q "comparación: FALTAN_FILAS" /tmp/cc-respaldo-4.out
	grep -q "pago (faltan 1)" /tmp/cc-respaldo-4.out
	test "$(admin -e "SELECT comparacion FROM respaldo ORDER BY id DESC LIMIT 1")" = "FALTAN_FILAS"
	# Y la base no acepta que cc_respaldo registre IGUAL mientras siga sin resolver (1644).
	ultimo=$(admin -e "SELECT CONCAT_WS(' ', secuencia_despues, hash_despues) FROM respaldo ORDER BY id DESC LIMIT 1")
	set -- $ultimo
	clave_respaldo="${RESPALDO_DB_CLAVE:?}"
	if mysql --protocol=TCP -h "${RESPALDO_DB_HOST:-127.0.0.1}" -P "${RESPALDO_DB_PUERTO:-3306}" -u cc_respaldo \
			-p"$clave_respaldo" cuentasclaras -e "INSERT INTO respaldo (inicio, fin, archivo, sha256, bytes, version_esquema,
			secuencia_antes, hash_antes, secuencia_despues, hash_despues, conteos, destino, comparacion, diferencias,
			creado_en, creado_por) VALUES (UTC_TIMESTAMP(6) - INTERVAL 5 HOUR, UTC_TIMESTAMP(6) - INTERVAL 5 HOUR,
			'cc-20000101-000000.sql.gz.age', REPEAT('a', 64), 1, '28', $1, '$2', $1, '$2', '{}', 'cirespaldos', 'IGUAL',
			NULL, UTC_TIMESTAMP(6) - INTERVAL 5 HOUR, 'cc_respaldo')" 2> /tmp/cc-respaldo-igual.err; then
		echo "ERROR: cc_respaldo registró IGUAL con la alerta sin resolver"; exit 1
	fi
	grep -q "1644.*filas faltantes sin resolver" /tmp/cc-respaldo-igual.err || { cat /tmp/cc-respaldo-igual.err; exit 1; }
	echo "OK (QA-S7-1): un segundo respaldo sigue diciendo FALTAN_FILAS (pago (faltan 1)) y la base rechaza IGUAL (1644)"
	;;

permisos-anidados)
	# Correcciones del sprint 7 (S7-M1): un DBA anida un rol con DELETE en cc_negocio. El respaldo lo guarda en su
	# manifiesto (permisos_objetos()) y el simulacro semanal lo compara con lo que da 02: FALLA con la línea de más.
	admin -e "CREATE ROLE IF NOT EXISTS 'cc_rol_extra'; GRANT DELETE ON cuentasclaras.pago TO 'cc_rol_extra';
		GRANT 'cc_rol_extra' TO 'cc_negocio'"
	set +e
	sh "$respaldar" > /tmp/cc-respaldo-5.out 2>&1
	set -e
	grep -q "ROL cc_rol_extra A cc_negocio" "$(ultimo_manifiesto)" || { cat /tmp/cc-respaldo-5.out; exit 1; }
	# shellcheck disable=SC2086
	$MYSQL_RESTAURACION -e "DROP DATABASE IF EXISTS cuentasclaras; DROP USER IF EXISTS cc_migrador, cc_app, cc_sistema, cc_respaldo; DROP ROLE IF EXISTS cc_negocio"
	RESTAURAR_ORIGEN=cirespaldos RESTAURAR_INFORME_DIR=/tmp/cc-informes-permisos RESTAURAR_ARRANCAR=no \
		sh "$restaurar" > /tmp/cc-permisos.out 2>&1 || true
	grep -q "\[FALLA\] permisos: .*ROL cc_rol_extra A cc_negocio" /tmp/cc-permisos.out || { cat /tmp/cc-permisos.out; exit 1; }
	# 02 recrea el rol: el anidado desaparece.
	$MYSQL_ADMIN < scripts/mysql/02-permisos-tablas.sql
	test "$(admin -e "SELECT COUNT(*) FROM mysql.role_edges WHERE TO_USER = 'cc_negocio'")" = "0"
	admin -e "DROP ROLE 'cc_rol_extra'"
	echo "OK (S7-M1): el simulacro semanal muestra el rol anidado en cc_negocio y 02 lo quita"
	;;

resuelto)
	# Correcciones del sprint 7 (QA-S7-1): Promotoría ya resolvió la alerta con su firma (ResolucionRespaldoMySqlTest): el
	# respaldo siguiente vuelve a comparar con el último manifiesto y dice IGUAL. Sprint 8, tanda 3: la resolvió la
	# Promotoría de CADA colegio afectado (el recorte de la bitácora los afecta a todos).
	test "$(admin -e "SELECT COUNT(*) FROM resolucion_respaldo")" -ge 1
	sh "$respaldar" | tee /tmp/cc-respaldo-6.out
	grep -q "comparación: IGUAL" /tmp/cc-respaldo-6.out
	echo "OK (QA-S7-1): resuelta por Promotoría, el respaldo siguiente vuelve a IGUAL"
	;;

faltan-filas-por-colegio)
	# Sprint 8, tanda 3 (V30, E25 y E28, decisión 131): una instalación compartida con varios colegios. Un DBA borra un
	# pago de UN colegio (A): el respaldo siguiente avisa «faltan filas» SOLO de A (colegios_faltan = [A], la diferencia
	# con su colegio) y el manifiesto cuenta por colegio. Después (ResolucionRespaldoPorColegioMySqlTest) la Promotoría de
	# otro colegio no lo puede resolver (1644) y la de A sí.
	clientes=$(admin -e "SELECT COUNT(*) FROM colegio WHERE tipo = 'COLEGIO'")
	[ "$clientes" -ge 2 ] || { echo "ERROR: hacen falta 2 colegios en la base y hay $clientes"; exit 1; }
	a=$(admin -e "SELECT p.colegio_id FROM pago p JOIN colegio c ON c.id = p.colegio_id WHERE c.tipo = 'COLEGIO'
		GROUP BY p.colegio_id ORDER BY p.colegio_id DESC LIMIT 1")
	test -n "$a"
	grep -q "\"pago@$a\": \[" "$(ultimo_manifiesto)" || { echo "ERROR: el manifiesto no cuenta los pagos del colegio $a"; exit 1; }
	admin -e "SET FOREIGN_KEY_CHECKS = 0; DELETE FROM pago WHERE colegio_id = $a ORDER BY id DESC LIMIT 1"
	set +e
	sh "$respaldar" > /tmp/cc-respaldo-7.out 2> /tmp/cc-respaldo-7.err
	codigo=$?
	set -e
	cat /tmp/cc-respaldo-7.out /tmp/cc-respaldo-7.err
	test "$codigo" = "3"
	grep -q "pago (faltan 1) \[colegio $a\]" /tmp/cc-respaldo-7.out
	test "$(admin -e "SELECT colegios_faltan FROM respaldo ORDER BY id DESC LIMIT 1")" = "[$a]" 		|| { echo "ERROR: colegios_faltan no es [$a]"; exit 1; }
	# Y la base no acepta un FALTAN_FILAS sin decir de qué colegios (ni uno que nombre a un colegio que no existe).
	ultimo=$(admin -e "SELECT CONCAT_WS(' ', secuencia_despues, hash_despues) FROM respaldo ORDER BY id DESC LIMIT 1")
	set -- $ultimo
	for valor in "NULL" "'[999999]'"; do
		if mysql --protocol=TCP -h "${RESPALDO_DB_HOST:-127.0.0.1}" -P "${RESPALDO_DB_PUERTO:-3306}" -u cc_respaldo 				-p"${RESPALDO_DB_CLAVE:?}" cuentasclaras -e "INSERT INTO respaldo (inicio, fin, archivo, sha256, bytes,
				version_esquema, secuencia_antes, hash_antes, secuencia_despues, hash_despues, conteos, destino, comparacion,
				diferencias, colegios_faltan, creado_en, creado_por) VALUES (UTC_TIMESTAMP(6) - INTERVAL 5 HOUR,
				UTC_TIMESTAMP(6) - INTERVAL 5 HOUR, 'cc-20000101-000001.sql.gz.age', REPEAT('a', 64), 1, '30', $1, '$2', $1,
				'$2', '{}', 'cirespaldos', 'FALTAN_FILAS', 'pago (faltan 1)', $valor, UTC_TIMESTAMP(6) - INTERVAL 5 HOUR,
				'cc_respaldo')" 2> /tmp/cc-respaldo-sin-colegios.err; then
			echo "ERROR: cc_respaldo registró FALTAN_FILAS con colegios_faltan = $valor"; exit 1
		fi
		grep -q "1644.*de que colegios" /tmp/cc-respaldo-sin-colegios.err || { cat /tmp/cc-respaldo-sin-colegios.err; exit 1; }
	done
	echo "OK (E25 y E28): el pago borrado del colegio $a se avisa solo a ese colegio (colegios_faltan = [$a]); sin decir de qué colegios, 1644"
	;;

resuelto-por-colegio)
	# Sprint 8, tanda 3: la Promotoría del colegio afectado ya resolvió (ResolucionRespaldoPorColegioMySqlTest): el respaldo
	# siguiente vuelve a IGUAL, y la otra Promotoría no dejó ninguna resolución.
	sh "$respaldar" | tee /tmp/cc-respaldo-8.out
	grep -q "comparación: IGUAL" /tmp/cc-respaldo-8.out
	echo "OK (E25): resuelta por la Promotoría del colegio afectado, el respaldo siguiente vuelve a IGUAL"
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

faltan-filas-asistencia)
	# Sprint 9, tanda 2 (H13): la asistencia es de solo inserción y está en tablas-vigiladas.txt. Un DBA borra una marca de
	# UN colegio: el respaldo siguiente avisa «faltan filas» SOLO de ese colegio. Si la fase 2 corrió en fin de semana o
	# feriado no hay marcas (el día lo decide la base): se omite con un aviso.
	a=$(admin -e "SELECT m.colegio_id FROM marca_asistencia m JOIN colegio c ON c.id = m.colegio_id WHERE c.tipo = 'COLEGIO'
		GROUP BY m.colegio_id ORDER BY m.colegio_id DESC LIMIT 1")
	if [ -z "$a" ]; then
		echo "OMITIDO: no hay marcas de asistencia (la fase 2 corrió en un día sin clases)"
		exit 0
	fi
	sh "$respaldar" > /tmp/cc-respaldo-9a.out 2>&1 || { cat /tmp/cc-respaldo-9a.out; exit 1; }
	grep -q "\"marca_asistencia@$a\": \[" "$(ultimo_manifiesto)" || { echo "ERROR: el manifiesto no cuenta las marcas del colegio $a"; exit 1; }
	admin -e "SET FOREIGN_KEY_CHECKS = 0; DELETE FROM marca_asistencia WHERE colegio_id = $a ORDER BY id DESC LIMIT 1"
	set +e
	sh "$respaldar" > /tmp/cc-respaldo-9b.out 2> /tmp/cc-respaldo-9b.err
	codigo=$?
	set -e
	cat /tmp/cc-respaldo-9b.out /tmp/cc-respaldo-9b.err
	test "$codigo" = "3"
	grep -q "marca_asistencia (faltan 1) \[colegio $a\]" /tmp/cc-respaldo-9b.out
	test "$(admin -e "SELECT colegios_faltan FROM respaldo ORDER BY id DESC LIMIT 1")" = "[$a]" \
		|| { echo "ERROR: colegios_faltan no es [$a]"; exit 1; }
	echo "OK (H13): la marca de asistencia borrada del colegio $a se avisa solo a ese colegio"
	;;

faltan-filas-areas)
	# Sprint 10, tanda 1: las áreas de cada asignación son de solo inserción y están en tablas-vigiladas.txt. Un DBA borra
	# una de UN colegio (las deja la fase 2: PermisosMySqlTest.flujoAsignacionDocenteConPermisosMinimos): el respaldo
	# siguiente avisa «faltan filas» de las áreas SOLO de ese colegio. El paso anterior (la marca de asistencia borrada)
	# puede dejar otro colegio sin resolver: por eso el primer respaldo puede salir con 3 y colegios_faltan puede tener más
	# de un colegio; lo que se exige es que la falta de las áreas sea una sola y del colegio afectado.
	a=$(admin -e "SELECT x.colegio_id FROM asignacion_area x JOIN colegio c ON c.id = x.colegio_id WHERE c.tipo = 'COLEGIO'
		GROUP BY x.colegio_id ORDER BY x.colegio_id DESC LIMIT 1")
	test -n "$a" || { echo "ERROR: la fase 2 no dejó áreas de asignaciones"; exit 1; }
	set +e
	sh "$respaldar" > /tmp/cc-respaldo-10a.out 2>&1
	codigo=$?
	set -e
	if [ "$codigo" != "0" ] && [ "$codigo" != "3" ]; then cat /tmp/cc-respaldo-10a.out; exit 1; fi
	if grep -q "asignacion_area (faltan" /tmp/cc-respaldo-10a.out; then
		cat /tmp/cc-respaldo-10a.out; echo "ERROR: faltaban áreas antes de borrar"; exit 1
	fi
	grep -q "\"asignacion_area@$a\": \[" "$(ultimo_manifiesto)" || { echo "ERROR: el manifiesto no cuenta las áreas del colegio $a"; exit 1; }
	admin -e "SET FOREIGN_KEY_CHECKS = 0; DELETE FROM asignacion_area WHERE colegio_id = $a ORDER BY id DESC LIMIT 1"
	set +e
	sh "$respaldar" > /tmp/cc-respaldo-10b.out 2> /tmp/cc-respaldo-10b.err
	codigo=$?
	set -e
	cat /tmp/cc-respaldo-10b.out /tmp/cc-respaldo-10b.err
	test "$codigo" = "3"
	grep -q "asignacion_area (faltan 1) \[colegio $a\]" /tmp/cc-respaldo-10b.out
	test "$(grep -o "asignacion_area (faltan [0-9]*) \[colegio [0-9]*\]" /tmp/cc-respaldo-10b.out | sort -u | wc -l)" = "1" \
		|| { echo "ERROR: la falta de las áreas no es solo del colegio $a"; exit 1; }
	admin -e "SELECT colegios_faltan FROM respaldo ORDER BY id DESC LIMIT 1" | grep -Eq "(\[|, ?)$a(\]|,)" \
		|| { echo "ERROR: colegios_faltan no incluye al colegio $a"; exit 1; }
	echo "OK (sprint 10): el área de asignación borrada del colegio $a se avisa solo a ese colegio"
	;;

faltan-filas-notas)
	# Sprint 10, tanda 2: las notas son de solo inserción y están en tablas-vigiladas.txt. Un DBA borra la última de UN
	# colegio (las deja AuditoriaSprint10NotasMySqlTest.flujoNotasConPermisosMinimos): el respaldo siguiente avisa «faltan
	# filas» de calificacion SOLO de ese colegio. Los pasos anteriores pueden dejar otros colegios sin resolver (código 3).
	a=$(admin -e "SELECT x.colegio_id FROM calificacion x JOIN colegio c ON c.id = x.colegio_id WHERE c.tipo = 'COLEGIO'
		GROUP BY x.colegio_id ORDER BY x.colegio_id DESC LIMIT 1")
	test -n "$a" || { echo "ERROR: el flujo de las notas no dejó notas"; exit 1; }
	set +e
	sh "$respaldar" > /tmp/cc-respaldo-11a.out 2>&1
	codigo=$?
	set -e
	if [ "$codigo" != "0" ] && [ "$codigo" != "3" ]; then cat /tmp/cc-respaldo-11a.out; exit 1; fi
	if grep -q "calificacion (faltan" /tmp/cc-respaldo-11a.out; then
		cat /tmp/cc-respaldo-11a.out; echo "ERROR: faltaban notas antes de borrar"; exit 1
	fi
	grep -q "\"calificacion@$a\": \[" "$(ultimo_manifiesto)" || { echo "ERROR: el manifiesto no cuenta las notas del colegio $a"; exit 1; }
	admin -e "SET FOREIGN_KEY_CHECKS = 0; DELETE FROM calificacion WHERE colegio_id = $a ORDER BY id DESC LIMIT 1"
	set +e
	sh "$respaldar" > /tmp/cc-respaldo-11b.out 2> /tmp/cc-respaldo-11b.err
	codigo=$?
	set -e
	cat /tmp/cc-respaldo-11b.out /tmp/cc-respaldo-11b.err
	test "$codigo" = "3"
	grep -q "calificacion (faltan 1) \[colegio $a\]" /tmp/cc-respaldo-11b.out
	test "$(grep -o "calificacion (faltan [0-9]*) \[colegio [0-9]*\]" /tmp/cc-respaldo-11b.out | sort -u | wc -l)" = "1" \
		|| { echo "ERROR: la falta de las notas no es solo del colegio $a"; exit 1; }
	admin -e "SELECT colegios_faltan FROM respaldo ORDER BY id DESC LIMIT 1" | grep -Eq "(\[|, ?)$a(\]|,)" \
		|| { echo "ERROR: colegios_faltan no incluye al colegio $a"; exit 1; }
	echo "OK (sprint 10): la nota borrada del colegio $a se avisa solo a ese colegio"
	;;

faltan-filas-libretas)
	# Sprint 10, tanda 3: las libretas emitidas son de solo inserción y están en tablas-vigiladas.txt. Un DBA borra la última
	# de UN colegio (las deja AuditoriaSprint10LibretasMySqlTest.flujoLibretasConPermisosMinimos): el respaldo siguiente
	# avisa «faltan filas» de libreta_emitida SOLO de ese colegio. Los pasos anteriores pueden dejar otros colegios sin
	# resolver (código 3).
	a=$(admin -e "SELECT x.colegio_id FROM libreta_emitida x JOIN colegio c ON c.id = x.colegio_id WHERE c.tipo = 'COLEGIO'
		GROUP BY x.colegio_id ORDER BY x.colegio_id DESC LIMIT 1")
	test -n "$a" || { echo "ERROR: el flujo de las libretas no dejó libretas"; exit 1; }
	set +e
	sh "$respaldar" > /tmp/cc-respaldo-12a.out 2>&1
	codigo=$?
	set -e
	if [ "$codigo" != "0" ] && [ "$codigo" != "3" ]; then cat /tmp/cc-respaldo-12a.out; exit 1; fi
	if grep -q "libreta_emitida (faltan" /tmp/cc-respaldo-12a.out; then
		cat /tmp/cc-respaldo-12a.out; echo "ERROR: faltaban libretas antes de borrar"; exit 1
	fi
	grep -q "\"libreta_emitida@$a\": \[" "$(ultimo_manifiesto)" || { echo "ERROR: el manifiesto no cuenta las libretas del colegio $a"; exit 1; }
	admin -e "SET FOREIGN_KEY_CHECKS = 0; DELETE FROM libreta_emitida WHERE colegio_id = $a ORDER BY id DESC LIMIT 1"
	set +e
	sh "$respaldar" > /tmp/cc-respaldo-12b.out 2> /tmp/cc-respaldo-12b.err
	codigo=$?
	set -e
	cat /tmp/cc-respaldo-12b.out /tmp/cc-respaldo-12b.err
	test "$codigo" = "3"
	grep -q "libreta_emitida (faltan 1) \[colegio $a\]" /tmp/cc-respaldo-12b.out
	test "$(grep -o "libreta_emitida (faltan [0-9]*) \[colegio [0-9]*\]" /tmp/cc-respaldo-12b.out | sort -u | wc -l)" = "1" 		|| { echo "ERROR: la falta de las libretas no es solo del colegio $a"; exit 1; }
	admin -e "SELECT colegios_faltan FROM respaldo ORDER BY id DESC LIMIT 1" | grep -Eq "(\[|, ?)$a(\]|,)" 		|| { echo "ERROR: colegios_faltan no incluye al colegio $a"; exit 1; }
	echo "OK (sprint 10): la libreta borrada del colegio $a se avisa solo a ese colegio"
	;;

*)
	echo "Uso: $0 sin-credenciales|primer-respaldo|e29|restauracion|e31|e30|e32|faltan-filas-sigue|restauracion-sin-clave|permisos-anidados|resuelto|faltan-filas-por-colegio|resuelto-por-colegio|simulado|faltan-filas-asistencia|faltan-filas-areas|faltan-filas-notas|faltan-filas-libretas" >&2
	exit 2
	;;
esac
