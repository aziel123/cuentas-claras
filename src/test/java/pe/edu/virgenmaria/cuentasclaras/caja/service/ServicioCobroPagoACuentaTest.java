package pe.edu.virgenmaria.cuentasclaras.caja.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.CobroRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.estado;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;

/** Si el colegio habilita el pago a cuenta (decisión 1, desactivado en el piloto): se imputa y queda resaltado. */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
@TestPropertySource(properties = "cuentasclaras.caja.permitir-pago-a-cuenta=true")
class ServicioCobroPagoACuentaTest {

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private ServicioAnulacionPagos anulaciones;

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioDescuentos descuentos;

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones bandeja;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private JdbcTemplate jdbc;

	private Familias f;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		como(EscenarioCaja.CAJA);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void pagoACuentaHabilitadoImputaYQuedaResaltado() {
		Long marzo = cuota(jdbc, f.mateo(), "PEN-2027-03");
		Long abril = cuota(jdbc, f.mateo(), "PEN-2027-04");

		Long pagoId = cobro.cobrar(aCuenta(List.of(abril, marzo), "500.00"));

		assertThat(jdbc.queryForMap("SELECT total, a_cuenta FROM pago WHERE id = ?", pagoId))
				.containsEntry("a_cuenta", true).satisfies(p -> assertThat((BigDecimal) p.get("total"))
						.isEqualByComparingTo("500.00"));
		// Primero la que vence antes, completa; la última queda parcial.
		assertThat(estado(jdbc, marzo)).isEqualTo("PAGADA");
		assertThat(estado(jdbc, abril)).isEqualTo("PARCIAL");
		assertThat(EscenarioCaja.pagado(jdbc, abril)).isEqualByComparingTo("50.00");
		assertThat(jdbc.queryForList("SELECT descripcion FROM comprobante_linea ORDER BY orden", String.class))
				.containsExactly("Pensión marzo 2027 · Mateo Quispe Huamán",
						"Pensión abril 2027 · Mateo Quispe Huamán (a cuenta)");
		Map<String, Object> evento = EscenarioEscolar.ultimoEvento(jdbc, "PAGO_A_CUENTA");
		assertThat(evento).containsEntry("entidad_id", pagoId.toString());
		assertThat(AccionAuditoria.PAGO_A_CUENTA.requiereAtencion()).isTrue();
	}

	@Test
	void pagoACuentaRespetaElMinimoYEsMenorQueElTotal() {
		Long marzo = cuota(jdbc, f.mateo(), "PEN-2027-03");
		Long abril = cuota(jdbc, f.mateo(), "PEN-2027-04");

		assertThatThrownBy(() -> cobro.cobrar(aCuenta(List.of(marzo, abril), "40.00")))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("mínimo es S/ 50.00");
		assertThatThrownBy(() -> cobro.cobrar(aCuenta(List.of(marzo, abril), "900.00")))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("menor que el total");
		assertThatThrownBy(() -> cobro.cobrar(aCuenta(List.of(marzo, abril), "100.05")))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("múltiplo de S/ 0.10");
		assertThat(EscenarioEscolar.contar(jdbc, "pago")).isZero();
	}

	/**
	 * Hallazgo 11 de QA: el descuento sobre una cuota PARCIAL, con un pago a cuenta real (antes se armaba con un UPDATE
	 * de monto_pagado, un estado que la aplicación no produce y que el trigger de MySQL rechaza).
	 */
	@Test
	void descuentoSobreCuotaParcialConPagoACuentaReal() {
		Long abril = cuota(jdbc, f.mateo(), "PEN-2027-04");
		cobro.cobrar(aCuenta(List.of(abril), "400.00", "450.00"));
		assertThat(estado(jdbc, abril)).isEqualTo("PARCIAL");
		como(pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION);
		assertThatThrownBy(() -> descuentos.solicitar(pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones
				.descuento(f.mateo(), pe.edu.virgenmaria.cuentasclaras.cobranza.model.TipoDescuento.HERMANOS, "50",
						List.of(abril)))).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("mayor que lo que falta pagar");
		assertThat(descuentos.revisar(pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones.descuento(
				f.mateo(), pe.edu.virgenmaria.cuentasclaras.cobranza.model.TipoDescuento.HERMANOS, "10", List.of(abril)))
				.total()).isEqualByComparingTo("45.00");
	}

