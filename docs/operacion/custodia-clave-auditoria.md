# Custodia de la clave de la bitácora (AUDITORIA_CLAVE_HMAC)

La bitácora de auditoría se sella con una cadena HMAC-SHA256. Quien tiene la **clave** y además acceso a la base de datos puede reescribir eventos y recalcular la cadena sin que se note. Por eso la clave y el acceso a la base **nunca** deben estar en las mismas manos.

## Quién la custodia
| Persona o rol | ¿Tiene la clave? | Por qué |
|---|---|---|
| **Promotoría** (dueña del colegio) | **Sí**: copia sellada, en sobre firmado o en un gestor de contraseñas personal | Es la principal interesada en que nadie altere la bitácora |
| Gestor de secretos del servidor de la aplicación | Sí, solo como variable de entorno del proceso | La aplicación la necesita para sellar |
| Quien administra el **servidor de base de datos** (DBA, hosting) | **No** | Tiene acceso directo a las tablas: con la clave podría falsificar la cadena |
| Personal del colegio (Caja, Administración, Dirección) | No | No la necesitan |
| Desarrolladores | No (usan la clave de desarrollo, que la aplicación rechaza en producción) | — |

## Reglas
- Genera la clave con al menos 32 caracteres aleatorios, por ejemplo con `openssl rand -base64 48`, en un equipo de confianza y en presencia de Promotoría.
- Nunca la envíes por chat ni por correo. Nunca la escribas en el repositorio, en tickets ni en logs.
- La aplicación se niega a arrancar en producción si la clave falta o es la de desarrollo (cualquier clave que contenga `no-usar-en-produccion`).
- **No hay rotación** por ahora: si la clave cambia, los eventos anteriores ya no se pueden verificar con la nueva. Si se sospecha que se filtró, sigue `incidente-auditoria.md`: preserva la evidencia, verifica la bitácora con la clave anterior y recién después decide con Promotoría.
- Si se pierde la clave, la bitácora sigue funcionando, pero ya no se puede comprobar que esté íntegra. Por eso la copia de Promotoría es obligatoria.

## La huella: el control que no depende de la clave
Al pulsar **Verificar integridad**, la pantalla muestra una huella (número de evento, fecha y código). Promotoría la anota fuera del sistema; desde el sprint 4 le llegará cada día por WhatsApp o correo. En la siguiente verificación la escribe en «Última huella que anotaste». Si ese evento ya no está, o su código cambió, la bitácora fue recortada o alterada, aunque quien lo hizo tuviera la clave.
