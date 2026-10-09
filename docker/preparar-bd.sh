#!/bin/sh
# Paso posterior a la migración (se ejecuta en cada `docker compose up`; todo es idempotente):
#   0. Sprint 7: los usuarios cc_respaldo y cc_sistema y el rol cc_negocio en una base creada antes del sprint 7
#      (04-una-vez-sprint-7.sql).
#   1. Permisos de cc_app, cc_sistema y cc_respaldo (como administrador; 02 empieza quitándolo todo y es la fuente única).
#   2. Triggers (como cc_migrador).
# Mismo orden que el job `mysql` del CI y docs/operacion/mysql-usuarios.md.
set -eu
echo "Cuentas Claras: usuarios del sprint 7 (04-una-vez-sprint-7.sql, idempotente)..."
sed -e "s/__CLAVE_RESPALDO__/${CC_CLAVE_RESPALDO}/" -e "s/__CLAVE_SISTEMA__/${CC_CLAVE_SISTEMA}/" \
    /scripts/04-una-vez-sprint-7.sql | mysql -h mysql -uroot -p"${MYSQL_ROOT_PASSWORD}"
echo "Cuentas Claras: aplicando permisos de cc_app, cc_sistema y cc_respaldo (02-permisos-tablas.sql)..."
mysql -h mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" < /scripts/02-permisos-tablas.sql
echo "Cuentas Claras: aplicando triggers (03-triggers.sql)..."
mysql -h mysql -ucc_migrador -p"${CC_CLAVE_MIGRADOR}" cuentasclaras < /scripts/03-triggers.sql
echo "Cuentas Claras: base de datos lista."
