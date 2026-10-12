#!/bin/sh
# Cuentas Claras · script de arranque de la VM (sprint 8, tanda 3). Google Cloud lo corre como root en CADA arranque
# (metadata startup-script, lo deja crear-infra.sh): por eso es idempotente. Solo prepara la máquina; no lee secretos ni
# arranca la aplicación (eso lo hacen cc-secretos.service y desplegar.sh).
# - Docker y Compose de los paquetes de Ubuntu (sin repositorios ni binarios de terceros).
# - La zona horaria de Lima (el temporizador del respaldo dice 02:30 de Lima).
# - /etc/cc (700, root): app.env, respaldo.env, caddy.env e imagen.env. /etc/cc/certs (755): el certificado PÚBLICO de la
#   autoridad de Cloud SQL y su almacén PKCS12, que la aplicación (uid 10001) lee montado en /certs.
# - Actualizaciones de seguridad automáticas (unattended-upgrades, el valor de Ubuntu).
set -eu

if ! command -v docker > /dev/null 2>&1 || ! docker compose version > /dev/null 2>&1; then
	apt-get update -q
	DEBIAN_FRONTEND=noninteractive apt-get install -y -q docker.io docker-compose-v2 unattended-upgrades
	systemctl enable --now docker
fi

timedatectl set-timezone America/Lima

install -d -m 700 -o root -g root /etc/cc
install -d -m 755 -o root -g root /etc/cc/certs
install -d -m 755 -o root -g root /opt/cuentas-claras

# El registro de Docker no crece sin límite.
if [ ! -f /etc/docker/daemon.json ]; then
	printf '{\n  "log-driver": "json-file",\n  "log-opts": { "max-size": "20m", "max-file": "5" }\n}\n' > /etc/docker/daemon.json
	systemctl restart docker
fi
