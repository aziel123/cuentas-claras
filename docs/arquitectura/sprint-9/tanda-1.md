# Sprint 9 · Tanda 1 · Brief (resumen posterior)

> QA-S9-11: la tanda 1 se implementó el 14 y 15 de octubre de 2026, **antes** del «Método por sprint» (21 de octubre), y
> no tuvo brief. Este archivo es un resumen breve para quien lea los briefs en orden. El detalle completo, con las
> desviaciones y los resultados de la verificación, está en la sección **«Tanda 1 · Implementación»** de
> `docs/arquitectura/sprint-9-asistencia-comunicados.md`.

## Alcance
- Asignación de docentes a secciones (tutor, docente de área y auxiliar), propuesta por Dirección o Administración y
  aprobada por otra persona de Dirección en la bandeja (`ASIGNACION_DOCENTE`); terminarla, con la firma de Promotoría o
  Dirección.
- El portal del docente: solo sus secciones con asignación VIGENTE y los nombres de sus alumnos, nunca montos ni deudas.
- Nada de asistencia ni comunicados (tandas 2 y 3).

## Base
- Migración `V32__asignaciones_docentes.sql`: `asignacion_docente` y los tipos nuevos de `acceso_dato_personal`.
- `02`: INSERT y UPDATE por columna de `asignacion_docente` (sin DELETE).
- `03`: `cc_docente_de_seccion`, `trg_asignacion_docente_nace`, `trg_asignacion_docente_estado` y la versión nueva de
  `trg_usuario_rol_alta` (DOCENTE no se combina con CAJA ni ADMINISTRACION). **88 triggers y 99 huellas.**

## Pruebas obligatorias (las que se hicieron)
- H2: asignaciones, IDOR de las rutas del docente, el docente sin montos (`DocenteSinCobranzaTest`, `DtoDocenteSinDatosTest`)
  y las reglas de arquitectura.
- MySQL real: `AuditoriaSprint9MySqlTest` (E3, E4, E43 y E46) y el flujo de asignaciones de `PermisosMySqlTest`.

## Riesgos que quedaron
- Un director que también enseña no aprueba su propia asignación (es «involucrado»: `AutoaprobacionSolicitudException`).
- Las asignaciones del año siguiente se copian con «Proponer las del año anterior», una solicitud por cada una.
- Los riesgos completos están en la sección «Tanda 1 · Implementación» del diseño.
