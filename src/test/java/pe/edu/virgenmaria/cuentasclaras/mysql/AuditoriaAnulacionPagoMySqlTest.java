package pe.edu.virgenmaria.cuentasclaras.mysql;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Auditoría del sprint 7, hallazgo S7-A1 (ALTO), INVERTIDA tras las correcciones: con la clave de cc_app (un empleado con
 * acceso al servidor o una inyección SQL) ya NO se anula un pago en efectivo VIGENTE sin la aprobación firmada de nadie.
 * Cada paso del ataque responde 1644 y el cierre a ciegas sigue esperando ese efectivo:
 * <ul>
 *   <li>la nota de crédito que anula su boleta (trg_comprobante_correlativo: necesita la ANULACION_PAGO de ese pago
 *       aprobada y firmada);</li>
 *   <li>la fila anulacion_pago con una solicitud cualquiera (trg_anulacion_pago_registro: necesita SU solicitud aprobada,
 *       del mismo solicitante, aprobador, tipo y motivo, firmada por Promotoría o Dirección);</li>
 *   <li>el pago ANULADO sin su anulación (trg_pago_anulacion);</li>
 *   <li>fabricar la aprobación: insertar una solicitud ya APROBADA (trg_solicitud_cambio_nace) o aprobar una pendiente
 *       sin la firma de quien figura como aprobador (trg_solicitud_cambio_resuelta).</li>
 * </ul>
 * Corre en el job mysql del CI después de la fase 2 (que deja pagos en efectivo vigentes), con CC_PRUEBA_MYSQL=true.
 */
@SpringBootTest
@ActiveProfiles({ "test", "mysql" })
@EnabledIfEnvironmentVariable(named = "CC_PRUEBA_MYSQL", matches = "true")
class AuditoriaAnulacionPagoMySqlTest {

	private static final int MYSQL_SIGNAL = 1644;

	@Autowired
	private JdbcTemplate jdbc;

