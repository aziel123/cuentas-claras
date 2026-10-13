#!/bin/sh
# Cuentas Claras · despliegue reproducible de una instalación en Google Cloud (sprint 8, tanda 3; sección 7.4 del diseño;
# decisión 134; docs/operacion/despliegue-gcp.md). Corre en la MÁQUINA DEL RESPONSABLE TÉCNICO, autenticado con gcloud:
#
#   CC_PROYECTO=<proyecto> sh deploy/desplegar.sh compartida ghcr.io/<org>/cuentas-claras@sha256:<digest>
#   CC_PROYECTO=<proyecto> CC_MARCA_NOMBRE="<marca>" CC_DOMINIO_APP=demo.<dominio> CC_ACME_CORREO=<correo> \
#     sh deploy/desplegar.sh demo ghcr.io/<org>/cuentas-claras@sha256:<digest>
#
# La imagen va SIEMPRE por digest (la que publicó el CI para un commit de main), nunca por etiqueta. Pasos (instalación con
# base): 1. respaldo «antes de desplegar»; 2. detener la aplicación; 3. migrar con cc_migrador; 4. 02 como root y 03 como
# cc_migrador; 5. arrancar con el digest nuevo; 6. esperar que esté lista y leer en el log las líneas del verificador de
# prod (huellas, privilegios, TLS y plataforma): si falta alguna, la aplicación se detiene; 7. anotar el digest.
# Las claves de root y de cc_migrador se leen de Secret Manager EN ESTA MÁQUINA y viajan por la entrada estándar de SSH:
# la VM nunca las guarda. Ventana: después de las 21:00 o en fin de semana (decisión 88), unos 2 minutos sin servicio.
set -eu

aqui=$(cd "$(dirname "$0")" && pwd)
CC_RAIZ=$(cd "$aqui/.." && pwd)
CC_INSTANCIA="${1:?Uso: desplegar.sh <instalacion> <imagen@sha256:...>}"
imagen="${2:?Falta la imagen por digest: ghcr.io/<org>/cuentas-claras@sha256:<digest>}"
export CC_INSTANCIA CC_RAIZ
# shellcheck source=deploy/gcp/variables.sh
. "$aqui/gcp/variables.sh"

echo "$imagen" | grep -Eq '^[a-z0-9./_-]+@sha256:[0-9a-f]{64}$' \
	|| { echo "La imagen va por digest (…@sha256:<64 hex>), nunca por etiqueta." >&2; exit 1; }
vm="sudo sh $CC_DIR_VM/deploy/vm/en-la-vm.sh"

cc_paso "Archivos de despliegue en la VM $CC_VM"
cc_copiar_repo
# Si el paquete de GHCR es privado: un token de SOLO LECTURA (read:packages) en el secreto <prefijo>-ghcr-lectura
# («usuario:token»). Se usa una vez para bajar la imagen y no queda en la línea de comandos.
if lectura=$(cc_secreto ghcr-lectura 2> /dev/null) && [ -n "$lectura" ]; then
	echo "$lectura" | cut -d: -f2- | cc_ssh "sudo docker login ghcr.io -u $(echo "$lectura" | cut -d: -f1) --password-stdin"
fi

if [ "$CC_INSTANCIA" = "demo" ]; then
	: "${CC_MARCA_NOMBRE:?Define CC_MARCA_NOMBRE}"
	: "${CC_DOMINIO_APP:?Define CC_DOMINIO_APP (demo.<dominio>)}"
	: "${CC_ACME_CORREO:?Define CC_ACME_CORREO}"
	cc_paso "Demo: /etc/cc/demo.env y /etc/cc/caddy.env (sin secretos)"
	{
		echo "# La demo NO lleva secretos (sección 8.3): sus claves se generan al azar al arrancar."
		echo "CC_MARCA_NOMBRE=$CC_MARCA_NOMBRE"
		echo "CC_URL_PUBLICA=https://$CC_DOMINIO_APP"
		[ -z "${CC_OPERADORES:-}" ] || echo "CC_OPERADORES=$CC_OPERADORES"
	} | cc_ssh "sudo tee /etc/cc/demo.env > /dev/null && sudo chmod 600 /etc/cc/demo.env"
	printf 'CC_DOMINIO_APP=%s\nCC_ACME_CORREO=%s\n' "$CC_DOMINIO_APP" "$CC_ACME_CORREO" \
		| cc_ssh "sudo tee /etc/cc/caddy.env > /dev/null && sudo chmod 600 /etc/cc/caddy.env"
	cc_paso "Demo: arrancar, comprobar que no sale a internet y programar el reinicio de las 03:00"
	cc_ssh "$vm demo $imagen"
	exit 0
fi

cc_paso "1. Respaldo «antes de desplegar»"
cc_ssh "$vm respaldo-previo"

cc_paso "2. Detener la aplicación"
cc_ssh "$vm detener"

cc_paso "3. Migrar con cc_migrador (la imagen nueva, modo migrar)"
cc_secreto db-migrador | cc_ssh "$vm migrar $imagen"

cc_paso "4. Permisos (02, como root) y triggers (03, como cc_migrador)"
cc_secreto db-root | { cat; echo; } | cc_ssh "$vm sql-root 02-permisos-tablas.sql"
cc_secreto db-migrador | { cat; echo; } | cc_ssh "$vm sql-migrador 03-triggers.sql"

cc_paso "5 y 6. Arrancar con el digest nuevo y comprobar el verificador de prod"
cc_ssh "$vm arrancar $imagen"

cc_paso "7. Listo: $imagen en «$CC_INSTANCIA» (anotado en /etc/cc/desplegado.txt de la VM)"
