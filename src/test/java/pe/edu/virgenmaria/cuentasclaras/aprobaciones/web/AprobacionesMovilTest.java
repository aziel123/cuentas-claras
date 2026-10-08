package pe.edu.virgenmaria.cuentasclaras.aprobaciones.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioContactoPersonal;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sprint 6, tanda 2 (decisión 72): aprobar desde el celular es la MISMA bandeja con otra vista. Quien pidió o participó
 * no aprueba desde ningún dispositivo (P8); ningún enlace (GET) aprueba y sin sesión se va al login (P9).
 */
@PruebaIntegracion
class AprobacionesMovilTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ServicioContactoPersonal contacto;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private ColegioRepository colegios;

	@Autowired
	private JdbcTemplate jdbc;

	private Usuario promotora;

	private Usuario directora;

	private Long solicitud;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		Usuario lucia = guardar("lucia.caja", Rol.CAJA);
		promotora = guardar("promotora", Rol.PROMOTOR);
		directora = guardar("directora", Rol.DIRECTOR);
		// Promotoría pide el cambio de contacto de la cajera: lo resuelve otra persona (Dirección).
		UsuariosDePrueba.iniciarSesion(promotora);
		solicitud = contacto.solicitar(lucia.getId(), "987 111 222", null, "Cambió de número de celular");
		SecurityContextHolder.clearContext();
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	private Usuario guardar(String nombre, Rol rol) {
		return UsuariosDePrueba.guardar(usuarios, codificador, 1L, nombre, UsuariosDePrueba.CLAVE, false, rol);
	}

	@Test
	void laVistaDelCelularListaYElDetalleResuelveConLosMismosPost() throws Exception {
		mvc.perform(get("/aprobaciones").param("vista", "movil").with(UsuariosDePrueba.como(directora)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("data-pantalla=\"aprobaciones-movil\"")))
				.andExpect(content().string(containsString("href=\"/aprobaciones/" + solicitud + "\"")))
				.andExpect(content().string(containsString("Cambio de celular o correo del personal")));
		mvc.perform(get("/aprobaciones/" + solicitud).with(UsuariosDePrueba.como(directora))).andExpect(status().isOk())
				.andExpect(content().string(containsString("action=\"/aprobaciones/" + solicitud + "/aprobar\"")))
				.andExpect(content().string(containsString("name=\"volver\" value=\"movil\"")))
				.andExpect(content().string(containsString("No se puede deshacer")));
		mvc.perform(post("/aprobaciones/" + solicitud + "/aprobar").with(csrf()).with(UsuariosDePrueba.como(directora))
				.param("volver", "movil")).andExpect(redirectedUrl("/aprobaciones?vista=movil"))
				.andExpect(flash().attributeExists("exito"));
		assertThat(jdbc.queryForObject("SELECT estado FROM solicitud_cambio WHERE id = ?", String.class, solicitud))
				.isEqualTo("APROBADA");
	}

	/** P8: quien la pidió ve el detalle sin botones y, si fuerza el POST, se rechaza y queda en la bitácora. */
	@Test
	void quienLaPidioNoLaApruebaDesdeElCelular() throws Exception {
		mvc.perform(get("/aprobaciones/" + solicitud).with(UsuariosDePrueba.como(promotora))).andExpect(status().isOk())
				.andExpect(content().string(not(containsString("/aprobar\""))))
				.andExpect(content().string(containsString("la resuelve otra persona")));
		mvc.perform(post("/aprobaciones/" + solicitud + "/aprobar").with(csrf()).with(UsuariosDePrueba.como(promotora))
				.param("volver", "movil")).andExpect(redirectedUrl("/aprobaciones?vista=movil"))
				.andExpect(flash().attributeExists("error"));
		assertThat(jdbc.queryForObject("SELECT estado FROM solicitud_cambio WHERE id = ?", String.class, solicitud))
				.isEqualTo("PENDIENTE");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'AUTOAPROBACION_RECHAZADA'",
				Long.class)).isEqualTo(1);
	}

	/** P9: el enlace del mensaje sin sesión va al login; un GET no aprueba (405); sin CSRF, 403. */
	@Test
	void ningunEnlaceApruebaYSinSesionVaAlLogin() throws Exception {
		mvc.perform(get("/aprobaciones/" + solicitud)).andExpect(status().is3xxRedirection())
				.andExpect(header().string("Location", endsWith("/login")));
		mvc.perform(get("/aprobaciones/" + solicitud + "/aprobar").with(UsuariosDePrueba.como(directora)))
				.andExpect(status().isMethodNotAllowed());
		mvc.perform(post("/aprobaciones/" + solicitud + "/aprobar").with(UsuariosDePrueba.como(directora)))
				.andExpect(status().isForbidden());
		// Un «volver» inventado no redirige afuera.
		mvc.perform(post("/aprobaciones/" + solicitud + "/rechazar").with(csrf()).with(UsuariosDePrueba.como(directora))
				.param("motivo", "No lo confirmó en persona").param("volver", "https://otro.sitio"))
				.andExpect(redirectedUrl("/aprobaciones"));
		assertThat(jdbc.queryForObject("SELECT estado FROM solicitud_cambio WHERE id = ?", String.class, solicitud))
				.isEqualTo("RECHAZADA");
	}

	/** Caja no entra; el colegio B no ve la solicitud del A (404). */
	@Test
	void cajaNoEntraYOtroColegioRecibe404() throws Exception {
		mvc.perform(get("/aprobaciones/" + solicitud).with(UsuariosDePrueba.como(Rol.CAJA)))
				.andExpect(status().isForbidden());
		long colegioB = colegios.save(new Colegio("Colegio de Prueba B")).getId();
		mvc.perform(get("/aprobaciones/" + solicitud).with(UsuariosDePrueba.como(UsuariosDePrueba.autenticado(colegioB,
				992L, "director.b", "Director B", false, EnumSet.of(Rol.DIRECTOR))))).andExpect(status().isNotFound());
	}
}
