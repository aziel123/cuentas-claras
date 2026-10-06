package pe.edu.virgenmaria.cuentasclaras.comun.prueba;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Deja la base de pruebas como la dejan las migraciones. Las pruebas que confirman
 * transacciones (por ejemplo las de aislamiento entre colegios o de concurrencia) la llaman
 * antes y después, porque el H2 en memoria se comparte entre las clases de prueba.
 * <p>
 * Usa JDBC directo a propósito: en la aplicación ningún usuario ni evento se borra.
 */
public final class LimpiezaBaseDatos {

	public static final String HASH_INICIAL = "0".repeat(64);

	private LimpiezaBaseDatos() {
	}

	public static void limpiar(JdbcTemplate jdbc) {
		// Sprint 3 (tanda 3): verificación bancaria, depósitos y cierres; (tanda 2): descuentos y anulaciones.
		jdbc.update("DELETE FROM reembolso");
		jdbc.update("DELETE FROM verificacion_bancaria");
		jdbc.update("DELETE FROM deposito_caja");
		jdbc.update("DELETE FROM cierre_caja");
		jdbc.update("DELETE FROM ajuste_cuota");
		jdbc.update("DELETE FROM descuento");
		jdbc.update("DELETE FROM anulacion_pago");
		// Sprint 3 (tanda 1): libro de pagos, cajas y comprobantes, de las hijas a las madres.
		jdbc.update("DELETE FROM aplicacion_pago WHERE revierte_id IS NOT NULL");
		jdbc.update("DELETE FROM aplicacion_pago");
		jdbc.update("DELETE FROM pago WHERE reemplaza_pago_id IS NOT NULL");
		jdbc.update("DELETE FROM pago");
		// Sprint 4 (tanda 1): pagos en línea, después del pago que enlaza su orden.
		jdbc.update("DELETE FROM evento_pasarela");
		jdbc.update("DELETE FROM orden_pago_cuota");
		jdbc.update("DELETE FROM orden_pago");
		jdbc.update("DELETE FROM caja_diaria");
		jdbc.update("DELETE FROM comprobante_linea");
		jdbc.update("UPDATE comprobante SET reemplaza_id = NULL WHERE reemplaza_id IS NOT NULL");
		jdbc.update("DELETE FROM comprobante WHERE modifica_id IS NOT NULL");
		jdbc.update("DELETE FROM comprobante");
		jdbc.update("DELETE FROM serie_comprobante");
		// Sprint 2: de las hijas a las madres (FK compuestas). Correcciones del sprint 3: la cuota y el apoderado
		// enlazan su solicitud aprobada (en H2 no hay triggers que lo impidan).
		jdbc.update("UPDATE cuota SET anulacion_solicitud_id = NULL WHERE anulacion_solicitud_id IS NOT NULL");
		jdbc.update("UPDATE apoderado SET ruc = NULL, razon_social = NULL, facturacion_solicitud_id = NULL "
				+ "WHERE facturacion_solicitud_id IS NOT NULL");
		jdbc.update("DELETE FROM solicitud_cambio");
		jdbc.update("DELETE FROM cuota");
		jdbc.update("DELETE FROM linea_saldo_inicial");
		jdbc.update("DELETE FROM lote_saldo_inicial");
		jdbc.update("DELETE FROM plan_pension");
		jdbc.update("DELETE FROM importacion_alumnos");
		jdbc.update("DELETE FROM matricula");
		jdbc.update("DELETE FROM alumno");
		jdbc.update("UPDATE usuario SET apoderado_id = NULL WHERE apoderado_id IS NOT NULL");
		jdbc.update("DELETE FROM apoderado");
		jdbc.update("DELETE FROM familia");
		jdbc.update("DELETE FROM seccion");
		jdbc.update("DELETE FROM anio_escolar");
		jdbc.update("DELETE FROM evento_auditoria");
		jdbc.update("UPDATE auditoria_cadena SET ultima_secuencia = 0, ultimo_hash = ? WHERE id = 1", HASH_INICIAL);
		jdbc.update("DELETE FROM usuario_rol");
		jdbc.update("DELETE FROM usuario");
		jdbc.update("DELETE FROM colegio WHERE id <> 1");
		jdbc.update("DELETE FROM configuracion_bd");
	}
}
