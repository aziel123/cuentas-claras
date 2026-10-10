# Acta de conformidad · Cuentas Claras

> Plantilla (sección 15.4 de `docs/arquitectura/sprint-7-endurecimiento.md`). Se imprime, se completa a mano o en el archivo y se firma al terminar el sprint 7. Los espacios con «______» se completan el día de la firma. Una copia firmada queda en el colegio y otra con el responsable técnico; **no se sube firmada al repositorio** (lleva nombres y firmas).

## 1. Partes, fecha y lugar
- **El colegio:** Colegio Virgen María, representado por
  - Promotoría: ______________________________________ DNI: ____________
  - Dirección: ______________________________________ DNI: ____________
- **El responsable técnico de la plataforma:** ______________________________________ DNI: ____________
- **Fecha:** ____/____/______ · **Hora:** ______ · **Lugar:** ______________________________________

## 2. Alcance entregado
- **Hitos del plan de desarrollo:** H1 ☐ · H2 ☐ · H3 ☐ (`docs/plan-de-desarrollo.md`).
- **Funciones entregadas:** las de `docs/estado-del-proyecto.md` a la fecha de esta acta (se adjunta impresa como anexo A).
- **Versión entregada:**
  - Etiqueta de Git: ______________________
  - SHA del commit: ________________________________________
  - Versión del esquema de la base (última migración): V______
  - Versión que muestra *Estado técnico y respaldos*: ______________________

## 3. Evidencias (anexos)
| Anexo | Evidencia | Referencia (enlace, archivo o fecha) | Adjunto |
|---|---|---|---|
| A | `docs/estado-del-proyecto.md` impreso a la fecha | | ☐ |
| B | El CI en verde de la versión entregada, con los jobs `mysql` y `respaldo` | | ☐ |
| C | El informe de la auditoría del sprint 7 **sin hallazgos críticos ni altos abiertos** | | ☐ |
| D | El acta del **primer simulacro presencial de restauración**, firmada (`docs/operacion/acta-simulacro-restauracion.md`) | | ☐ |
| E | La lista de asistencia de la capacitación, firmada (`docs/entrega/capacitacion.md`, sección 5) | | ☐ |
| F | Los manuales impresos (`docs/manuales/`) y los enlaces de los videos 1, 2, 4 y 7 (`docs/entrega/guion-videos.md`) | | ☐ |
| G | El informe del último respaldo y del último simulacro automático (`restauracion-AAAAMMDD-HHMMSS.json`) | | ☐ |

## 4. Riesgos residuales aceptados
El colegio declara que se le explicó cada riesgo y lo entiende. Marcar cada casilla.

**Del sprint 7 (sección 17 del diseño)**
| # | Riesgo | Qué lo detecta o lo limita | Entendido |
|---|---|---|---|
| 1 | Si quedó algún camino de escritura sin pasar por la conexión correcta de la base, esa función **se detiene** hasta corregirla | Falla cerrada: no deja pasar una operación sin control; las pruebas con permisos mínimos | ☐ |
| 2 | Quien toma el **servidor de la aplicación** tiene sus claves, la clave de la bitácora y las sesiones abiertas | No lo evita: lo hace visible (huella diaria a Promotoría, respaldos que no se pueden alterar, avisos a las familias, llamada de control) y lo limita (el servidor no puede borrar ni leer los respaldos) | ☐ |
| 3 | Un **administrador de la base** con todos los privilegios puede cambiar triggers, funciones y filas | Simulacro semanal, anclas y conteos de los respaldos, y la clave de la bitácora en manos de Promotoría | ☐ |
| 4 | Lo que una persona hace por sí misma (un cobro, un pedido de anulación, subir un extracto) **no lleva firma de sesión**: con la clave de la aplicación se podría registrar una fila a nombre de otra persona, sin aprobar nada | Aviso a la familia, cierre a ciegas, conciliación y bitácora | ☐ |
| 5 | **Robo de la sesión del navegador:** quien la tenga aprueba como esa persona mientras siga abierta. No hay segundo factor (decisión 86) | Cookie protegida, 30 minutos de inactividad, 10 horas como máximo, una sesión por persona y «quien pide no aprueba» | ☐ |
| 6 | La aplicación puede leer las claves de las cuentas **cifradas** (no las claves en sí) | Cifrado fuerte: habría que descifrarlas fuera del sistema | ☐ |
| 7 | La muestra de la llamada de control se puede leer con la clave de la aplicación desde el lunes 00:10, igual que la ve Promotoría | Ya no se puede elegir ni predecir antes | ☐ |
| 8 | Se pueden perder hasta **24 horas de datos** (respaldo diario); un borrado indebido se detecta en el respaldo siguiente, no al instante | En el día: la huella por hora y el resumen de las 19:30; los pagos de ese tramo se reconstruyen con boletas y banco | ☐ |
| 9 | El **vigilante externo se apaga** si el repositorio pasa 60 días sin actividad | Aviso una semana antes y el manual del operador; alternativa de la decisión 94 | ☐ |
| 10 | El **simulacro semanal** corre en la máquina del responsable técnico: si está apagada, no corre. Los datos se descifran en esa máquina | El simulacro mensual presencial; disco cifrado y copia que se borra al terminar | ☐ |
| 11 | La plataforma funciona en **un solo servidor**: con dos, hay que rehacer los límites, las sesiones y las alertas en memoria | Decisión técnica a revisar si crece el colegio | ☐ |
| 12 | **Ley 29733:** los plazos salen de fuentes secundarias; la anonimización de contactos es manual; la bitácora y los mensajes guardan nombres que no se pueden borrar sin romper la cadena (se conservan por obligación legal y así se responde a un pedido de cancelación) | Confirmación del asesor legal (pendiente del punto 6) | ☐ |

