package pe.edu.virgenmaria.cuentasclaras.mysql;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest
@ActiveProfiles({ "test", "mysql" })
@EnabledIfEnvironmentVariable(named = "CC_PRUEBA_MYSQL", matches = "true")
class AuditoriaAnulacionPagoMySqlTest {

	@Autowired
	private JdbcTemplate jdbc;

	/** S7-A1: con cc_app se anula un pago en efectivo VIGENTE sin firma y el cierre deja de esperar ese efectivo. */
	@Test
	void conCcAppSeAnulaUnPagoEnEfectivoSinFirma() {
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
		Long apId = ((Number) pago.get("ap_id")).longValue();
		Long cuotaId = ((Number) pago.get("cuota_id")).longValue();
		String cajero = (String) pago.get("cajero");
		BigDecimal total = (BigDecimal) pago.get("total");
		BigDecimal esperadoAntes = efectivoEsperado(cajaId);
		Long solicitud = jdbc.queryForObject("SELECT MIN(id) FROM solicitud_cambio WHERE colegio_id = ? AND id NOT IN (SELECT solicitud_id FROM anulacion_pago)", Long.class,
				colegio);
		assumeTrue(solicitud != null, "no hay ninguna solicitud_cambio para la FK");
		Long serieNc = jdbc.queryForObject(
				"SELECT id FROM serie_comprobante WHERE colegio_id = ? AND tipo = 'NOTA_CREDITO' LIMIT 1", Long.class,
				colegio);
		assumeTrue(serieNc != null, "no hay serie de nota de credito");
		jdbc.update("UPDATE serie_comprobante SET ultimo_numero = ultimo_numero + 1, actualizado_en = NOW(6) WHERE id = ?",
				serieNc);
		String serieStr = jdbc.queryForObject("SELECT serie FROM serie_comprobante WHERE id = ?", String.class, serieNc);
		Integer numero = jdbc.queryForObject("SELECT ultimo_numero FROM serie_comprobante WHERE id = ?", Integer.class,
				serieNc);
		jdbc.update("INSERT INTO comprobante (colegio_id, serie_id, tipo, serie, numero, fecha_emision, "
				+ "receptor_tipo_documento, receptor_numero_documento, receptor_nombre, moneda, total, afectacion_igv, "
				+ "modifica_id, motivo_nota, proveedor, estado_envio, intentos, creado_en, creado_por, actualizado_en) "
				+ "SELECT colegio_id, ?, 'NOTA_CREDITO', ?, ?, CURRENT_DATE, receptor_tipo_documento, "
				+ "receptor_numero_documento, receptor_nombre, 'PEN', total, afectacion_igv, id, 'robo (S7-A1)', "
				+ "proveedor, 'PENDIENTE', 0, NOW(6), ?, NOW(6) FROM comprobante WHERE id = ?", serieNc, serieStr, numero, cajero,
				comprobanteId);
		Long notaId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbc.update("INSERT INTO anulacion_pago (colegio_id, pago_id, solicitud_id, nota_credito_id, tipo, motivo, monto, "
				+ "cajero_pago, solicitado_por, aprobado_por, posterior_al_cierre, creado_en, creado_por, actualizado_en) "
				+ "VALUES (?, ?, ?, ?, 'CORRECCION', 'reproduccion S7-A1', ?, ?, 'x.pide', 'y.aprueba', FALSE, "
				+ "NOW(6), 'y.aprueba', NOW(6))", colegio, pagoId, solicitud, notaId, total, cajero);
		int filas = jdbc.update("UPDATE pago SET estado = 'ANULADO', actualizado_en = NOW(6) WHERE id = ?", pagoId);
		jdbc.update("INSERT INTO aplicacion_pago (colegio_id, pago_id, cuota_id, tipo, monto, revierte_id, creado_en, "
				+ "creado_por, actualizado_en) VALUES (?, ?, ?, 'REVERSION', ?, ?, NOW(6), 'y.aprueba', NOW(6))", colegio,
				pagoId, cuotaId, total.negate(), apId);
		jdbc.update("UPDATE cuota SET monto_pagado = (SELECT COALESCE(SUM(monto), 0) FROM aplicacion_pago WHERE cuota_id = ?), "
				+ "estado = 'PENDIENTE', actualizado_en = NOW(6) WHERE id = ?", cuotaId, cuotaId);
		assertThat(filas).as("cc_app marco el pago ANULADO").isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT estado FROM pago WHERE id = ?", String.class, pagoId)).isEqualTo("ANULADO");
		assertThat(efectivoEsperado(cajaId)).as("el cierre ya no espera el efectivo anulado").isEqualByComparingTo(esperadoAntes.subtract(total));
	}

	private BigDecimal efectivoEsperado(Long cajaId) {
		return jdbc.queryForObject("SELECT COALESCE(SUM(total), 0) FROM pago WHERE caja_diaria_id = ? "
				+ "AND medio = 'EFECTIVO' AND estado = 'VIGENTE'", BigDecimal.class, cajaId);
	}
}
