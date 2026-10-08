package pe.edu.virgenmaria.cuentasclaras.panel.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.CobroRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioAnulacionPagos;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.FamiliaMorosa;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.CifrasCobranza;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioDescuentos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPanel;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.panel.dto.VistaPanel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;

/**
 * QA sprint 6: pago parcial (a cuenta, con la opción del colegio activada; mismo contexto que
 * {@code ServicioCobroPagoACuentaTest}).
 * <p>
 * Dado que Pedro Flores paga S/ 200.00 a cuenta de la matrícula vencida de Sebastián (S/ 350.00), cuando la promotora
 * abre el panel, entonces lo cobrado sube S/ 200.00, la deuda vencida baja S/ 200.00 (la cuota queda PARCIAL con S/ 150.00)
 * y la familia sigue morosa desde la misma fecha (su cuota más antigua no cambió).
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
@TestPropertySource(properties = "cuentasclaras.caja.permitir-pago-a-cuenta=true")
class CifrasPanelPagoParcialTest {

	private static final LocalDate HOY = LocalDate.of(2027, 4, 15);

	@Autowired
	private PanelPromotoria panel;

	@Autowired
	private CifrasCobranza cobranza;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private ServicioAnulacionPagos anulaciones;

	@Autowired
	private ServicioDescuentos descuentos;

	@Autowired
	private BandejaAprobaciones bandeja;

	@Autowired
	private RelojAjustable reloj;

	@Autowired
	private JdbcTemplate jdbc;

	private EscenarioPanel.Datos datos;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		datos = EscenarioPanel.preparar(estructura, alumnos, planes, cobro, anulaciones, descuentos, bandeja, reloj, jdbc);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void debeContarElPagoParcialEnLoCobradoYDejarSuSaldoComoDeudaVencida() {
		Long matricula = cuota(jdbc, datos.f().sebastian(), "MAT-2027");
		como(EscenarioCaja.CAJA);
		cobro.cobrar(new CobroRequest(UUID.randomUUID(), datos.f().flores(), List.of(matricula), MedioPago.EFECTIVO, null,
				new BigDecimal("200.00"), new BigDecimal("200.00"), new BigDecimal("350.00"), TipoComprobante.BOLETA, null,
				null, null));

		assertThat(EscenarioCaja.estado(jdbc, matricula)).isEqualTo("PARCIAL");
		como(EscenarioCobranza.PROMOTORIA);
		VistaPanel p = panel.ver();
		assertThat(p.hoy().cobrado()).isEqualTo("S/ 1,000.00");
		assertThat(p.hoy().pagos()).isEqualTo(3);
		assertThat(p.hoy().efectivo()).isEqualTo("S/ 550.00");
		assertThat(p.deuda().monto()).isEqualTo("S/ 1,400.00");
		assertThat(p.deuda().familias()).isEqualTo(2);
		assertThat(p.deuda().hasta60()).as("su cuota más antigua sigue siendo la del 28/02").isEqualTo(2);
		FamiliaMorosa flores = cobranza.familiasMorosas(HOY).stream()
				.filter(m -> m.familiaId().equals(datos.f().flores())).findFirst().orElseThrow();
		assertThat(flores.monto()).isEqualByComparingTo("600.00").hasScaleOf(2);
		assertThat(flores.dias()).isEqualTo(46);
	}
}
