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
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EnlacesDePrueba;
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
 * Mitigación de suplantación. Sprint 5 (A2): ya no hay clave temporal visible; el enlace de un solo uso llega DIRECTO al
 * celular del titular y vence a las 48 horas. El titular ve quién restableció su acceso, Promotoría ve qué revisar y la
 * bitácora marca "Revisar" los restablecimientos y las altas sensibles.
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

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.comunicacion.proceso.DespachoMensajes despacho;

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.comunicacion.proveedor.BuzonSimulado buzon;

	@Autowired
	private ServicioActivacionCuenta activacion;

	private Usuario promotora;

	private Usuario director;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		buzon.vaciar();
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
	void elEnlaceDelPersonalVenceALas48HorasYNadieMasLoVe() throws Exception {
		UsuarioCreado primero = crear("pedro.caja", Rol.CAJA);
		UsuarioCreado segundo = crear("rosa.caja", Rol.CAJA);
		assertThat(primero.enviadoA()).startsWith("Enlace enviado a WhatsApp +51 *** ***");
		String rutaPedro = EnlacesDePrueba.recibido(despacho, buzon, 1L, celular("pedro.caja"));
		String rutaRosa = EnlacesDePrueba.recibido(despacho, buzon, 1L, celular("rosa.caja"));
		// Con la clave al azar no entra nadie: nunca se mostró y ya venció.
		ingresar("pedro.caja", "cualquier-clave-123").andExpect(redirectedUrl("/login?error"));

		reloj.avanzar(Duration.ofHours(47).plusMinutes(59));
		assertThat(activacion.vista(1L, EnlacesDePrueba.token(rutaPedro))).hasValueSatisfying(v ->
				assertThat(v.personal()).isTrue());
		mvc.perform(post(rutaPedro).with(csrf()).param("documento", "pedro.caja").param("clave", CLAVE_PEDRO)
				.param("confirmacion", CLAVE_PEDRO)).andExpect(redirectedUrl("/login?cuenta-activada"));
		ingresar("pedro.caja", CLAVE_PEDRO).andExpect(redirectedUrl("/inicio"));
		assertThat(contar("ACCESO_PERSONAL_ACTIVADO")).isEqualTo(1);

		reloj.avanzar(Duration.ofMinutes(1));
		mvc.perform(get(rutaRosa)).andExpect(content().string(containsString("Este enlace ya no sirve")));
	}

	@Test
	void unEnlaceVencidoSeRestableceConOtroDe48Horas() throws Exception {
		UsuarioCreado creado = crear("pedro.caja", Rol.CAJA);
		String primero = EnlacesDePrueba.recibido(despacho, buzon, 1L, celular("pedro.caja"));
		reloj.avanzar(Duration.ofHours(49));
		mvc.perform(get(primero)).andExpect(content().string(containsString("Este enlace ya no sirve")));

		UsuarioCreado nueva = conSesion(promotora, () -> servicio.restablecerClave(creado.id(), "El enlace le venció"));
		assertThat(nueva.enviadoA()).doesNotContain("/activar/");
		String segundo = EnlacesDePrueba.recibido(despacho, buzon, 1L, celular("pedro.caja"));
		assertThat(segundo).isNotEqualTo(primero);
		mvc.perform(post(segundo).with(csrf()).param("documento", "pedro.caja").param("clave", CLAVE_PEDRO)
				.param("confirmacion", CLAVE_PEDRO)).andExpect(redirectedUrl("/login?cuenta-activada"));
		ingresar("pedro.caja", CLAVE_PEDRO).andExpect(redirectedUrl("/inicio"));
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
				() -> servicio.crear(new CrearUsuarioRequest("Nombre de " + nombreUsuario, nombreUsuario, null,
						celular(nombreUsuario).substring(3), Set.of(rol))));
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

	private static final String CLAVE_PEDRO = "la clave que elegi para caja 2026";

	/** Un celular distinto por usuario (+519XXXXXXXX). */
	static String celular(String nombreUsuario) {
		return UsuariosDePrueba.celular(nombreUsuario);
	}

	private ResultActions ingresar(String usuario, String clave) throws Exception {
		return mvc.perform(post("/login").with(csrf()).param("usuario", usuario).param("clave", clave));
	}

	private long contar(String accion) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion = ?", Long.class, accion);
	}
}