	/** S7-A1: el ataque del auditor, paso por paso: todos fallan con 1644 y el pago sigue VIGENTE. */
	@Test
	void conCcAppYaNoSeAnulaUnPagoEnEfectivoSinFirma() {
		List<Map<String, Object>> candidatos = jdbc.queryForList(
				"SELECT p.id, p.colegio_id, p.caja_diaria_id, p.cajero, p.comprobante_id, p.total, ap.id AS ap_id, "
						+ "ap.cuota_id FROM pago p JOIN aplicacion_pago ap ON ap.pago_id = p.id AND ap.tipo = 'APLICACION' "
						+ "WHERE p.estado = 'VIGENTE' AND p.medio = 'EFECTIVO' AND p.origen = 'CAJA' "
						+ "AND NOT EXISTS (SELECT 1 FROM anulacion_pago n WHERE n.pago_id = p.id) "
						+ "AND NOT EXISTS (SELECT 1 FROM aplicacion_pago r WHERE r.revierte_id = ap.id) "
						+ "AND NOT EXISTS (SELECT 1 FROM comprobante nc WHERE nc.modifica_id = p.comprobante_id) LIMIT 1");
		assumeTrue(!candidatos.isEmpty(), "no hay un pago en efectivo VIGENTE sembrado que anular");
		Map<String, Object> pago = candidatos.get(0);
		Long pagoId = ((Number) pago.get("id")).longValue();
		Long colegio = ((Number) pago.get("colegio_id")).longValue();
		Long cajaId = ((Number) pago.get("caja_diaria_id")).longValue();
		Long comprobanteId = ((Number) pago.get("comprobante_id")).longValue();
		String cajero = (String) pago.get("cajero");
		BigDecimal total = (BigDecimal) pago.get("total");
		BigDecimal esperadoAntes = efectivoEsperado(cajaId);
		Long solicitud = jdbc.queryForObject("SELECT MIN(id) FROM solicitud_cambio WHERE colegio_id = ? AND id NOT IN "
				+ "(SELECT solicitud_id FROM anulacion_pago)", Long.class, colegio);
		assumeTrue(solicitud != null, "no hay ninguna solicitud_cambio para la FK");
		Long serieNc = jdbc.queryForObject(
				"SELECT id FROM serie_comprobante WHERE colegio_id = ? AND tipo = 'NOTA_CREDITO' LIMIT 1", Long.class,
				colegio);
		assumeTrue(serieNc != null, "no hay serie de nota de credito");

		// 1. La nota de crédito que anula la boleta del pago, sin la anulación aprobada y firmada: 1644. (Con el número
		// actual de la serie: el trigger mira la serie antes que la anulación, y así la prueba no deja un hueco en ella.)
		String serieStr = jdbc.queryForObject("SELECT serie FROM serie_comprobante WHERE id = ?", String.class, serieNc);
		Integer numero = jdbc.queryForObject("SELECT ultimo_numero FROM serie_comprobante WHERE id = ?", Integer.class,
				serieNc);
		assertThat(codigo(() -> jdbc.update("INSERT INTO comprobante (colegio_id, serie_id, tipo, serie, numero, "
				+ "fecha_emision, receptor_tipo_documento, receptor_numero_documento, receptor_nombre, moneda, total, "
				+ "afectacion_igv, modifica_id, motivo_nota, proveedor, estado_envio, intentos, creado_en, creado_por, "
				+ "actualizado_en) SELECT colegio_id, ?, 'NOTA_CREDITO', ?, ?, CURRENT_DATE, receptor_tipo_documento, "
				+ "receptor_numero_documento, receptor_nombre, 'PEN', total, afectacion_igv, id, 'robo (S7-A1)', "
				+ "proveedor, 'PENDIENTE', 0, NOW(6), ?, NOW(6) FROM comprobante WHERE id = ?", serieNc, serieStr, numero,
				cajero, comprobanteId))).as("nota de crédito sin anulación aprobada").isEqualTo(MYSQL_SIGNAL);

		// 2. La anulación con una solicitud cualquiera y la nota de crédito de OTRO pago (la única que se puede tener): 1644.
		Long otraNota = jdbc.queryForObject("SELECT MIN(id) FROM comprobante WHERE colegio_id = ? AND tipo = 'NOTA_CREDITO'",
				Long.class, colegio);
		if (otraNota != null) {
			assertThat(codigo(() -> jdbc.update("INSERT INTO anulacion_pago (colegio_id, pago_id, solicitud_id, "
					+ "nota_credito_id, tipo, motivo, monto, cajero_pago, solicitado_por, aprobado_por, posterior_al_cierre, "
					+ "creado_en, creado_por, actualizado_en) VALUES (?, ?, ?, ?, 'CORRECCION', 'reproduccion S7-A1', ?, ?, "
					+ "'x.pide', 'y.aprueba', FALSE, NOW(6), 'y.aprueba', NOW(6))", colegio, pagoId, solicitud, otraNota,
					total, cajero))).as("anulación sin su solicitud aprobada y firmada").isEqualTo(MYSQL_SIGNAL);
		}

		// 3. El pago ANULADO sin su anulación: 1644.
		assertThat(codigo(() -> jdbc.update("UPDATE pago SET estado = 'ANULADO', actualizado_en = NOW(6) WHERE id = ?",
				pagoId))).as("pago anulado sin anulación").isEqualTo(MYSQL_SIGNAL);

		// 4. Fabricar la aprobación: una solicitud ya APROBADA «por la directora» (1644) o aprobar una pendiente sin la
		// firma de la sesión de quien figura como aprobador (1644).
		assertThat(codigo(() -> jdbc.update("INSERT INTO solicitud_cambio (colegio_id, tipo, entidad, entidad_id, resumen, "
				+ "datos, motivo, estado, pendiente, solicitado_por, resuelto_por, resuelto_en, creado_en, creado_por, "
				+ "actualizado_en) VALUES (?, 'ANULACION_PAGO', 'pago', ?, 'Anulación (S7-A1)', '{\"tipo\":\"CORRECCION\"}', "
				+ "'reproduccion S7-A1', 'APROBADA', NULL, 'x.pide', 'y.aprueba', NOW(6), NOW(6), 'x.pide', NOW(6))", colegio,
				pagoId))).as("solicitud que nace APROBADA").isEqualTo(MYSQL_SIGNAL);
		jdbc.update("INSERT INTO solicitud_cambio (colegio_id, tipo, entidad, entidad_id, resumen, datos, motivo, estado, "
				+ "pendiente, solicitado_por, creado_en, creado_por, actualizado_en) VALUES (?, 'ANULACION_PAGO', 'pago', ?, "
				+ "'Anulación (S7-A1)', '{\"tipo\":\"CORRECCION\"}', 'reproduccion S7-A1 pendiente', 'PENDIENTE', TRUE, "
				+ "'x.pide', NOW(6), 'x.pide', NOW(6))", colegio, pagoId);
		Long pendiente = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		assertThat(codigo(() -> jdbc.update("UPDATE solicitud_cambio SET estado = 'APROBADA', pendiente = NULL, "
				+ "resuelto_por = 'y.aprueba', resuelto_en = NOW(6), actualizado_en = NOW(6) WHERE id = ?", pendiente)))
				.as("aprobar sin la firma de quien aprueba").isEqualTo(MYSQL_SIGNAL);

		assertThat(jdbc.queryForObject("SELECT estado FROM pago WHERE id = ?", String.class, pagoId)).isEqualTo("VIGENTE");
		assertThat(efectivoEsperado(cajaId)).as("el cierre sigue esperando ese efectivo").isEqualByComparingTo(esperadoAntes);
	}

	private BigDecimal efectivoEsperado(Long cajaId) {
		return jdbc.queryForObject("SELECT COALESCE(SUM(total), 0) FROM pago WHERE caja_diaria_id = ? "
				+ "AND medio = 'EFECTIVO' AND estado = 'VIGENTE'", BigDecimal.class, cajaId);
	}

	/** El código de error de MySQL de la sentencia (0 si no falló). */
	private static int codigo(Runnable sentencia) {
		try {
			sentencia.run();
			return 0;
		}
		catch (DataAccessException e) {
			Throwable causa = e;
			while (causa != null) {
				if (causa instanceof SQLException sql) {
					return sql.getErrorCode();
				}
				causa = causa.getCause();
			}
			throw e;
		}
	}
}
