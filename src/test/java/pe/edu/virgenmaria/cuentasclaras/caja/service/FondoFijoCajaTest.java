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
import pe.edu.virgenmaria.cuentasclaras.caja.dto.ConteoRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.DepositoRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.model.ResultadoConteo;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.CAJA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.efectivo;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;

/**
 * Hallazgos 3 de QA (M14 y M17): con un fondo fijo de S/ 100 (en las demás pruebas vale 0 y estas reglas no se ven),
 * el esperado del cierre INCLUYE el fondo y el depósito esperado lo RESTA.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
@TestPropertySource(properties = "cuentasclaras.caja.fondo-fijo=100.00")
class FondoFijoCajaTest {

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private ServicioCierreCaja cierre;

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
		como(CAJA);
		cobro.cobrar(efectivo(f.quispe(), List.of(cuota(jdbc, f.mateo(), "PEN-2027-03")), "450.00", "450.00"));
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void esperadoIncluyeElFondoFijo() {
		assertThat(jdbc.queryForObject("SELECT fondo_fijo FROM caja_diaria", BigDecimal.class))
				.isEqualByComparingTo("100.00");
		// Contar solo lo cobrado (sin el fondo) no cuadra.
		assertThat(cierre.contar(new ConteoRequest(new BigDecimal("550.00"), null))).isEqualTo(ResultadoConteo.COINCIDE);

		Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM cierre_caja");
		assertThat((BigDecimal) fila.get("fondo_fijo")).isEqualByComparingTo("100.00");
		assertThat((BigDecimal) fila.get("efectivo_cobrado")).isEqualByComparingTo("450.00");
		assertThat((BigDecimal) fila.get("esperado")).isEqualByComparingTo("550.00");
		assertThat((BigDecimal) fila.get("diferencia")).isEqualByComparingTo("0.00");
	}

	@Test
	void depositoEsperadoRestaElFondoFijo() {
		cierre.contar(new ConteoRequest(new BigDecimal("550.00"), null));
		var estado = cierre.estado();
		assertThat(estado.porDepositar()).singleElement()
				.satisfies(d -> assertThat(d.esperado()).isEqualByComparingTo("450.00"));

		cierre.registrarDeposito(new DepositoRequest(estado.porDepositar().getFirst().cajaId(),
				estado.cuentas().getFirst(), "DEP-4501", estado.hoy(), new BigDecimal("450.00"), null));
		Map<String, Object> deposito = jdbc.queryForMap("SELECT monto, esperado FROM deposito_caja");
		assertThat((BigDecimal) deposito.get("esperado")).isEqualByComparingTo("450.00");
		assertThat((BigDecimal) deposito.get("monto")).isEqualByComparingTo("450.00");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'DEPOSITO_DIFERENTE'",
				Long.class)).isZero();
	}
}
