# Acta del simulacro de restauración

> Plantilla (sprint 7, tandas 1 y 3). **Se completa en el colegio el día del simulacro presencial**, frente a la máquina del simulacro, con Promotoría y el responsable técnico (primer lunes del mes, unos 20 minutos). La restauración **nunca** se hace en el servidor de producción: se hace en la máquina del simulacro, en un MySQL vacío y desechable. Solo la prueba E33 (comprobación 8) se hace **en el servidor del colegio** o con su clave del almacenamiento. Procedimiento: [respaldos.md](respaldos.md).
>
> **El primer simulacro presencial es requisito del acta de conformidad** (anexo D de `docs/entrega/acta-de-conformidad.md`). La copia firmada se guarda en el colegio; en el repositorio solo se anota la fecha del simulacro, sin firmas ni datos.

## Datos del simulacro
| Campo | Valor |
|---|---|
| Fecha y hora de inicio y de fin | |
| ¿Es el primer simulacro presencial? | ☐ Sí ☐ No (N.° ____ ) |
| Lugar | |
| Máquina usada (no es el servidor de producción) y si tiene el disco cifrado | |
| **Versión de la aplicación:** etiqueta de Git y SHA del commit | |
| Versión del jar y del esquema según el manifiesto (`V__`) | |
| **Respaldo restaurado** (`cc-AAAAMMDD-HHMMSS.sql.gz.age`) | |
| Fecha y hora del respaldo | |
| SHA-256 del archivo según su manifiesto | |
| Origen (`RESTAURAR_ORIGEN`: almacenamiento del colegio; no el destino simulado) | |
| Quién descifró y con qué llave | ☐ Promotoría ☐ Responsable técnico |
| Quién escribió la clave HMAC (solo Promotoría) | |

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
| 10 | Las huellas de los triggers y funciones de la copia, leídas como administrador, coinciden con las del jar | | |
| 11 | Los conteos de las tablas de solo inserción son iguales o mayores que los del manifiesto | | |
| 12 | La llave de Promotoría para abrir los respaldos y su clave HMAC siguen en su custodia (sobre o gestor personal) | | |

### Detalle de la prueba E33 (comprobación 8)
| Campo | Valor |
|---|---|
| Desde dónde se probó | ☐ El servidor del colegio ☐ La máquina del simulacro con la clave del servidor |
| Archivo que se intentó borrar | |
| Comando usado (sin claves) | |
| Mensaje de error obtenido (debe ser de permiso) | |
| ¿El archivo sigue en el almacenamiento después del intento? | ☐ Sí ☐ No |

**Informe generado:** `restauracion-AAAAMMDD-HHMMSS.json` (adjuntar). Resultado global: ☐ Todo pasó ☐ Algo falló (ver abajo).

## Si algo no pasó
Qué falló, qué se hizo y quién responde (si la cadena o las anclas no verifican: [incidente-auditoria.md](incidente-auditoria.md); si hubo datos personales expuestos: [incidente-datos-personales.md](incidente-datos-personales.md)).

| # de comprobación | Qué falló | Qué se hizo | Responsable | Fecha límite |
|---|---|---|---|---|
| | | | | |
| | | | | |

## Firmas
| Promotoría | Responsable técnico |
|---|---|
| Firma: | Firma: |
| Nombre: | Nombre: |
| Fecha: | Fecha: |
