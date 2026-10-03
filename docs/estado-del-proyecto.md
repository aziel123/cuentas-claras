# Estado del proyecto · Cuentas Claras

> Última actualización: 3 de octubre de 2026.
> Resumen para retomar el trabajo: qué está hecho, cómo probarlo y qué hay que decidir con el colegio.

## Avance

| Sprint | Estado | Rama | Pruebas |
|---|---|---|---|
| 0 · Arranque | ✅ Terminado | `main` | 3 |
| 1 · Fundaciones (seguridad, roles, auditoría) | ✅ Terminado, auditado y corregido | `claude/sprint-1-fundaciones` | 317 |
| 2 · Datos del colegio (alumnos, Excel, pensiones, saldo inicial) | ✅ Terminado, auditado y corregido | `claude/sprint-2-datos-colegio` | 713 |
| 3 · Caja (pagos, comprobantes, anulaciones, descuentos, cierre ciego, conciliación) | ✅ Terminado, auditado y corregido | `claude/sprint-3-correcciones` | 967 |

Las ramas están **apiladas**: cada una parte de la anterior y contiene todo su trabajo. La más completa es `claude/sprint-3-correcciones`. Ninguna está unida a `main` todavía. El CI de GitHub solo corre en `main` y en los PR, así que se ejecutará por primera vez cuando se abra el PR. Todas las pruebas, incluidas las de MySQL 8 real, se corrieron localmente durante el desarrollo.

Cada sprint siguió el mismo flujo:
1. `arquitecto-software` diseña.
2. `backend-spring` implementa en tandas verificadas.
3. `auditor-seguridad-antifraude` y `qa-tester` revisan en paralelo.
4. `backend-spring` corrige todos los hallazgos.

Los diseños están en `docs/arquitectura/`.

## Qué hace hoy la plataforma

### Seguridad y control
- Inicio de sesión con bloqueo tras 5 intentos fallidos. Los intentos en paralelo no evitan el bloqueo.
- 6 roles con permisos verificados en el servidor y combinaciones de roles prohibidas (Caja no se combina con Dirección, Promotoría ni Administración).
- Cada colegio ve solo sus datos. La base de datos también impide referencias cruzadas entre colegios.
- **Bitácora de auditoría inmutable** con sello criptográfico encadenado y botón "Verificar integridad". La promotora puede anotar la "huella" para detectar si la bitácora fue recortada.
- **Bandeja de aprobaciones**: retiros, matrículas tardías y cambios de contacto o de responsable de pago los aprueba otra persona. Quien pide no aprueba; lo exige el código y también la base de datos.
- Solo Promotoría crea cuentas de Caja y Administración o les restablece la clave.
- En MySQL, la aplicación no puede borrar datos financieros ni modificar montos o fechas de cuotas. Si alguien le da permisos de más, la aplicación se niega a arrancar.

### Datos del colegio y deudas
- Años escolares, secciones, alumnos, apoderados, familias (con hermanos) y matrículas.
- Importación desde Excel en 3 pasos, protegida contra archivos maliciosos, idempotente y de todo o nada.
- Planes de pensión con doble aprobación y versiones. Si el plan cambia mientras se revisa, la aprobación se rechaza.
- Cronograma de cuotas generado por el sistema: la cajera nunca decide montos.
- Saldo inicial con doble control. Quien confirma escribe a ciegas el total del informe del contador.

### Caja (sprint 3)
- Cobro en segundos: la cajera elige cuotas y nunca escribe montos. Boleta simulada con numeración sin huecos.
- Libro de pagos de solo inserción. En MySQL es imposible tener una cuota pagada sin pago.
- Anulaciones, devoluciones, correcciones, descuentos, becas y reaperturas solo con aprobación de otra persona. Las devoluciones en efectivo exigen haber hablado con el apoderado.
- Cierre de caja a ciegas con un solo reconteo, aprobación del cierre y depósito. El faltante llega como alerta a la promotora el mismo día.
- Conciliación a ciegas: Administración escribe el número, la fecha y el monto que ve en el banco. Un número de operación no se puede reutilizar cambiando su formato.
- Alertas críticas por pagos digitales sin verificar, efectivo sin depositar, devoluciones pendientes y comprobantes inconsistentes.
- 28 triggers en MySQL. Si falta alguno, la aplicación no arranca.
- Usuarios demo adicionales: `caja2`, `caja.b`.

