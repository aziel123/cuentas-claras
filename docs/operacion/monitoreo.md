# Monitoreo sin servicios pagos

> Sprint 7, tanda 1 (diseño: `docs/arquitectura/sprint-7-endurecimiento.md`, sección 10). Para el responsable técnico.

## Salud de la aplicación
| Ruta | Para qué | Responde |
|---|---|---|
| `/actuator/health` | Estado público | Solo `{"status":"UP"}` o `DOWN`, sin detalles |
| `/actuator/health/liveness` | Sonda de vida (reinicio del contenedor) | Solo el estado |
| `/actuator/health/readiness` | Sonda de disponibilidad: incluye la base | Solo el estado. El verificador de permisos corre al arrancar: si falla, la aplicación no llega a estar lista |
| `/salud/respaldo` | Vigilante externo | `OK`, `ATRASADO` (sin respaldo en 26 h) o `REVISAR` (el último encontró filas faltantes). Sin fechas ni datos |
| `/panel/sistema` | Promotoría | Último respaldo, procesos automáticos, errores de hoy por huella, bitácora, base, disco y versión |

Los indicadores internos (respaldo, procesos, bitácora, disco) **no cambian** el estado público: un respaldo atrasado
no es «aplicación caída». Se ven en `/panel/sistema` y van a las alertas técnicas.

## Logs (prod y piloto)
- Una línea **JSON (ECS)** por evento a la salida estándar. El servidor los rota y los guarda **30 días** (decisión 95).
- Cada línea de una petición lleva `id_peticion` (12 hexadecimales), `colegio`, `usuario_id` (nunca el nombre) y `ruta`
  como **patrón** (`/familias/{id}`), nunca la URL real (los enlaces de activación llevan secretos).
- **Enmascarado:** el mensaje, la excepción y la traza pasan por `Enmascarar.enTexto`: DNI, RUC, celulares, correos,
  tokens y el valor de un «Duplicate entry» de MySQL salen ocultos. Una entrada con saltos de línea no parte la línea
  (no se puede falsificar una entrada).
- **Código de error:** la página de error muestra el `id_peticion` («Código de error: a1b2c3d4e5f6»), nunca el mensaje.
  Si alguien avisa de un error, pídele ese código y búscalo en el log.

## Procesos automáticos (latidos)
Cada tarea programada que termina **sin error** deja su latido (Spring observa cada ejecución; ningún proceso tiene
que acordarse de avisar). Un proceso está **atrasado** si:
- corre cada cierto tiempo y no latió en 4 intervalos (2 minutos como mínimo; el envío de mensajes, cada 30 s, se
  atrasa a los 2 minutos);
- corre a una hora (cron) y no terminó bien en los 15 minutos siguientes.
Tras un arranque hay **10 minutos de gracia**. Son críticos: el envío de mensajes, la huella diaria y por hora y el
resumen de las 19:30.

## Alertas técnicas (`AlertasTecnicas`, cada 5 minutos)
Salen por **correo directo** (SMTP de `spring.mail.*`, remitente `CORREO_REMITENTE`) a `CC_OPERADOR_CORREO`. No pasan por
la tabla de mensajes: si la base se cae, la alerta igual sale. Una misma alerta, como máximo una vez por hora; a las
07:00, un resumen técnico. Sin datos personales.

| Alerta | Cuándo | Gravedad | Qué hacer |
|---|---|---|---|
| `PROCESO_ATRASADO` | Un latido fuera de su ventana | CRÍTICA (mensajes, huellas, resumen) o ATENCIÓN | Revisar el log por el nombre del proceso; reiniciar si está colgado |
| `SIN_RESPALDO` | Más de 26 h sin un respaldo (real, en prod) | CRÍTICA, también a Promotoría | Revisar el temporizador y el último error de `respaldar.sh` ([respaldos.md](respaldos.md)) |
| `FALTAN_FILAS` | El último respaldo encontró filas faltantes o un ancla distinta | CRÍTICA, también a Promotoría | No tocar nada: [incidente-auditoria.md](incidente-auditoria.md) |
| `BITACORA` | El eslabón no apunta al último evento | CRÍTICA | [incidente-auditoria.md](incidente-auditoria.md) |
| `BASE` / `POOL` | La base no responde o hay peticiones esperando conexión en dos revisiones | CRÍTICA | Revisar MySQL y las conexiones |
| `ERRORES` / `ERROR_NUEVO` | 5 o más errores de una huella en 15 minutos, o una huella nueva | ATENCIÓN | Buscar el código de petición en el log |
| `DISCO` | Menos de 15 % libre | ATENCIÓN | Rotación de logs y espacio |

Sin `CC_OPERADOR_CORREO`, el remitente o el servidor de correo, las alertas quedan **apagadas** y `/panel/sistema` lo
dice en rojo.

## Vigilante externo (`.github/workflows/vigilancia.yml`)
Cuando el servidor está caído, nadie adentro puede avisar. Un workflow de GitHub, cada 15 minutos y gratis:
1. `/actuator/health` responde UP;
2. `/salud/respaldo` responde `OK`;
3. el certificado TLS vence en más de 14 días.
Si algo falla, el workflow falla y **GitHub envía un correo al dueño del repositorio**.

Configuración: en el repositorio, *Settings → Secrets and variables → Actions → Variables*, crear `CC_URL_PUBLICA` (por
ejemplo `https://cuentasclaras.colegio.pe`). Sin ella, el workflow no vigila nada.

**Probar el aviso (E37):** *Actions → Vigilancia → Run workflow* con una URL que responda 503: el workflow falla y llega
el correo. También se prueba en local: `VIGILANCIA_URL=http://127.0.0.1:8080 sh scripts/vigilancia/vigilar.sh`.

**Límites conocidos:**
- la cron de GitHub puede atrasarse varios minutos;
- GitHub **apaga los workflows programados** de un repositorio público tras **60 días sin actividad**. El paso
  «actividad» falla desde el día 53 para avisar con una semana; basta un commit (por ejemplo, la fecha del último
  simulacro mensual, sin firmas ni datos: el repositorio es público; el acta firmada se guarda en el colegio). La alternativa es la capa gratuita de un servicio de monitoreo de disponibilidad (decisión 94).
