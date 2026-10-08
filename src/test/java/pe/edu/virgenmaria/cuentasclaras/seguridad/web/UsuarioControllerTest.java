package pe.edu.virgenmaria.cuentasclaras.seguridad.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Pantallas de usuarios: alta sin clave visible (sprint 5: el enlace va directo al titular), ficha con modales y avisos.
 */
@PruebaIntegracion
class UsuarioControllerTest {

	private static final Pattern CLAVE_TEMPORAL = Pattern.compile("clave-temporal-valor[^>]*>([^<]+)<");

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private JdbcTemplate jdbc;

	private Usuario promotora;

	private Usuario caja;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		promotora = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "promotora", UsuariosDePrueba.CLAVE, false,
				Rol.PROMOTOR);
		caja = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "caja", UsuariosDePrueba.CLAVE, false, Rol.CAJA);
	}

	@AfterEach
	void limpiar() {
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void laListaMuestraLosUsuariosConSuEstado() throws Exception {
		mvc.perform(get("/usuarios").with(UsuariosDePrueba.como(promotora)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("caja")))
				.andExpect(content().string(containsString("Activo")))
				.andExpect(content().string(containsString("Nuevo usuario")));
	}

	@Test
	void crearSoloDiceADondeSeEnvioElEnlace() throws Exception {
		MvcResult resultado = mvc.perform(post("/usuarios").with(UsuariosDePrueba.como(promotora)).with(csrf())
						.param("nombreCompleto", "Lucía Ramos").param("nombreUsuario", "lucia.ramos")
						.param("telefonoWhatsapp", "966 777 321").param("roles", "CAJA"))
				.andExpect(status().isOk())
				.andExpect(view().name("usuarios/creado"))
				.andExpect(content().string(containsString("Enlace enviado a WhatsApp +51 *** *** 321")))
				.andExpect(header().string("Cache-Control", containsString("no-store")))
				.andReturn();
		assertThat(resultado.getResponse().getContentAsString()).doesNotContain("/activar/")
				.doesNotContain("Clave temporal");
		assertThat(CLAVE_TEMPORAL.matcher(resultado.getResponse().getContentAsString()).find()).isFalse();
		assertThat(jdbc.queryForObject("SELECT telefono_whatsapp FROM usuario WHERE nombre_usuario = 'lucia.ramos'",
				String.class)).isEqualTo("+51966777321");
	}

	@Test
	void sinCelularNiCorreoElFormularioLoPide() throws Exception {
		mvc.perform(post("/usuarios").with(UsuariosDePrueba.como(promotora)).with(csrf())
						.param("nombreCompleto", "Sin Contacto").param("nombreUsuario", "sin.contacto").param("roles", "CAJA"))
				.andExpect(status().isOk()).andExpect(view().name("usuarios/formulario"))
				.andExpect(content().string(containsString("Escribe el celular (WhatsApp) o el correo")));
	}

	@Test
	void elFormularioMuestraLosErrores() throws Exception {
		mvc.perform(post("/usuarios").with(UsuariosDePrueba.como(promotora)).with(csrf())
						.param("nombreCompleto", "Sin Roles").param("nombreUsuario", "sin.roles"))
				.andExpect(status().isOk())
				.andExpect(view().name("usuarios/formulario"))
				.andExpect(content().string(containsString("Elige al menos un rol.")));
		mvc.perform(post("/usuarios").with(UsuariosDePrueba.como(promotora)).with(csrf())
						.param("nombreCompleto", "Otra Caja").param("nombreUsuario", "CAJA").param("roles", "CAJA"))
				.andExpect(content().string(containsString("Ya existe un usuario «caja»")));
	}

	@Test
	void laFichaTieneModalesConMotivoObligatorio() throws Exception {
		mvc.perform(get("/usuarios/" + caja.getId()).with(UsuariosDePrueba.como(promotora)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("popovertarget=\"modal-desactivar\"")))
				.andExpect(content().string(containsString("popovertarget=\"modal-clave\"")))
				.andExpect(content().string(containsString("popovertarget=\"modal-roles\"")))
				.andExpect(content().string(containsString("minlength=\"10\"")))
				.andExpect(content().string(containsString("action=\"/usuarios/" + caja.getId() + "/desactivar\"")));
	}

	@Test
	void unMotivoCortoMuestraUnAvisoYNoCambiaNada() throws Exception {
		MvcResult resultado = mvc.perform(post("/usuarios/" + caja.getId() + "/desactivar")
						.with(UsuariosDePrueba.como(promotora)).with(csrf()).param("motivo", "corto"))
				.andExpect(redirectedUrl("/usuarios/" + caja.getId()))
				.andReturn();

		mvc.perform(get("/usuarios/" + caja.getId()).with(UsuariosDePrueba.como(promotora))
						.flashAttrs(resultado.getFlashMap()))
				.andExpect(content().string(containsString("El motivo debe tener entre 10 y 500 caracteres.")));
		assertThat(jdbc.queryForObject("SELECT activo FROM usuario WHERE id = ?", Boolean.class, caja.getId())).isTrue();
	}

	@Test
	void desactivarMuestraLaConfirmacion() throws Exception {
		MvcResult resultado = mvc.perform(post("/usuarios/" + caja.getId() + "/desactivar")
						.with(UsuariosDePrueba.como(promotora)).with(csrf()).param("motivo", "Dejó de trabajar aquí"))
				.andReturn();

		mvc.perform(get("/usuarios/" + caja.getId()).with(UsuariosDePrueba.como(promotora))
						.flashAttrs(resultado.getFlashMap()))
				.andExpect(content().string(containsString("quedó desactivado")))
				.andExpect(content().string(containsString("Desactivado")))
				.andExpect(content().string(containsString("popovertarget=\"modal-reactivar\"")));
	}

	@Test
	void direccionNoPuedeRestablecerLaClaveDeUnaPromotora() throws Exception {
		Usuario director = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "director", UsuariosDePrueba.CLAVE, false,
				Rol.DIRECTOR);
		String hashAntes = jdbc.queryForObject("SELECT clave_hash FROM usuario WHERE id = ?", String.class,
				promotora.getId());

		mvc.perform(post("/usuarios/" + promotora.getId() + "/restablecer-clave").with(UsuariosDePrueba.como(director))
						.with(csrf()).param("motivo", "Intento de suplantación"))
				.andExpect(status().isForbidden())
				.andExpect(content().string(not(containsString("clave-temporal-valor"))));
		assertThat(jdbc.queryForObject("SELECT clave_hash FROM usuario WHERE id = ?", String.class, promotora.getId()))
				.isEqualTo(hashAntes);
	}

	@Test
	void rolesInvalidosOVaciosMuestranUnMensajeClaro() throws Exception {
		mvc.perform(post("/usuarios").with(UsuariosDePrueba.como(promotora)).with(csrf())
						.param("nombreCompleto", "Rol Inventado").param("nombreUsuario", "rol.inventado")
						.param("roles", "SUPERUSUARIO"))
				.andExpect(status().isOk())
				.andExpect(view().name("usuarios/formulario"))
				.andExpect(content().string(containsString("Elige roles de la lista.")));

		MvcResult sinRoles = mvc.perform(post("/usuarios/" + caja.getId() + "/roles").with(UsuariosDePrueba.como(promotora))
						.with(csrf()).param("motivo", "Motivo suficiente"))
				.andExpect(redirectedUrl("/usuarios/" + caja.getId()))
				.andReturn();
		mvc.perform(get("/usuarios/" + caja.getId()).with(UsuariosDePrueba.como(promotora))
						.flashAttrs(sinRoles.getFlashMap()))
				.andExpect(content().string(containsString("Elige al menos un rol.")));
	}

	@Test
	void elNombreDeUsuarioSeNormalizaYElNombreCompletoSeEscapa() throws Exception {
		mvc.perform(post("/usuarios").with(UsuariosDePrueba.como(promotora)).with(csrf())
						.param("nombreCompleto", "<script>alert(1)</script> Ramos").param("nombreUsuario", "  Lucia.RAMOS ")
						.param("correo", "lucia@colegio.pe").param("roles", "DOCENTE"))
				.andExpect(status().isOk());

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE nombre_usuario = 'lucia.ramos'", Long.class))
				.isEqualTo(1);
		mvc.perform(get("/usuarios").with(UsuariosDePrueba.como(promotora)))
				.andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt; Ramos")))
				.andExpect(content().string(not(containsString("<script>alert(1)</script>"))));
	}

	@Test
	void nadieVeAccionesSobreSiMismo() throws Exception {
		mvc.perform(get("/usuarios/" + promotora.getId()).with(UsuariosDePrueba.como(promotora)))
				.andExpect(content().string(containsString("Este es tu usuario")))
				.andExpect(content().string(not(containsString("modal-desactivar"))));
	}
}
