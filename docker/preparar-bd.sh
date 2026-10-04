#!/bin/sh
# Paso posterior a la migración (se ejecuta en cada `docker compose up`; todo es idempotente):
#   1. Permisos por tabla de cc_app (como administrador).
#   2. Triggers (como cc_migrador).
# Mismo orden que el job `mysql` del CI y docs/operacion/mysql-usuarios.md.
set -eu
echo "Cuentas Claras: aplicando permisos de cc_app (02-permisos-tablas.sql)..."
mysql -h mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" < /scripts/02-permisos-tablas.sql
echo "Cuentas Claras: aplicando triggers (03-triggers.sql)..."
mysql -h mysql -ucc_migrador -p"${CC_CLAVE_MIGRADOR}" cuentasclaras < /scripts/03-triggers.sql
echo "Cuentas Claras: base de datos lista."
