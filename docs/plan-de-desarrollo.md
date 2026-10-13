# Plan de desarrollo: Cuentas Claras para el Colegio Virgen María

> Plataforma de gestión escolar con **cobranza antifraude** como primer módulo.
> Marca: azul `#1b4f9c` y celeste `#38aee6` (detalle en la skill `sistema-diseno`).
> Referencia visual: `docs/prototipo/cuentas-claras.html`.
> Fecha del plan: 2 de octubre de 2026.
> Actualizado el 10 de octubre de 2026: fase 2 (sprints 8 a 10), con la versión 1 terminada en `main`.

## Meta

**Que la matrícula 2027 (diciembre a febrero) se cobre íntegramente en el sistema.** El colegio empieza el año 2027 sin cuaderno de cobros, con cada sol trazable desde el primer día.

| Hito | Fecha objetivo | Qué significa para el colegio |
|---|---|---|
| **H1 · Caja controlada** | 27 nov 2026 | Caja cobra en el sistema, emite comprobante y cierra caja todos los días. Funciona en paralelo al cuaderno durante 2 semanas. |
| **H2 · Cero digitación** | 11 dic 2026 | Los pagos digitales y bancarios entran solos, el banco se concilia subiendo un archivo y cada pago tiene su comprobante electrónico. |
| **H3 · Familias y control total** | 5 feb 2027 | La matrícula 2027 se cobra en el sistema, las familias reciben WhatsApp y usan el portal, la promotora tiene su panel y la auditoría de seguridad está aprobada. Se deja el cuaderno. |
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

> **Reordenamiento (6 oct 2026, decisión del colegio):** la prioridad es no sumar trabajo manual al personal. Por eso los pagos digitales y la conciliación automática pasan al sprint 4, y las familias y el panel quedan en los sprints 5 y 6. Los sprints 0 a 3 terminaron con unas 7 semanas de adelanto, así que las fechas de abajo son el límite, no la fecha real.

### Sprint 4 · Cero digitación (30 nov – 11 dic) → **H2**
- **Pagos digitales con pasarela** (Yape, Plin, tarjeta): la pasarela confirma el pago, el sistema lo registra solo, emite el comprobante y lo deja en la auditoría. Nadie lo digita.
- **Recaudación bancaria por código de alumno**: el padre paga en el banco o en su app con el código del alumno; el archivo diario del banco se importa y los pagos se aplican solos.
- **Conciliación automática con el extracto bancario**: Administración sube el extracto una vez al día y el sistema empareja todos los movimientos. Solo se revisan las diferencias. Reemplaza la conciliación a mano del sprint 3.
- **Comprobante electrónico automático vía OSE**: cada pago se envía al OSE y queda su estado (aceptado, observado o rechazado). Mientras el colegio termina el trámite, funciona con un conector simulado.
- **Terminado cuando:** un pago con Yape se refleja solo, sin que nadie lo digite, y la conciliación del día cuadra subiendo un solo archivo.

### Sprint 5 · Familias y matrícula 2027 (14 dic – 8 ene, con feriados)
- Notificación por WhatsApp (y correo de respaldo) en cada pago, anulación y descuento. **Recordatorios automáticos** antes de cada vencimiento, sin bloquear evaluaciones, según INDECOPI.
- **Clave temporal directo al titular** (cierra el hallazgo A2 de la auditoría del sprint 1): al crear un usuario o restablecer su clave, la clave temporal se envía por WhatsApp o correo al titular y **ya no se muestra a quien la generó**. Mientras tanto rige la mitigación del sprint 1: la clave temporal vence a las 48 horas, el titular ve quién restableció su clave, Promotoría tiene la tarjeta «Para revisar» y la bitácora marca esos cambios para revisar.
- **Huella diaria de la bitácora** a Promotoría por WhatsApp o correo (número de evento, fecha y código).
- Portal de familias, pensado para celular: estado de cuenta, cuotas, comprobantes descargables, pago en línea e historial de mensajes.
- Proceso de **matrícula 2027**: renovación, generación del cronograma 2027 y cobro de la matrícula.
- **Terminado cuando:** una familia real paga la matrícula 2027 desde su celular y recibe su comprobante por WhatsApp. El cuaderno se archiva.

### Sprint 6 · Panel de la promotora (11 – 22 ene)
- Panel para celular: cobrado hoy y en el mes, deuda vencida, familias morosas y % de pagos digitales.
- **Resumen diario** automático y **aprobaciones desde el celular**.
- **Alertas**: caja con diferencia, anulaciones pendientes y cierre no realizado a cierta hora.
- Reportes: morosidad por grado, ingresos por medio de pago y exportación a Excel para el contador.
- **Terminado cuando:** la promotora usa el panel a diario sin pedir reportes a nadie.

