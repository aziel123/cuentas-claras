# Entrega de Cuentas Claras al colegio

Sprint 7 (sección 15 de `docs/arquitectura/sprint-7-endurecimiento.md`). Aquí está todo lo que se entrega al Colegio Virgen María junto con la plataforma, y qué es requisito para firmar el acta de conformidad.

| Documento | Para qué sirve | ¿Requisito del acta? |
|---|---|---|
| [Manuales de una página por rol](../manuales/README.md) | Promotoría, Dirección, Administración, Caja, Docentes, Familias (también volante) y responsable técnico | **Sí**: impresos (anexo F) |
| [Guion de los videos](guion-videos.md) | Nueve videos de 60 a 120 segundos, sin listar | **Sí, los videos 1, 2, 4 y 7** publicados (anexo F). Los videos 3, 5, 6, 8 y 9, hasta 2 semanas después |
| [Plan de capacitación](capacitacion.md) | Sesiones S1 a S6, preguntas de verificación, lista de asistencia, seguimiento de 2 semanas y métricas de adopción | **Sí**: capacitación hecha y lista de asistencia firmada (anexo E). S6 (familias) y los docentes pueden ser posteriores |
| [Acta de conformidad](acta-de-conformidad.md) | La plantilla que firman Promotoría, Dirección y el responsable técnico | Es el acta |
| [Acta del simulacro de restauración](../operacion/acta-simulacro-restauracion.md) | Se llena en cada simulacro presencial mensual | **Sí**: el primer simulacro presencial, firmado (anexo D) |
| [Incidente de datos personales](../operacion/incidente-datos-personales.md) | Qué hacer si se exponen datos personales, con la notificación a la Autoridad en 48 horas (a confirmar por el asesor legal) | Se entrega; no hace falta llenarla |

## Sprint 8 · documentos para vender la plataforma a otros colegios
| Documento | Para qué sirve | Estado |
|---|---|---|
| [Términos de servicio](terminos-de-servicio.md) | Contrato entre el proveedor y cada colegio | **Borrador: a revisar por un abogado** antes del primer contrato |
| [Contrato de encargo de tratamiento de datos](contrato-encargo-tratamiento-datos.md) | Ley 29733: el colegio es el responsable y el proveedor, el encargado (anexo A de los términos) | **Borrador: a revisar por un abogado** |
| [Plantillas de WhatsApp para Meta](plantillas-whatsapp-meta.md) | Las 26 plantillas `_v2` (con el nombre del colegio como variable; 5 nuevas en las correcciones del sprint 8), listas para enviar a Meta | Listas; el nombre comercial sigue siendo provisional (no va en las plantillas) |
| [Suscripción](../operacion/suscripcion.md) | Cómo se cobra a cada colegio: fuera del sistema | Decisión 128 |
| [Alta de un colegio](../operacion/alta-de-un-colegio.md) · [Guía de la demo](../ventas/guia-demo.md) | Para el operador de la plataforma y para ventas | — |

## Lo que pide el acta para firmar (resumen)
- [ ] CI en verde de la versión entregada, con los jobs `mysql` y `respaldo` (anexo B).
- [ ] Auditoría del sprint 7 **sin hallazgos críticos ni altos abiertos** (anexo C).
- [ ] Primer simulacro presencial de restauración con un respaldo real, con su acta firmada (anexo D).
- [ ] Capacitación hecha (S1 a S5) y lista de asistencia firmada (anexo E).
- [ ] Manuales impresos y videos 1, 2, 4 y 7 publicados sin listar (anexo F).
- [ ] Informe del último respaldo y del último simulacro automático (anexo G).
- [ ] Riesgos residuales explicados y marcados «Entendido», decisiones 82 a 107 con lo que eligió el colegio, y pendientes con fecha y responsable.

## Documentos de operación relacionados
[respaldos.md](../operacion/respaldos.md) · [monitoreo.md](../operacion/monitoreo.md) · [mysql-usuarios.md](../operacion/mysql-usuarios.md) · [custodia-clave-auditoria.md](../operacion/custodia-clave-auditoria.md) · [incidente-auditoria.md](../operacion/incidente-auditoria.md) · [cierre-de-caja.md](../operacion/cierre-de-caja.md)

**Todo plazo de la Ley 29733** que aparece en estos documentos (20 y 10 días hábiles, 48 horas y los de conservación) está marcado «a confirmar por el asesor legal»: salen de fuentes secundarias, no del texto oficial del DS 016-2024-JUS.
