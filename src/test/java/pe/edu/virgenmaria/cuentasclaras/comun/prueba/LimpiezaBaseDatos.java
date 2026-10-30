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
		// Sprint 9, tanda 2 (V33): las marcas apuntan a sí mismas (reemplaza_id), al registro, a la sesión y a la solicitud;
		// el consentimiento, al apoderado y a la sesión.
		// La cadena se borra desde la última (nadie la reemplaza) hacia la primera: el CHECK no deja vaciar reemplaza_id.
		// Sprint 9, tanda 3: comunicados (la confirmacion y los destinatarios antes que el comunicado; la fe de erratas
		// antes que el original), codigos modulares y exportaciones de la nomina.
		jdbc.update("DELETE FROM confirmacion_lectura");
		jdbc.update("DELETE FROM comunicado_destinatario");
		while (jdbc.update("DELETE FROM comunicado WHERE id NOT IN (SELECT corrige_id FROM comunicado "
				+ "WHERE corrige_id IS NOT NULL)") > 0) {
			// la cadena de fe de erratas se borra de la ultima a la primera
		}
		jdbc.update("DELETE FROM comunicado_adjunto");
		jdbc.update("DELETE FROM codigo_modular");
		jdbc.update("DELETE FROM exportacion_nomina");
		while (jdbc.update("DELETE FROM marca_asistencia WHERE id NOT IN (SELECT reemplaza_id FROM marca_asistencia "
				+ "WHERE reemplaza_id IS NOT NULL)") > 0) {
			// sigue con la marca anterior de cada cadena
		}
		jdbc.update("DELETE FROM registro_asistencia");
		jdbc.update("DELETE FROM consentimiento_aviso");
		jdbc.update("DELETE FROM configuracion_academica");
		jdbc.update("DELETE FROM aviso_asistencia_corte");
		// Sprint 10, tanda 3 (V37): las entregas apuntan a su libreta; las libretas, de la última versión a la primera, al
		// periodo, la matrícula y la solicitud; la exportación y la delegación, a la sesión y la cuenta.
		// Correcciones del sprint 10 (V38): la historia de la matrícula (en H2 la insertan las pruebas).
		jdbc.update("DELETE FROM matricula_historial");
		jdbc.update("DELETE FROM entrega_libreta");
		while (jdbc.update("DELETE FROM libreta_emitida WHERE id NOT IN (SELECT reemplaza_id FROM libreta_emitida "
				+ "WHERE reemplaza_id IS NOT NULL)") > 0) {
			// sigue con la versión anterior de cada cadena
		}
		jdbc.update("DELETE FROM exportacion_calificaciones");
		jdbc.update("DELETE FROM delegacion_siagie");
		// Sprint 10, tanda 2 (V36): los borradores apuntan a su nota; las notas, de la última a la primera de cada cadena,
		// a su lote y a su solicitud; las exoneraciones, al periodo desde el que rigen.
		jdbc.update("DELETE FROM borrador_conclusion");
		while (jdbc.update("DELETE FROM calificacion WHERE id NOT IN (SELECT reemplaza_id FROM calificacion "
				+ "WHERE reemplaza_id IS NOT NULL)") > 0) {
			// sigue con la nota anterior de cada cadena
		}
		jdbc.update("DELETE FROM lote_calificacion");
		jdbc.update("DELETE FROM exoneracion_area");
		// Sprint 10, tanda 1 (V35): las áreas de cada asignación, antes que la asignación; los periodos y la configuración de
		// evaluación apuntan al año. El catálogo (sin colegio) es de la migración: no se borra.
		jdbc.update("DELETE FROM asignacion_area");
		jdbc.update("DELETE FROM periodo_evaluacion");
		jdbc.update("DELETE FROM configuracion_evaluacion");
		// Sprint 9, tanda 1 (V32): la asignación docente apunta a su solicitud, a la sección, al año y a la cuenta.
		jdbc.update("DELETE FROM asignacion_docente");
		// Correcciones del sprint 7 (V27): la resolución de una alerta apunta al respaldo y a la cuenta; la primera Dirección,
		// a la cuenta; el estado de la cuenta, a su solicitud.
		jdbc.update("DELETE FROM resolucion_respaldo");
		jdbc.update("DELETE FROM primera_direccion");
		// Sprint 8, tanda 1 (V28): la primera Promotoría apunta al titular y al operador; el logo, a su colegio.
		jdbc.update("DELETE FROM primera_promotoria");
		// Sprint 8, tanda 2 (V29): los conectores apuntan a su solicitud y a su colegio.
		jdbc.update("DELETE FROM conector_colegio");
		// Correcciones del sprint 8 (V31): los cambios del contacto del titular y del correo de control; la foto del emisor
		// de cada comprobante apunta a su logo.
		jdbc.update("DELETE FROM cambio_contacto_titular");
		jdbc.update("DELETE FROM cambio_correo_control");
		jdbc.update("UPDATE comprobante SET emisor_logo_id = NULL WHERE emisor_logo_id IS NOT NULL");
		jdbc.update("DELETE FROM colegio_logo");
		jdbc.update("UPDATE usuario SET estado_solicitud_id = NULL WHERE estado_solicitud_id IS NOT NULL");
		// Sprint 7, tanda 1 (V24): el registro de respaldos (técnico, sin colegio; en MySQL solo lo escribe cc_respaldo).
		jdbc.update("DELETE FROM respaldo");
		// Sprint 7, tanda 3 (V26): el registro de accesos a datos personales apunta a la cuenta, a la sesión, a la familia y
		// al alumno (en MySQL cc_app no lo borra: 1142; solo H2).
		jdbc.update("DELETE FROM acceso_dato_personal");
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
		// Sprint 11, tanda 1 (V39): el uso de la facultad del titular apunta a su mensaje y a las cuentas; el colegio apunta a
		// su cambio de estado (FK circular) y el cambio a los operadores.
		jdbc.update("DELETE FROM uso_facultad_titular");
		// Un colegio dado de baja o suspendido vuelve a PRODUCCION (los de las pruebas se borran abajo; el 1 queda así).
		jdbc.update("UPDATE colegio SET estado = 'PRODUCCION', suspendido_en = NULL, produccion_desde = "
				+ "COALESCE(produccion_desde, CURRENT_TIMESTAMP), produccion_por = COALESCE(produccion_por, 'prueba') "
				+ "WHERE estado NOT IN ('PREPARACION', 'PRODUCCION')");
		jdbc.update("UPDATE colegio SET cambio_estado_id = NULL, facultad_cambio_id = NULL, facultad_bloqueada = FALSE "
				+ "WHERE cambio_estado_id IS NOT NULL OR facultad_cambio_id IS NOT NULL");
		// Sprint 11, tanda 2 (V41): la exportación apunta a su cambio de estado y la descarga a su exportación; el colegio,
		// a su acta de supresión.
		// El cierre apunta a su exportación y la exportación a su suspensión (FK circular, las dos obligatorias por CHECK):
		// se borran juntas sin revisar las FK (solo en H2).
		jdbc.update("DELETE FROM descarga_exportacion");
		jdbc.update("UPDATE colegio SET acta_supresion_id = NULL, retencion_legal_motivo = NULL, retencion_legal_desde = NULL, "
				+ "retencion_legal_por = NULL WHERE acta_supresion_id IS NOT NULL OR retencion_legal_por IS NOT NULL");
		jdbc.update("DELETE FROM acta_supresion");
		jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
		try {
			jdbc.update("DELETE FROM exportacion_colegio");
			jdbc.update("DELETE FROM cambio_estado_colegio");
		}
		finally {
			jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
		}
		jdbc.update("UPDATE enlace_activacion SET mensaje_id = NULL WHERE mensaje_id IS NOT NULL");
		// Sprint 11, tanda 1c (V40): la solicitud y el conector apuntan al mensaje de su código.
		jdbc.update("UPDATE solicitud_cambio SET codigo_mensaje_id = NULL, codigo_escrito_sha256 = NULL WHERE codigo_mensaje_id IS NOT NULL");
		jdbc.update("UPDATE conector_colegio SET codigo_mensaje_id = NULL, codigo_escrito_sha256 = NULL WHERE codigo_mensaje_id IS NOT NULL");
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
		// Sprint 8, tanda 1 (V28): quedan el colegio 1 (en producción, como lo deja V28 en una base con cuentas) y la fila
		// PLATAFORMA (donde viven los operadores); los colegios que crean las pruebas se borran.
		jdbc.update("DELETE FROM colegio WHERE id <> 1 AND tipo <> 'PLATAFORMA'");
		jdbc.update("UPDATE colegio SET estado = 'PRODUCCION', produccion_desde = COALESCE(produccion_desde, "
				+ "CURRENT_TIMESTAMP), produccion_por = COALESCE(produccion_por, 'prueba'), ruc = NULL, razon_social = NULL, "
				+ "direccion = NULL, correo_institucional = NULL, titular_nombre = NULL, titular_documento = NULL, "
				+ "contrato_codigo = NULL, aviso_privacidad_version = NULL, aviso_privacidad_aprobado_por = NULL, "
				+ "aviso_privacidad_aprobado_en = NULL, actualizado_por = NULL, suspendido_en = NULL WHERE id = 1");
		jdbc.update("DELETE FROM configuracion_bd");
	}
}
