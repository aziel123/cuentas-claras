#!/bin/sh
# Cuentas Claras · crea (o completa) la infraestructura de UNA instalación en Google Cloud, región Santiago (sprint 8,
# tanda 3; decisiones 110, 111, 134 y 135; docs/operacion/despliegue-gcp.md). Idempotente: lo que ya existe no se toca,
# así que se puede volver a correr después de un error.
#
#   CC_PROYECTO=<proyecto> sh deploy/gcp/crear-infra.sh compartida   # la instalación compartida (VM + Cloud SQL)
#   CC_PROYECTO=<proyecto> sh deploy/gcp/crear-infra.sh demo         # la demo (solo una VM, sin base ni secretos)
#   CC_PROYECTO=<proyecto> sh deploy/gcp/crear-infra.sh <colegio>    # una instalación dedicada
#
# Crea: las APIs, la VPC con su subred, el cortafuegos (SSH solo por IAP; 80 y 443 para Caddy), el acceso privado a
# servicios (Cloud SQL por IP privada), la instancia de Cloud SQL para MySQL 8.4 (marca log_bin_trust_function_creators,
# TLS obligatorio, respaldos diarios, PITR de 7 días, protección contra borrado), la IP estática, la cuenta de servicio de
# la VM (solo lee SUS secretos), los secretos (vacíos o con una clave al azar) y la VM e2-small con Docker.
# NO crea los usuarios de MySQL: los crea deploy/gcp/preparar-base.sh con 01-usuarios.sql, por SQL (H13: un usuario
# creado desde la consola recibe cloudsqlsuperuser y prod no arranca).
#
# Requisitos: gcloud autenticado con una cuenta con permisos de propietario del proyecto y la facturación activa.
# Las opciones de gcloud son de referencia (sección 7.3 del diseño): si alguna cambió, «gcloud ... --help» lo dice.
set -eu

aqui=$(cd "$(dirname "$0")" && pwd)
CC_INSTANCIA="${1:-${CC_INSTANCIA:-compartida}}"
export CC_INSTANCIA
# shellcheck source=deploy/gcp/variables.sh
. "$aqui/variables.sh"

demo="no"
[ "$CC_INSTANCIA" != "demo" ] || demo="si"

existe() {
	"$@" > /dev/null 2>&1
}

aleatoria() {
	# $1 bytes al azar en base64 (sin saltos de línea).
	openssl rand -base64 "$1" | tr -d '\n'
}

command -v gcloud > /dev/null || { echo "Falta gcloud (Google Cloud CLI)." >&2; exit 1; }
command -v openssl > /dev/null || { echo "Falta openssl." >&2; exit 1; }

cc_paso "Proyecto $CC_PROYECTO, región $CC_REGION, instalación «$CC_INSTANCIA»"
cc_gcloud services enable compute.googleapis.com iap.googleapis.com secretmanager.googleapis.com \
	servicenetworking.googleapis.com sqladmin.googleapis.com logging.googleapis.com monitoring.googleapis.com

cc_paso "Red privada $CC_VPC y subred $CC_SUBRED ($CC_SUBRED_RANGO)"
if ! existe cc_gcloud compute networks describe "$CC_VPC"; then
	cc_gcloud compute networks create "$CC_VPC" --subnet-mode=custom
fi
if ! existe cc_gcloud compute networks subnets describe "$CC_SUBRED" --region "$CC_REGION"; then
	cc_gcloud compute networks subnets create "$CC_SUBRED" --network "$CC_VPC" --region "$CC_REGION" \
		--range "$CC_SUBRED_RANGO" --enable-private-ip-google-access
fi

cc_paso "Cortafuegos: SSH solo desde IAP; 80 y 443 hacia Caddy. Nada más entra."
if ! existe cc_gcloud compute firewall-rules describe cc-permitir-ssh-iap; then
	cc_gcloud compute firewall-rules create cc-permitir-ssh-iap --network "$CC_VPC" --direction INGRESS \
		--action ALLOW --rules tcp:22 --source-ranges 35.235.240.0/20 --target-tags cc-vm
