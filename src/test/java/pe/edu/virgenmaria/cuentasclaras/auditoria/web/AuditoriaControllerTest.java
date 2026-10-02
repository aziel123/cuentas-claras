package pe.edu.virgenmaria.cuentasclaras.auditoria.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.Actor;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.comun.config.ConfiguracionTiempo;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bitácora legible para la promotora y verificación de integridad.
 */
@PruebaIntegracion
class AuditoriaControllerTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private AuditoriaService auditoria;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private java.time.Clock reloj;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		auditoria.registrar(new Actor(1L, 7L, "caja", "CAJA", "190.40.1.2"), AccionAuditoria.INGRESO_EXITOSO,
				"usuario", "7", null, null, null);
		auditoria.registrar(new Actor(1L, 7L, "caja", "CAJA", "190.40.1.2"), AccionAuditoria.ACCESO_DENEGADO,
				null, null, null, null, "GET /usuarios");
		auditoria.registrar(new Actor(1L, 1L, "director", "DIRECTOR", "190.40.1.3"),
				AccionAuditoria.USUARIO_DESACTIVADO, "usuario", "9", "activo", "inactivo",
				"Usuario docente. Motivo: Dejó el colegio");
	}

	@AfterEach
	void limpiar() {
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void laBitacoraSeLeeEnLenguajeClaroYHoraDeLima() throws Exception {
		String hoy = LocalDate.now(reloj).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
		mvc.perform(get("/auditoria").with(UsuariosDePrueba.como(Rol.PROMOTOR)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Ingresó al sistema")))
				.andExpect(content().string(containsString("Intentó entrar a una página sin permiso")))
				.andExpect(content().string(containsString("Desactivó un usuario")))
				.andExpect(content().string(containsString("activo → inactivo")))
				.andExpect(content().string(containsString("Motivo: Dejó el colegio")))
				.andExpect(content().string(containsString("190.40.1.2")))
				.andExpect(content().string(containsString("Dirección")))
				.andExpect(content().string(containsString(hoy)))
				.andExpect(content().string(containsString("Revisar")));
	}

	@Test
	void losValoresTecnicosSeMuestranEnLenguajeClaro() throws Exception {
		auditoria.registrar(Actor.sistema(1L), AccionAuditoria.USUARIO_CREADO, "usuario", "5", null,
				"roles=CAJA; activo; clave temporal", "Usuario lucia.ramos (Lucía Ramos).");
		auditoria.registrar(Actor.sistema(1L), AccionAuditoria.ROLES_CAMBIADOS, "usuario", "5", "CAJA",
				"ADMINISTRACION,DIRECTOR", null);
		mvc.perform(get("/auditoria").with(UsuariosDePrueba.como(Rol.PROMOTOR)))
				.andExpect(content().string(containsString("Roles: Caja; activo; clave temporal")))
				.andExpect(content().string(containsString("Caja → Administración,Dirección")))
				.andExpect(content().string(not(containsString("— →"))))
				.andExpect(content().string(not(containsString("roles=CAJA"))));
	}

	@Test
	void elFiltroPorFechasExcluyeLoQueEstaFuera() throws Exception {
		LocalDate ayer = LocalDate.now(reloj).minusDays(1);
		mvc.perform(get("/auditoria").with(UsuariosDePrueba.como(Rol.PROMOTOR))
						.param("desde", ayer.minusDays(5).toString()).param("hasta", ayer.toString()))
				.andExpect(content().string(containsString("No hay eventos con estos filtros")))
				.andExpect(content().string(not(containsString("class=\"tabla\""))));
	}

	@Test
	void unRangoInvalidoMuestraUnAviso() throws Exception {
		mvc.perform(get("/auditoria").with(UsuariosDePrueba.como(Rol.PROMOTOR))
						.param("desde", "2026-10-02").param("hasta", "2026-09-01"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("no puede ser anterior")));
	}

	@Test
	void laBitacoraSePagina() throws Exception {
		for (int i = 0; i < 55; i++) {
			auditoria.registrar(Actor.sistema(1L), AccionAuditoria.SESION_CERRADA, null, null, null, null, null);
		}
		mvc.perform(get("/auditoria").with(UsuariosDePrueba.como(Rol.PROMOTOR)))
				.andExpect(content().string(containsString("Página 1 de 2")))
				.andExpect(content().string(containsString("Más antiguos")));
	}

	@Test
	void promotoriaVerificaYVeQueLaBitacoraEstaIntegra() throws Exception {
		MvcResult resultado = mvc.perform(post("/auditoria/verificar-integridad")
						.with(UsuariosDePrueba.como(Rol.PROMOTOR)).with(csrf()))
				.andExpect(redirectedUrl("/auditoria"))
				.andReturn();

		mvc.perform(get("/auditoria").with(UsuariosDePrueba.como(Rol.PROMOTOR)).flashAttrs(resultado.getFlashMap()))
				.andExpect(content().string(containsString("La bitácora está íntegra: se revisaron 3 eventos")))
				.andExpect(content().string(containsString("Verificó la integridad de la bitácora")));
	}

	@Test
	void seFiltraPorUsuarioPorAccionYSoloRevisar() throws Exception {
		mvc.perform(get("/auditoria").param("usuario", "Director").with(UsuariosDePrueba.como(Rol.PROMOTOR)))
				.andExpect(content().string(containsString("Desactivó un usuario")))
				.andExpect(content().string(not(containsString("<td data-etiqueta=\"IP\" class=\"num\">190.40.1.2</td>"))));
		mvc.perform(get("/auditoria").param("accion", "INGRESO_EXITOSO").with(UsuariosDePrueba.como(Rol.PROMOTOR)))
				.andExpect(content().string(containsString("190.40.1.2")))
				.andExpect(content().string(not(containsString("activo → inactivo"))));
		mvc.perform(get("/auditoria").param("soloRevisar", "true").with(UsuariosDePrueba.como(Rol.PROMOTOR)))
				.andExpect(content().string(containsString("GET /usuarios")))
				.andExpect(content().string(not(containsString("activo → inactivo"))));
	}

	@Test
	void laVerificacionMuestraLaHuellaParaAnotar() throws Exception {
		MvcResult resultado = mvc.perform(post("/auditoria/verificar-integridad")
						.with(UsuariosDePrueba.como(Rol.PROMOTOR)).with(csrf()))
				.andReturn();
		String codigo = jdbc.queryForObject("SELECT SUBSTRING(hash, 1, 16) FROM evento_auditoria WHERE secuencia = 4",
				String.class);

		mvc.perform(get("/auditoria").with(UsuariosDePrueba.como(Rol.PROMOTOR)).flashAttrs(resultado.getFlashMap()))
				.andExpect(content().string(containsString("Anota esta huella")))
				.andExpect(content().string(containsString(codigo)))
				.andExpect(content().string(containsString("sprint 4")));
	}

	@Test
	void unaHuellaMalEscritaOQueNoEstaSeAvisa() throws Exception {
		MvcResult malEscrita = mvc.perform(post("/auditoria/verificar-integridad").with(UsuariosDePrueba.como(Rol.PROMOTOR))
						.with(csrf()).param("huellaSecuencia", "2").param("huellaCodigo", "xyz"))
				.andReturn();
		mvc.perform(get("/auditoria").with(UsuariosDePrueba.como(Rol.PROMOTOR)).flashAttrs(malEscrita.getFlashMap()))
				.andExpect(content().string(containsString("El código de la huella son 16 caracteres")));

		MvcResult inexistente = mvc.perform(post("/auditoria/verificar-integridad").with(UsuariosDePrueba.como(Rol.PROMOTOR))
						.with(csrf()).param("huellaSecuencia", "999").param("huellaCodigo", "0123456789abcdef"))
				.andReturn();
		mvc.perform(get("/auditoria").with(UsuariosDePrueba.como(Rol.PROMOTOR)).flashAttrs(inexistente.getFlashMap()))
				.andExpect(content().string(containsString("La bitácora fue ALTERADA")))
				.andExpect(content().string(containsString("recortada")));
	}

	@Test
	void unaBitacoraAlteradaMuestraDesdeQueEvento() throws Exception {
		jdbc.update("UPDATE evento_auditoria SET valor_nuevo = 'activo' WHERE secuencia = 3");

		MvcResult resultado = mvc.perform(post("/auditoria/verificar-integridad")
						.with(UsuariosDePrueba.como(Rol.PROMOTOR)).with(csrf()))
				.andReturn();

		mvc.perform(get("/auditoria").with(UsuariosDePrueba.como(Rol.PROMOTOR)).flashAttrs(resultado.getFlashMap()))
				.andExpect(content().string(containsString("La bitácora fue ALTERADA desde el evento 3")))
				.andExpect(content().string(containsString("role=\"alert\"")));
	}
}
