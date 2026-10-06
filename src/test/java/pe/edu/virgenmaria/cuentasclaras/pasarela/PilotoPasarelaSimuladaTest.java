package pe.edu.virgenmaria.cuentasclaras.pasarela;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.pasarela.dto.AplicacionIngresoRequest;
import pe.edu.virgenmaria.cuentasclaras.pasarela.dto.PagoEnLineaRequest;
import pe.edu.virgenmaria.cuentasclaras.pasarela.dto.SeleccionPagoRequest;
import pe.edu.virgenmaria.cuentasclaras.pasarela.service.ServicioIngresosPorRevisar;
import pe.edu.virgenmaria.cuentasclaras.pasarela.service.ServicioPagoEnLinea;
import pe.edu.virgenmaria.cuentasclaras.pasarela.simulada.PasarelaSimulada.Accion;
import pe.edu.virgenmaria.cuentasclaras.pasarela.simulada.SimuladorPagos;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;

/**
 * Correcciones del sprint 4 (S4-M1): con la marca {@code cuentasclaras.entorno.nombre: PILOTO}, todas las páginas
 * muestran la franja del entorno de prueba y un pago con la pasarela SIMULADA queda POR REVISAR (SIMULADA_EN_PILOTO):
 * no registra pago, no emite comprobante, no marca cuotas pagadas y no se puede aplicar.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
@TestPropertySource(properties = "cuentasclaras.entorno.nombre=PILOTO")
class PilotoPasarelaSimuladaTest {

	@Autowired
	private ServicioPagoEnLinea pagoEnLinea;

	@Autowired
	private SimuladorPagos simulador;

	@Autowired
	private ServicioIngresosPorRevisar ingresos;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	private Familias f;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void enElPilotoUnPagoSimuladoNoMarcaCuotasPagadasNiSeAplica() {
		Long marzo = cuota(jdbc, f.mateo(), "PEN-2027-03");
		UsuarioAutenticado rosa = new UsuarioAutenticado(40L, 1L, "rosa.familia", "Rosa", null, true, false, false,
				EnumSet.of(Rol.APODERADO), f.rosa());
		como(rosa);
		BigDecimal total = pagoEnLinea.revisar(new SeleccionPagoRequest(List.of(marzo))).total();
		String referencia = pagoEnLinea.crearOrden(new PagoEnLineaRequest(UUID.randomUUID(), List.of(marzo), total, false));
		simulador.simular(referencia, Accion.YAPE);

		assertThat(jdbc.queryForObject("SELECT CONCAT(estado, ' ', motivo_revision) FROM orden_pago WHERE referencia = ?",
				String.class, referencia)).isEqualTo("POR_REVISAR SIMULADA_EN_PILOTO");
		assertThat(EscenarioCaja.estado(jdbc, marzo)).isEqualTo("PENDIENTE");
		assertThat(contar(jdbc, "pago")).isZero();
		assertThat(contar(jdbc, "comprobante")).isZero();

		Long ordenId = jdbc.queryForObject("SELECT id FROM orden_pago WHERE referencia = ?", Long.class, referencia);
		como(EscenarioCobranza.ADMINISTRACION);
		assertThatThrownBy(() -> ingresos.solicitarAplicacion(ordenId, new AplicacionIngresoRequest(f.quispe(),
				List.of(marzo), "Aplicarlo a marzo como si fuera real"))).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("SIMULADO");
		assertThat(EscenarioCaja.estado(jdbc, marzo)).isEqualTo("PENDIENTE");
	}

	@Test
	void todasLasPaginasMuestranLaFranjaDelPiloto() throws Exception {
		SecurityContextHolder.clearContext();
		mvc.perform(get("/login")).andExpect(content().string(containsString("PILOTO · Entorno de prueba")));
		mvc.perform(get("/inicio").with(UsuariosDePrueba.como(EscenarioCobranza.PROMOTORIA)))
				.andExpect(content().string(containsString("PILOTO · Entorno de prueba")));
	}
}
