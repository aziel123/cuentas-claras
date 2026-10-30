# Contrato de encargo de tratamiento de datos personales (Ley 29733)

> **BORRADOR · A REVISAR POR UN ABOGADO antes de firmar el primer contrato** (decisión 137). Esquema de la sección 13.2 de
> `docs/arquitectura/sprint-8-lista-para-vender.md`, según la Ley 29733 de Protección de Datos Personales y su reglamento
> (DS 016-2024-JUS). **Los plazos de la ley que aparecen aquí salen de fuentes secundarias, no del texto oficial: los
> confirma el abogado.** Es el anexo A de los términos de servicio (`terminos-de-servicio.md`).

## 1. Partes, objeto y duración
- **Responsable del tratamiento:** el colegio [razón social, RUC], representado por [nombre, DNI].
- **Encargado del tratamiento:** el proveedor [razón social, RUC], representado por [nombre, DNI].
- **Objeto:** el encargado trata los datos personales que el colegio registra en la plataforma **solo para prestarle el
  servicio** contratado. **Nunca para fines propios:** ni publicidad, ni perfiles, ni venta, ni cesión, ni entrenamiento de
  modelos, ni estadísticas fuera del servicio.
- **Duración:** la de los términos de servicio, más el plazo de conservación bloqueada de la cláusula 9.

## 2. Datos y titulares
| Titulares | Datos | Finalidad |
|---|---|---|
| **Alumnos (menores de edad)** | Nombres, documento (DNI, CE o pasaporte), fecha de nacimiento, grado y sección, matrícula, deudas, pagos y comprobantes | Matrícula y cobranza del colegio |
| **Apoderados** | Nombres, documento, parentesco, celular, correo, pagos, comprobantes, avisos recibidos y su consentimiento | Avisos de pago, estado de cuenta, portal de las familias |
| **Personal del colegio** | Nombres, usuario, rol, celular, correo, registros de acceso y de las operaciones que hace | Seguridad, segregación de funciones y bitácora |
| Más adelante, si se contratan | Asistencia, notas y comunicados | Módulos académico y de comunicación |

No se tratan datos sensibles (salud, religión, origen) y la plataforma no los pide. Los registros (logs) del sistema no
guardan datos personales en claro (se enmascaran).

## 3. Instrucciones del responsable
El encargado trata los datos según las **instrucciones del colegio**, que son las que da por las pantallas y la
configuración de la plataforma (quién tiene qué rol, qué se importa, qué se anula, a quién se avisa). Cualquier
instrucción fuera de eso, **por escrito**. El encargado avisa si una instrucción le parece contraria a la ley.

## 4. Medidas de seguridad
Las de los sprints 1 a 8 de la plataforma, descritas en el **documento de seguridad** del sprint 7 (anexo), entre ellas:
- cifrado en tránsito (https y TLS a la base de datos) y respaldos cifrados con dos claves, fuera del proveedor de nube, con
  bloqueo de objetos y restauración de prueba;
- acceso mínimo por rol y segregación de funciones (quien cobra no aprueba); sesión única y de duración máxima;
- **aislamiento entre colegios** en una instalación compartida (cada consulta filtra por el colegio y la base lo vuelve a
  exigir);
- **el operador de la plataforma no ve los datos de las familias** (solo la ficha del colegio y una lista de sí/no y conteos);
- bitácora inmutable de cada operación, con registro de **quién vio datos personales**;
- los secretos de los conectores del colegio (OSE, pasarela) cifrados y que nadie vuelve a ver;
- el entorno de demostración usa solo datos ficticios y no admite importar listas reales.

## 5. Personal del encargado
- Todo el personal del encargado con acceso técnico firma un **compromiso de confidencialidad**, que sigue vigente después de
  su salida.
- Lista de quién tiene acceso técnico a la base de datos y a los respaldos (los **2 custodios** de la plataforma y el
  administrador de la base): [nombres y cargos]. El encargado la actualiza y avisa al colegio si cambia.