## Cómo probarlo en tu computadora
Requisito: Java 21.
```bash
git clone https://github.com/aziel123/cuentas-claras
cd cuentas-claras
git checkout claude/sprint-3-correcciones
./mvnw spring-boot:run
```
Abre http://localhost:8080. Usuarios de demostración, todos con la clave `demo-cuentas-claras-2026`:
- `promotor`
- `director`
- `administracion`
- `caja`
- `docente`
- `apoderado`
- `promotor.b`: otro colegio, para ver el aislamiento.

Para probar la importación están `docs/ux/ejemplo-importacion.xlsx` (con 2 errores a propósito) y `docs/ux/ejemplo-importacion-corregido.xlsx`.

## Decisiones para confirmar con el colegio
Todas tienen un valor por defecto ya implementado y se pueden cambiar.

| # | Tema | Valor actual |
|---|---|---|
| 1 | ¿Una persona puede ser Dirección y Administración a la vez? | **No** (prohibido por la auditoría) |
| 2 | Caja combinada con Dirección, Promotoría o Administración | **No** |
| 3 | Sesiones simultáneas por persona | **Una** (¿hay PC compartidas?) |
| 4 | "Olvidé mi contraseña" | Lo restablece Promotoría. La entrega por WhatsApp o correo llega en el sprint 4 |
| 5 | Navegadores de las PC del colegio | Se necesita Chrome, Edge, Safari o Firefox de 2024 en adelante |
| 6 | Fecha de corte del cronograma 2026 | **01/12/2026**: lo anterior entra como saldo inicial certificado por el contador |
| 7 | Pensión por nivel o por grado | **Por nivel** |
| 8 | Pensiones y vencimientos | **10, último día de marzo a diciembre**; matrícula al último día de febrero |
| 9 | Matrícula mayor que la pensión | **Bloqueada** (DS 005-2021-MINEDU). Confirmar con un asesor legal |
| 10 | Ingreso a mitad de año | Pensiones completas desde el mes de ingreso, **con aprobación** |
| 11 | Grados ofrecidos | Inicial 3–5, Primaria 1–6, Secundaria 1–5. ¿Hay cuna o aulas mixtas? |
| 12 | Documentos de los alumnos | DNI, CE y pasaporte. ¿Hay alumnos con CPP/PTP o sin documento? |
| 13 | Promotoría en alumnos y pensiones | Solo lectura (con datos personales ocultos en parte) más aprobaciones |
| 14 | Deudas de años anteriores a 2026 | Fuera del sistema, salvo que se pidan |
| 15 | Vencimiento en domingo o feriado | Se mantiene, sin mora |
| 16 | Pago parcial en caja | Desactivado en el piloto |
| 17 | Hora límite de cierre de caja | 19:00 |
| 18 | Quién deposita y quién verifica | Deposita la cajera al día hábil siguiente; verifica Administración |
| 19 | Comprobante durante el piloto | Simulado (sin valor tributario); el colegio sigue con su comprobante legal hasta conectar el OSE |
| 20 | IGV de pensiones | Inafecto. Confirmar con el contador |
| 21 | Días hábiles | De lunes a viernes, sin feriados (por ahora) |

La lista completa está en los documentos de `docs/arquitectura/`, incluida la sección 16 de `sprint-3-caja.md`.

## Pendiente fuera del código
- [ ] Reunión de descubrimiento con el colegio (kit en `docs/ux/`).
- [ ] Trámites largos: verificación de WhatsApp Business, proveedor de comprobantes electrónicos (OSE), pasarela de pagos, Yape o Plin empresarial.
- [ ] Elegir el hosting (decisión D3) para tener un entorno de pruebas en internet.
- [ ] Revisar y unir las ramas a `main` mediante un PR, para que corra el CI de GitHub, incluido el job de MySQL.

## Riesgos conocidos
- La aplicación está pensada para **una sola instancia**: las sesiones y algunos límites viven en memoria.
- La clave temporal todavía la ve quien la genera. Se corrige en el sprint 4 con la entrega directa al titular.
- Durante el piloto, la única constancia del padre es la boleta impresa (WhatsApp llega en el sprint 4).
- La huella de la bitácora solo detecta un recorte si la promotora la anota. Desde el sprint 4 se le enviará a diario.
- Los triggers de MySQL requieren `log_bin_trust_function_creators`; está documentado en `docs/operacion/mysql-usuarios.md`.
