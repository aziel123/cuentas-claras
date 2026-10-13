# Cuentas Claras · nombres y valores por defecto del despliegue en Google Cloud (sprint 8, tanda 3; decisión 110).
# Lo cargan (con «.») crear-infra.sh, preparar-base.sh, paso-0.sh y desplegar.sh. Cambia los valores con variables de
# entorno ANTES de correrlos (por ejemplo: CC_PROYECTO=mi-proyecto sh deploy/gcp/crear-infra.sh compartida).
# Aquí NO va ninguna clave: las claves viven solo en Secret Manager.

: "${CC_PROYECTO:?Define CC_PROYECTO con el id del proyecto de Google Cloud (gcloud projects list)}"
CC_REGION="${CC_REGION:-southamerica-west1}"          # Santiago de Chile (decisión 110)
CC_ZONA="${CC_ZONA:-southamerica-west1-a}"
CC_INSTANCIA="${CC_INSTANCIA:-compartida}"             # compartida | demo | <colegio> (una instalación dedicada)

# Red privada (VPC) y el rango para los servicios administrados (Cloud SQL por IP privada).
CC_VPC="${CC_VPC:-cc-vpc}"
CC_SUBRED="${CC_SUBRED:-cc-subred-${CC_REGION}}"
CC_SUBRED_RANGO="${CC_SUBRED_RANGO:-10.20.0.0/24}"
CC_RANGO_SERVICIOS="${CC_RANGO_SERVICIOS:-cc-rango-servicios}"

# Base de datos (sección 7.3): MySQL 8.4 Enterprise, db-g1-small, zonal, SSD de 10 GB con crecimiento.
CC_SQL="${CC_SQL:-cc-${CC_INSTANCIA}}"
CC_SQL_VERSION="${CC_SQL_VERSION:-MYSQL_8_4}"
CC_SQL_TIER="${CC_SQL_TIER:-db-g1-small}"

# La VM (sección 7.1): e2-small con Ubuntu LTS, IP externa estática, SSH solo por IAP.
CC_VM="${CC_VM:-cc-vm-${CC_INSTANCIA}}"
CC_VM_TIPO="${CC_VM_TIPO:-e2-small}"
CC_VM_DISCO_GB="${CC_VM_DISCO_GB:-20}"
CC_VM_IMAGEN_FAMILIA="${CC_VM_IMAGEN_FAMILIA:-ubuntu-2404-lts-amd64}"
CC_IP="${CC_IP:-cc-ip-${CC_INSTANCIA}}"
CC_CUENTA_SERVICIO="${CC_CUENTA_SERVICIO:-cc-vm-${CC_INSTANCIA}}"

# Prefijo de los secretos de esta instalación en Secret Manager (cada instalación lee SOLO los suyos).
CC_SECRETOS="${CC_SECRETOS:-cc-${CC_INSTANCIA}}"

# Dónde vive el repositorio en la VM (sin claves: solo scripts y compose).
CC_DIR_VM="${CC_DIR_VM:-/opt/cuentas-claras}"

cc_gcloud() {
	gcloud --project "$CC_PROYECTO" --quiet "$@"
}

cc_paso() {
	printf '\n== %s\n' "$*"
}

# Un comando en la VM, por SSH a través de IAP (la VM no acepta SSH desde internet). La entrada estándar pasa tal cual:
# así llegan las claves, sin quedar en la línea de comandos ni en un archivo de la VM.
cc_ssh() {
	gcloud --project "$CC_PROYECTO" compute ssh "$CC_VM" --zone "$CC_ZONA" --tunnel-through-iap --quiet --command "$1"
}

# Copia a la VM lo que necesita del repositorio (deploy/ y scripts/: sin código fuente ni claves).
cc_copiar_repo() {
	: "${CC_RAIZ:?El script debe definir CC_RAIZ (la raíz del repositorio) antes de cargar variables.sh}"
	tar -C "$CC_RAIZ" -czf - deploy scripts | cc_ssh "sudo install -d -m 755 $CC_DIR_VM && sudo tar -xzf - -C $CC_DIR_VM \
		&& sudo find $CC_DIR_VM -name '*.sh' -exec sed -i 's/\r\$//' {} + && sudo chmod -R go-w $CC_DIR_VM"
}

# Las claves de MySQL que se leen en la máquina del responsable técnico (nunca en la VM).
cc_secreto() {
	gcloud --project "$CC_PROYECTO" secrets versions access latest --secret "$CC_SECRETOS-$1" --quiet
}
