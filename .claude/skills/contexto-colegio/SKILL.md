---
name: contexto-colegio
description: Conocimiento del dominio del sistema de gestión escolar para colegios privados del Perú. Incluye actores, módulos, glosario, reglas antifraude obligatorias de cobranza y normativa (SUNAT, SIAGIE, INDECOPI, Ley 29733). Cárgala antes de diseñar, implementar, probar o revisar cualquier funcionalidad del sistema.
---

# Contexto del dominio: gestión escolar

## Cliente
**Colegio Virgen María** (I.E.P. privada, Perú). La plataforma se llama **Cuentas Claras** y lleva la marca del colegio (azul y celeste).

## El problema que resolvemos
Un colegio privado peruano sin herramientas informáticas perdió **más de S/ 70,000**. Una auxiliar era la única encargada de cobrar pensiones: recibía el efectivo, lo registraba (o no) y lo custodiaba sin que nadie más lo viera. El sistema debe hacer que eso **no pueda volver a pasar sin detectarse el mismo día**.

## Actores
| Rol | Qué hace | Dispositivo principal |
|---|---|---|
| **Promotor o dueño** | Ve los ingresos, la morosidad y las alertas. Aprueba excepciones grandes. | Celular |
| **Director** | Aprueba anulaciones, descuentos y becas. Gestiona lo académico. | Escritorio y celular |
| **Administrador o contador** | Concilia banco y caja, configura pensiones y saca reportes. | Escritorio |
| **Cajero o secretaria** | Registra pagos presenciales y atiende a los padres. | Escritorio |
| **Docente** | Registra asistencia y notas. Envía comunicados a su sección. | Celular y escritorio |
| **Padre o apoderado** | Ve el estado de cuenta, paga, recibe comprobantes, notas y comunicados. | **Celular** |
| **Alumno** | Ve sus notas y tareas (en fases posteriores). | Celular |

## Módulos (orden de construcción)
1. **Cobranza** (MVP): alumnos, apoderados, matrícula, cronograma de cuotas, pagos, comprobantes, cierre de caja, conciliación, aprobaciones, auditoría y panel del dueño.
2. **Académico**: grados, secciones, cursos, asistencia, notas por competencias (Currículo Nacional) y exportación a SIAGIE.
3. **Comunicación**: comunicados, agenda y citaciones con confirmación de lectura.
4. **Aula virtual**: **no se construye**. Se integra Google Workspace for Education o Microsoft 365 A1 (ambos gratuitos).

## Glosario
- **Pensión**: cuota mensual de enseñanza (normalmente de marzo a diciembre, 10 cuotas).
- **Matrícula**: pago anual de inscripción.
- **Cuota**: obligación de pago con monto, fecha de vencimiento y estado (`PENDIENTE`, `PAGADA`, `PARCIAL`, `VENCIDA`, `ANULADA`).
- **Pago**: dinero recibido. Se aplica a una o más cuotas. Tiene medio de pago (`EFECTIVO`, `YAPE`, `PLIN`, `TRANSFERENCIA`, `RECAUDACION_BANCARIA`, `TARJETA`).
- **Comprobante**: boleta o factura electrónica emitida a SUNAT vía OSE/PSE.
- **Cierre de caja**: corte diario por cajero que compara el efectivo esperado contra el contado.
- **Conciliación**: cruce de los pagos registrados contra los movimientos del banco.
- **Mora**: interés por pago tardío.
- **SIAGIE**: sistema del MINEDU donde los colegios registran matrícula y notas oficiales.

## Reglas antifraude OBLIGATORIAS
Todo diseño, código o revisión debe cumplirlas:

1. **Segregación de funciones**: quien cobra no aprueba anulaciones, descuentos ni su propio cierre de caja.
2. **Deuda generada por el sistema**: las cuotas se generan automáticamente desde la configuración de pensiones. El cajero no decide montos.
3. **Ningún pago sin comprobante**: todo pago registrado emite comprobante electrónico en el momento.
4. **Aviso inmediato al apoderado**: todo pago dispara una notificación (WhatsApp o correo) con monto, concepto y número de comprobante. El padre funciona como auditor.
5. **Inmutabilidad**: los pagos y las cuotas no se borran ni se editan. Solo se **anulan**, con motivo obligatorio y aprobación de un rol superior, y queda registro.
6. **Descuentos y becas con aprobación**: los registra administración y los aprueba el director. Quedan en auditoría.
7. **Cierre de caja diario obligatorio**: compara lo esperado (pagos en efectivo registrados), lo contado y lo depositado. Cualquier diferencia genera una alerta al dueño.
8. **Auditoría inmutable**: cada operación financiera registra quién, qué, cuándo, desde dónde, el valor anterior y el nuevo. La tabla solo admite inserciones.
9. **Preferir pagos digitales**: el diseño empuja hacia la recaudación bancaria, Yape/Plin empresarial o pasarela. El efectivo es la excepción.
10. **Transparencia al padre**: el portal muestra el estado de cuenta completo, de modo que cualquier deuda falsa o pago faltante sale a la luz.

## Reglas técnicas transversales
- Dinero en `BigDecimal` con escala 2 y moneda PEN. Formato visual `S/ 1,250.00`.
- Multi-colegio: toda entidad de negocio tiene `colegioId` y toda consulta filtra por él.
- Zona horaria `America/Lima`.
- Documento de identidad: DNI (8 dígitos), CE o pasaporte.

## Normativa a respetar
- **SUNAT**: facturación electrónica obligatoria (boleta para personas naturales, factura con RUC).
- **Código de Protección y Defensa del Consumidor (Ley 29571) e INDECOPI**: el colegio **no puede condicionar las evaluaciones al pago** de pensiones. El sistema no debe bloquear la toma de exámenes ni las notas por deuda. El cobro de intereses moratorios tiene límites legales; antes de configurar moras, debe validarlo un asesor legal.
- **Ley 29733 de Protección de Datos Personales**: los datos de menores se tratan con acceso mínimo por rol, con consentimiento y sin datos sensibles en logs.
- **MINEDU y SIAGIE**: las notas oficiales se registran en SIAGIE. El sistema facilita la exportación, no lo reemplaza.
