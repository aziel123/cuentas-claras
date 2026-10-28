#!/bin/sh
# Cuentas Claras · sprint 11, tanda 1 (AP-3, H9): el reloj de las reglas de la base no lo mueve la conexión.
# Cuenta en scripts/mysql/03-triggers.sql (fuera de los comentarios «--») los usos de UTC_TIMESTAMP, NOW(, CURDATE,
# CURRENT_TIMESTAMP y CURRENT_DATE, que obedecen a SET timestamp de la sesión, y falla si son más que los que permite
# scripts/mysql/reloj-pendiente.txt (la primera línea que no es comentario). Así ningún trigger nuevo vuelve a usarlos y
# el conteo solo puede bajar. Se corre desde la raíz del repositorio: sh scripts/ci/reloj-real.sh
set -eu
TRIGGERS="${1:-scripts/mysql/03-triggers.sql}"
PENDIENTE="${2:-scripts/mysql/reloj-pendiente.txt}"
permitidos=$(grep -v '^[[:space:]]*#' "$PENDIENTE" | grep -E '^[[:space:]]*[0-9]+[[:space:]]*$' | head -n 1 | tr -d '[:space:]')
if [ -z "$permitidos" ]; then
  echo "ERROR: $PENDIENTE no tiene el número de usos permitidos"; exit 1
fi
usos=$(sed -e 's/--.*$//' "$TRIGGERS" | grep -oE 'UTC_TIMESTAMP|NOW\(|CURDATE|CURRENT_TIMESTAMP|CURRENT_DATE' | wc -l | tr -d '[:space:]')
echo "Usos del reloj de la sesión en $TRIGGERS: $usos (permitidos: $permitidos)"
if [ "$usos" -gt "$permitidos" ]; then
  echo "ERROR: hay $usos usos de UTC_TIMESTAMP, NOW(, CURDATE, CURRENT_TIMESTAMP o CURRENT_DATE y se permiten $permitidos."
  echo "       Usa cc_ahora_lima_real() (o cc_hoy_lima()): SET timestamp de la sesión no la mueve (H9)."
  sed -e 's/--.*$//' "$TRIGGERS" | grep -nE 'UTC_TIMESTAMP|NOW\(|CURDATE|CURRENT_TIMESTAMP|CURRENT_DATE' || true
  exit 1
fi
if [ "$usos" -lt "$permitidos" ]; then
  echo "AVISO: bajaron a $usos: actualiza $PENDIENTE (el conteo solo baja)."
fi
# Sprint 11, tanda 2 (AP2-6): tampoco la resta a mano de la zona («INTERVAL 5 HOUR»): usa cc_ahora_lima_real().
zona=$(sed -e 's/--.*$//' "$TRIGGERS" | grep -oE 'INTERVAL 5 HOUR' | wc -l | tr -d '[:space:]')
if [ "$zona" -gt 0 ]; then
  echo "ERROR: hay $zona usos de «INTERVAL 5 HOUR» (la zona de Lima a mano). Usa cc_ahora_lima_real() o cc_hoy_lima()."
  sed -e 's/--.*$//' "$TRIGGERS" | grep -nE 'INTERVAL 5 HOUR' || true
  exit 1
fi
echo "OK: ningún uso nuevo del reloj que la conexión mueve."
