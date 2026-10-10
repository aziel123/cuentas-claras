# Incidente de datos personales

> Plantilla (sprint 7, tanda 3; sección 8.5 de `docs/arquitectura/sprint-7-endurecimiento.md`). Para cuando datos personales de las familias, de los alumnos o del personal se exponen, se pierden o los usa quien no debía. Ley 29733 de Protección de Datos Personales y su reglamento (DS 016-2024-JUS).
>
> **Plazo de notificación a la Autoridad Nacional de Protección de Datos Personales: 48 horas desde que el colegio conoce el incidente, a confirmar por el asesor legal.** El plazo sale de fuentes secundarias (resúmenes de estudios jurídicos), no del texto oficial del DS 016-2024-JUS. Hasta que el asesor lo confirme, trabaja con 48 horas.
>
> **Quién responde:** Dirección (decisión 101), salvo que el colegio designe a otra persona: ______________________.

## 1. Qué cuenta como incidente (ejemplos)
- Una lista de familias (morosos, contactos, Excel) salió del colegio o se envió a quien no correspondía.
- Una cuenta del personal o de una familia la usó otra persona (clave compartida, celular perdido con la sesión abierta).
- Alerta de que **una persona vio más de 50 fichas en un día** y no tiene una razón de trabajo.
- Un respaldo, una copia restaurada o un log con datos personales quedó fuera de su lugar (otra computadora, un USB, un correo).
- Acceso no autorizado al servidor, a la base de datos o al almacenamiento de respaldos.
- Un aviso a una familia llegó a un celular o correo equivocado.

Si además hay dinero o la bitácora de por medio, sigue **también** [incidente-auditoria.md](incidente-auditoria.md). Si se perdió o alteró la base, sigue la sección «Desastre» de [respaldos.md](respaldos.md).

## 2. Las primeras horas
1. **Anota la fecha y hora en que el colegio se enteró.** Desde ahí cuentan las 48 horas (a confirmar por el asesor legal).
2. **Avisa** a quien responde por los datos personales, a Promotoría y al responsable técnico. Si se sospecha de alguien del personal, no le avises hasta preservar la evidencia.
3. **Contén sin borrar nada:**
   - cuenta comprometida: cambia la clave (cierra todas sus sesiones) o desactiva la cuenta;
   - clave técnica expuesta: rótala (manual del operador, «Rotación de claves»);
   - archivo enviado por error: pide por escrito a quien lo recibió que lo elimine y lo confirme.
4. **Preserva la evidencia** (como en la sección 3 de [incidente-auditoria.md](incidente-auditoria.md)): el registro de accesos a datos personales del periodo, la bitácora, la ficha de quién consultó, los mensajes enviados y **los logs del servidor antes de que roten (se guardan 30 días)**. Calcula la huella (`sha256sum`) de cada copia y guárdala fuera del servidor.
5. **Llama al asesor legal** para confirmar si corresponde notificar y en qué plazo.

## 3. Ficha del incidente (completar)
| Campo | Valor |
|---|---|
| N.° de incidente | |
| Fecha y hora en que el colegio se enteró | |
| Quién lo detectó y cómo (alerta, familia, personal, responsable técnico) | |
| Fecha y hora estimada en que ocurrió | |
| **Qué pasó** (en palabras simples) | |
| Banco de datos afectado | ☐ Alumnos y familias ☐ Personal |
| **Qué datos** | ☐ Nombres y apellidos ☐ Documento de identidad ☐ Fecha de nacimiento ☐ Grado y sección ☐ Celular ☐ Correo ☐ Parentesco ☐ RUC o razón social ☐ Pagos, cuotas o deudas ☐ Mensajes enviados ☐ Claves (cifradas) ☐ Otros: ______ |
| ¿Incluye datos de menores de edad? | ☐ Sí ☐ No |
| **A cuántas familias afecta** | ____ familias · ____ alumnos · ____ personas del personal (la lista va en un anexo que no se publica) |
| Riesgo para las familias | ☐ Bajo ☐ Medio ☐ Alto · Por qué: |
| **Qué se hizo** (contención, con fecha y hora) | |
| Evidencia preservada (archivos y huellas) | |
| **Quién responde** | |

## 4. Notificación a la Autoridad Nacional de Protección de Datos Personales
- **Plazo:** 48 horas desde que el colegio se enteró (**a confirmar por el asesor legal**).
- **Quién la envía:** quien responde por los datos personales, con el visto bueno del asesor legal.
- **Canal y formato:** los que indique la Autoridad; los confirma el asesor legal.
- **Contenido:** la ficha de la sección 3 (sin la lista de familias, salvo que la Autoridad la pida).

| Campo | Valor |
|---|---|
| Fecha y hora de envío | |
| Canal | |
| N.° de constancia o expediente | |
| Quién la envió | |
| Respuesta o requerimientos de la Autoridad | |

## 5. Aviso a las familias (cuando corresponda)
- **Cuándo:** si el incidente puede afectar a las familias (por ejemplo, si se expusieron sus celulares o los datos de sus hijos). Lo decide quien responde con el asesor legal.
- **Cómo:** por el celular o correo registrado de cada familia afectada y, si son muchas, también en una reunión. El aviso **no repite** los datos expuestos.
- **Texto modelo** (ajustar con el asesor legal):

> Hola, te escribe el Colegio Virgen María. El ____/____ detectamos que [qué pasó, en una frase]. Los datos involucrados son [cuáles]; no incluye [lo que no se expuso, por ejemplo tu clave]. Ya [qué hicimos]. Te recomendamos [qué hacer]. Recuerda: el colegio nunca te pedirá tu clave por teléfono ni por WhatsApp. Si tienes preguntas, escríbenos desde «¿Algo no cuadra?» en tu portal o comunícate con Dirección al ______.

| Campo | Valor |
|---|---|
| ¿Corresponde avisar? Por qué | |
| Fecha y hora del aviso | |
| Canal | |
| Familias avisadas | |

## 6. Cierre
| Campo | Valor |
|---|---|
| Causa | |
| Qué se cambió para que no se repita | |
| Cambios al documento de seguridad o a los manuales | |
| Fecha de cierre | |

| Quien responde por los datos personales | Promotoría | Responsable técnico |
|---|---|---|
| Firma y fecha: | Firma y fecha: | Firma y fecha: |

La ficha firmada **no se sube al repositorio** (lleva datos de las familias): se guarda con las actas del colegio.
