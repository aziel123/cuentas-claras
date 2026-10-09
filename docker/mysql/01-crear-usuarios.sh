#!/bin/sh
# Se ejecuta UNA sola vez, cuando el contenedor de MySQL arranca con el volumen vacío.
# Crea la base `cuentasclaras`, los usuarios cc_migrador, cc_app, cc_sistema y cc_respaldo y el rol cc_negocio con las
# claves del archivo .env (mismo contenido que scripts/mysql/01-usuarios.sql, sin dejar las claves escritas en ningún
# archivo).
set -eu
sed -e "s/__CLAVE_MIGRADOR__/${CC_CLAVE_MIGRADOR}/" \
    -e "s/__CLAVE_APP__/${CC_CLAVE_APP}/" \
    -e "s/__CLAVE_SISTEMA__/${CC_CLAVE_SISTEMA}/" \
    -e "s/__CLAVE_RESPALDO__/${CC_CLAVE_RESPALDO}/" \
    /scripts/01-usuarios.sql | mysql -uroot -p"${MYSQL_ROOT_PASSWORD}"
echo "Cuentas Claras: base cuentasclaras, usuarios cc_migrador, cc_app, cc_sistema y cc_respaldo y rol cc_negocio creados."