fi
if ! existe cc_gcloud compute firewall-rules describe cc-permitir-web; then
	cc_gcloud compute firewall-rules create cc-permitir-web --network "$CC_VPC" --direction INGRESS \
		--action ALLOW --rules tcp:80,tcp:443,udp:443 --source-ranges 0.0.0.0/0 --target-tags cc-vm
fi

if [ "$demo" = "no" ]; then
	cc_paso "Acceso privado a servicios ($CC_RANGO_SERVICIOS): Cloud SQL solo por IP privada"
	if ! existe cc_gcloud compute addresses describe "$CC_RANGO_SERVICIOS" --global; then
		cc_gcloud compute addresses create "$CC_RANGO_SERVICIOS" --global --purpose VPC_PEERING --prefix-length 20 \
			--network "$CC_VPC"
	fi
	if ! cc_gcloud services vpc-peerings list --network "$CC_VPC" --format 'value(peering)' 2> /dev/null \
			| grep -q servicenetworking; then
		cc_gcloud services vpc-peerings connect --service servicenetworking.googleapis.com \
			--ranges "$CC_RANGO_SERVICIOS" --network "$CC_VPC"
	fi

	cc_paso "Secretos de la instalación en Secret Manager (prefijo $CC_SECRETOS)"
	# Los que se generan al azar aquí (nadie los escribe a mano) y los que agrega después el responsable técnico.
	for secreto in db-root db-migrador db-app db-sistema db-respaldo auditoria-hmac clave-conectores; do
		nombre="$CC_SECRETOS-$secreto"
		if ! existe cc_gcloud secrets describe "$nombre"; then
			cc_gcloud secrets create "$nombre" --replication-policy=user-managed --locations "$CC_REGION"
		fi
		if ! existe cc_gcloud secrets versions access latest --secret "$nombre"; then
			case "$secreto" in
				clave-conectores) aleatoria 32 | cc_gcloud secrets versions add "$nombre" --data-file=- ;;
				auditoria-hmac) aleatoria 48 | cc_gcloud secrets versions add "$nombre" --data-file=- ;;
				# Las claves de MySQL: sin caracteres que haya que escapar en una URL, un archivo .env o SQL.
				*) aleatoria 36 | tr '+/' '-_' | tr -d '=' | cc_gcloud secrets versions add "$nombre" --data-file=- ;;
			esac
		fi
	done
	for secreto in whatsapp-token whatsapp-secreto-app whatsapp-token-verificacion smtp-clave rclone-respaldo; do
		nombre="$CC_SECRETOS-$secreto"
		if ! existe cc_gcloud secrets describe "$nombre"; then
			cc_gcloud secrets create "$nombre" --replication-policy=user-managed --locations "$CC_REGION"
			echo "  (vacío) $nombre: agrégale su valor con «gcloud secrets versions add $nombre --data-file=-»"
		fi
	done

	cc_paso "Cloud SQL para MySQL 8.4 ($CC_SQL): IP privada, TLS obligatorio, respaldos y PITR de 7 días"
	if ! existe cc_gcloud sql instances describe "$CC_SQL"; then
		clave_root=$(cc_gcloud secrets versions access latest --secret "$CC_SECRETOS-db-root")
		cc_gcloud sql instances create "$CC_SQL" \
			--database-version "$CC_SQL_VERSION" --edition ENTERPRISE --tier "$CC_SQL_TIER" \
			--region "$CC_REGION" --availability-type ZONAL \
			--network "projects/$CC_PROYECTO/global/networks/$CC_VPC" --no-assign-ip \
			--ssl-mode ENCRYPTED_ONLY \
			--database-flags log_bin_trust_function_creators=on \
			--storage-type SSD --storage-size 10GB --storage-auto-increase \
			--backup-start-time 07:00 --enable-bin-log --retained-backups-count 14 --retained-transaction-log-days 7 \
			--maintenance-window-day SUN --maintenance-window-hour 9 \
			--deletion-protection --root-password "$clave_root"
		unset clave_root
	fi
