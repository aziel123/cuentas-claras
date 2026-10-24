# Sprint 11 · Tanda 1c · La Promotoría aplica directo (brief)

> Léase primero. Diseño: `docs/arquitectura/sprint-11-ciclo-colegio.md`, **sección 3.16** (incluye su auditoría en papel P1 a
> P8) y decisiones 214 a 219. Va **después de la tanda 1 y antes de la 1b** (decisión 214). Rama `claude/sprint-11-ciclo-colegio`,
> PR en borrador: **la tanda está terminada cuando el CI del PR está en verde.** Antes de codear, `auditor-seguridad-antifraude`
> revisa el diseño en papel (esta tanda cambia una regla de fondo: sus hallazgos críticos y altos se corrigen aquí primero).

## 1. Decisiones (la principal está confirmada por el usuario; las demás, valor por defecto)
1. **Confirmada:** la Promotoría (el dueño) no pide aprobación a personas de menor jerarquía. Sus acciones normales se aplican
   directo, con firma, bitácora y aviso inmediato a las demás Promotorías y al correo de control.
2. **Sensibles (216):** con código por WhatsApp (10 minutos, una vez). Son roles y estado de cuentas directivas, anulaciones de
   pago y de cuota, devoluciones, descuentos y becas, partidas manuales, contacto y responsable de pago de apoderados, y todo
   conector.
3. **Conectores de dinero (218):** cuenta bancaria, pasarela y recaudación. Llevan código y 24 horas de espera con avisos. Los
   frena otra Promotoría o un operador; el socio Promotoría los aprueba de inmediato.
4. **OSE (217):** directo con código, sin espera.
5. **Lo que no cambia:** la Promotoría no cobra; nada se borra ni se edita; lo que piden los demás roles sigue con dos personas
   (la Promotoría puede aprobarlo); quien ejecuta una devolución sigue siendo otra persona.
6. **A5 (215):** deja de bloquear a la Promotoría y se mantiene para Dirección.
7. **Contacto propio (219, P1):** nunca directo. Lo aprueba otra Promotoría; el del titular, dos operadores.
8. **Ajustes:** la facultad del titular solo crea cuentas directivas, y dar o quitar esos roles es una directa sensible; se quitan
   la condición de PRODUCCION de la 201 y AP-1 (**decisión 220, confirmada por el usuario**: quitar Dirección en producción es
   directa con código y el riesgo queda aceptado); A8 pasa a ser un aviso; 3.15 y la 211 quedan
   superadas para la Promotoría.

## 2. Alcance
- **Solicitud directa:** si quien pide es una Promotoría activa, `RegistroSolicitudes` crea la `solicitud_cambio` PENDIENTE y en la
  misma transacción la resuelve APROBADA con `directa = TRUE`, la firma `solicitud_cambio:{id}:APROBADA` y, si es sensible, el
  `codigo_mensaje_id`. El manejador del tipo la ejecuta como hoy. **No se crea un camino de ejecución nuevo.**
- **Flujos fuera de `solicitud_cambio`** (plan de pensiones, saldo inicial, lote de recaudación, extracto, partidas, cierre mensual,
  feriados y descuentos): la Promotoría crea y aprueba, o confirma, en un paso. Pantallas con un solo botón: «Aprobar y aplicar».
- **`SegundaFirma`** reemplaza las comprobaciones de A5 en los 11 servicios que hoy llaman a `ControlParticipantes`. A5 sigue
  igual para todos los roles salvo la Promotoría.
- **Código por WhatsApp (`CodigoPromotoria`):**
  - «Enviar código» manda 6 dígitos al celular de la cuenta (respaldo por correo si WhatsApp falla);
  - el mensaje guarda solo la SHA-256 del código;
  - la acción se envía con el código y la base comprueba el mensaje con `cc_codigo_promotoria`.
- **Conectores:**
  - la Promotoría propone y aprueba: el OSE queda VIGENTE; la cuenta, la pasarela y la recaudación quedan EN_ESPERA 24 horas;
  - durante la espera: «Frenar» (otra Promotoría o un operador, con motivo) y «Aprobar ahora» (el socio, con su código);
  - `AplicacionConectoresEnEspera` (cada 15 minutos, `sistema.conectores`) aplica los vencidos;
  - alerta CRÍTICA en el panel y avisos al correo de control, a las Promotorías y a los operadores al entrar en espera, al
    aplicarse y al frenarse.
- **Avisos (`AvisosAccionDirecta`):**
  - cada directa avisa al momento a las demás Promotorías activas y al correo de control, en la misma transacción;
  - el resumen diario al correo de control lista las directas del día;
  - quien pierde un rol directivo recibe su aviso.
