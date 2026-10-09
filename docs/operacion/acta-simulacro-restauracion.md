# Acta del simulacro de restauración

> Plantilla (sprint 7, tanda 1). Se llena en el simulacro **mensual y presencial** (primer lunes del mes, unos 20
> minutos) con Promotoría y el responsable técnico. Procedimiento: [respaldos.md](respaldos.md). El primer simulacro es
> requisito del acta de conformidad.

| Campo | Valor |
|---|---|
| Fecha y hora | |
| Lugar y máquina usada (no es el servidor) | |
| Respaldo restaurado (`cc-AAAAMMDD-HHMMSS.sql.gz.age`) | |
| Versión del esquema y de la aplicación (manifiesto) | |
| Quién escribió la clave HMAC (solo Promotoría) | |
| Quién descifró (Promotoría o responsable técnico) | |

## Comprobaciones
| # | Comprobación | Resultado (Sí / No) | Observación |
|---|---|---|---|
| 1 | El SHA-256 del archivo coincide con su manifiesto | | |
| 2 | La copia se cargó en un MySQL vacío y desechable | | |
| 3 | `verificar-respaldo` con la clave HMAC: la cadena verifica completa | | |
| 4 | Las anclas de los 7 respaldos anteriores están, con su hash | | |
| 5 | Los libros cuadran (lo pagado y lo descontado de cada cuota, pagos con su boleta, series sin huecos) | | |
| 6 | La huella del último resumen que Promotoría recibió por WhatsApp coincide con la de la copia | | |
| 7 | La aplicación arrancó en prod sobre la copia (verificador y readiness) | | |
| 8 | Con la clave del servidor, **borrar** un respaldo del almacenamiento da error de permiso (E33) | | |
| 9 | El contenedor y la copia se eliminaron al terminar | | |

Informe generado: `restauracion-AAAAMMDD-HHMMSS.json` (adjuntar).

## Si algo no pasó
Qué falló, qué se hizo y quién responde (si la cadena o las anclas no verifican: [incidente-auditoria.md](incidente-auditoria.md)).

## Firmas
| Promotoría | Responsable técnico |
|---|---|
| | |
