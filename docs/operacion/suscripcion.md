# Suscripción de los colegios a la plataforma (se cobra fuera del sistema)

> Sprint 8, tanda 3 (decisión 128; sección 3.10 de `docs/arquitectura/sprint-8-lista-para-vender.md`). Cómo cobra el
> proveedor a cada colegio. **La plataforma no cobra la suscripción:** no tiene pantallas, tablas ni procesos para eso, a
> propósito.

## Cómo se cobra
1. **Contrato firmado antes del alta** (decisión 137): términos de servicio y encargo de tratamiento
   (`docs/entrega/terminos-de-servicio.md` y `contrato-encargo-tratamiento-datos.md`). El código del contrato es el que el
   operador escribe en el alta (`colegio.contrato_codigo`): es lo único de la suscripción que guarda la plataforma.
2. **Factura electrónica mensual** emitida por el proveedor con **su propio sistema autorizado** por SUNAT (no con la
   plataforma: la plataforma emite las boletas de cada colegio a las familias, con el OSE del colegio).
3. **Pago por transferencia** del colegio a la cuenta del proveedor.
4. **Seguimiento en una hoja de cálculo** (colegio, contrato, monto, vencimiento, factura, pago, días de atraso) hasta tener
   unos 20 colegios. Entonces se revisa si conviene automatizarlo.

## Por qué fuera del sistema
- Con pocos clientes, el cobro automático (tarjeta recurrente, mora, suspensión) cuesta más de lo que ahorra.
- **Cobrar al colegio no es un vector de fraude contra el colegio**: los controles de la plataforma protegen el dinero de las
  familias, no la relación comercial con el proveedor.
- **No se mezclan** el dinero de las familias (del colegio) y el del proveedor: ninguna cuenta, conector ni pasarela de un
  colegio recibe pagos del proveedor, ni al revés.

## Si un colegio no paga
- Aviso por escrito al representante legal y al correo de contacto del contrato.
- **Suspensión solo con 30 días de aviso por escrito**, y solo del acceso del **personal** del colegio.
- **Nunca se suspenden los avisos de pago a las familias ni su acceso a sus comprobantes y a su estado de cuenta** mientras
  el colegio cobre con la plataforma (cláusula 7 de los términos).
- Si el contrato termina: exportación de los datos en 30 días, conservación bloqueada y eliminación con acta (cláusula 8).
- **Cómo se suspende al personal** (cuando corresponda, con el aviso cumplido): lo hace la Promotoría del colegio o, si el
  contrato terminó, el proveedor por un procedimiento con acta (la plataforma no tiene un botón de suspensión, por diseño: la
  baja quedó fuera del sistema en este sprint, decisión 132). Ese procedimiento lo redacta el abogado junto con los
  términos.

## Pendiente
- Precio, reajuste y mora: los decide el proveedor con el abogado (los términos los dejan entre corchetes).
- Revisar este modelo al llegar a unos 20 colegios.