- **Textos:** se ajustan los que dicen «la aprueba otra persona de Promotoría o Dirección» cuando quien mira es Promotoría
  (F-D7: corto, con un tooltip «Se aplica directo. Se avisa a las demás Promotorías y al correo de control»).
- **Fuera de alcance:** la guía de la 1b (se escribe ya con estas reglas); un «dispositivo conocido» o un segundo factor al
  ingresar (decisión futura, si el usuario lo pide).

## 3. Migración `V40__promotoria_directa.sql`
- SQL en la sección 3.16 del diseño: `solicitud_cambio.directa` y `codigo_mensaje_id` (UNIQUE), y `ck_solicitud_cambio_directa`;
  `conector_colegio.directa`, `aplica_desde`, `frenado_por`, `frenado_en` y `motivo_freno`.
- **Se reemplazan tres CHECK de V29, copiando sus listas:** `ck_conector_colegio_estado` (+ `EN_ESPERA` y `FRENADO`),
  `ck_conector_colegio_resolucion` (EN_ESPERA con `aplica_desde` y `resuelto_por`; FRENADO con `frenado_por`, `frenado_en` y
  motivo de 20 caracteres o más) y `ck_conector_colegio_actor` (`resuelto_por = propuesto_por` solo con `directa`).
- **Un caso NULL por CHECK:** `directa`, `codigo_mensaje_id`, `aplica_desde`, `frenado_por` y `motivo_freno`.
- `TipoMensaje.CODIGO_PROMOTORIA` (guarda solo la SHA-256); `EstadoConector` + `EN_ESPERA` y `FRENADO`; `SolicitudCambio` y
  `ConectorColegio` con las columnas nuevas, y `aplicaDesde` con `insertable = false` (la pone la base).
- **Antes de seguir, comprobar en H2 y MySQL 8.4:** los `DROP` y `ADD CONSTRAINT` de `conector_colegio` con conectores vigentes,
  y que la renumeración no choque (la tanda 2 pasa a V41: ningún V40 publicado antes).

## 4. MySQL: funciones, triggers y huellas
- `01-usuarios.sql` sin cambios. `02-permisos-tablas.sql`:
  - `UPDATE (directa, codigo_mensaje_id)` de `solicitud_cambio` para `cc_negocio`;
  - `UPDATE (directa, aplica_desde, frenado_por, frenado_en, motivo_freno)` de `conector_colegio` para `cc_negocio` y para
    `cc_sistema` (la aplicación por `sistema.conectores` y el freno del operador).
- `03-triggers.sql` (SQL en 3.16; todo con `cc_ahora_lima_real()` y `<=>`):
  - **función nueva** `cc_codigo_promotoria(mensaje, colegio, nombre)`;
  - **versión nueva** de `cc_solicitud_firmada` (acepta `directa`);
  - **7 triggers con versión nueva:**
    - `trg_solicitud_cambio_resuelta`: reglas de `directa`: rol PROMOTOR activo, colegio que opera, firma, código si es
      sensible, correo de control en PRODUCCION y nunca el contacto propio;
    - `trg_conector_colegio_estado`: OSE directo; espera de 24 horas; aplicación por `sistema.conectores`; socio; freno;
    - `trg_feriado_anulacion`, `trg_partida_conciliacion_estado` y `trg_comunicado_estado`: la directa;
    - `trg_uso_facultad_titular_nace`: sin la condición de PRODUCCION ni AP-1, y el código con la función nueva;
    - `trg_mensaje_nace`: `CODIGO_PROMOTORIA`;
  - **sin cambios** (y una prueba por cada uno que lo demuestre): los 10 que usan `cc_solicitud_firmada`, los 3 de «quien ejecuta
    es otro», los 3 de «quien cobró no verifica», `trg_usuario_identidad` y `trg_usuario_rol_*`, y los de los operadores
    (inventario completo en 3.16);
  - encabezado: «Versión anterior (V39, sprint 11, tanda 1): 117 triggers y 16 funciones (19 con huella; 136 huellas)».
- **Conteo esperado:** **117 triggers** (ninguno nuevo); funciones de `03` 16 → **17**, con huella 19 → **20**; huellas 136 →
  **137**. Prod: «Huellas de los 117 triggers y las 20 funciones verificadas». Tanda 2 (V41): 124, 21 y 145.
- `VerificadorPermisosBaseDatos`:
  - 1644 nuevos: una solicitud APROBADA con `directa` y sin firma; un conector que nace EN_ESPERA;
  - 3819 nuevo: `directa` con `resuelto_por` distinto de `solicitado_por`;
  - `HuellasObjetosBd` = 137;
  - el paso `reloj-real` del CI sigue bajando.

