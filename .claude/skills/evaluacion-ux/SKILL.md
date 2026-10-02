---
name: evaluacion-ux
description: Métodos y plantillas de experiencia de usuario para el proyecto, como guiones de entrevista con el colegio, plantilla de persona, journey map, evaluación heurística de Nielsen, plan de prueba de usabilidad con SUS y métricas de adopción. Úsala al investigar usuarios, evaluar una pantalla o planificar pruebas con usuarios reales.
---

# Evaluación y research UX

## 1. Guiones de entrevista (descubrimiento)
Duran entre 30 y 45 minutos y se hacen una a una. Pregunta por hechos pasados, no por opiniones sobre el futuro.

**Director o promotor**
- Cuénteme cómo fue el último mes de cobranza, de principio a fin.
- ¿Cómo se entera hoy de cuánto dinero entró? ¿Cada cuánto?
- ¿Qué información le hubiera permitido detectar antes lo que pasó?
- ¿Quién aprueba hoy los descuentos o las excepciones? ¿Cómo queda registro?
- ¿Qué reportes le pide la UGEL o el contador?

**Cajera o secretaria**
- Muéstreme cómo registra un pago hoy (observar, no solo preguntar).
- ¿Qué hace cuando un padre dice "yo ya pagué"?
- ¿Qué es lo más tedioso de su día? ¿Qué le genera errores?

**Docente**
- ¿Cómo registra hoy la asistencia y las notas? ¿Cuánto tiempo le toma por bimestre?
- ¿Cómo se comunica con los padres?

**Padre o apoderado**
- ¿Cómo paga hoy la pensión? ¿Qué le incomoda?
- ¿Usa Yape, Plin o banca por internet? ¿Desde el celular o la computadora?
- ¿Cómo se entera de las notas y de los comunicados del colegio?

## 2. Plantilla de persona
```
Nombre ficticio · Rol · Edad aproximada
Nivel digital: bajo / medio / alto · Dispositivo principal
Objetivos (qué quiere lograr)
Frustraciones actuales
Contexto de uso (dónde, cuándo, con qué interrupciones)
Cita textual de la entrevista
Estado: VALIDADA (con N entrevistas) / HIPÓTESIS
```

## 3. Journey map
Columnas: **Etapa → Acción → Punto de contacto → Pensamiento o emoción → Dolor → Oportunidad**.
Recorridos prioritarios:
1. El padre paga la pensión del mes.
2. La cajera atiende a un padre y cierra caja.
3. El director revisa los ingresos y aprueba una anulación.
4. El docente registra las notas del bimestre.

## 4. Evaluación heurística (Nielsen)
Por cada pantalla, revisa las 10 heurísticas:
1. Visibilidad del estado del sistema
2. Relación con el mundo real (lenguaje del colegio)
3. Control y libertad (deshacer, cancelar)
4. Consistencia y estándares
5. Prevención de errores
6. Reconocer antes que recordar
7. Flexibilidad y eficiencia (atajos para la cajera)
8. Diseño estético y minimalista
9. Ayudar a reconocer, diagnosticar y recuperarse de errores
10. Ayuda y documentación

Formato de hallazgo: `Pantalla · Heurística · Problema · Severidad (0 a 4) · Recomendación`.
Escala de severidad: 0 = no es problema, 1 = cosmético, 2 = menor, 3 = mayor, 4 = catastrófico (bloquea la tarea o permite fraude).

## 5. Plan de prueba de usabilidad
- **Participantes**: 5 por perfil (bastan para encontrar la mayoría de los problemas).
- **Tareas** (ejemplos):
  - Cajera: "Registra el pago en efectivo de la pensión de abril de Juan Pérez".
  - Padre: "Revisa cuánto debes y paga la cuota vencida con Yape".
  - Director: "Revisa si la caja de ayer cuadró y aprueba la anulación pendiente".
- **Métricas**: tasa de éxito, tiempo por tarea, número de errores y **SUS** (System Usability Scale, 10 preguntas). Meta: SUS ≥ 70.
- **Guion**: bienvenida, "estamos probando el sistema, no a usted", pensar en voz alta, tareas sin ayuda y preguntas finales.

## 6. Métricas de adopción y de impacto
| Métrica | Meta inicial |
|---|---|
| % de pagos digitales (no efectivo) | > 60 % al 3.er mes |
| % de padres que activaron el portal | > 70 % |
| Cierres de caja sin diferencia | 100 % (las diferencias se explican el mismo día) |
| Morosidad | Bajar 20 % frente al año anterior |
| Tiempo para registrar un pago presencial | < 1 minuto |
| Docentes que registran notas en el sistema | 100 % al primer bimestre |

## 7. Microcopy: ejemplos
| Situación | ❌ Evitar | ✅ Usar |
|---|---|---|
| Pago registrado | "Transacción exitosa" | "Pago registrado. Enviamos la boleta B001-123 al correo y WhatsApp de la familia." |
| Error de validación | "Campo inválido" | "Ingresa un monto mayor a S/ 0.00" |
| Anular pago | "¿Está seguro?" | "Vas a solicitar anular el pago de S/ 450.00. El director debe aprobarlo. Escribe el motivo:" |
| Lista vacía | "No hay datos" | "Esta familia no tiene cuotas pendientes. ¡Está al día!" |
