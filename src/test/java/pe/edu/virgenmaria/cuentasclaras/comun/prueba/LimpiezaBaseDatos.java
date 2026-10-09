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
		// Sprint 7, tanda 1 (V24): el registro de respaldos (técnico, sin colegio; en MySQL solo lo escribe cc_respaldo).
		jdbc.update("DELETE FROM respaldo");
		// Sprint 7, tanda 2 (V25): las firmas apuntan a la sesión y a la cuenta; la sesión, a la cuenta; la cuenta, a su
		// solicitud de roles (en MySQL nada de esto se limpia: solo H2).
		jdbc.update("DELETE FROM firma_operacion");
		jdbc.update("DELETE FROM sesion_usuario");
		jdbc.update("UPDATE usuario SET roles_solicitud_id = NULL WHERE roles_solicitud_id IS NOT NULL");
		// Sprint 6, tanda 3 (V22): la llamada de control apunta a la familia.
		jdbc.update("DELETE FROM llamada_control");
		jdbc.update("DELETE FROM muestra_llamada");
		jdbc.update("DELETE FROM delegacion_llamada");
		// Sprint 6, tanda 2 (V21): la foto del resumen diario (sus mensajes se borran con los demás, más abajo).
		jdbc.update("DELETE FROM resumen_diario");
		// Correcciones del sprint 5 (V20): la verificación apunta a su mensaje y al apoderado; la huella de la hora.
		jdbc.update("DELETE FROM verificacion_contacto");
		jdbc.update("DELETE FROM huella_hora");
		// Sprint 5 (tanda 3): cierre mensual (apunta a la cuenta), semilla del muestreo y feriados extra.
		jdbc.update("DELETE FROM cierre_mensual_banco");
		jdbc.update("DELETE FROM semilla_muestreo");
		jdbc.update("DELETE FROM feriado");
		// Sprint 5 (tanda 2): los avisos de la familia apuntan a pagos y cuotas; la renovación, a sus matrículas.
		jdbc.update("DELETE FROM aviso_familia");
		jdbc.update("DELETE FROM renovacion_matricula");
		// Sprint 5 (tanda 1): el enlace apunta a su mensaje y el respaldo al WhatsApp FALLIDO; la huella, a la bitácora.
		jdbc.update("UPDATE enlace_activacion SET mensaje_id = NULL WHERE mensaje_id IS NOT NULL");
		jdbc.update("DELETE FROM mensaje WHERE respaldo_de_id IS NOT NULL");
		jdbc.update("DELETE FROM mensaje");
		jdbc.update("DELETE FROM huella_bitacora");
		// Sprint 4 (tanda 3): la conciliación apunta a pagos, depósitos, lotes, liquidaciones y reembolsos; el extracto se
		// apunta a sí mismo (el anterior y el que lo confirmó): se borra del último al primero.
		jdbc.update("DELETE FROM verificacion_bancaria");
		jdbc.update("DELETE FROM partida_conciliacion");
		jdbc.update("DELETE FROM movimiento_bancario");
		// Una cadena confirmada junta se apunta en círculo (el anterior lo confirmó el siguiente): se corta apuntando cada
		// uno a sí mismo (sigue cumpliendo los CHECK; solo en H2, en MySQL no se limpia).
		jdbc.update("UPDATE extracto_bancario SET confirmacion_extracto_id = id WHERE confirmacion_extracto_id IS NOT NULL");
		for (int vuelta = 0; vuelta < 1000 && jdbc.queryForObject("SELECT COUNT(*) FROM extracto_bancario", Long.class) > 0;
				vuelta++) {
			jdbc.update("DELETE FROM extracto_bancario e WHERE NOT EXISTS (SELECT 1 FROM extracto_bancario s "
					+ "WHERE s.anterior_id = e.id) AND NOT EXISTS (SELECT 1 FROM extracto_bancario c "
					+ "WHERE c.confirmacion_extracto_id = e.id AND c.id <> e.id)");
		}
		jdbc.update("DELETE FROM cuenta_bancaria");
		jdbc.update("DELETE FROM liquidacion_linea");
		jdbc.update("DELETE FROM liquidacion_pasarela");
		// Sprint 3 (tanda 3): verificación bancaria, depósitos y cierres; (tanda 2): descuentos y anulaciones.
		jdbc.update("DELETE FROM reembolso");
		jdbc.update("DELETE FROM reembolso_pasarela");
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
		// Sprint 4 (tanda 2): recaudación bancaria, después del pago que enlaza su línea; el archivo, después del lote.
		jdbc.update("DELETE FROM linea_recaudacion");
		jdbc.update("DELETE FROM lote_recaudacion");
		jdbc.update("DELETE FROM archivo_cargado");
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
		jdbc.update("UPDATE apoderado SET contacto_solicitud_id = NULL WHERE contacto_solicitud_id IS NOT NULL");
		// Sprint 6, tanda 2: el contacto del personal enlaza su solicitud aprobada (FK compuesta). En H2 no hay
		// trg_usuario_contacto; en MySQL no se limpia (PermisosMySqlTest usa nombres únicos).
		jdbc.update("UPDATE usuario SET contacto_solicitud_id = NULL WHERE contacto_solicitud_id IS NOT NULL");
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
		jdbc.update("DELETE FROM enlace_activacion");
		jdbc.update("DELETE FROM usuario_rol");
		jdbc.update("DELETE FROM usuario");
		jdbc.update("DELETE FROM configuracion_colegio");
		jdbc.update("DELETE FROM colegio WHERE id <> 1");
		jdbc.update("DELETE FROM configuracion_bd");
	}
}
