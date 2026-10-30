# Sprint 11 · Correcciones del QA y de la auditoría del código (sobre 0798691)

> Mismo PR #22. Migración nueva **V42** (columnas, sin triggers nuevos): los conteos siguen en **124 triggers, 22 funciones
> y 146 huellas**. Cambian los textos de `trg_colegio_cambio`, `trg_cambio_estado_colegio_estado` (ya en la tanda 2),
> `trg_exportacion_colegio_cambio` (QA-S11-01 y B1), `trg_mensaje_nace` (QA-S11-02), `trg_acta_supresion_nace` y
> `_estado`, `cc_supresion_en_curso` y `huellas_objetos()` (02): sus huellas se recalculan solas desde 02 y 03 empaquetados.

## 0. QA (merge de `claude/sprint-11-qa`, 0a91552)
- **QA-S11-01 (alto, corregido por el QA):** el enlace nuevo tras 5 intentos fallidos nacía bloqueado. Cada enlace nuevo
  vuelve a 5 intentos; el trigger lo acepta solo con otro `token_hash` y sin DNI ni código. Pruebas del QA:
  `QaSprint11EntregaTest`, `QaSprint11CicloCompletoTest` y su caso de MySQL.
- **QA-S11-02:** tope de **3 códigos por hora** y por exportación: `EntregaExportacion.confirmarDni` y `trg_mensaje_nace`.
  Prueba: `CorreccionesSprint11EntregaTest.tresCodigosPorHoraComoMaximo`.
- **QA-S11-04:** la descarga de una libreta que no coincide, en CERRADO, solo deja el evento `LIBRETA_HUELLA_NO_COINCIDE`
  (ningún mensaje) y la familia ve «en revisión». Prueba en H2 con el portal real: `LibretaEnCerradoTest`. **Pendiente:**
  la versión con MySQL real necesita un colegio con libretas emitidas y cerrado, y el único de MySQL con libretas
  (`AuditoriaSprint10LibretasMySqlTest`) lo usa después el job de respaldo: queda para la tanda siguiente, con un escenario
  propio.
- **QA-S11-06:** nota en el README (detener la demo con Ctrl+C y, si se cortó en la primera carga, borrar `.demo-db/`).

## Auditoría del código
| Hallazgo | Qué se hizo | Prueba |
|---|---|---|
| **A1 (alto)** actas externas | `cuentasclaras.supresion.actas-dir: ${CC_DIR_ACTAS}` en `application-prod.yaml`, sin valor por defecto; `VerificadorSupresion` no arranca en prod si falta, está vacío o el directorio no existe. Interfaz `FuenteActasExternas` (hoy `ActasEnDirectorio`); el despliegue sincroniza el depósito con bloqueo de objetos a `/var/lib/cuentasclaras/actas` con `deploy/systemd/cc-actas.service` (rclone, `CC_ACTAS_DEPOSITO`) antes de arrancar; `compose.prod.yml` la monta de solo lectura. Leer el depósito directo queda para cuando la VM tenga sus credenciales (otra implementación de la interfaz). | `VerificadorSupresionTest.enProdLosDirectoriosSonObligatorios` |
| **A2 (alto)** conservación | V42: el retiro se **pide** (firma `colegio:{id}:CONSERVACION_RETIRADA:{n}` y el código de WhatsApp de esa acción, huella `SHA2('CONSERVACION_RETIRADA|id|n')`), la base lo deja para **24 horas**, lo aplica `AplicacionRetirosConservacion` (sistema.plataforma, cada 15 min) y un operador lo **frena** (`colegio:{id}:CONSERVACION_FRENADA:{n}`, botón en «Exportación y supresión»). Claves numeradas `:CONSERVACION:{n}` (se puede volver a dar). Con una suspensión APROBADA no cambia (servicio y trigger). | `AutorizacionConservacionTest` (3), MySQL `elRetiroDeLaConservacionEsperaYSeFrena` |
| **M1** retención | `:RETENCION_LEGAL:{n}` y `:RETENCION_LEGAL_RETIRADA:{n}`, contadas en el colegio de plataforma. | `RetencionLegalTest` (se pone y se quita dos veces), MySQL |
| **M2** plazo y triggers | `cc_supresion_en_curso`, `inicio_plazo` y el paso a EJECUTANDO cuentan desde la hora del **evento encadenado** (`COLEGIO_SUSPENDIDO`, `COLEGIO_CERRADO`, `COLEGIO_DADO_DE_BAJA`): moverla rompe la cadena. `huellas_objetos()` devuelve «recreado <fecha>» para un trigger creado más de 2 minutos después del primero de 03 (un DROP/CREATE posterior): prod no arranca; cargar 03 completo lo arregla. La prueba de MySQL ya no quita triggers: el cierre «de hace 45 días» lo aplica la aplicación con su reloj atrasado. | MySQL `suprimirSqlDeUnColegioNoTocaAOtro`, `unTriggerRecreadoDespuesDeCargar03NoPasaLasHuellas` |
| **B1** intentos | V42 `intentos_totales`: solo sube, y sube con cada intento del enlace; a los **15** el operador ya no manda otro enlace (se pide otra exportación). Coordinado con QA-S11-01: los de cada enlace vuelven a 0. | `CorreccionesSprint11EntregaTest.elTotalDeIntentosSoloSubeYTopaLosEnlacesNuevos` |
| **B2** reaplicar.sh | El JSON del acta va en hexadecimal (`CONVERT(UNHEX(...) USING utf8mb4)`). | — (script) |
| **B3** | `cuentasclaras.exportacion.directorio: ${CC_DIR_EXPORTACIONES}` obligatorio en prod (mismo verificador). `cadena_verificada_en` solo con la verificación **ÍNTEGRA** de `sistema.auditoria` de ese colegio en la bitácora de hace 10 minutos o menos. | `VerificadorSupresionTest`, MySQL `laCadenaDelActaLaAnotaSoloSistemaAuditoria` |

## Desviaciones y residuales
- El plazo de supresión depende de la hora del reloj de la APLICACIÓN al registrar el evento (la cadena impide moverla
  después, no al escribirla). Residual aceptado: quien controla el reloj del servidor de la aplicación.
- La detección de triggers recreados usa una ventana de 2 minutos desde el primer trigger de 03: un DROP/CREATE en los 2
  primeros minutos tras una carga no se ve (la carga la hace el DBA en el despliegue).
- Despliegue nuevo: `CC_DIR_ACTAS`, `CC_DIR_EXPORTACIONES`, `CC_ACTAS_DEPOSITO` y `cc-actas.service`
  (`docs/operacion/fin-de-un-colegio.md`, `.env.ejemplo`, `deploy/vm/cargar-secretos.sh`).