## 5. Segregación de funciones (lo que queda)
| Quién pide | Quién aprueba | Cómo |
|---|---|---|
| Promotoría | Ella misma | Directa, con firma y avisos; con código si es sensible; los conectores de dinero, con 24 horas de espera o el socio |
| Promotoría, su propio contacto | Otra Promotoría (el titular: dos operadores) | Como hoy (P1) |
| Dirección | Promotoría u otra Dirección que no preparó su cuenta (A5 sigue) | Como hoy |
| Administración, Caja o Docente | Promotoría (sin A5) o Dirección (con A5) | Como hoy |
| Una devolución de dinero aprobada | La ejecuta una persona distinta de quien la aprobó | Como hoy (base) |
| Caja | Nunca Promotoría como cajera | `trg_usuario_rol_alta` (sin cambio) |

## 6. Archivos que toca
- **Base:** `V40__promotoria_directa.sql`; `scripts/mysql/02-permisos-tablas.sql` y `03-triggers.sql`;
  `scripts/mysql/reloj-pendiente.txt` (si baja).
- **`seguridad`:** `service.SegundaFirma` y `service.CodigoPromotoria` (nuevos); `ControlParticipantes` (sin cambios: lo llama
  `SegundaFirma`); `ManejadorCambioRoles`, `ManejadorEstadoCuenta`, `ServicioUsuarios` y `FacultadTitular` (el código pasa a
  `CodigoPromotoria`).
- **`aprobaciones`:**
  - `RegistroSolicitudes` (la directa);
  - `BandejaAprobaciones` (`puedeResolver` con `SegundaFirma`; no lanza `AutoaprobacionSolicitudException` a la Promotoría);
  - `SolicitudCambio` (columnas nuevas).
- **Los 11 servicios con A5:** `ServicioPlanesPension`, `ServicioSaldoInicial`, `ServicioRecaudacion`, `ServicioExtractos`,
  `ServicioPartidas`, `ResponsablesPartida`, `ServicioCierreMensual`, `ServicioAvisosFamilia`, `MensajesPromotoria`,
  `BandejaAprobaciones` y la de descuentos. También `ServicioFeriados`.
- **`conectores`:** `ConectorColegio`, `EstadoConector`, `ConectoresColegio` (espera, freno y socio) y
  `proceso.AplicacionConectoresEnEspera`. El freno del operador va en `plataforma` (`PlataformaController`), por
  `EjecucionPlataforma`.
- **`comunicacion`:** `TipoMensaje.CODIGO_PROMOTORIA`, sus plantillas, `AvisosAccionDirecta` y el resumen diario.
- **Plantillas:**
  - los botones «Aprobar y aplicar», con el campo del código cuando es sensible, en planes, saldo inicial, descuentos,
    anulaciones, roles y conectores;
  - la espera del conector con «Frenar» y «Aprobar ahora»;
  - el aviso de A8.
- **Transversales:** `AccionAuditoria` (+`ACCION_DIRECTA_PROMOTORIA`, `CONECTOR_EN_ESPERA`, `CONECTOR_FRENADO` y
  `CODIGO_PROMOTORIA_ENVIADO`), `ClaveFirma`, `VerificadorPermisosBaseDatos`, `HuellasObjetosBd` y `PermisosEsperados`.
- **Documentos:** `docs/manuales/promotoria.md` («Lo que haces directo y lo que te pide un código»), `operador.md` (frenar un
  conector), `docs/entrega/terminos-de-servicio.md` (la espera y el freno, para el abogado), `ci.yml` (V1–V40, 137 y 117/20).

## 7. Pruebas obligatorias
**H2 (`./mvnw -B verify`, también con la zona de Los Ángeles):**
- `PromotoriaDirectaTest`:
  - el titular, **sin Dirección**, aprueba el plan que propuso `testadmin` (A5 ya no lo bloquea);
  - el titular crea y aprueba su propio plan, feriado y lote de saldo inicial en un paso;
  - cada directa deja su evento con la marca «aplicada directo» y avisa a las demás Promotorías y al correo de control;
  - si no se puede avisar, no se guarda.
- `AccionesSensiblesTest`: anulación de pago, descuento, dar DIRECTOR y quitar DIRECTOR en PRODUCCION. Cada una se rechaza sin
  código, con un código vencido, con el código de otra acción y con el código de otra persona, y se aplica con el código
  correcto. La anulación avisa también a la familia.
- `ConectorEsperaTest`:
  - la cuenta bancaria propuesta por el titular queda EN_ESPERA y se aplica sola a las 24 horas (reloj fijo);
  - la frena el socio y la frena el operador (con aviso a todos);
  - el socio la aprueba de inmediato;
  - una sola Promotoría no puede «aprobar ahora» lo suyo;
  - el OSE queda VIGENTE al instante con código;
  - la conciliación sigue marcando un abono que no llega.