### Sprint 7 · Endurecimiento y entrega (25 ene – 5 feb) → **H3**
- Auditoría de seguridad completa: OWASP, IDOR entre familias y entre colegios, y datos personales (Ley 29733).
- Respaldos diarios probados (restaurar un respaldo de verdad), monitoreo y registro de errores.
- Manuales de una página por rol, videos cortos y capacitación presencial.
- **Terminado cuando:** el auditor no reporta hallazgos críticos ni altos, un respaldo se restauró con éxito y el colegio firma la conformidad.

### Fase académica (marzo – abril 2027) → **H4**
- Asistencia diaria con aviso a los padres. Comunicados con confirmación de lectura.
- Notas por competencias del Currículo Nacional, libretas en PDF y exportación para SIAGIE.
- Integración con Google Workspace for Education o Microsoft 365 A1 como aula virtual (no se construye una propia).

> **Desde el 10 de octubre de 2026** esta fase se reparte en los sprints 9 y 10 de la fase 2 (abajo). La integración con Google Workspace for Education o Microsoft 365 A1 queda para después del sprint 10.

---

## Fase 2 · Culminar la plataforma y salir a vender (desde el 12 de octubre de 2026)

> Agregado el 10 de octubre de 2026. La versión 1 (sprints 0 a 7) está en `main`. La fase 2 prepara la plataforma para venderla a otros colegios privados del Perú con tres bloques: **lista para vender**, **fase académica** y **comunicación con los padres**. Diseño del sprint 8: `docs/arquitectura/sprint-8-lista-para-vender.md`.

### Por qué este orden
1. **Primero, lista para vender (sprint 8).**
   - La ventana de compra de los colegios va de setiembre a febrero y ya está abierta, y la CADEP de ADECOPA es a mediados de noviembre (estudio de mercado).
   - Sin una demo pública, sin el alta de un colegio sin tocar la base y sin un despliegue en internet, no se puede vender ni operar un segundo colegio.
   - Los colegios nuevos tienen que estar en producción antes de cobrar la matrícula 2027 (diciembre a febrero).
2. **Después, asistencia y comunicados (sprint 9).**
   - Los usan docentes y familias desde el primer día de clases (mediados de marzo).
   - Aprovechan lo que ya existe: los mensajes por WhatsApp con correo de respaldo, el portal y las plantillas por colegio del sprint 8.
   - Se agrega la **nómina de matrícula para el SIAGIE**, porque la matrícula se registra en el SIAGIE al inicio del año, antes que las notas.
3. **Al final, notas, libretas y SIAGIE (sprint 10).**
   - Las notas recién se usan al cierre del primer bimestre o trimestre (mayo).
   - Dependen de normas que hay que confirmar antes (escalas, competencias por grado y la plantilla de carga del SIAGIE).
   - Entre el sprint 9 y el 10 queda una **ventana de matrícula** sin funciones nuevas, para dar de alta colegios y acompañar la matrícula 2027.

**Ajuste respecto de la propuesta inicial:** la nómina de matrícula para el SIAGIE pasa del sprint 10 al 9. El 10 queda con las notas, las libretas y la exportación de notas.

### Hitos de la fase 2
| Hito | Fecha objetivo | Qué significa |
|---|---|---|
| **H5 · Lista para vender** | 6 nov 2026 | Demo pública en internet, alta de un colegio desde la plataforma sin tocar la base, instalación compartida desplegada y reproducible, y plantillas de contrato listas para el abogado |
| **H6 · Asistencia y comunicados** | 4 dic 2026 | Listos para usarse desde el primer día de clases de 2027, con la nómina para el SIAGIE |
| **H7 · Notas, libretas y SIAGIE** | 12 feb 2027 | Listos para el primer bimestre o trimestre de 2027 |

### Decisiones nuevas
| # | Decisión | Recomendación | Por qué |
|---|---|---|---|
| D3 | **Hosting** (se concreta) | Google Cloud en Santiago: VM con la aplicación y Cloud SQL para MySQL 8.4, unos US$ 65-75 al mes por instalación. **Antes, el paso 0: probar los 75 triggers en Cloud SQL** | Es la región de nube grande más cercana a Lima, y la marca `log_bin_trust_function_creators` está documentada (decisión 110 del sprint 8) |
| D8 | **Instalación compartida o dedicada** | Compartida para los colegios nuevos; el Virgen María, dedicada | Costo por colegio bajo, sin cambiar la custodia que se acordó con el Virgen María (decisiones 108 y 109) |
| D9 | **Nombre comercial** | Elegirlo **antes de la tanda 3 del sprint 8**, porque bloquea el dominio, el nombre visible de WhatsApp y la publicidad. En el código es un solo parámetro | «Cuentas Claras» no gusta (decisión 122) |
| D10 | **Suscripción** | Fuera del sistema al inicio (contrato y factura mensual) | Pocos clientes; no es un vector de fraude contra el colegio (decisión 128) |
| D11 | **Abogado** | Revisar los términos de servicio y el contrato de encargo antes del primer contrato | Ley 29733, Ley 32323 y DL 1044 (estudio de mercado) |
| D12 | **Normas académicas** | Confirmar con la Dirección de un colegio piloto, **antes del diseño del sprint 10**: las escalas de calificación vigentes, las competencias por grado, los periodos (bimestre o trimestre) y la plantilla de carga del SIAGIE | Es normativa del MINEDU que cambia y que no se verificó |

