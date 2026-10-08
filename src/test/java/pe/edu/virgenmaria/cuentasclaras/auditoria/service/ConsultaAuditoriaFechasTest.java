package pe.edu.virgenmaria.cuentasclaras.auditoria.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.Actor;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import java.time.Instant;
import java.time.LocalDate;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rango de fechas de la bitácora en hora de Lima (no del servidor) y paginación.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class ConsultaAuditoriaFechasTest {

	private static final LocalDate PRIMERO_DE_OCTUBRE = LocalDate.of(2026, 10, 1);

	@Autowired
	private ConsultaAuditoriaService consulta;

	@Autowired
	private AuditoriaService auditoria;

	@Autowired
	private RelojAjustable reloj;

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		UsuariosDePrueba.iniciarSesion(promotora());
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void unEventoALas2330DeLimaEsDeEseDiaAunqueEnUtcSeaElSiguiente() {
		registrarEn("2026-10-02T04:30:00Z"); // 1 de octubre, 23:30 en Lima

		assertThat(consulta.listar(PRIMERO_DE_OCTUBRE, PRIMERO_DE_OCTUBRE, 0).getContent()).hasSize(1);
		assertThat(consulta.listar(PRIMERO_DE_OCTUBRE.plusDays(1), PRIMERO_DE_OCTUBRE.plusDays(1), 0).getContent())
				.isEmpty();
	}

	@Test
	void elDiaIncluyeDesdeLas0000HastaLas2359DeLima() {
		registrarEn("2026-10-01T05:00:00Z"); // 00:00:00 en Lima
		registrarEn("2026-10-02T04:59:59.999999Z"); // 23:59:59.999999 en Lima
		registrarEn("2026-10-01T04:59:59.999999Z"); // 30 de septiembre, 23:59 en Lima

		assertThat(consulta.listar(PRIMERO_DE_OCTUBRE, PRIMERO_DE_OCTUBRE, 0).getContent())
				.extracting(EventoVista::secuencia).containsExactly(2L, 1L);
	}

	@Test
	void unRangoDeMasDeUnAnoSeRechaza() {
		assertThatThrownBy(() -> consulta.listar(PRIMERO_DE_OCTUBRE.minusYears(1).minusDays(1), PRIMERO_DE_OCTUBRE, 0))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("un año");
		assertThat(consulta.listar(PRIMERO_DE_OCTUBRE.minusDays(365), PRIMERO_DE_OCTUBRE, 0)).isNotNull();
	}

	@Test
	void laSegundaPaginaTieneLosEventosMasAntiguos() {
		reloj.fijar(Instant.parse("2026-10-01T15:00:00Z"));
		IntStream.range(0, 55).forEach(i -> auditoria.registrar(Actor.sistema(1L), AccionAuditoria.SESION_CERRADA,
				null, null, null, null, null));

		assertThat(consulta.listar(PRIMERO_DE_OCTUBRE, PRIMERO_DE_OCTUBRE, 1).getContent())
				.extracting(EventoVista::secuencia).containsExactly(5L, 4L, 3L, 2L, 1L);
		assertThat(consulta.listar(PRIMERO_DE_OCTUBRE, PRIMERO_DE_OCTUBRE, -3).getContent())
				.first().extracting(EventoVista::secuencia).isEqualTo(55L);
		assertThat(consulta.listar(PRIMERO_DE_OCTUBRE, PRIMERO_DE_OCTUBRE, 9).getContent()).isEmpty();
	}

	@Test
	void unaFechaOPaginaMalEscritaNoDaError500() throws Exception {
		SecurityContextHolder.clearContext();
		mvc.perform(get("/auditoria").param("desde", "ayer").with(UsuariosDePrueba.como(promotora())))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Revisa los filtros")));
		mvc.perform(get("/auditoria").param("pagina", "-5").with(UsuariosDePrueba.como(promotora())))
				.andExpect(status().isOk());
		mvc.perform(get("/auditoria").param("accion", "INVENTADA").with(UsuariosDePrueba.como(promotora())))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Revisa los filtros")));
	}

	@Test
	void cajaNoPuedeConsultarLaBitacoraNiLlamandoAlServicio() {
		SecurityContextHolder.getContext().setAuthentication(UsuariosDePrueba.autenticacion(
				UsuariosDePrueba.autenticado(Rol.CAJA)));

		assertThatThrownBy(() -> consulta.listar(PRIMERO_DE_OCTUBRE, PRIMERO_DE_OCTUBRE, 0))
				.isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> consulta.paraRevisar()).isInstanceOf(AccessDeniedException.class);
	}

	private void registrarEn(String instante) {
		reloj.fijar(Instant.parse(instante));
		auditoria.registrar(Actor.sistema(1L), AccionAuditoria.SESION_CERRADA, null, null, null, null, null);
	}

	private static pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado promotora() {
		return UsuariosDePrueba.autenticado(Rol.PROMOTOR);
	}
}
