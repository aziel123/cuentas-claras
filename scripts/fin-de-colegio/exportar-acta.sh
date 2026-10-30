#!/bin/sh
# Cuentas Claras · sprint 11, tanda 2 (AP2-3): después de suprimir.sql, el acta EJECUTADA se escribe FUERA de la base: un
# JSON con todas sus columnas y su SHA-256, en ACTAS_DIR y, si hay destino, en el depósito con bloqueo de objetos (el de
# los respaldos, prefijo «actas/»). reaplicar.sh las lee antes de abrir la aplicación al restaurar un respaldo y
# VerificadorSupresion se niega a arrancar si una no está aplicada en la base.
# Uso: ACTAS_DIR=/ruta [ACTAS_DESTINO=remoto:deposito/actas] sh exportar-acta.sh <id del acta>
# La conexión: MYSQL_DBA (por ejemplo «mysql -h host -u cc_migrador -p...»).
set -eu
id="${1:?Falta el id del acta}"
: "${MYSQL_DBA:?Falta MYSQL_DBA (la conexión del DBA)}"
dir="${ACTAS_DIR:?Falta ACTAS_DIR}"
mkdir -p "$dir"
json=$($MYSQL_DBA -N cuentasclaras -e "SELECT JSON_OBJECT('id', id, 'colegio_id', colegio_id, 'plataforma_id', plataforma_id,
	'tipo', tipo, 'estado', estado, 'motivo', motivo, 'propuesto_por', propuesto_por, 'operador_id', operador_id,
	'aprobado_por', aprobado_por, 'aprobador_id', aprobador_id, 'aprobado_en', aprobado_en, 'inicio_plazo', inicio_plazo,
	'respaldos_vencen_el', respaldos_vencen_el, 'cadena_verificada_en', cadena_verificada_en, 'ejecutado_por', ejecutado_por,
	'ejecutando_desde', ejecutando_desde, 'huella_eventos', huella_eventos, 'ultima_secuencia', ultima_secuencia,
	'filas_suprimidas', filas_suprimidas, 'eventos_tachados', eventos_tachados, 'ejecutada_en', ejecutada_en,
	'creado_en', creado_en, 'creado_por', creado_por) FROM acta_supresion WHERE id = $id AND estado = 'EJECUTADA'")
[ -n "$json" ] || { echo "ERROR: el acta $id no está EJECUTADA"; exit 1; }
colegio=$(echo "$json" | sed -n 's/.*"colegio_id": *\([0-9]*\).*/\1/p')
archivo="$dir/acta-$colegio-$id.json"
printf '%s\n' "$json" > "$archivo"
sha256sum "$archivo" | cut -d' ' -f1 > "$archivo.sha256"
if [ -n "${ACTAS_DESTINO:-}" ]; then
	rclone copy "$archivo" "$ACTAS_DESTINO"
	rclone copy "$archivo.sha256" "$ACTAS_DESTINO"
fi
echo "OK: acta $id escrita en $archivo (SHA-256 $(cat "$archivo.sha256"))"
