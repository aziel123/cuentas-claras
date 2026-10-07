# Continuar el proyecto en tu PC con Claude Code

> Guía para seguir el desarrollo de **Cuentas Claras** en local, sin la sesión en la nube. Escrita el 7 de octubre de 2026, al cerrar el sprint 4.

## 1. Dónde está todo
Todo el trabajo está en GitHub, en `aziel123/cuentas-claras`. Cada sprint tiene su rama, y cada rama parte de la anterior y contiene todo lo previo:

| Rama | Contenido |
|---|---|
| `main` | Solo el sprint 0, el proyecto base |
| `claude/sprint-1-fundaciones` … `claude/sprint-3-correcciones` | Sprints 1 a 3 |
| **`claude/sprint-4-cero-digitacion`** | **La más completa: sprints 0 a 4, con 1427 pruebas en verde.** Desde aquí se continúa. |

Lo importante para retomar:
- `CLAUDE.md`: reglas del proyecto. Claude Code lo lee solo al abrir la carpeta.
- `.claude/agents/` y `.claude/skills/`: agentes y skills del proyecto. También se cargan solos.
- `docs/plan-de-desarrollo.md`: el plan, con los sprints reordenados; sigue el **sprint 5**.
- `docs/estado-del-proyecto.md`: qué está hecho, decisiones pendientes con el colegio y riesgos.
- `docs/arquitectura/`: el diseño y las correcciones de cada sprint.
- `docs/operacion/instalacion-local.md`: cómo levantar la plataforma con Docker.
- `docs/video/`: fuente del video de presentación.

## 2. Requisitos en Windows
- **Git** y **Docker Desktop**, que ya tienes.
- **Java 21**, para correr las pruebas y la app sin Docker: https://adoptium.net (Temurin 21, instalador .msi).
- **Claude Code**: sigue la guía oficial en https://code.claude.com/docs/en/setup. En Windows suele bastar con abrir PowerShell y ejecutar `irm https://claude.ai/install.ps1 | iex`; si tienes Node, también funciona `npm install -g @anthropic-ai/claude-code`.

## 3. Actualizar tu carpeta
En PowerShell:
```powershell
cd C:\Users\amedina\cuentas-claras\cuentas-claras
git fetch
git checkout claude/sprint-4-cero-digitacion
git pull
```
Tu archivo `.env` no se toca, porque Git lo ignora.

Para comprobar que todo funciona, corre las pruebas: `.\mvnw.cmd -B verify`. Tardan unos minutos y deben terminar con `BUILD SUCCESS`.

## 4. Abrir Claude Code en la carpeta
```powershell
cd C:\Users\amedina\cuentas-claras\cuentas-claras
claude
```
Para comprobar que cargó todo, pregúntale «¿qué agentes y skills tiene este proyecto?». Debe nombrar 6 agentes (arquitecto-software, backend-spring, qa-tester, auditor-seguridad-antifraude, disenador-ui e investigador-ux) y 4 skills.

## 5. Primer mensaje para seguir con el sprint 5
Copia y pega esto en el nuevo chat:

