package pe.edu.virgenmaria.cuentasclaras.pasarela;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.pasarela.dto.PagoEnLineaRequest;
import pe.edu.virgenmaria.cuentasclaras.pasarela.dto.SeleccionPagoRequest;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.CobroConfirmado;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.EstadoCobro;
import pe.edu.virgenmaria.cuentasclaras.pasarela.service.ServicioPagoEnLinea;
import pe.edu.virgenmaria.cuentasclaras.pasarela.simulada.PasarelaSimulada;
import pe.edu.virgenmaria.cuentasclaras.pasarela.simulada.PasarelaSimulada.Accion;
import pe.edu.virgenmaria.cuentasclaras.pasarela.simulada.SimuladorPagos;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.estado;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;

/**
 * QA del sprint 4 (sección 10.1, punto 3): la CONSULTA a la pasarela confirma un monto MAYOR que el de la orden o otra
 * moneda. El simulador solo sabe cobrar de menos; aquí se altera su respuesta. Nada se aplica, la orden queda
 * POR_REVISAR con el motivo crítico correcto.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class RespuestasAnomalasPasarelaQaTest {

	@MockitoSpyBean
	private PasarelaSimulada pasarela;

	@Autowired
	private ServicioPagoEnLinea pagoEnLinea;

	@Autowired
	private SimuladorPagos simulador;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private RelojAjustable reloj;

	@Autowired
	private JdbcTemplate jdbc;

	private Familias f;

	@BeforeEach
	void preparar() {
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
		f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		como(new UsuarioAutenticado(40L, 1L, "rosa.familia", "Rosa", null, true, false, false, EnumSet.of(Rol.APODERADO),
				f.rosa()));
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	/** La pasarela responde PAGADO pero con el cobro que devuelve {@code cambio}. */
	private void responderCon(UnaryOperator<CobroConfirmado> cambio) {
		doAnswer(inv -> {
			EstadoCobro real = (EstadoCobro) inv.callRealMethod();
			return real.estado() == EstadoCobro.Estado.PAGADO ? EstadoCobro.pagado(cambio.apply(real.cobro())) : real;
		}).when(pasarela).consultar(anyString());
	}

	private String pagarMarzo() {
		Long marzo = cuota(jdbc, f.mateo(), "PEN-2027-03");
		BigDecimal total = pagoEnLinea.revisar(new SeleccionPagoRequest(List.of(marzo))).total();
		String referencia = pagoEnLinea.crearOrden(new PagoEnLineaRequest(UUID.randomUUID(), List.of(marzo), total, false));
		simulador.simular(referencia, Accion.YAPE);
		return referencia;
	}

	@Test
	void debeDejarPorRevisarConMontoDistintoUnCobroMayorQueLaOrden() {
		responderCon(c -> new CobroConfirmado(c.cargoId(), c.operacionCanonica(), c.monto().add(new BigDecimal("0.01")),
				c.moneda(), c.medio(), c.pagadoEn()));

		String referencia = pagarMarzo();

		assertThat(jdbc.queryForMap("SELECT estado, motivo_revision FROM orden_pago WHERE referencia = ?", referencia))
				.containsEntry("estado", "POR_REVISAR").containsEntry("motivo_revision", "MONTO_DISTINTO");
		assertThat(estado(jdbc, cuota(jdbc, f.mateo(), "PEN-2027-03"))).isEqualTo("PENDIENTE");
		assertThat(contar(jdbc, "pago")).isZero();
	}

	@Test
	void debeDejarPorRevisarConMonedaDistintaUnCobroEnDolares() {
		responderCon(c -> new CobroConfirmado(c.cargoId(), c.operacionCanonica(), c.monto(), "USD", c.medio(),
				c.pagadoEn()));

		String referencia = pagarMarzo();

		assertThat(jdbc.queryForMap("SELECT estado, motivo_revision FROM orden_pago WHERE referencia = ?", referencia))
				.containsEntry("estado", "POR_REVISAR").containsEntry("motivo_revision", "MONEDA_DISTINTA");
		assertThat(contar(jdbc, "pago")).isZero();
	}

	@Test
	void debeNoRegistrarNadaSiLaPasarelaConfirmaUnMontoConTresDecimales() {
		responderCon(c -> new CobroConfirmado(c.cargoId(), c.operacionCanonica(), new BigDecimal("450.001"), c.moneda(),
				c.medio(), c.pagadoEn()));

		String referencia = pagarMarzo();

		assertThat(jdbc.queryForObject("SELECT estado FROM orden_pago WHERE referencia = ?", String.class, referencia))
				.isEqualTo("CREADA");
		assertThat(contar(jdbc, "pago")).isZero();
		assertThat(estado(jdbc, cuota(jdbc, f.mateo(), "PEN-2027-03"))).isEqualTo("PENDIENTE");
	}
}
