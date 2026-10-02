# Plan de desarrollo: Cuentas Claras para el Colegio Virgen María

> Plataforma de gestión escolar con **cobranza antifraude** como primer módulo.
> Marca: azul `#1b4f9c` y celeste `#38aee6` (detalle en la skill `sistema-diseno`).
> Referencia visual: `docs/prototipo/cuentas-claras.html`.
> Fecha del plan: 2 de octubre de 2026.

## Meta

**Que la matrícula 2027 (diciembre a febrero) se cobre íntegramente en el sistema.** El colegio empieza el año 2027 sin cuaderno de cobros, con cada sol trazable desde el primer día.

| Hito | Fecha objetivo | Qué significa para el colegio |
|---|---|---|
| **H1 · Caja controlada** | 27 nov 2026 | Caja cobra en el sistema, emite comprobante y cierra caja todos los días. Funciona en paralelo al cuaderno durante 2 semanas. |
| **H2 · Matrícula 2027** | 11 dic 2026 | La matrícula 2027 se cobra en el sistema. Las familias reciben WhatsApp y ven su estado de cuenta. Se deja el cuaderno. |
| **H3 · Control total** | 5 feb 2027 | Pagos digitales, panel de la promotora, conciliación bancaria y auditoría de seguridad aprobada. |
| **H4 · Académico** | Mar–abr 2027 | Asistencia y comunicados al iniciar las clases. Notas por competencias en el primer bimestre. |

---

## Decisiones que hay que tomar antes de empezar

| # | Decisión | Recomendación | Por qué |
|---|---|---|---|
| D1 | **Versión del stack** | ✅ **Decidido (2 oct):** Spring Boot 4.1.1, Java 21, MySQL 8, Thymeleaf, Flyway. | El proyecto anterior usaba Spring Boot 2.6 y Java 11, que **ya no reciben parches de seguridad**. |
| D2 | **Repositorio** | ✅ **Decidido (2 oct):** repositorio propio `aziel123/cuentas-claras`. | Separa el entregable del proyecto de clase anterior. |
| D3 | **Hosting** | Un proveedor administrado (aplicación + MySQL administrado con respaldos diarios) en una región cercana a Perú. | Respaldos y certificados automáticos. Nada corre en una PC del colegio. |
| D4 | **Comprobantes electrónicos** | Integrarse con el proveedor OSE/PSE que el colegio ya use. Si no tiene ninguno, elegir uno con API (por ejemplo, Nubefact). | El comprobante al instante es una regla antifraude obligatoria. |
| D5 | **WhatsApp** | WhatsApp Business Platform con plantillas aprobadas. Correo electrónico como respaldo. | La verificación del negocio **tarda semanas**: hay que iniciarla en el sprint 0. |
| D6 | **Pagos digitales** | Una pasarela que acepte Yape, Plin y tarjeta, más la recaudación bancaria con código de alumno. | Confirma el pago automáticamente, sin depender de capturas de pantalla. |
| D7 | **Saldo inicial de deudas** | Cargar las deudas pendientes **validadas por el contador**, no copiadas del cuaderno. | Después del robo, el cuaderno no es confiable. |

---

## Sprints (de 2 semanas)

Cada funcionalidad sigue el flujo de agentes del proyecto:
`investigador-ux` → `arquitecto-software` → `disenador-ui` → `backend-spring` → `qa-tester` → `auditor-seguridad-antifraude`.

### Sprint 0 · Arranque (5 – 16 oct)
- Reunión de descubrimiento con el colegio usando el kit de `docs/ux/` y llenado de la ficha de datos.
- Tomar las decisiones D1 a D7.
- **Iniciar los trámites largos**: verificación de WhatsApp Business, cuenta con el OSE, pasarela y Yape/Plin empresarial.
- Crear el proyecto base (D1, D2): Maven, perfiles `dev` y `prod`, migraciones de base de datos con Flyway, CI en GitHub Actions (compilar y probar en cada PR) y despliegue a un entorno de pruebas.
- **Terminado cuando:** el proyecto vacío se despliega solo al hacer merge y el colegio aprobó el alcance.

### Sprint 1 · Fundaciones (19 – 30 oct)
- **Seguridad**: inicio de sesión, contraseñas con BCrypt y roles (`PROMOTOR`, `DIRECTOR`, `ADMINISTRACION`, `CAJA`, `DOCENTE`, `APODERADO`). Permisos en cada endpoint.
- **Multi-colegio**: `colegioId` en cada entidad de negocio y filtro obligatorio en las consultas.
- **Auditoría inmutable**: tabla de solo inserción y un servicio que registra cada operación sensible.
- **Interfaz base**: layout Thymeleaf con los tokens azul y celeste, y fragmentos de botón, tabla, badge, alerta y modal de confirmación.
- **Terminado cuando:** cada rol entra y ve solo su menú, y el auditor no encuentra endpoints sin protección.

### Sprint 2 · Datos del colegio (2 – 13 nov)
- Año escolar, niveles, grados, secciones, alumnos y apoderados (con relación familia–alumnos).
- **Importación desde Excel** de alumnos y apoderados. Es clave para la adopción: nadie tiene que digitar 300 alumnos.
- Configuración de matrícula y pensiones por nivel, con fechas de vencimiento.
- **Generación automática del cronograma de cuotas.** Caja no decide montos.
- Carga del saldo inicial validado (D7).
- **Terminado cuando:** los datos reales del colegio están cargados y cada alumno tiene su cronograma correcto, revisado con el contador.