	/** Si la cuota recibió un pago a cuenta mientras el descuento esperaba aprobación, no se aplica. */
	@Test
	void descuentoCambiadoDesdeLaSolicitudConPagoACuentaReal() {
		Long marzo = cuota(jdbc, f.mateo(), "PEN-2027-03");
		como(pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION);
		Long id = descuentos.solicitar(pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones.descuento(
				f.mateo(), pe.edu.virgenmaria.cuentasclaras.cobranza.model.TipoDescuento.HERMANOS, "50", List.of(marzo)));
		como(EscenarioCaja.CAJA);
		cobro.cobrar(aCuenta(List.of(marzo), "400.00", "450.00"));

		assertThatThrownBy(() -> pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones.aprueba(
				pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.DIRECCION, bandeja, jdbc, "descuento", id))
				.isInstanceOf(ReglaNegocioException.class).hasMessageStartingWith("El descuento cambió desde que se pidió");
		assertThat(jdbc.queryForObject("SELECT estado FROM descuento WHERE id = ?", String.class, id))
				.isEqualTo("SOLICITADO");
		assertThat(EscenarioEscolar.contar(jdbc, "ajuste_cuota")).isZero();
	}

	/** Entre la revisión y el cobro, otra caja registró un pago a cuenta: el monto cambió y se pide revisar de nuevo. */
	@Test
	void montoCambiadoDesdeLaRevisionConPagoACuentaReal() {
		Long marzoMateo = cuota(jdbc, f.mateo(), "PEN-2027-03");
		Long marzoValeria = cuota(jdbc, f.valeria(), "PEN-2027-03");
		var revision = cobro.revisar(f.quispe(), new pe.edu.virgenmaria.cuentasclaras.caja.dto.SeleccionCobroRequest(
				List.of(marzoMateo, marzoValeria), MedioPago.EFECTIVO), null);
		cobro.cobrar(aCuenta(List.of(marzoValeria), "100.00", "450.00"));

		assertThatThrownBy(() -> cobro.cobrar(new CobroRequest(revision.clave(), f.quispe(), revision.cuotaIds(),
				MedioPago.EFECTIVO, null, new BigDecimal("1000"), null, revision.total(), TipoComprobante.BOLETA, null,
				null, null))).isInstanceOf(MontoCambiadoException.class)
				.hasMessage("El monto cambió desde que lo revisaste (ahora es S/ 800.00). Revisa de nuevo antes de cobrar.");
		assertThat(EscenarioEscolar.contar(jdbc, "pago")).isEqualTo(1);
		var nueva = cobro.revisar(f.quispe(), new pe.edu.virgenmaria.cuentasclaras.caja.dto.SeleccionCobroRequest(
				revision.cuotaIds(), MedioPago.EFECTIVO), revision.clave());
		assertThat(nueva.total()).isEqualByComparingTo("800.00");
		assertThat(nueva.clave()).isEqualTo(revision.clave());
	}

	/**
	 * A2: una devolución por «pago duplicado» se acepta solo si OTRO pago vigente cubre alguna de esas cuotas (aquí, dos
	 * pagos de la misma cuota: uno a cuenta y el resto).
	 */
	@Test
	void devolucionPorPagoDuplicadoExigeOtroPagoVigenteDeEsasCuotas() {
		Long abril = cuota(jdbc, f.mateo(), "PEN-2027-04");
		Long primero = cobro.cobrar(aCuenta(List.of(abril), "400.00", "450.00"));
		Long segundo = cobro.cobrar(EscenarioCaja.efectivo(f.quispe(), List.of(abril), "50.00", "50.00"));
		anulaciones.solicitarDevolucion(segundo, pe.edu.virgenmaria.cuentasclaras.caja.model.CausaDevolucion.PAGO_DUPLICADO,
				"La mamá pagó el saldo y el papá también");
		assertThat(jdbc.queryForObject("SELECT datos FROM solicitud_cambio", String.class)).contains("PAGO_DUPLICADO");
		// Anulado el primero, el segundo ya no tiene «otro pago» de esa cuota.
		pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones.aprueba(
				pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.DIRECCION, bandeja, jdbc, "pago", segundo);
		como(EscenarioCaja.CAJA);
		assertThatThrownBy(() -> anulaciones.solicitarDevolucion(primero,
				pe.edu.virgenmaria.cuentasclaras.caja.model.CausaDevolucion.PAGO_DUPLICADO,
				"La familia dice que también lo pagó dos veces")).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("no es un pago duplicado");
	}

	private CobroRequest aCuenta(List<Long> cuotas, String monto, String total) {
		return new CobroRequest(UUID.randomUUID(), f.quispe(), cuotas, MedioPago.EFECTIVO, null, new BigDecimal(monto),
				new BigDecimal(monto), new BigDecimal(total), TipoComprobante.BOLETA, null, null, null);
	}

	private CobroRequest aCuenta(List<Long> cuotas, String monto) {
		return new CobroRequest(UUID.randomUUID(), f.quispe(), cuotas, MedioPago.EFECTIVO, null, new BigDecimal(monto),
				new BigDecimal(monto), new BigDecimal("900.00"), TipoComprobante.BOLETA, null, null, null);
	}
}