### Método (igual que en la versión 1)
Cada sprint sigue este orden:
1. Diseño (`arquitecto-software`, con `investigador-ux` y `disenador-ui` antes cuando hay pantallas nuevas).
2. **3 tandas** verificadas, en H2 y en MySQL 8 real, cada una con su migración.
3. **QA y auditoría en paralelo** (`qa-tester` y `auditor-seguridad-antifraude`).
4. **Correcciones**, con una prueba por hallazgo.

### Sprint 8 · Lista para vender (12 oct – 6 nov) → **H5**
Diseño completo: `docs/arquitectura/sprint-8-lista-para-vender.md`.
- **Paso 0 (medio día):** probar los 75 triggers, el rol `cc_negocio` y el verificador en una instancia de prueba de Cloud SQL. Si falla, se cambia de proveedor antes de escribir código.
- **Tanda 1 · Plataforma, alta y marca (V28):**
  - rol «Operador de la plataforma», que no pertenece a ningún colegio y no ve datos de las familias;
  - alta de un colegio con su RUC, razón social, logo, niveles y año escolar;
  - primera Promotoría una sola vez, con enlace de activación al titular, que confirma con su DNI;
  - estados del colegio: «en preparación» (no cobra) y «en producción»;
  - marca del producto en un solo parámetro, y marca del colegio en su portal, sus boletas y sus mensajes.
- **Tanda 2 · Puesta en marcha y conectores (V29):**
  - asistente con lista de verificación: equipo con segregación, año y secciones, pensiones, alumnos desde Excel, saldo inicial, OSE, cuenta bancaria, pasarela y recaudación;
  - conectores por colegio, con secretos cifrados y doble control: cambiar adónde entra el dinero lo aprueba otra persona y se avisa a toda la Promotoría;
  - paso a producción con la firma de la Promotoría.
- **Tanda 3 · Demo, despliegue y documentos (V30):**
  - demo pública con un colegio ficticio por visitante, franja DEMO, sin salida a internet y reinicio diario;
  - respaldos de la instalación compartida («Faltan filas» por colegio; custodia de la plataforma);
  - despliegue reproducible en Google Cloud: imagen construida en el CI, Caddy con TLS, Secret Manager, y `cc_app`, `cc_sistema` y `cc_respaldo` en Cloud SQL;
  - plantillas de términos de servicio y de contrato de encargo (Ley 29733) en `docs/entrega/`;
  - suscripción fuera del sistema.
- **QA, auditoría y correcciones** (2 – 6 nov).
- **Terminado cuando:** la demo está en internet y un promotor la recorre solo desde su celular; un segundo colegio se dio de alta en la instalación compartida sin tocar la base a mano, completó su lista y pasó a producción; el operador no pudo ver ningún dato de sus familias; el despliegue se reprodujo desde cero en un proyecto limpio; las plantillas legales están listas para el abogado; y el auditor no reporta hallazgos críticos ni altos.

### Sprint 9 · Asistencia y comunicados (9 nov – 4 dic) → **H6**
Módulos `academico` (nuevo) y `comunicacion`. Cobranza no depende de ninguno de los dos. El docente nunca ve deudas ni pagos (INDECOPI y acceso mínimo).
- **Tanda 1 · Docentes y secciones:** asignación de docentes y tutores a secciones y cursos por año escolar, que aprueba Dirección. El docente ve solo a los alumnos de sus secciones, sin documentos ni contactos completos.
- **Tanda 2 · Asistencia diaria con aviso a los padres:**
  - el docente o tutor marca desde el celular (presente, tarde, falta o falta justificada) en menos de 1 minuto por sección; un registro por sección y día;
  - cambiarlo después del día lo aprueba Dirección y queda en la bitácora;
  - el apoderado del alumno ausente o tardío recibe el aviso por WhatsApp (con correo de respaldo) el mismo día, a una hora fija;
  - alerta a Dirección si una sección no registró a cierta hora;
  - reporte mensual por alumno y sección; el apoderado puede justificar desde el portal.
