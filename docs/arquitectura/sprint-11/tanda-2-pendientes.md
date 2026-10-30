# Sprint 11 · Tanda 2 · Pendientes heredados de las tandas 1, 1c y 1b (brief)

> Léase con `tanda-2.md`: es la **misma tanda 2** y el mismo PR #22. Estos puntos no dependen de la exportación ni de la
> supresión, así que pueden ir primero o en paralelo. Sin migración propia (lo de la base va en V41 y `03`). Conteos los de
> `tanda-2.md`: 124 triggers, 22 funciones y 146 huellas. **La tanda está terminada cuando el CI del PR está en verde.**

## 1. Los pendientes
1. **Reloj real hasta 0** (detalle en `tanda-2.md`, sección 4, y AP2-6 en 3.17):
   - los 27 usos en 18 triggers pasan a `cc_ahora_lima_real()` o `cc_hoy_lima()`, reemplazando la **expresión completa**
     `UTC_TIMESTAMP(6) - INTERVAL 5 HOUR`;
   - los intervalos compuestos se corrigen (`UTC_TIMESTAMP() - INTERVAL 6 HOUR` pasa a `cc_ahora_lima_real() - INTERVAL 1
     HOUR`);
   - `reloj-pendiente.txt` queda en 0 y `scripts/ci/reloj-real.sh` exige 0 usos y 0 `INTERVAL 5 HOUR`;
   - `RelojEnSuLimiteMySqlTest`: cada ventana se prueba un segundo antes y uno después de su límite (48 horas de la nota tardía,
     la hora de la llamada de control, 5 minutos de las firmas, 12 horas de la sesión y las demás que cambian);
   - las pruebas de MySQL de los sprints 7 a 10 sobre esos 18 triggers siguen verdes sin cambios.
2. **`th:if` (o `th:unless`) junto a `th:replace` en el mismo elemento** (hallazgo de la 1b): el `th:replace` se evalúa
   igual. Son **19 usos en 8 plantillas**:
   - `familia/comunicados.html` (5), `comunicados/detalle.html` (3), `comunicados/lista.html` (3);
   - `comunicados/nuevo.html`, `familia/comunicado.html` y `familia/mis-datos.html` (2 cada una);
   - `aprobaciones/cajas.html` y `avisos-familias/bandeja.html` (1 cada una).
   Cada uno pasa a `<th:block th:if="…"><div th:replace="…"></div></th:block>`. `PlantillasIfReplaceTest` recorre todas las
   plantillas y falla si un elemento tiene los dos atributos.
3. **Tarjeta de cambio de cuenta bancaria (F-D7, de la 1b):**
   - el ⚠ con su tooltip «Confirma el número con el banco antes de aprobar»;
   - titular, prueba de conexión y motivo en «Ver detalle» (`<details>`);
   - **número de cuenta y CCI completos siguen visibles** fuera del `<details>` (`ControlesVisiblesTest`).
4. **El resumen diario cuenta las aprobaciones externas (F-D5):** hoy `contarPendientes` (`sistema.panel`) no las suma, porque
   los puertos filtran por el rol de la sesión. Cada puerto gana `contarPendientesDelColegio(colegioId)`, sin filtro de rol ni de
   participantes, que solo llama el resumen. `ResumenDiarioTest`: un plan enviado y un feriado propuesto suman 2.
5. **La suspensión aplicada, en MySQL:** las pruebas que la tanda 1 dejó en H2 porque sin V41 no se podía proponer:
   `a4SuspensionConCierrePorRevisarFalla`, `a4SuspensionConComprobantePendienteFalla`, `a4EfectivoEnLasUltimas24HorasFalla`,
   `h3OrdenDentroDeLas2HorasFalla`, `f10SesionDelPersonalEnSuspendidoFalla`, `a5ActivacionDeApoderadoEnSuspendidoPasa`,
   `a5RecordatorioEnSuspendidoFalla` y `f1SuspensionAntesDe30DiasFalla` (la espera de 30 días sale de la base; se prueba con
   `espera_directa_horas` y la suya, como en la 1c).
6. **La alerta a los operadores de A1c-4** (quien cambió la cuenta bancaria intenta confirmar la conciliación), que la 1c no
   implementó.

## 2. Archivos que toca
- `scripts/mysql/03-triggers.sql` (los 18 triggers), `reloj-pendiente.txt`, `scripts/ci/reloj-real.sh`.
- Las 8 plantillas con `th:if` y `th:replace`.
- `aprobaciones/bandeja.html` (la tarjeta de la cuenta bancaria).
- `aprobaciones.api.PendientesExternos` y sus 7 adaptadores (`contarPendientesDelColegio`), y el resumen diario.
- `mysql/AuditoriaSprint11CicloMySqlTest` (las 8 de la suspensión aplicada).
- `ConectoresColegio` o `ServicioExtractos` (la alerta de A1c-4).

## 3. Terminado cuando
`reloj-pendiente.txt` está en 0 y cada ventana pasa su prueba de límite; ninguna plantilla tiene `th:if` con `th:replace` en el
mismo elemento; la tarjeta de la cuenta bancaria muestra el ⚠ y «Ver detalle» con el número y el CCI a la vista; el resumen
diario cuenta las aprobaciones externas; la suspensión aplicada tiene sus pruebas en MySQL; los operadores reciben la alerta de
A1c-4; y el CI del PR en borrador está en verde.
