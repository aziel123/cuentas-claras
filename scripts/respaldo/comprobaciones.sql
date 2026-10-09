-- Cuentas Claras · comprobaciones de consistencia de una copia restaurada (sprint 7, tanda 1).
-- Las corre «java -jar cuentas-claras.jar verificar-respaldo» (el jar lleva este archivo como db/respaldo/
-- comprobaciones.sql). Al cargar los datos de un respaldo los triggers todavía no están instalados (los de nacimiento
-- rechazarían filas históricas): estas consultas comprueban los libros después de cargar.
-- Reglas del archivo: cada consulta termina en «;» al final de una línea y devuelve UNA fila (control, problemas);
-- problemas > 0 es una falla. Compatible con MySQL 8 y con H2 en modo MySQL. Los montos se comparan en DECIMAL, al
-- centavo. Ninguna devuelve datos personales: solo conteos.

-- Lo pagado de cada cuota es la suma de su libro de aplicaciones (trg_cuota_libro).
SELECT 'cuota_pagado_es_su_libro' AS control, COUNT(*) AS problemas FROM cuota c
 WHERE c.monto_pagado <> (SELECT COALESCE(SUM(a.monto), 0.00) FROM aplicacion_pago a WHERE a.cuota_id = c.id);

-- Lo descontado de cada cuota es la suma de sus ajustes aprobados (trg_cuota_libro).
SELECT 'cuota_descuento_es_sus_ajustes' AS control, COUNT(*) AS problemas FROM cuota c
 WHERE c.monto_descuento <> (SELECT COALESCE(SUM(j.monto), 0.00) FROM ajuste_cuota j WHERE j.cuota_id = c.id);

-- Cada pago tiene su boleta o factura por el mismo total (trg_pago_registro).
SELECT 'pago_con_su_comprobante' AS control, COUNT(*) AS problemas FROM pago p
  LEFT JOIN comprobante c ON c.id = p.comprobante_id
 WHERE c.id IS NULL OR c.tipo NOT IN ('BOLETA', 'FACTURA') OR c.total <> p.total;

-- Las aplicaciones de un pago no pasan su total (trg_aplicacion_pago_registro).
SELECT 'aplicaciones_no_pasan_el_pago' AS control, COUNT(*) AS problemas FROM pago p
 WHERE (SELECT COALESCE(SUM(a.monto), 0.00) FROM aplicacion_pago a WHERE a.pago_id = p.id) > p.total;

-- El libro de aplicaciones no tiene huérfanos (al cargar no se comprobaron las FK).
SELECT 'aplicaciones_con_pago_y_cuota' AS control, COUNT(*) AS problemas FROM aplicacion_pago a
  LEFT JOIN pago p ON p.id = a.pago_id
  LEFT JOIN cuota c ON c.id = a.cuota_id
 WHERE p.id IS NULL OR c.id IS NULL;

-- Todo pago ANULADO tiene su anulación registrada, y toda anulación es de un pago ANULADO (trg_pago_anulacion).
SELECT 'pago_anulado_con_su_anulacion' AS control, COUNT(*) AS problemas FROM pago p
 WHERE p.estado = 'ANULADO' AND NOT EXISTS (SELECT 1 FROM anulacion_pago n WHERE n.pago_id = p.id);

SELECT 'anulacion_de_un_pago_anulado' AS control, COUNT(*) AS problemas FROM anulacion_pago n
  LEFT JOIN pago p ON p.id = n.pago_id
 WHERE p.id IS NULL OR p.estado <> 'ANULADO';

-- Las series no tienen huecos: del 1 al último número, cada número una sola vez (trg_comprobante_correlativo).
SELECT 'series_sin_huecos' AS control, COUNT(*) AS problemas FROM serie_comprobante s
 WHERE s.ultimo_numero <> (SELECT COUNT(*) FROM comprobante c WHERE c.serie_id = s.id)
    OR s.ultimo_numero <> (SELECT COUNT(DISTINCT c.numero) FROM comprobante c WHERE c.serie_id = s.id)
    OR s.ultimo_numero <> (SELECT COALESCE(MAX(c.numero), 0) FROM comprobante c WHERE c.serie_id = s.id);
