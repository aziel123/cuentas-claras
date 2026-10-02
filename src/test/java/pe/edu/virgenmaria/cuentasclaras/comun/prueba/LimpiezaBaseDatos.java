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
		// Sprint 2: de las hijas a las madres (FK compuestas).
		jdbc.update("DELETE FROM solicitud_cambio");
		jdbc.update("DELETE FROM cuota");
		jdbc.update("DELETE FROM linea_saldo_inicial");
		jdbc.update("DELETE FROM lote_saldo_inicial");
		jdbc.update("DELETE FROM plan_pension");
		jdbc.update("DELETE FROM importacion_alumnos");
		jdbc.update("DELETE FROM matricula");
		jdbc.update("DELETE FROM alumno");
		jdbc.update("DELETE FROM apoderado");
		jdbc.update("DELETE FROM familia");
		jdbc.update("DELETE FROM seccion");
		jdbc.update("DELETE FROM anio_escolar");
		jdbc.update("DELETE FROM evento_auditoria");
		jdbc.update("UPDATE auditoria_cadena SET ultima_secuencia = 0, ultimo_hash = ? WHERE id = 1", HASH_INICIAL);
		jdbc.update("DELETE FROM usuario_rol");
		jdbc.update("DELETE FROM usuario");
		jdbc.update("DELETE FROM colegio WHERE id <> 1");
	}
}