### Sprint 3 · Caja (16 – 27 nov) → **H1**
- Buscar alumno por nombre o DNI y ver sus cuotas.
- Registrar pago (uno o varios conceptos) → comprobante electrónico → auditoría.
- **Anulación solo con aprobación** de Dirección, con motivo y nota de crédito.
- Descuentos y becas: Administración los solicita y Dirección los aprueba.
- **Cierre de caja diario**: esperado contra contado, con explicación obligatoria si hay diferencia.
- **Terminado cuando:** pasan las pruebas de QA con los escenarios de fraude y el auditor confirma que caja no puede borrar, editar ni autoaprobar nada.
- **Piloto**: 2 semanas en paralelo al cuaderno, conciliando ambos cada día.

### Sprint 4 · Familias y matrícula 2027 (30 nov – 11 dic) → **H2**
- Notificación por WhatsApp (y correo de respaldo) en cada pago, anulación y descuento.
- Portal de familias, pensado para celular: estado de cuenta, cuotas, comprobantes descargables e historial de mensajes.
- Proceso de **matrícula 2027**: renovación, generación del cronograma 2027 y cobro de la matrícula.
- **Terminado cuando:** una familia real paga la matrícula 2027 y recibe su comprobante por WhatsApp. El cuaderno se archiva.

### Sprint 5 · Panel de la promotora (14 dic – 8 ene, con feriados)
- Panel para celular: cobrado hoy y en el mes, deuda vencida, familias morosas y % de pagos digitales.
- **Alertas**: caja con diferencia, anulaciones pendientes y cierre no realizado a cierta hora.
- Reportes: morosidad por grado, ingresos por medio de pago y exportación a Excel para el contador.
- Recordatorios automáticos de deuda. Sin bloquear evaluaciones, según INDECOPI.
- **Terminado cuando:** la promotora usa el panel a diario sin pedir reportes a nadie.

### Sprint 6 · Pagos digitales y conciliación (11 – 22 ene)
- Integración con la pasarela (Yape, Plin, tarjeta): confirmación automática, comprobante y WhatsApp.
- Recaudación bancaria por código de alumno: importar el archivo del banco y aplicar los pagos.
- **Conciliación bancaria**: pagos registrados frente a movimientos del banco, con alertas de diferencias.
- **Terminado cuando:** un pago con Yape se refleja solo, sin que nadie lo digite, y la conciliación del mes cuadra.

### Sprint 7 · Endurecimiento y entrega (25 ene – 5 feb) → **H3**
- Auditoría de seguridad completa: OWASP, IDOR entre familias y entre colegios, y datos personales (Ley 29733).
- Respaldos diarios probados (restaurar un respaldo de verdad), monitoreo y registro de errores.
- Manuales de una página por rol, videos cortos y capacitación presencial.
- **Terminado cuando:** el auditor no reporta hallazgos críticos ni altos, un respaldo se restauró con éxito y el colegio firma la conformidad.

### Fase académica (marzo – abril 2027) → **H4**
- Asistencia diaria con aviso a los padres. Comunicados con confirmación de lectura.
- Notas por competencias del Currículo Nacional, libretas en PDF y exportación para SIAGIE.
- Integración con Google Workspace for Education o Microsoft 365 A1 como aula virtual (no se construye una propia).

---

## Plan de adopción (en paralelo a los sprints)

| Momento | Con quién | Qué se hace |
|---|---|---|
| Sprint 2 | Dirección y Administración | Validar los datos cargados y las pensiones. Ellos aprueban su información. |
| Sprint 3 (piloto) | Caja | Acompañamiento presencial los primeros días. Cierre de caja juntos. |
| Sprint 4 | Familias | Comunicado del colegio: "desde ahora recibirá su comprobante por WhatsApp". Guía de una página para pagar con Yape. |
| Sprint 5 | Promotora | Sesión de 30 minutos con el panel en su celular. |
| Sprint 7 | Todo el personal | Capacitación por rol y manuales impresos. |
| Marzo | Docentes | Asistencia y comunicados desde el celular. |

**Métricas de adopción** (de la skill `evaluacion-ux`): al menos 60 % de pagos digitales al tercer mes, al menos 70 % de familias con el portal activado y 100 % de cierres de caja explicados el mismo día.

---

## Definición de terminado (para cada funcionalidad)
- [ ] Cumple las reglas antifraude de `contexto-colegio`.
- [ ] Dinero en `BigDecimal`, sin borrados físicos, con auditoría y filtro por `colegioId`.
- [ ] Pruebas unitarias y de integración que pasan en CI.
- [ ] Revisada por `auditor-seguridad-antifraude`, sin hallazgos críticos ni altos abiertos.
- [ ] Pantallas con el sistema de diseño (azul y celeste), accesibles (AA) y probadas en celular.
- [ ] Textos revisados con el microcopy de `evaluacion-ux`.

## Riesgos principales

| Riesgo | Mitigación |
|---|---|
| La aprobación de WhatsApp o del OSE se demora | Iniciar los trámites en el sprint 0. Usar correo de respaldo y comprobante PDF mientras tanto. |
| Datos iniciales incorrectos | Saldo inicial validado por el contador (D7). Piloto en paralelo al cuaderno. |
| Resistencia del personal | Acompañamiento presencial y pantallas simples. El sistema les ahorra trabajo, no les suma. |
| Internet inestable en el colegio | Pantallas ligeras. El portal de familias funciona con datos móviles. Contar con un plan B de conectividad (router 4G). |
| Plazo de matrícula muy justo | Si el H2 se retrasa, se prioriza cobrar la matrícula en caja (H1), y el portal y WhatsApp llegan después. |
