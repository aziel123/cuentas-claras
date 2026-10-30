#!/bin/sh
# Cuentas Claras · sprint 11, tanda 2 (sección 3.11 y AP2-3): al RESTAURAR un respaldo, vuelve a suprimir los colegios cuyas
# actas se ejecutaron (las de ACTAS_DIR, escritas por exportar-acta.sh): un respaldo anterior a la supresión no los
# resucita. Escribe el SQL en la salida estándar: restaurar-y-verificar.sh lo aplica como administrador DESPUÉS de cargar
# los datos y ANTES de 02 y 03 (sin triggers: es la copia que se está verificando). Comprueba la SHA-256 de cada acta.
# Uso: sh reaplicar.sh <ACTAS_DIR>
set -eu
dir="${1:-}"
aqui=$(cd "$(dirname "$0")" && pwd)
[ -n "$dir" ] && [ -d "$dir" ] || { echo "-- sin actas externas: nada que reaplicar"; exit 0; }
borrados=$(sed -n '/^SET FOREIGN_KEY_CHECKS = 0;/,/^SET FOREIGN_KEY_CHECKS = 1;/p' "$aqui/suprimir.sql" | grep -v '@cc_filas')
for archivo in "$dir"/acta-*.json; do
	[ -f "$archivo" ] || continue
	esperado=$(cat "$archivo.sha256" 2>/dev/null || true)
	calculado=$(sha256sum "$archivo" | cut -d' ' -f1)
	if [ "$esperado" != "$calculado" ]; then
		echo "ERROR: la SHA-256 del acta $(basename "$archivo") no coincide" >&2
		exit 1
	fi
	# Correcciones del sprint 11 (B2): el JSON va en hexadecimal (UNHEX): ni una comilla ni una barra invertida del acta
	# cambian lo que lee MySQL (con o sin NO_BACKSLASH_ESCAPES).
	hex=$(od -An -v -tx1 "$archivo" | tr -d ' ' | tr -d '\n')
	cat <<SQL
-- $(basename "$archivo")
SET @cc_json = CONVERT(UNHEX('$hex') USING utf8mb4);
SET @cc_colegio = CAST(JSON_EXTRACT(@cc_json, '\$.colegio_id') AS UNSIGNED);
SET @cc_acta = CAST(JSON_EXTRACT(@cc_json, '\$.id') AS UNSIGNED);
$borrados
UPDATE evento_auditoria SET nombre_usuario = 'suprimido', usuario_id = NULL, roles = NULL, valor_anterior = NULL,
    valor_nuevo = NULL, detalle = NULL, ip = NULL
 WHERE colegio_id = @cc_colegio AND secuencia <= CAST(JSON_EXTRACT(@cc_json, '\$.ultima_secuencia') AS UNSIGNED);
INSERT IGNORE INTO acta_supresion (id, colegio_id, plataforma_id, tipo, estado, motivo, propuesto_por, operador_id,
    aprobado_por, aprobador_id, aprobado_en, inicio_plazo, respaldos_vencen_el, cadena_verificada_en, ejecutado_por,
    ejecutando_desde, huella_eventos, ultima_secuencia, filas_suprimidas, eventos_tachados, ejecutada_en, creado_en,
    creado_por, actualizado_en)
SELECT @cc_acta, @cc_colegio, JSON_EXTRACT(@cc_json, '\$.plataforma_id'), JSON_UNQUOTE(JSON_EXTRACT(@cc_json, '\$.tipo')),
    'EJECUTADA', JSON_UNQUOTE(JSON_EXTRACT(@cc_json, '\$.motivo')), JSON_UNQUOTE(JSON_EXTRACT(@cc_json, '\$.propuesto_por')),
    JSON_EXTRACT(@cc_json, '\$.operador_id'), JSON_UNQUOTE(JSON_EXTRACT(@cc_json, '\$.aprobado_por')),
    JSON_EXTRACT(@cc_json, '\$.aprobador_id'), JSON_UNQUOTE(JSON_EXTRACT(@cc_json, '\$.aprobado_en')),
    JSON_UNQUOTE(JSON_EXTRACT(@cc_json, '\$.inicio_plazo')), JSON_UNQUOTE(JSON_EXTRACT(@cc_json, '\$.respaldos_vencen_el')),
    JSON_UNQUOTE(JSON_EXTRACT(@cc_json, '\$.cadena_verificada_en')), JSON_UNQUOTE(JSON_EXTRACT(@cc_json, '\$.ejecutado_por')),
    JSON_UNQUOTE(JSON_EXTRACT(@cc_json, '\$.ejecutando_desde')), JSON_UNQUOTE(JSON_EXTRACT(@cc_json, '\$.huella_eventos')),
    JSON_EXTRACT(@cc_json, '\$.ultima_secuencia'), JSON_UNQUOTE(JSON_EXTRACT(@cc_json, '\$.filas_suprimidas')),
    JSON_EXTRACT(@cc_json, '\$.eventos_tachados'), JSON_UNQUOTE(JSON_EXTRACT(@cc_json, '\$.ejecutada_en')),
    JSON_UNQUOTE(JSON_EXTRACT(@cc_json, '\$.creado_en')), JSON_UNQUOTE(JSON_EXTRACT(@cc_json, '\$.creado_por')), NOW(6);
UPDATE colegio SET estado = 'SUPRIMIDO', acta_supresion_id = @cc_acta, razon_social = NULL, direccion = NULL,
    correo_institucional = NULL, titular_nombre = NULL, titular_documento = NULL, aviso_privacidad_aprobado_por = NULL,
    produccion_por = NULL, actualizado_por = NULL, conservacion_autorizada_por = NULL, conservacion_autorizada_en = NULL
 WHERE id = @cc_colegio;
SQL
done
