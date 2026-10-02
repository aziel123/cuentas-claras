package pe.edu.virgenmaria.cuentasclaras.comun.multicolegio;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.Actor;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Aislamiento entre colegios visto desde la web: la Dirección del colegio B no ve ni toca nada del A.
 */
@PruebaIntegracion
class AislamientoColegiosWebTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private ColegioRepository colegios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private AuditoriaService auditoria;

	@Autowired
	private JdbcTemplate jdbc;

	private Usuario cajaA;

	private Usuario promotoraA;

	private Usuario directorB;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		long colegioB = colegios.save(new Colegio("Colegio de Prueba B")).getId();
		promotoraA = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "promotora.a", UsuariosDePrueba.CLAVE, false,
				Rol.PROMOTOR);
		cajaA = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "caja.a", UsuariosDePrueba.CLAVE, false, Rol.CAJA);
		directorB = UsuariosDePrueba.guardar(usuarios, codificador, colegioB, "director.b", UsuariosDePrueba.CLAVE,
				false, Rol.DIRECTOR);
		auditoria.registrar(Actor.sistema(1L), AccionAuditoria.USUARIO_CREADO, "usuario", "1", null, null,
				"evento-secreto-del-colegio-a");
		auditoria.registrar(Actor.sistema(colegioB), AccionAuditoria.USUARIO_CREADO, "usuario", "2", null, null,
				"evento-propio-del-colegio-b");
	}

	@AfterEach
	void limpiar() {
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void directorDelColegioBRecibe404AlAbrirUsuarioDelColegioA() throws Exception {
		mvc.perform(get("/usuarios/" + cajaA.getId()).with(UsuariosDePrueba.como(directorB)))
				.andExpect(status().isNotFound());
	}

	@Test
	void directorDelColegioBNoPuedeDesactivarUsuarioDelColegioA() throws Exception {
		mvc.perform(post("/usuarios/" + cajaA.getId() + "/desactivar").with(UsuariosDePrueba.como(directorB))
						.with(csrf()).param("motivo", "Intento desde otro colegio"))
				.andExpect(status().isNotFound());

		assertThat(jdbc.queryForObject("SELECT activo FROM usuario WHERE id = ?", Boolean.class, cajaA.getId())).isTrue();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'USUARIO_DESACTIVADO'",
				Long.class)).isZero();
	}

	@Test
	void listaDeUsuariosDelColegioBNoMuestraLosDelColegioA() throws Exception {
		mvc.perform(get("/usuarios").with(UsuariosDePrueba.como(directorB)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("director.b")))
				.andExpect(content().string(not(containsString("caja.a"))))
				.andExpect(content().string(not(containsString("promotora.a"))));
	}

	@Test
	void bitacoraDelColegioBNoMuestraEventosDelColegioA() throws Exception {
		mvc.perform(get("/auditoria").with(UsuariosDePrueba.como(directorB)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("evento-propio-del-colegio-b")))
				.andExpect(content().string(not(containsString("evento-secreto-del-colegio-a"))));

		mvc.perform(get("/auditoria").with(UsuariosDePrueba.como(promotoraA)))
				.andExpect(content().string(containsString("evento-secreto-del-colegio-a")))
				.andExpect(content().string(not(containsString("evento-propio-del-colegio-b"))));
	}
}