- **Tanda 3 · Comunicados con confirmación de lectura y nómina para el SIAGIE:**
  - comunicados de Dirección (a todo el colegio, un nivel, un grado o una sección) y del docente (a sus secciones), con texto y un PDF adjunto validado;
  - aviso por WhatsApp con el título, y lectura en el portal;
  - la familia confirma «Leído»: la confirmación es explícita, no el doble check de WhatsApp;
  - estadísticas de enviado, entregado, leído y confirmado, y recordatorio a 48 h a quien no confirmó;
  - un comunicado enviado no se edita: se envía una fe de erratas;
  - **nómina de matrícula exportable para el SIAGIE**, si se confirma el formato (D12).
- **Terminado cuando:** un docente registra la asistencia de su sección desde el celular en menos de 1 minuto; el apoderado del alumno ausente recibe el aviso ese mismo día; un comunicado de Dirección muestra cuántas familias confirmaron la lectura; la nómina de una sección sale en el formato del SIAGIE; y el auditor confirma que un docente no ve deudas, ni alumnos de otras secciones ni de otro colegio.

### Ventana de matrícula (7 dic 2026 – 8 ene 2027): sin funciones nuevas
- Altas de colegios nuevos y acompañamiento de su puesta en marcha.
- Matrícula 2027 del Colegio Virgen María y de los colegios nuevos en el sistema.
- Soporte y correcciones. Lo que se aprenda entra en el diseño del sprint 10.

### Sprint 10 · Notas por competencias, libretas y SIAGIE (11 ene – 12 feb 2027) → **H7**
Antes del diseño, la decisión D12 (normas vigentes confirmadas con un colegio piloto).
- **Tanda 1 · Currículo y periodos:**
  - áreas y competencias del Currículo Nacional por nivel y grado, en un catálogo versionado que el colegio no edita;
  - periodos del año (bimestres o trimestres, por colegio);
  - escalas de calificación por nivel, según la norma vigente.
- **Tanda 2 · Registro de notas:**
  - el docente califica por competencia a los alumnos de sus cursos y secciones, con conclusión descriptiva donde la norma la pida;
  - Dirección cierra el periodo;
  - **una nota cambiada después del cierre exige una solicitud aprobada por otra persona y queda en la bitácora.** Es la misma regla antifraude que la del dinero: nada se edita en silencio.
- **Tanda 3 · Libretas en PDF y exportación para el SIAGIE:**
  - libreta (informe de progreso) en PDF por alumno y periodo, con el logo del colegio, descargable en el portal;
  - **la libreta nunca se retiene por deuda** (INDECOPI), con una prueba que lo demuestra;
  - exportación de las notas de cada sección en la plantilla de carga del SIAGIE.
- **Terminado cuando:** un docente registra las notas de su curso por competencias; Dirección cierra el periodo; cada familia descarga su libreta en PDF desde el portal, también si tiene deuda; una nota cambiada después del cierre exige aprobación y queda en la bitácora; y el archivo de una sección real se carga sin errores en el SIAGIE.

### Adopción de cada colegio nuevo (en paralelo a los sprints)
| Momento | Con quién | Qué se hace |
|---|---|---|
| Antes del alta | Promotor (representante legal) | Firma de los términos de servicio y del contrato de encargo; datos de la ficha |
| Día 1 | Titular y operador | Alta y activación de la primera Promotoría frente al titular; correo externo de control |
| Semana 1 | Administración y Dirección | Asistente de puesta en marcha: equipo, año y secciones, pensiones, Excel de alumnos, saldo inicial validado por su contador y conectores |
| Semanas 2 y 3 | Caja y todo el personal | Capacitación (sesiones S1 a S5 del sprint 7) y 2 semanas en paralelo a su cuaderno o sistema anterior |
| Paso a producción | Promotoría | Firma el paso; comunicado a las familias: «desde ahora recibirá su comprobante por WhatsApp» |
| Cada trimestre | Promotoría y operador | Verificación de la bitácora con 3 huellas que elige la Promotoría |
| Marzo de 2027 | Docentes | Asistencia y comunicados desde el celular (sprint 9) |

---

## Plan de adopción (en paralelo a los sprints)

| Momento | Con quién | Qué se hace |
|---|---|---|
| Sprint 2 | Dirección y Administración | Validar los datos cargados y las pensiones. Ellos aprueban su información. |
| Sprint 3 (piloto) | Caja | Acompañamiento presencial los primeros días. Cierre de caja juntos. |
| Sprint 4 | Familias y Administración | Guía de una página para pagar con Yape o en el banco con el código del alumno. Administración aprende a subir el extracto diario. |
| Sprint 5 | Familias | Comunicado del colegio: "desde ahora recibirá su comprobante por WhatsApp". |
| Sprint 6 | Promotora | Sesión de 30 minutos con el panel en su celular. |
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
