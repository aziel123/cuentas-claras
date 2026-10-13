# Alta de un colegio en la plataforma (guía del operador)

> Sprint 8, tanda 3 (sección 13.3 del diseño; secciones 3.2 a 3.5). Para el **operador de la plataforma** (una persona de
> `CC_OPERADORES`). El operador da de alta el colegio y su primera Promotoría **una sola vez**; después **no puede entrar** al
> colegio ni ver los datos de sus familias (lo exigen la aplicación y la base de datos).

## Antes de la reunión
1. **El contrato firmado** por el representante legal: términos de servicio y encargo de tratamiento
   (`docs/entrega/`). Sin contrato no hay alta (decisión 137).
2. **Los datos de la ficha**, sacados del contrato (no de un mensaje):
   - nombre comercial, razón social, **RUC** (11 dígitos; la plataforma valida el dígito verificador), dirección fiscal y
     correo institucional;
   - niveles (Inicial, Primaria, Secundaria) y el año escolar inicial (año, inicio y fin de clases);
   - el **código del contrato**;
   - el **titular** de la primera Promotoría: nombre, **DNI** y su celular o correo (el que use en la reunión);
   - el logo (PNG o JPEG, opcional).
3. El **correo externo de control**: del contador o de un tercero de confianza que diga el contrato. Nunca el del operador.
4. Coordinar una **reunión de puesta en marcha** (en persona o por videollamada) con el titular y, si se puede, con quien será
   Dirección y Administración.

## En la reunión
1. En `/plataforma/colegios/nuevo`, el operador llena la ficha y la confirma (con la firma de su sesión). Se crean, en este
   orden: el colegio (en preparación), la firma, la cuenta del titular con su rol de Promotoría y su enlace de activación
   (48 horas), y el año escolar y el logo. Si algo falla, la ficha dice qué falta y se reintenta con «Completar alta».
2. **El titular activa su cuenta frente al operador:** abre el enlace que le llegó, escribe **su DNI** (el del contrato) y
   elige su clave. Ve «Tu colegio y tu cuenta de Promotoría los creó el operador … el …».
   - Si el contacto tenía un error de tipeo: «Corregir contacto y reenviar» (solo antes de activar). Le llega un aviso al
     contacto anterior y al correo institucional.
3. El operador registra el **correo externo de control** (con su firma). Ese correo recibe el resumen diario, la huella de la
   bitácora y el aviso del paso a producción.
4. **Desde aquí el operador ya no puede crear cuentas en ese colegio** (lo rechaza la base). Las siguientes las crea la
   Promotoría desde «Usuarios y roles».

## Después: el asistente de puesta en marcha (lo hace el colegio)
La Promotoría, Dirección y Administración completan `/puesta-en-marcha`; el operador ve la misma lista como sí/no y conteos en
`/plataforma/colegios/{id}`, **sin nombres, documentos, contactos ni montos**:

| # | Paso | Quién |
|---|---|---|
| 1 | Datos y logo del colegio | Operador en el alta; después, Promotoría |
| 2 | Equipo con segregación: una Dirección y al menos una Caja, personas distintas de la Promotoría | Promotoría |
| 3 | Año escolar y secciones | Administración o Dirección |
| 4 | Un plan de pensiones aprobado por nivel | Administración propone, otra persona aprueba |
| 5 | Alumnos (Excel) | Administración |
| 6 | Saldo inicial confirmado, o «el colegio empieza sin deudas anteriores» firmado | Administración y Promotoría |
| 7 | Comprobantes: OSE real probado, o «uso el comprobante simulado» firmado | Administración propone, otra persona aprueba |
| 8 | Cuenta bancaria | Promotoría y otra persona |
| 9 | WhatsApp y correo (un mensaje entregado a la Promotoría) | Automático |
| 10 | Correo externo de control | Operador |
| 11 | Aviso de privacidad del colegio aprobado | Dirección |
| 12 | Respaldo de la instalación al día | Automático |
| 13 y 14 | Pago en línea y recaudación (opcionales) | Promotoría y otra persona |

Con la lista completa, **la Promotoría** pasa el colegio a producción con su firma (nadie más puede, y no hay vuelta atrás).
Desde entonces se registran pagos y salen los avisos a las familias.

**Acompañamiento:** 2 semanas en paralelo al cuaderno o al sistema anterior (como el piloto del Colegio Virgen María), con
una llamada semanal para revisar las alertas y el resumen diario.

## Si el colegio pierde a su única Promotoría (decisión 115)
Fuera del sistema: carta del representante legal y un procedimiento del responsable técnico con **acta** (la nueva cuenta
de Promotoría la crea el DBA con las mismas reglas de la base). Por eso se recomienda al colegio tener **2 cuentas de
Promotoría**, o Promotoría y una Dirección de confianza.

## Lo que el operador no puede hacer (y por qué)
- Entrar a cualquier pantalla de un colegio (403): no ve familias, pagos ni morosos.
- Crear una segunda cuenta en un colegio que ya tiene su primera Promotoría, ni ponerse de titular sin que el titular
  real lo note (el DNI del contrato, el correo institucional y la bitácora del colegio, que nombra al operador).
- Pasar un colegio a producción, cambiar su RUC en producción o volverlo a preparación.
- Cambiar los conectores (adónde entra el dinero): eso lo propone y aprueba el colegio.
