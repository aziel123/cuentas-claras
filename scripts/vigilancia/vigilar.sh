#!/bin/sh
# Cuentas Claras · vigilante externo (sprint 7, tanda 1; .github/workflows/vigilancia.yml y docs/operacion/monitoreo.md).
# Corre FUERA del servidor (GitHub Actions, gratis): si la aplicación o el servidor se caen, las alertas internas no
# salen; este script sí falla, y GitHub avisa por correo al dueño del repositorio.
#   1. /actuator/health responde UP (solo el estado, sin detalles);
#   2. /salud/respaldo responde OK (sin fechas ni datos; ATRASADO o REVISAR es falla);
#   3. el certificado TLS vence en más de VIGILANCIA_DIAS_CERTIFICADO días (14).
# Variables: VIGILANCIA_URL (https://cuentasclaras.colegio.pe), VIGILANCIA_DIAS_CERTIFICADO.
set -eu

url="${VIGILANCIA_URL:?Falta VIGILANCIA_URL}"
url="${url%/}"
minimo="${VIGILANCIA_DIAS_CERTIFICADO:-14}"
fallas=0

falla() {
	echo "FALLA: $1"
	# Anotación visible en el resumen del workflow de GitHub (fuera de GitHub es una línea más).
	echo "::error::$1"
	fallas=$((fallas + 1))
}

salud=$(curl -sS --max-time 20 "$url/actuator/health" 2>&1 || true)
if echo "$salud" | grep -q '"status" *: *"UP"'; then
	echo "OK: la aplicación responde UP"
else
	falla "la aplicación no responde UP en /actuator/health ($(echo "$salud" | head -c 120))"
fi

respaldo=$(curl -sS --max-time 20 "$url/salud/respaldo" 2>&1 || true)
if [ "$respaldo" = "OK" ]; then
	echo "OK: el último respaldo está al día"
else
	falla "el respaldo no está al día: /salud/respaldo responde «$(echo "$respaldo" | head -c 60)»"
fi

case "$url" in
https://*)
	servidor=$(echo "$url" | sed -e 's#^https://##' -e 's#/.*$##')
	host="${servidor%%:*}"
	puerto=443
	[ "$servidor" = "$host" ] || puerto="${servidor##*:}"
	vence=$(echo | openssl s_client -servername "$host" -connect "$host:$puerto" 2>/dev/null \
		| openssl x509 -noout -enddate 2>/dev/null | cut -d= -f2 || true)
	if [ -z "$vence" ]; then
		falla "no se pudo leer el certificado TLS de $host"
	else
		dias=$(( ($(date -d "$vence" +%s) - $(date +%s)) / 86400 ))
		if [ "$dias" -lt "$minimo" ]; then
			falla "el certificado TLS de $host vence en $dias días (renovarlo antes de $minimo)"
		else
			echo "OK: el certificado TLS de $host vence en $dias días"
		fi
	fi
	;;
*)
	echo "AVISO: $url no es https; no se revisa el certificado"
	;;
esac

[ "$fallas" -eq 0 ] || exit 1