- `ContactoPropioNoDirectoTest` (P1): la Promotoría que cambia su celular deja la solicitud PENDIENTE para otra Promotoría; el
  titular, por el camino de dos operadores.
- `DireccionSigueConDosPersonasTest`: lo que pide Dirección espera aprobación; una Dirección que creó la cuenta de un docente no
  aprueba lo de ese docente (A5); la Promotoría sí.
- `DemasRolesSinCambioTest`: Administración, Caja y Docente no aprueban lo suyo; la Promotoría no puede ser CAJA; quien aprueba
  una devolución no la ejecuta.
- **Pruebas que cambian de sentido** («la Promotoría no aprueba lo suyo», en `ServicioPlanesPensionTest`,
  `ServicioSaldoInicialTest`, `BandejaAprobacionesTest`, `FeriadosTest`, `CambioConectorTest`, `AltaACobroTest` (paso 4) y las
  de la facultad): cada una se reemplaza por la misma comprobación con Administración o Dirección como autor. La lista de las
  reemplazadas va en «Tanda 1c · Implementación».
- `ReglasArquitecturaTest`: ningún servicio llama a `ControlParticipantes` sin pasar por `SegundaFirma`.

**MySQL real (`mysql/AuditoriaSprint11PromotoriaMySqlTest`). Todas 1644 salvo aviso; las fechas se simulan como DBA:**
- `directaSinRolPromotorFalla`, `directaDeQuienNoLaPidioFalla` (3819), `directaSinFirmaFalla`, `directaSensibleSinCodigoFalla`,
  `codigoUsadoFalla` (UNIQUE), `codigoVencidoFalla`, `codigoDeOtraPersonaFalla`, `codigoAOtroContactoFalla`,
  `directaEnColegioSuspendidoFalla`, `directaEnProduccionSinCorreoDeControlFalla`, `p1ContactoPropioDirectoFalla`.
- `solicitudFirmadaAceptaDirecta` (pasa); `resueltaPorQuienLaPidioSinDirectaFalla` (como hoy).
- `conectorEnEsperaAntesDe24HorasFalla` (también con `SET timestamp` a +25 horas: sigue fallando), `conectorEnEsperaVencidoPasa`
  (`sistema.conectores`), `socioApruebaAhoraPasa`, `mismaPromotoriaApruebaAhoraFalla`, `frenoPorOperadorPasa`,
  `frenoSinMotivoFalla` (3819), `frenadoNoCambia`, `oseDirectoConCodigoPasa`, `oseSimuladoEnProduccionFalla` (como hoy).
- `feriadoDirectoPasa`, `partidaDirectaPasa`, `comunicadoDirectoPasa`, `facultadEnProduccionConCodigoPasa`.
- **Lo que no cambia:** `devolucionEjecutadaPorQuienAproboFalla`, `promotoriaComoCajaFalla`, `direccionApruebaLoSuyoFalla`
  (con la clave de cc_app y sin `directa`), `administracionDirectaFalla`.
- `PermisosMySqlTest`: el flujo del titular con permisos mínimos (plan directo, cuenta bancaria en espera y aplicada por
  `sistema.conectores`, anulación con código). `huellas_objetos()` = 137; arranque prod con 117 y 20.

## 8. Riesgos
- **Cuenta de Promotoría robada** (el principal): la mitigan el código en lo sensible, el contacto protegido (P1), los avisos
  inmediatos a las demás Promotorías y al correo de control, la espera con freno en el dinero y la conciliación.
- **Una sola Promotoría:** nada de lo suyo tiene segunda firma; lo ven el correo de control y la huella diaria.
- **Se cambia una función central y 7 triggers:** las pruebas de los sprints 2 a 10 deben seguir verdes, salvo las que cambian
  de sentido, que se reemplazan sin perder cobertura.
- **WhatsApp caído:** traba lo sensible; respaldo por correo.
- **Revisión legal:** la espera y el freno del operador van en los términos de servicio (el operador interviene en el dinero del
  colegio solo para frenar, nunca para aplicar).

## 9. Terminado cuando
La Promotoría aprueba y aplica lo suyo en un paso, con firma y avisos, y aprueba lo de las cuentas que creó. Lo sensible le pide
un código que llega a su celular. Una cuenta bancaria nueva espera 24 horas, salvo que un socio la apruebe, y se puede frenar. Su
propio celular no lo cambia sola. Lo que piden los demás roles sigue con dos personas. El CI del PR en borrador está en verde (117
triggers, 20 funciones, 137 huellas).
