# Fin de un colegio: exportación, cierre, conservación y supresión con acta

> Sprint 11, tanda 2 (diseño: `docs/arquitectura/sprint-11-ciclo-colegio.md`, secciones 3.9 a 3.11 y 3.17). Quién: los
> operadores de la plataforma (dos personas distintas) y el DBA (`cc_migrador`). Nunca es automático.

## 1. Plazos
| Caso | Las familias consultan hasta | Base legal |
|---|---|---|
| CERRADO con la autorización del titular (paso 16) | la suspensión aplicada + 12 meses | DS 016-2024-JUS, art. 31.2 |
| CERRADO sin autorización | el cierre + 30 días | — |
| BAJA (colegio en preparación) | la baja + 30 días | — |

La base calcula `conservar_hasta` (columna generada): nadie la escribe. Lo tributario (5 años) lo guarda el colegio con su
exportación. Con **retención legal** (pedido escrito de una autoridad o un litigio) no se suprime.

## 2. Exportación
1. Se genera sola al aplicarse la SUSPENSION (`sistema.exportacion`). Antes del cierre, si la del colegio tiene más de 30
   días, el operador pulsa **Generar la exportación de cierre** (Plataforma → colegio → Estado → Exportación y supresión).
   A pedido escrito del colegio: **Pedir la exportación** con la referencia de la carta (firma del operador).
2. El enlace llega **solo por correo** al titular; escribe su DNI, recibe el código **solo por WhatsApp** (10 minutos), ve
   la clave **una vez** y descarga hasta 3 veces en 30 días. A los 5 intentos fallidos el enlace se anula: **Nuevo enlace**.
3. El correo externo de control recibe la SHA-256 del ZIP y del manifiesto, cada descarga, el descifrado de la clave y el
   borrado del archivo a los 30 días.
4. Si una libreta no coincide con su huella, la exportación queda **FALLIDA** y llega una alerta CRÍTICA: no se entrega.

El ZIP vive en `CC_DIR_EXPORTACIONES` (fuera de los respaldos). Se abre con 7-Zip o con el Explorador de Windows 11.

**Despliegue (correcciones del sprint 11, A1 y B3):** en prod `CC_DIR_EXPORTACIONES` y `CC_DIR_ACTAS` son obligatorios (sin
ellos no arranca). En la VM, `/etc/cc/instalacion.env` lleva `CC_ACTAS_DEPOSITO` (el remoto de rclone del depósito de los
respaldos, prefijo `actas/`) y se habilita `deploy/systemd/cc-actas.service`, que antes de Docker copia las actas a
`/var/lib/cuentasclaras/actas` (la aplicación la monta de solo lectura).

**Conservación (correcciones, A2):** el titular la autoriza con su firma (se puede volver a dar). Para retirarla pide un
código por WhatsApp y firma: el retiro se aplica a las 24 horas. En ese tiempo un operador puede **Frenar el retiro**
(Exportación y supresión). Con una suspensión aprobada no cambia.

## 3. Cierre
Dos operadores proponen y aprueban **Cerrar** (SUSPENDIDO → CERRADO). La base exige la exportación del mismo colegio,
generada después de la suspensión, ENTREGADA o con 30 días. Se avisa al titular, a la Promotoría, al correo institucional y
al de control, con la fecha de supresión.

## 4. Supresión con acta (instalación compartida)
1. `RevisionConservacion` avisa 30 días antes y el día que vence la conservación.
2. Un operador **propone el acta** (motivo y fecha en que vence el último respaldo con datos del colegio: como máximo 24 meses
   desde el inicio del plazo). **Otro operador la aprueba**.
3. Justo antes de ejecutarla, un operador pulsa **Verificar la cadena**: `sistema.auditoria` verifica la bitácora completa y
   lo anota en el acta. El DBA tiene **1 hora**.
4. El DBA, en **una sola conexión**:
   ```sh
   mysql -h <host> -u cc_migrador -p cuentasclaras -e "SET @cc_acta = <id>; SOURCE scripts/fin-de-colegio/suprimir.sql"
   ```
   La base guarda su conexión y 4 horas de vigencia, calcula la huella de los eventos del colegio, deja borrar y tachar
   solo lo de ese colegio y pasa el colegio a SUPRIMIDO con la ficha mínima (nombre, RUC, contrato).
5. Fuera de la base: `ACTAS_DIR=/ruta ACTAS_DESTINO=remoto:deposito/actas MYSQL_DBA="mysql ..." sh scripts/fin-de-colegio/exportar-acta.sh <id>`
   (JSON con su SHA-256 en el depósito con bloqueo de objetos). Se imprime el acta (**Imprimir el acta**) y se firma.
6. Al **restaurar un respaldo**, `restaurar-y-verificar.sh` corre `reaplicar.sh` con `RESTAURAR_ACTAS_DIR` antes de abrir la
   aplicación. En producción, `VerificadorSupresion` no deja arrancar si un acta externa no está aplicada o si un colegio
   suprimido tiene filas.

## 5. Instalación dedicada (Virgen María)
El fin definitivo es eliminar la instalación completa (Cloud SQL, VM y el depósito cuando vence su bloqueo). El acta lo dice
con las fechas; no hace falta tachar.

## 6. Qué no se suprime
`exclusiones.txt`: la bitácora (se tacha), sus huellas, los cambios de estado, las exportaciones (sus huellas), las descargas
y el acta. Riesgos residuales: la bitácora de la plataforma guarda el nombre del colegio y del titular; quien tiene
`CC_CLAVE_CONECTORES` y el archivo puede descifrar el ZIP fuera de la aplicación sin dejar evento.
