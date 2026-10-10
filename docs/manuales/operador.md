# Manual del responsable técnico · Cuentas Claras

*Colegio Virgen María · Para quien opera el servidor, la base y los respaldos · Versión 1, octubre de 2026 · Puede ocupar más de una página*

Detalle en `docs/operacion/`: [respaldos.md](../operacion/respaldos.md), [monitoreo.md](../operacion/monitoreo.md), [mysql-usuarios.md](../operacion/mysql-usuarios.md), [custodia-clave-auditoria.md](../operacion/custodia-clave-auditoria.md). Ninguna clave va en este manual, en los scripts ni en el repositorio.

## Tu día con Cuentas Claras
1. **Cada mañana (07:00)** llega el resumen técnico a `CC_OPERADOR_CORREO`. Si no llegó, las alertas pueden estar apagadas: `/panel/sistema` (Promotoría) lo muestra en rojo.
2. **Respaldo diario 02:30** (`cuentas-claras-respaldo.timer`). `/salud/respaldo` debe responder `OK`; `ATRASADO` es más de 26 h sin respaldo y `REVISAR` es que el último encontró filas faltantes.
3. **Cada lunes:** lee el informe del simulacro automático del domingo 04:00 (`restauracion-AAAAMMDD-HHMMSS.json`), que corre en tu máquina con Docker y sin la clave HMAC. Si tu máquina estuvo apagada, no hubo simulacro: córrelo a mano.
4. **Primer lunes de cada mes, 20 minutos con Promotoría:** simulacro presencial con la clave HMAC que escribe Promotoría, comparación de la huella del último resumen de WhatsApp, prueba de borrado con la clave del servidor (E33) y [acta firmada](../operacion/acta-simulacro-restauracion.md).
5. **Cada despliegue** (después de las 21:00 o en fin de semana, unos 2 minutos sin servicio):
   1. respaldo con `respaldar.sh` (si falla, **no se despliega**);
   2. detener la aplicación;
   3. `java -jar cuentas-claras.jar migrar` con `DB_MIGRADOR_USUARIO` y `DB_MIGRADOR_CLAVE` (solo en este paso);
   4. `04-una-vez-sprint-7.sql` (solo la primera vez), `02-permisos-tablas.sql` como administrador y `03-triggers.sql` como `cc_migrador`;
   5. arrancar con `cc_app` y `cc_sistema`: el verificador comprueba permisos, huellas de los 73 triggers y las 5 funciones, TLS y la configuración del respaldo. Si algo falla, **no arranca** y el log dice qué revisar;
   6. confirmar `/actuator/health/readiness` = UP.
6. **Cada 50 días como máximo:** un commit en el repositorio para que GitHub no apague el vigilante (regla de los 60 días, abajo).
7. **Cada lunes:** revisa las propuestas de Dependabot (dependencias, imagen base y acciones). El job `dependencias` del CI falla si una dependencia tiene una vulnerabilidad CRÍTICA o ALTA con arreglo: no se despliega hasta actualizarla.
8. **Al instalar el servidor:** mide el ingreso. El costo de BCrypt es 12 (`cuentasclaras.seguridad.costo-bcrypt`); si el ingreso tarda más de 500 ms, bájalo a 11. El sitio va solo por https: la cookie es `__Host-CCSESION` y el HSTS dura un año. Si todo el colegio sale a internet por una sola IP y el personal ve «Demasiados intentos desde esta conexión», sube `cuentasclaras.sesion.intentos-por-ip`.

## Lo que el sistema no te deja hacer, y por qué
- **Arrancar prod** con privilegios de más, un trigger o función distinto del jar (aunque conserve el nombre), un trigger de más, sin TLS hacia MySQL, sin `cc_sistema` o con el mismo usuario en las dos conexiones, con la clave HMAC de desarrollo o con la fila `respaldo_simulado`: cada uno abre un camino de fraude.
- **Migrar desde la aplicación:** Flyway está apagado en prod; solo `migrar` con `cc_migrador`, que la aplicación nunca recibe.
- **Editar o borrar la bitácora, pagos o cuotas** con `cc_app` o `cc_sistema` (1142, 1143 o 1644). Tampoco aprobar a nombre de otra persona: falta la firma de su sesión.
- **Borrar o leer los respaldos desde el servidor:** la clave del almacenamiento no tiene permiso de borrar (bloqueo de 35 días) y el servidor solo tiene las claves públicas `age`.
- **Ver datos personales en los logs:** salen enmascarados (DNI, RUC, celular, correo, tokens) y se guardan 30 días.
- **Tener la clave HMAC y el acceso de administrador a la base en las mismas manos:** la custodia es de Promotoría.

