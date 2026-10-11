package pe.edu.virgenmaria.cuentasclaras.privacidad;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.util.EnumSet;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sprint 8, tanda 1 (sección 3.7, decisión 125): el aviso público no nombra a ningún colegio; el de la familia
 * ({@code /familia/privacidad}) nombra al colegio de la sesión como responsable y al proveedor como encargado; Dirección
 * (y solo Dirección) aprueba la versión vigente para SU colegio, con auditoría y sin tocar a otro colegio.
 */
@PruebaIntegracion
class AvisoPrivacidadColegioTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ColegioRepository colegios;

	@Autowired
	private JdbcTemplate jdbc;

	private long colegioB;

	@BeforeEach
	void preparar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
		jdbc.update("UPDATE colegio SET razon_social = 'I.E.P. Los Álamos S.A.C.', ruc = '20123456789', "
				+ "direccion = 'Av. Los Álamos 123, Lima' WHERE id = 1");
		colegioB = colegios.save(new Colegio("Colegio de Prueba B")).getId();
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	private Map<String, Object> aviso(long colegio) {
		return jdbc.queryForMap("SELECT aviso_privacidad_version, aviso_privacidad_aprobado_por, aviso_privacidad_aprobado_en "
				+ "FROM colegio WHERE id = ?", colegio);
	}

	@Test
	void elAvisoPublicoNoNombraAlColegioYElDeLaFamiliaSi() throws Exception {
		String nombre = jdbc.queryForObject("SELECT nombre FROM colegio WHERE id = 1", String.class);
		mvc.perform(get("/privacidad")).andExpect(status().isOk())
				.andExpect(content().string(allOf(containsString("encargado del tratamiento"), not(containsString(nombre)),
						not(containsString("20123456789")))));

		UsuarioAutenticado rosa = new UsuarioAutenticado(40L, 1L, "rosa.familia", "Rosa", null, true, false, false,
				EnumSet.of(Rol.APODERADO), 1L);
		mvc.perform(get("/familia/privacidad").with(UsuariosDePrueba.como(rosa))).andExpect(status().isOk())
				.andExpect(content().string(allOf(containsString(nombre), containsString("I.E.P. Los Álamos S.A.C."),
						containsString("RUC 20123456789"), containsString("Av. Los Álamos 123, Lima"),
						containsString("responsable"), containsString("encargado del tratamiento"),
						containsString("2027-01"))));
		// Es del portal de la familia: el personal no entra por ahí.
		mvc.perform(get("/familia/privacidad").with(UsuariosDePrueba.como(EscenarioCobranza.CAJA)))
				.andExpect(status().isForbidden());
	}

	@Test
	void direccionApruebaLaVersionVigenteConAuditoriaYSoloDireccion() throws Exception {
		assertThat(aviso(1L)).containsEntry("aviso_privacidad_version", null);
		mvc.perform(get("/colegio/privacidad").with(UsuariosDePrueba.como(EscenarioCobranza.DIRECCION)))
				.andExpect(status().isOk())
				.andExpect(content().string(allOf(containsString("Pendiente"), containsString("Aprobar la versión vigente"))));

		mvc.perform(post("/colegio/privacidad/aprobar").with(csrf()).with(UsuariosDePrueba.como(EscenarioCobranza.DIRECCION)))
				.andExpect(redirectedUrl("/colegio/privacidad"))
				.andExpect(flash().attribute("exito", containsString("2027-01")));

		assertThat(aviso(1L)).containsEntry("aviso_privacidad_version", "2027-01")
				.containsEntry("aviso_privacidad_aprobado_por", "director");
		assertThat(aviso(1L).get("aviso_privacidad_aprobado_en")).isNotNull();
		Map<String, Object> evento = jdbc.queryForMap("SELECT nombre_usuario, entidad, entidad_id, valor_anterior, "
				+ "valor_nuevo FROM evento_auditoria WHERE accion = 'PRIVACIDAD_COLEGIO_APROBADA'");
		assertThat(evento).containsEntry("nombre_usuario", "director").containsEntry("entidad", "colegio")
				.containsEntry("entidad_id", "1").containsEntry("valor_anterior", null).containsEntry("valor_nuevo", "2027-01");

		// Queda a la vista: aprobado por quién y cuándo; y aprobar otra vez la misma versión no cambia nada.
		mvc.perform(get("/colegio/privacidad").with(UsuariosDePrueba.como(EscenarioCobranza.PROMOTORIA)))
				.andExpect(status().isOk())
				.andExpect(content().string(allOf(containsString("Aprobado"), containsString("director"),
						not(containsString("Aprobar la versión vigente")))));
		mvc.perform(post("/colegio/privacidad/aprobar").with(csrf()).with(UsuariosDePrueba.como(EscenarioCobranza.DIRECCION)))
				.andExpect(redirectedUrl("/colegio/privacidad"))
				.andExpect(flash().attribute("error", containsString("ya está aprobada")));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'PRIVACIDAD_COLEGIO_APROBADA'",
				Long.class)).isEqualTo(1);

		// Promotoría, Administración y Caja no aprueban (segunda capa: @PreAuthorize en el servicio).
		for (UsuarioAutenticado otro : new UsuarioAutenticado[] { EscenarioCobranza.PROMOTORIA,
				EscenarioCobranza.ADMINISTRACION, EscenarioCobranza.CAJA }) {
			mvc.perform(post("/colegio/privacidad/aprobar").with(csrf()).with(UsuariosDePrueba.como(otro)))
					.andExpect(status().isForbidden());
		}
	}

	@Test
	void laDireccionDelColegioBApruebaSoloElSuyo() throws Exception {
		UsuarioAutenticado direccionB = UsuariosDePrueba.autenticado(colegioB, 93L, "director.b", "Dirección B", false,
				EnumSet.of(Rol.DIRECTOR));

		mvc.perform(post("/colegio/privacidad/aprobar").with(csrf()).with(UsuariosDePrueba.como(direccionB)))
				.andExpect(redirectedUrl("/colegio/privacidad"));

		assertThat(aviso(colegioB)).containsEntry("aviso_privacidad_version", "2027-01")
				.containsEntry("aviso_privacidad_aprobado_por", "director.b");
		assertThat(aviso(1L)).containsEntry("aviso_privacidad_version", null)
				.containsEntry("aviso_privacidad_aprobado_por", null);
		assertThat(jdbc.queryForMap("SELECT colegio_id, entidad_id FROM evento_auditoria WHERE accion = "
				+ "'PRIVACIDAD_COLEGIO_APROBADA'")).containsEntry("colegio_id", colegioB)
				.containsEntry("entidad_id", String.valueOf(colegioB));
		// La familia del colegio B ve su propio colegio como responsable, no el 1.
		UsuarioAutenticado familiaB = new UsuarioAutenticado(41L, colegioB, "familia.b", "Familia B", null, true, false,
				false, EnumSet.of(Rol.APODERADO), 1L);
		mvc.perform(get("/familia/privacidad").with(UsuariosDePrueba.como(familiaB))).andExpect(status().isOk())
				.andExpect(content().string(allOf(containsString("Colegio de Prueba B"), not(containsString("20123456789")))));
	}
}