**De sprints anteriores, que el sprint 7 no cierra**
| # | Riesgo | Qué lo detecta o lo limita | Entendido |
|---|---|---|---|
| 13 | Extracto de varios días, y un abono y un cargo de montos distintos que se compensan | Dependen del formato real del banco; conviene subir el extracto a diario | ☐ |
| 14 | Una cuenta de familia activada desde otra conexión | La familia no puede entrar y avisa; restablecimiento por Promotoría | ☐ |
| 15 | **Colusión** entre quien sube y quien confirma el extracto o la recaudación, o entre Promotoría y Dirección para los días no laborables | Bitácora, archivo original con su huella, estado de cuenta oficial y cierre mensual a ciegas | ☐ |
| 16 | Quien controla el celular o correo de una familia lo puede verificar; alias de correo de otros proveedores | Llamada de control | ☐ |
| 17 | La llamada de control es un **muestreo** (pocas familias por semana) | Avisos a todas las familias y «¿Algo no cuadra?» | ☐ |
| 18 | El aviso inmediato de un cierre con diferencia se pierde si falla su envío | El operador lo ve como error y lo cubre el resumen del día siguiente | ☐ |
| 19 | **Proveedores reales pendientes** (comprobante electrónico, pasarela de pagos, banco): el comprobante simulado no tiene valor tributario | El colegio sigue emitiendo su comprobante legal hasta conectar el proveedor | ☐ |

**Nuevos de la implementación de las tandas 1 y 2**
| # | Riesgo | Qué lo detecta o lo limita | Entendido |
|---|---|---|---|
| 20 | Un destino de respaldo mal configurado en el servidor (una carpeta local) se trataría como real | La configuración del servidor y el manual del operador | ☐ |
| 21 | Una fila insertada y borrada entre dos respaldos no deja rastro en los conteos | La bitácora, si era una operación financiera | ☐ |
| 22 | Quien accede al servidor de la aplicación ve las claves de las dos conexiones; la firma de una aprobación vale 5 minutos; el registro general de MySQL debe estar apagado | Separación de credenciales y verificación al arrancar | ☐ |

**Nuevos de la tanda 3 y de la auditoría final** (completar el día de la firma)
| # | Riesgo | Qué lo detecta o lo limita | Entendido |
|---|---|---|---|
| 23 | | | ☐ |
| 24 | | | ☐ |

## 5. Decisiones tomadas
Valor por defecto entre corchetes (sección 18 del diseño). Si el colegio eligió el valor por defecto, se escribe «Por defecto». Las decisiones 1 a 81 constan en `docs/estado-del-proyecto.md` (anexo A).