## Si algo no cuadra
| Alerta (correo) | Gravedad | Qué hacer |
|---|---|---|
| `PROCESO_ATRASADO` | CRÍTICA si es mensajes, huellas o resumen | Busca el proceso en el log; reinicia si está colgado. Avisa a Promotoría si el resumen de las 19:30 no salió |
| `SIN_RESPALDO` | CRÍTICA (también Promotoría) | Revisa el temporizador y el último error de `respaldar.sh`; respalda a mano |
| `FALTAN_FILAS` | CRÍTICA (también Promotoría) | **No toques nada.** Sigue [incidente-auditoria.md](../operacion/incidente-auditoria.md). La alerta sigue en cada respaldo hasta que Promotoría la resuelva con motivo en `/panel/sistema` (tú no puedes) |
| `BITACORA` | CRÍTICA | El eslabón no apunta al último evento: [incidente-auditoria.md](../operacion/incidente-auditoria.md) |
| `BASE` / `POOL` | CRÍTICA | Revisa MySQL, conexiones y consultas lentas |
| `ERRORES` / `ERROR_NUEVO` | ATENCIÓN | Busca el `id_peticion` (el «código de error» que ve la persona) en el log |
| `DISCO` | ATENCIÓN | Rotación de logs y espacio libre (menos de 15 %) |
| Correo de GitHub «Vigilancia» falló | — | La aplicación no responde, el respaldo no está `OK` o el certificado vence en menos de 14 días |

**Vigilante externo y regla de los 60 días.** `.github/workflows/vigilancia.yml` consulta cada 15 minutos `/actuator/health`, `/salud/respaldo` y el certificado (variable `CC_URL_PUBLICA`). GitHub **apaga los workflows programados** de un repositorio público tras 60 días sin actividad: el paso «actividad» falla desde el día 53. Basta un commit sin datos personales (por ejemplo, la fecha del último simulacro). Si ya se apagó, reactívalo en *Actions*. Alternativa: la capa gratuita de un servicio de monitoreo de disponibilidad (decisión 94).

**Desastre (base perdida o alterada).** 1) Preserva la evidencia ([incidente-auditoria.md](../operacion/incidente-auditoria.md)). 2) Restaura el último respaldo verificado en una base nueva (`restaurar-y-verificar.sh` sin `RESTAURAR_DOCKER`, con `RESTAURAR_ARRANCAR=no`). 3) Cambia las claves de `cc_app`, `cc_sistema`, `cc_respaldo` y `cc_migrador`. 4) Apunta `DB_URL` a la base nueva y arranca. 5) Promotoría compara el resumen y la huella de los días entre el respaldo y la caída con lo que recibió por WhatsApp; esos pagos se reconstruyen con las boletas y el banco. 6) Si hubo exposición de datos personales, sigue [incidente-datos-personales.md](../operacion/incidente-datos-personales.md) (notificación en 48 horas, a confirmar por el asesor legal).

**Rotación de claves** (al salir alguien con acceso, ante sospecha, después de un incidente y con la frecuencia que acuerde el colegio; en la ventana de despliegue):
- **MySQL:** `ALTER USER` de `cc_app`, `cc_sistema`, `cc_respaldo` o `cc_migrador`, actualiza el gestor de secretos y el entorno (`DB_CLAVE`, `DB_SISTEMA_CLAVE`, `RESPALDO_DB_CLAVE`) y reinicia. Al reiniciar se cierran las sesiones abiertas.
- **Almacenamiento de respaldos:** crea una clave de aplicación nueva **sin permiso de borrar**, cámbiala en `RCLONE_CONFIG_*`, revoca la anterior y repite la prueba E33.
- **Claves `age`:** genera el par nuevo fuera del servidor y cambia `RESPALDO_AGE_DESTINATARIOS`. Conserva la privada anterior mientras existan respaldos cifrados con ella (12 meses).
- **Proveedores** (WhatsApp, correo, OSE, pasarela): rota el token en el proveedor y en el entorno.
- **Clave HMAC:** **no se rota** ([custodia-clave-auditoria.md](../operacion/custodia-clave-auditoria.md)).

**Datos personales.** El registro de accesos se guarda 2 años y lo purga el DBA con un script revisado, nunca la aplicación (plazo a confirmar por el asesor legal). La anonimización de los contactos del reporte «Datos con plazo vencido» es manual por ahora: se coordina con Promotoría y queda en un acta.

## Dónde ver el video
| Video 9 · Si algo no cuadra en el colegio |
|---|
| Código QR del video 9: se agrega cuando el video esté publicado |
