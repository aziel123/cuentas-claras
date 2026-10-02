package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.ConsultaAuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.EventoVista;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.FiltroBitacora;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.CambiarRolesRequest;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.CrearUsuarioRequest;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.UsuarioCreado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.time.Duration;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/**
 * Mitigación de suplantación (hasta que en el sprint 4 la clave temporal llegue directo al titular):
 * la clave temporal vence a las 48 horas, el titular ve quién restableció su clave, Promotoría ve qué revisar
 * y la bitácora marca "Revisar" los restablecimientos y las altas sensibles.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class ClaveTemporalTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private RelojAjustable reloj;

	@Autowired
	private ServicioUsuarios servicio;

	@Autowired
	private ConsultaAuditoriaService consulta;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private JdbcTemplate jdbc;

	private Usuario promotora;

	private Usuario director;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		promotora = guardar("promotora", "María Elena Torres", Rol.PROMOTOR);
		director = guardar("director", "Jorge Salazar", Rol.DIRECTOR);
	}

	@AfterEach
	void limpiar() {
		// El reloj se comparte con las demás clases de prueba: se devuelve a su inicio.
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void laClaveTemporalVenceALas48Horas() throws Exception {
		UsuarioCreado primero = crear("pedro.caja", Rol.CAJA);
		UsuarioCreado segundo = crear("rosa.caja", Rol.CAJA);

		reloj.avanzar(Duration.ofHours(47).plusMinutes(59));
		ingresar("pedro.caja", primero.claveTemporal()).andExpect(redirectedUrl("/cuenta/cambiar-clave"));

		reloj.avanzar(Duration.ofMinutes(1));
		ingresar("rosa.caja", segundo.claveTemporal()).andExpect(redirectedUrl("/login?vencida"));
		mvc.perform(get("/login").param("vencida", ""))
				.andExpect(content().string(containsString("Tu clave temporal venció: dura 48 horas")));
		assertThat(contar("INGRESO_RECHAZADO_CLAVE_VENCIDA")).isEqualTo(1);
	}

	@Test
	void unaClaveVencidaSeRestableceConOtras48Horas() throws Exception {
		UsuarioCreado creado = crear("pedro.caja", Rol.CAJA);
		reloj.avanzar(Duration.ofHours(49));
		ingresar("pedro.caja", creado.claveTemporal()).andExpect(redirectedUrl("/login?vencida"));

		UsuarioCreado nueva = conSesion(promotora, () -> servicio.restablecerClave(creado.id(), "La clave temporal le venció"));

		ingresar("pedro.caja", nueva.claveTemporal()).andExpect(redirectedUrl("/cuenta/cambiar-clave"));
	}

	@Test
	void elTitularVeQuienRestablecioSuClave() throws Exception {
		Usuario caja = guardar("lucia.caja", "Lucía Ramos", Rol.CAJA);
		// Auditoría A5 (sprint 2): la clave de Caja solo la restablece Promotoría.
		conSesion(promotora, () -> servicio.restablecerClave(caja.getId(), "Me dijo que la olvidó"));

		mvc.perform(get("/inicio").with(UsuariosDePrueba.como(UsuariosDePrueba.autenticado(1L, caja.getId(),
						"lucia.caja", "Lucía Ramos", false, EnumSet.of(Rol.CAJA)))))
				.andExpect(content().string(containsString("Tu clave fue restablecida por María Elena Torres el")))
				.andExpect(content().string(containsString("Si no lo pediste, avisa a Promotoría.")));
		assertThat(jdbc.queryForObject("SELECT clave_restablecida_por FROM usuario WHERE id = ?", String.class,
				caja.getId())).isEqualTo("promotora");

		reloj.avanzar(Duration.ofDays(31));
		mvc.perform(get("/inicio").with(UsuariosDePrueba.como(UsuariosDePrueba.autenticado(1L, caja.getId(),
						"lucia.caja", "Lucía Ramos", false, EnumSet.of(Rol.CAJA)))))
				.andExpect(content().string(not(containsString("Tu clave fue restablecida"))));
	}

	@Test
	void promotoriaVeEnSuInicioLoQueHayQueRevisar() throws Exception {
		UsuarioCreado caja = crear("pedro.caja", Rol.CAJA);
		UsuarioCreado docente = crear("ana.docente", Rol.DOCENTE);
		conSesion(director, () -> servicio.restablecerClave(docente.id(), "Ana olvidó su clave"));
		conSesion(promotora, () -> {
			servicio.cambiarRoles(caja.id(), new CambiarRolesRequest(EnumSet.of(Rol.DOCENTE), "Ahora dicta clases"));
			return null;
		});

		mvc.perform(get("/inicio").with(UsuariosDePrueba.como(promotora)))
				.andExpect(content().string(containsString("Para revisar")))
				.andExpect(content().string(containsString("Restableció la clave de un usuario")))
				.andExpect(content().string(containsString("Creó un usuario")))
				.andExpect(content().string(containsString("Cambió los roles de un usuario")))
				.andExpect(content().string(containsString("Nombre de pedro.caja (pedro.caja)")))
				.andExpect(content().string(containsString(">director<")));

		reloj.avanzar(Duration.ofDays(8));
		mvc.perform(get("/inicio").with(UsuariosDePrueba.como(promotora)))
				.andExpect(content().string(containsString("Nada para revisar")));
	}

	@Test
	void laBitacoraMarcaRevisarLosRestablecimientosYLasAltasSensibles() {
		crear("pedro.caja", Rol.CAJA);
		UsuarioCreado docente = crear("ana.docente", Rol.DOCENTE);
		conSesion(director, () -> servicio.restablecerClave(docente.id(), "Ana olvidó su clave"));
		UsuariosDePrueba.iniciarSesion(promotora);

		LocalDate hoy = LocalDate.now(reloj);
		List<EventoVista> todos = consulta.listar(FiltroBitacora.fechas(hoy, hoy), 0).getContent();
		List<EventoVista> revisar = consulta.listar(new FiltroBitacora(hoy, hoy, null, null, true), 0).getContent();

		assertThat(todos).hasSize(3);
		assertThat(revisar).extracting(EventoVista::accion)
				.containsExactly("Restableció la clave de un usuario", "Creó un usuario");
		assertThat(revisar).allMatch(EventoVista::requiereAtencion);
		assertThat(revisar.get(1).cambio()).contains("Caja");
		assertThat(todos).filteredOn(e -> e.cambio() != null && e.cambio().contains("Docente"))
				.singleElement().satisfies(e -> assertThat(e.requiereAtencion()).isFalse());
	}

	private UsuarioCreado crear(String nombreUsuario, Rol rol) {
		return conSesion(promotora,
				() -> servicio.crear(new CrearUsuarioRequest("Nombre de " + nombreUsuario, nombreUsuario, null, Set.of(rol))));
	}

	/** Llama al servicio con un usuario en sesión y la limpia: si no, MockMvc la usaría en las peticiones siguientes. */
	private static <T> T conSesion(Usuario usuario, java.util.function.Supplier<T> llamada) {
		UsuariosDePrueba.iniciarSesion(usuario);
		try {
			return llamada.get();
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	private Usuario guardar(String nombre, String nombreCompleto, Rol rol) {
		Usuario usuario = UsuariosDePrueba.guardar(usuarios, codificador, 1L, nombre, UsuariosDePrueba.CLAVE, false, rol);
		jdbc.update("UPDATE usuario SET nombre_completo = ? WHERE id = ?", nombreCompleto, usuario.getId());
		return usuario;
	}

	private ResultActions ingresar(String usuario, String clave) throws Exception {
		return mvc.perform(post("/login").with(csrf()).param("usuario", usuario).param("clave", clave));
	}

	private long contar(String accion) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion = ?", Long.class, accion);
	}
}
