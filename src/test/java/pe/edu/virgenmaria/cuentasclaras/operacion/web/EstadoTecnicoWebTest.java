package pe.edu.virgenmaria.cuentasclaras.operacion.web;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sprint 7, tanda 1 por la web: {@code /panel/sistema} solo para Promotoría y sin datos personales; {@code /salud/respaldo}
 * público y sin fechas (vigilante externo); las sondas de vida y de disponibilidad públicas y sin detalles; y la página de
 * error con el código de la petición en lugar del mensaje.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class EstadoTecnicoWebTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private RelojAjustable reloj;

	@BeforeEach
	void preparar() {
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		jdbc.update("DELETE FROM respaldo");
	}

	@AfterEach
	void limpiar() {
		jdbc.update("DELETE FROM respaldo");
	}

	@Test
	void soloPromotoriaVeElEstadoTecnico() throws Exception {
		mvc.perform(get("/panel/sistema").with(UsuariosDePrueba.como(Rol.PROMOTOR))).andExpect(status().isOk())
				.andExpect(content().string(allOf(containsString("Estado técnico"),
						containsString("Todavía no hay respaldos registrados"),
						containsString("Alertas técnicas apagadas"),
						containsString("Los procesos automáticos están apagados"))));
		for (Rol rol : new Rol[] { Rol.DIRECTOR, Rol.ADMINISTRACION, Rol.CAJA, Rol.DOCENTE, Rol.APODERADO }) {
			mvc.perform(get("/panel/sistema").with(UsuariosDePrueba.como(rol))).andExpect(status().isForbidden());
		}
		mvc.perform(get("/panel/sistema")).andExpect(status().is3xxRedirection());
	}

	@Test
	void conUnRespaldoDeHoyLaPromotoraLoVeYElVigilanteRecibeOk() throws Exception {
		mvc.perform(get("/salud/respaldo")).andExpect(status().isOk()).andExpect(content().string("ATRASADO"))
				.andExpect(header().string("Cache-Control", containsString("no-store")));

		respaldo("cc-20261002-023000.sql.gz.age", "2026-10-02 02:31:10", "IGUAL", null);

		mvc.perform(get("/panel/sistema").with(UsuariosDePrueba.como(Rol.PROMOTOR))).andExpect(status().isOk())
				.andExpect(content().string(allOf(
						containsString("Último respaldo: hoy 02:31, verificado en el destino"),
						containsString("Están todas las filas del respaldo anterior."))));
		mvc.perform(get("/salud/respaldo")).andExpect(content().string("OK"));
	}

	@Test
	void unRespaldoQueEncontroFilasFaltantesSeVeEnRojoYElVigilanteRecibeRevisar() throws Exception {
		respaldo("cc-20261002-023000.sql.gz.age", "2026-10-02 02:31:10", "FALTAN_FILAS", "pago (1)");

		mvc.perform(get("/panel/sistema").with(UsuariosDePrueba.como(Rol.PROMOTOR)))
				.andExpect(content().string(containsString("Faltan filas que existían en un respaldo anterior (pago (1); respaldo ")));
		mvc.perform(get("/salud/respaldo")).andExpect(content().string("REVISAR"));
	}

	@Test
	void lasSondasSonPublicasYSinDetalles() throws Exception {
		for (String sonda : new String[] { "/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness" }) {
			mvc.perform(get(sonda)).andExpect(status().isOk())
					.andExpect(content().string(allOf(containsString("UP"), not(containsString("components")),
							not(containsString("details")))));
		}
	}

	@Test
	void laPaginaDeErrorMuestraElCodigoDeLaPeticionYNoElMensaje() throws Exception {
		mvc.perform(get("/error").accept(org.springframework.http.MediaType.TEXT_HTML)
				.requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
				.requestAttr(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException("DNI 45678912 duplicado")))
				.andExpect(content().string(allOf(matchesPattern("(?s).*Código de error: <strong>[0-9a-f]{12}</strong>.*"),
						not(containsString("45678912")))));
	}

	private void respaldo(String archivo, String fin, String comparacion, String diferencias) {
		jdbc.update("INSERT INTO respaldo (inicio, fin, archivo, sha256, bytes, version_esquema, secuencia_antes, "
				+ "hash_antes, secuencia_despues, hash_despues, conteos, destino, comparacion, diferencias, creado_en, "
				+ "creado_por) VALUES (TIMESTAMP '2026-10-02 02:30:00', ?, ?, REPEAT('a', 64), 1024, '24', 0, "
				+ "REPEAT('0', 64), 0, REPEAT('0', 64), '{}', 'simulado', ?, ?, TIMESTAMP '2026-10-02 02:31:10', "
				+ "'cc_respaldo')", java.time.LocalDateTime.parse(fin.replace(" ", "T")), archivo, comparacion, diferencias);
	}
}