fi

cc_paso "IP estática $CC_IP"
if ! existe cc_gcloud compute addresses describe "$CC_IP" --region "$CC_REGION"; then
	cc_gcloud compute addresses create "$CC_IP" --region "$CC_REGION"
fi

cc_paso "Cuenta de servicio de la VM ($CC_CUENTA_SERVICIO): registros y SOLO sus secretos"
correo_cs="$CC_CUENTA_SERVICIO@$CC_PROYECTO.iam.gserviceaccount.com"
if ! existe cc_gcloud iam service-accounts describe "$correo_cs"; then
	cc_gcloud iam service-accounts create "$CC_CUENTA_SERVICIO" --display-name "VM de Cuentas Claras ($CC_INSTANCIA)"
fi
for rol in roles/logging.logWriter roles/monitoring.metricWriter; do
	cc_gcloud projects add-iam-policy-binding "$CC_PROYECTO" --member "serviceAccount:$correo_cs" --role "$rol" \
		--condition None > /dev/null
done
if [ "$demo" = "no" ]; then
	# La VM lee las claves de la aplicación y del respaldo. NUNCA las de root ni de cc_migrador (las lee solo el
	# responsable técnico, en su máquina, al desplegar).
	for secreto in db-app db-sistema db-respaldo auditoria-hmac clave-conectores whatsapp-token whatsapp-secreto-app \
			whatsapp-token-verificacion smtp-clave rclone-respaldo; do
		cc_gcloud secrets add-iam-policy-binding "$CC_SECRETOS-$secreto" --member "serviceAccount:$correo_cs" \
			--role roles/secretmanager.secretAccessor > /dev/null
	done
fi

cc_paso "VM $CC_VM ($CC_VM_TIPO, Ubuntu LTS, SSH solo por IAP con OS Login)"
if ! existe cc_gcloud compute instances describe "$CC_VM" --zone "$CC_ZONA"; then
	cc_gcloud compute instances create "$CC_VM" --zone "$CC_ZONA" --machine-type "$CC_VM_TIPO" \
		--image-family "$CC_VM_IMAGEN_FAMILIA" --image-project ubuntu-os-cloud \
		--boot-disk-size "${CC_VM_DISCO_GB}GB" --boot-disk-type pd-balanced \
		--subnet "$CC_SUBRED" --address "$CC_IP" --tags cc-vm \
		--service-account "$correo_cs" --scopes cloud-platform \
		--shielded-secure-boot --shielded-vtpm --shielded-integrity-monitoring \
		--metadata enable-oslogin=TRUE,block-project-ssh-keys=TRUE,cc-instancia="$CC_INSTANCIA" \
		--metadata-from-file startup-script="$aqui/../vm/arranque-vm.sh"
fi

ip=$(cc_gcloud compute addresses describe "$CC_IP" --region "$CC_REGION" --format 'value(address)')
cc_paso "Listo"
echo "IP pública de la instalación «$CC_INSTANCIA»: $ip"
echo "Siguiente:"
echo "  1. En el DNS del dominio: un registro A de app.<dominio> (o demo.<dominio>) a $ip."
if [ "$demo" = "no" ]; then
	echo "  2. sh deploy/gcp/preparar-base.sh $CC_INSTANCIA   (usuarios 01, certificado de Cloud SQL y /etc/cc en la VM)"
	echo "  3. sh deploy/desplegar.sh $CC_INSTANCIA <digest>   (migrar, 02, 03 y arrancar)"
else
	echo "  2. sh deploy/desplegar.sh demo <digest>   (sin base ni secretos)"
fi