| # | Tema | Por defecto | Lo que eligió el colegio | Fecha |
|---|---|---|---|---|
| 82 | Usuario de base de datos para los procesos del sistema | [Uno, `cc_sistema`, para los procesos y la identidad] | | |
| 83 | Duración máxima de una sesión | [10 horas aunque haya actividad y 30 minutos de inactividad] | | |
| 84 | Roles que asigna Promotoría sin aprobación | [Administración, Caja y Docente. Promotoría y Dirección, solo con solicitud aprobada por otra persona] | | |
| 85 | Alta de una cuenta de Promotoría o Dirección | [Nace sin ese rol; se asigna con la solicitud de la decisión 84] | | |
| 86 | Segundo factor para aprobar desde el celular | [No: firma de sesión, sesión única, 30 minutos y 10 horas] | | |
| 87 | Cuándo se fija la muestra de la llamada de control | [Lunes 00:10, la fija el sistema] | | |
| 88 | Frecuencia del respaldo y ventana de despliegue | [Diario 02:30 y antes de cada despliegue; despliegues después de las 21:00 o en fin de semana] | | |
| 89 | Dónde se guardan los respaldos | [Almacenamiento con bloqueo de objetos, clave sin permiso de borrar; 35 diarios y 12 mensuales; a nombre del colegio] | | |
| 90 | Quién puede abrir un respaldo | [Dos llaves: Promotoría y el responsable técnico, fuera de línea] | | |
| 91 | Simulacros de restauración | [Semanal automático y mensual presencial con Promotoría] | | |
| 92 | Copia física adicional (USB) | [No] | | |
| 93 | A quién llegan las alertas técnicas | [Correo al responsable técnico; «sin respaldo» y «faltan filas» también a Promotoría] | | |
| 94 | Vigilante externo | [Workflow de GitHub cada 15 minutos] | | |
| 95 | Conservación de los logs técnicos | [30 días] | | |
| 96 | Registro de quién ve datos personales | [Fichas, búsquedas, morosos, llamada de control, importación y contactos; 2 años (a confirmar por el asesor legal); alerta con más de 50 fichas en un día] | | |
| 97 | Pedidos sobre datos personales | [«Mis datos» al instante; pedidos por «¿Algo no cuadra?»; 20 días hábiles para el acceso y 10 para lo demás (a confirmar por el asesor legal), con aviso a los 7] | | |
| 98 | Plazos de conservación | [Lo financiero, mientras no prescriba (lo fija el contador); contactos de familias que se fueron sin deuda, 1 año (a confirmar por el asesor legal)] | | |
| 99 | Aviso de privacidad | [Lo redacta y aprueba el asesor legal; el sistema lo muestra y registra su aceptación] | | |
| 100 | Inscripción de los bancos de datos y flujo transfronterizo | [Trámite del colegio con su asesor, antes de la matrícula 2027] | | |
| 101 | Quién responde por los datos personales | [Dirección] | | |
| 102 | Dependencias con vulnerabilidades conocidas | [Revisión automática en cada cambio; bloquea las críticas o altas con arreglo; revisión semanal] | | |
| 103 | Cookie de sesión y conexión segura | [Cookie protegida; conexión segura obligatoria por 1 año, con subdominios] | | |
| 104 | Intentos de ingreso por conexión | [20 fallidos en 15 minutos; esa conexión espera 15 minutos, sin bloquear la cuenta] | | |
| 105 | Fechas de la capacitación | [Semana del 25 de enero, o antes si el piloto empieza antes] | | |
| 106 | Publicación de los videos | [Sin listar, en la cuenta del colegio, con datos de demostración] | | |
| 107 | Soporte después del acta | [30 días, por WhatsApp y correo, en horario escolar] | | |

## 6. Pendientes acordados
| # | Pendiente | Responsable | Fecha comprometida |
|---|---|---|---|
| 1 | Conectar el proveedor de comprobantes electrónicos real (OSE) | | |
| 2 | Conectar la pasarela de pagos real (Yape, Plin y tarjeta) | | |
| 3 | Adaptadores del banco para el extracto y la recaudación (con archivos reales anonimizados) | | |
| 4 | Verificación de WhatsApp Business del colegio | | |
| 5 | Hosting, dominio con conexión segura y almacenamiento de respaldos a nombre del colegio | | |
| 6 | Confirmar los plazos de la Ley 29733 (20 y 10 días hábiles, 48 horas y conservación) con el asesor legal | | |
| 7 | Aviso de privacidad redactado y aprobado por el asesor legal, publicado en la página «Aviso de privacidad» | | |
| 8 | Inscripción de los bancos de datos ante la Autoridad Nacional de Protección de Datos Personales y declaración del flujo transfronterizo | | |
| 9 | Documento de seguridad firmado por el colegio | | |
| 10 | Videos 3, 5, 6, 8 y 9 (hasta 2 semanas después del acta) | | |
| 11 | Capacitación de docentes con la fase académica (marzo) | | |
| 12 | Simulacro con un respaldo de producción, si el primero se hizo con el piloto | | |
| 13 | Anonimización automática de contactos (cuando exista el primer caso) | | |
| 14 | | | |

## 7. Soporte posterior
- **Periodo:** desde ____/____/______ hasta ____/____/______ (por defecto, 30 días).
- **Canal:** WhatsApp ______________________ y correo ______________________.
- **Horario:** ______________________ (por defecto, horario escolar).
- **Fuera de ese horario, solo incidentes críticos** (la plataforma no responde, «faltan filas», bitácora alterada o exposición de datos personales): ______________________.

## 8. Conformidad
**«El colegio recibe la plataforma y declara que funciona según lo acordado, con los pendientes y los riesgos descritos.»**

| Promotoría | Dirección | Responsable técnico |
|---|---|---|
| Firma: | Firma: | Firma: |
| Nombre: | Nombre: | Nombre: |
| Fecha: | Fecha: | Fecha: |