- Ese acceso queda registrado (registros de acceso del proveedor de nube) y se revisa [trimestralmente].

## 6. Subencargados y flujo transfronterizo (anexo B)
| Subencargado | Para qué | Dónde están los datos |
|---|---|---|
| Google Cloud (Google LLC) | Servidores y base de datos de la plataforma | Santiago de Chile (`southamerica-west1`) |
| Meta Platforms (WhatsApp Business) | Envío de los avisos por WhatsApp | Estados Unidos y otros |
| [Proveedor de correo] | Envío de los avisos por correo | [país] |
| [Cloudflare R2 o Backblaze B2] | Respaldos cifrados (el proveedor no tiene la clave para leerlos) | [país] |
| GitHub (Microsoft) | Código de la plataforma (sin datos personales) | Estados Unidos |

- Hay **flujo transfronterizo** a Chile y a Estados Unidos (decisión 136): el colegio lo declara ante la Autoridad Nacional
  de Protección de Datos Personales y lo informa a las familias en su aviso de privacidad (la plataforma muestra al
  proveedor como encargado).
- El encargado **avisa al colegio con [30 días]** antes de agregar o cambiar un subencargado; el colegio puede oponerse por
  escrito.
- **No son subencargados del proveedor** el OSE, la pasarela de pagos ni el banco: el colegio los contrata a su nombre.

## 7. Incidentes de seguridad
- El encargado **avisa al colegio en 24 horas** desde que conoce un incidente que afecte datos personales, con lo que sabe en
  ese momento (qué datos, de cuántas personas, desde cuándo, qué se hizo), usando la plantilla
  `docs/operacion/incidente-datos-personales.md`.
- Así el colegio puede notificar a la Autoridad en su plazo (**48 horas** según las fuentes consultadas; **a confirmar por
  el abogado**) y avisar a las familias si corresponde.
- El encargado conserva la evidencia y colabora con la investigación.

## 8. Derechos de los titulares (ARCO)
El encargado ayuda al colegio a atender los pedidos de acceso, rectificación, cancelación y oposición con las herramientas
que ya tiene la plataforma («Mis datos» de las familias, los pedidos con su plazo y el reporte de datos con plazo vencido).
Si un titular le escribe directamente al encargado, este lo deriva al colegio en [2 días hábiles].

## 9. Fin del encargo
- **Devolución (sprint 11; para revisión del abogado):** exportación completa de los datos (CSV por tabla y PDF de libretas y
  comunicados, en un ZIP cifrado con AES-256 y con su manifiesto de huellas), entregada al titular por dos canales (enlace
  por correo, código por WhatsApp) el día de la suspensión y disponible 30 días.
- **Conservación:** 12 meses desde la suspensión aplicada con la autorización expresa del titular; si no, 30 días después
  del cierre (baja: 30 días). Durante ese plazo las familias solo consultan; después, nadie entra. El encargado no conserva
  los datos para fines tributarios: los conserva el responsable con su exportación.
- **Retención legal:** con un pedido escrito de una autoridad o un litigio, el encargado conserva hasta que termine.
- **Supresión** al vencer el plazo, con un **acta** que aprueban dos operadores y que se envía al responsable: se borran los
  datos y se anonimizan sus eventos de la bitácora (común a los clientes). Los respaldos vencen a más tardar 24 meses
  después del inicio del plazo; al restaurar uno, la supresión se reaplica antes de abrir la aplicación.

## 10. Auditoría
Una vez al año, o después de un incidente, el colegio puede pedir la **evidencia de los controles**: el informe del último
simulacro de restauración, las auditorías de seguridad de los sprints, el CI en verde de la versión en producción y, en una
instalación compartida, el acta de la última verificación trimestral de su bitácora
(`docs/operacion/acta-verificacion-trimestral.md`).

Firmas: [responsable] · [encargado] · Lugar y fecha: [Lima, …]
