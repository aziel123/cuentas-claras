# Acta de la verificación trimestral de la bitácora (instalación compartida)

> Plantilla (sprint 8, tanda 3; sección 3.8 del diseño, decisión 130). En una instalación **compartida** la clave HMAC de la
> bitácora y la del respaldo las custodia la plataforma (2 custodios del proveedor), no la Promotoría de cada colegio. Para
> que cada colegio siga pudiendo comprobar que nadie alteró su historia, una vez por trimestre la Promotoría elige 3
> huellas diarias de SU historial de WhatsApp y el operador las busca, en una videollamada, en una copia restaurada y
> verificada del respaldo. Deben coincidir. En una instalación dedicada esto no hace falta (la Promotoría tiene su clave).

| Dato | Valor |
|---|---|
| Colegio | |
| Trimestre | |
| Fecha y hora de la videollamada (Lima) | |
| Promotoría (nombre) | |
| Operador de la plataforma (nombre) | |
| Respaldo restaurado (archivo `cc-AAAAMMDD-HHMMSS.sql.gz.age`) | |
| Resultado de `restaurar-y-verificar.sh` (cadena HMAC, anclas, conteos y libros) | OK / FALLA |

## Huellas elegidas por la Promotoría
La Promotoría las elige en el momento, de su historial de WhatsApp (mensaje «Huella del …» de la plantilla
`cc_huella_v2`), sin decirlas antes. El operador las busca en la copia restaurada (`huella_bitacora` del colegio, por fecha).

| # | Fecha de la huella | Evento y código que dice el WhatsApp | Evento y código en la copia restaurada | ¿Coinciden? |
|---|---|---|---|---|
| 1 | | | | |
| 2 | | | | |
| 3 | | | | |

## Resultado
- [ ] Las 3 coinciden y la copia verifica: la bitácora del colegio no se alteró hasta la fecha del respaldo.
- [ ] Alguna no coincide o la copia no verifica: se sigue `docs/operacion/incidente-auditoria.md` el mismo día y se avisa
      por escrito a la Promotoría y al correo externo de control del colegio.

Observaciones:

Firma de la Promotoría: ______   Firma del operador: ______
