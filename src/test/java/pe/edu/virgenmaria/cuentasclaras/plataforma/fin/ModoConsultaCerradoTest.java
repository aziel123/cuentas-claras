package pe.edu.virgenmaria.cuentasclaras.plataforma.fin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.time.Duration;
import java.time.LocalDate;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/**
 * Sprint 11, tanda 2 (3.8 y 3.10): en CERRADO las familias siguen en modo consulta hasta {@code conservar_hasta}
 * inclusive; el día siguiente, nadie entra (la sesión se invalida y vuelve al ingreso). El personal no entra.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class ModoConsultaCerradoTest extends BaseFinColegio {

	@Autowired
	private MockMvc mvc;

	@Test
	void lasFamiliasConsultanHastaConservarHastaYDespuesNadieEntra() throws Exception {
		long colegio = colegioEnProduccion("Colegio Consulta", EscenarioPlataforma.RUC_D, "titular.consulta", "45678971",
				"987650071");
		suspender(colegio);
		recibir(colegio, "45678971");
		cerrar(colegio);
		LocalDate hasta = jdbc.queryForObject("SELECT conservar_hasta FROM colegio WHERE id = ?", java.sql.Date.class,
				colegio).toLocalDate();
		UsuarioAutenticado familia = new UsuarioAutenticado(991L, colegio, "familia.cerrada", "Familia Cerrada", null,
				true, false, false, EnumSet.of(Rol.APODERADO), 1L);

		// El último día todavía consulta (no la saca el filtro).
		reloj.fijar(hasta.atTime(23, 0).atZone(java.time.ZoneId.of("America/Lima")).toInstant());
		String antes = mvc.perform(get("/familia").with(UsuariosDePrueba.como(familia))).andReturn().getResponse()
				.getRedirectedUrl();
		assertThat(antes).isNotEqualTo("/login?error");
		// El personal ya no entra.
		mvc.perform(get("/inicio").with(UsuariosDePrueba.como(usuario(colegio, "titular.consulta"))))
				.andExpect(redirectedUrl("/login?error"));

		// Un día después de conservar_hasta, tampoco la familia.
		reloj.mover(Duration.ofHours(2));
		mvc.perform(get("/familia").with(UsuariosDePrueba.como(familia))).andExpect(redirectedUrl("/login?error"));
	}
}