```text
Lee CLAUDE.md, docs/CONTINUAR-EN-LOCAL.md, docs/plan-de-desarrollo.md y docs/estado-del-proyecto.md.
Vamos a hacer el sprint 5 «Familias y matrícula 2027», con el mismo método de los sprints anteriores:

1. Crea la rama claude/sprint-5-familias desde claude/sprint-4-cero-digitacion.
2. Usa el agente arquitecto-software para escribir docs/arquitectura/sprint-5-familias.md, con el mismo nivel de
   detalle que sprint-4-cero-digitacion.md: modelo y migraciones desde V17, máquinas de estado, permisos por rol,
   cambios de MySQL (02-permisos-tablas.sql, 03-triggers.sql y el verificador), escenarios de fraude con su prueba,
   3 tandas verificables y decisiones para el colegio con su valor por defecto.
   Alcance:
   - Notificaciones por WhatsApp Business (con correo de respaldo) en cada pago, anulación y descuento, y
     recordatorios automáticos antes de cada vencimiento, sin bloquear evaluaciones (INDECOPI). Conector
     simulado por defecto mientras el colegio verifica su cuenta de WhatsApp.
   - Clave temporal directo al titular por WhatsApp o correo; nadie más la ve (hallazgo A2 del sprint 1 y
     S4-M2 del sprint 4).
   - Huella diaria de la bitácora a Promotoría.
   - Portal de familias para celular: estado de cuenta, cuotas, boletas descargables, pago en línea (ya existe
     desde el sprint 4) e historial de mensajes.
   - Matrícula 2027: renovación, cronograma 2027 y cobro de la matrícula.
   - Pendientes del sprint 4: semilla secreta en el muestreo de caja del sprint 3, feriados en el cálculo del
     día hábil y riesgos residuales de docs/arquitectura/sprint-4-correcciones.md.
3. Implementa cada tanda con el agente backend-spring, con ./mvnw -B verify en verde y commit por tanda.
4. Al terminar, corre en paralelo qa-tester y auditor-seguridad-antifraude, cada uno en su propio git worktree
   para no pisarse. QA marca con @Disabled las pruebas que revelan errores; el auditor reproduce cada ataque.
5. Corrige todos los hallazgos con backend-spring, activa las pruebas @Disabled y convierte los ataques en pruebas
   que demuestren que ya fallan. Documenta en docs/arquitectura/sprint-5-correcciones.md.
6. Verifica tú mismo la suite completa, actualiza docs/estado-del-proyecto.md y haz push de la rama.
No abras PR ni hagas merge a main sin preguntarme.
```

## 6. Cómo trabajamos (para que el nuevo chat siga igual)
- **Un sprint = diseño, 3 tandas, QA y auditoría en paralelo, y correcciones.** Nunca se codea sin el diseño del arquitecto.
- **Reglas no negociables:**
  - dinero en `BigDecimal` con escala 2;
  - nada se borra ni se edita, se anula con motivo y aprobación;
  - todo queda en la bitácora inmutable;
  - `colegioId` en todo;
  - quien cobra no aprueba;
  - las confirmaciones sensibles se hacen a ciegas;
  - lo que entra solo lo registra un actor de sistema (`sistema.*`), nunca una persona.
- **Base de datos:** el esquema cambia solo con migraciones nuevas. No se editan las publicadas; la última es V16. Todo permiso o trigger nuevo va en `scripts/mysql/02-permisos-tablas.sql` y `03-triggers.sql` (hoy hay 41) y en `VerificadorPermisosBaseDatos`. Si falta alguno, producción no arranca.
- **Conectores externos** (pasarela, OSE, banco, WhatsApp): siempre con un simulado por defecto, y el real solo con credenciales. El simulado nunca debe poder actuar en producción.
- **Pantallas:** blanco predominante, con azul y celeste como acentos (skill `sistema-diseno`), sin estilos ni scripts en línea (lo impide la CSP) y pensadas para celular.

## 7. Pruebas con MySQL real (opcional)
Las 46 pruebas de MySQL (`MigracionMySqlTest` y `PermisosMySqlTest`) se omiten si no hay MySQL. Para correrlas en tu PC, replica los pasos del job `mysql` de `.github/workflows/`: crear usuarios con `01`, migrar, aplicar `02` y `03`, comprobar, y luego las pruebas con la variable `CC_PRUEBA_MYSQL`. Los detalles están en `docs/operacion/mysql-usuarios.md`. También corren solas en GitHub cuando se abra un PR.

## 8. Pendientes fuera del código
- **Abrir el PR hacia `main`**, para que el CI de GitHub corra por primera vez, incluido el job de MySQL.
- **Trámites del colegio:** verificación de WhatsApp Business, contrato con un OSE (por ejemplo Nubefact), pasarela de pagos (por defecto Culqi) y recaudación con el banco.
- **Confirmar con el colegio las decisiones** de `docs/estado-del-proyecto.md`. Todas tienen un valor por defecto ya implementado.
- **Elegir el hosting** (decisión D3).
- **Video de presentación:** falta grabar la voz en off con `docs/video/Guion-narracion.md` y, si quieren, agregar música de fondo.
