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

	private CobroRequest aCuenta(List<Long> cuotas, String monto) {
		return new CobroRequest(UUID.randomUUID(), f.quispe(), cuotas, MedioPago.EFECTIVO, null, new BigDecimal(monto),
				new BigDecimal(monto), new BigDecimal("900.00"), TipoComprobante.BOLETA, null, null, null);
	}
}
