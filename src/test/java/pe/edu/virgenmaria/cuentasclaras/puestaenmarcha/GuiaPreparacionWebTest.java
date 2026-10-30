package pe.edu.virgenmaria.cuentasclaras.puestaenmarcha;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ColegioEnPreparacionException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.TokenDeSesionDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.dto.FilaPaso;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.dto.ListaVerificacion;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service.EstadoPuestaEnMarcha;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service.GuiaPuestaEnMarcha;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sprint 11, tanda 1b (sección 3.14 y decisión 210): la guía de la puesta en marcha en un colegio en PREPARACION, sobre la
 * aplicación completa. BannerPreparacion (los 5 inicios del personal, nunca un botón a una ruta con 403), InicioPreparacion
 * (sin cifras ni gráficos), PuestaEnMarchaVista (estado, responsable, «Ir», «Siguiente», firmas y producción deshabilitada),
 * EstadosVaciosPaso y PreparacionAdvice (409 con el motivo y el enlace).
 */
@PruebaIntegracion
class GuiaPreparacionWebTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ColegioRepository colegios;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TokenDeSesionDePrueba tokens;

	@Autowired
	private EstadoPuestaEnMarcha estado;

	private long colegio;

	private final Map<Rol, Usuario> personal = new EnumMap<>(Rol.class);

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		tokens.olvidar();
		colegio = colegios.saveAndFlush(Colegio.enPreparacion("Colegio Guía", "Colegio Guía S.A.C.",
				EscenarioPlataforma.RUC_A, "Av. 1", "info@guia.edu.pe", EnumSet.of(Nivel.PRIMARIA), "Titular", "45678921",
				null)).getId();
		for (Rol rol : List.of(Rol.PROMOTOR, Rol.DIRECTOR, Rol.ADMINISTRACION, Rol.CAJA, Rol.DOCENTE)) {
			personal.put(rol, UsuariosDePrueba.guardar(usuarios, codificador, colegio, rol.name().toLowerCase() + ".guia",
					UsuariosDePrueba.CLAVE, false, rol));
		}
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		tokens.olvidar();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void elBannerApareceEnLosCincoIniciosDelPersonal() throws Exception {
		for (Usuario u : personal.values()) {
			mvc.perform(get("/inicio").with(UsuariosDePrueba.como(u))).andExpect(status().isOk())
					.andExpect(content().string(containsString("data-guia")))
					.andExpect(content().string(containsString("Tu colegio está en preparación: faltan")))
					.andExpect(content().string(containsString("<progress")))
					.andExpect(content().string(containsString("<label for=\"guia-progreso\"")));
		}
		// Caja: nada le toca en preparación.
		mvc.perform(get("/inicio").with(UsuariosDePrueba.como(personal.get(Rol.CAJA))))
				.andExpect(content().string(containsString("Por ahora no tienes pasos pendientes")))
				.andExpect(content().string(not(containsString("data-guia-boton"))));
		// La plantilla del apoderado no la incluye nunca (las familias no ven la preparación).
		assertThat(Files.readString(Path.of("src/main/resources/templates/inicio/apoderado.html")))
				.doesNotContain("bannerPreparacion");
	}

	@Test
	void enPreparacionElInicioDePromotoriaYDireccionNoTraeCifrasNiGraficos() throws Exception {
		for (Rol rol : List.of(Rol.PROMOTOR, Rol.DIRECTOR)) {
			mvc.perform(get("/inicio").with(UsuariosDePrueba.como(personal.get(rol)))).andExpect(status().isOk())
					.andExpect(content().string(containsString("data-guia-proximos")))
					.andExpect(content().string(not(containsString("data-tablero-kpis"))))
					.andExpect(content().string(not(containsString("data-tablero-graficos"))))
					.andExpect(content().string(not(containsString("S/ 0.00"))));
		}
		String html = mvc.perform(get("/inicio").with(UsuariosDePrueba.como(personal.get(Rol.PROMOTOR))))
				.andReturn().getResponse().getContentAsString();
		// Los datos y el equipo ya están (los trae el alta): el siguiente es el año escolar, con su botón.
		assertThat(html).contains("data-guia-siguiente").contains("data-paso=\"anio\"")
				.containsPattern("href=\"/colegio\"[^>]*data-guia-boton");
	}

	@Test
	void enProduccionNoHayBanner() throws Exception {
		jdbc.update("UPDATE colegio SET estado = 'PRODUCCION', produccion_desde = NOW(), produccion_por = 'promotor.guia' "
				+ "WHERE id = ?", colegio);
		for (Usuario u : personal.values()) {
			mvc.perform(get("/inicio").with(UsuariosDePrueba.como(u))).andExpect(status().isOk())
					.andExpect(content().string(not(containsString("data-guia"))));
		}
	}

	@Test
	void nuncaUnBotonQueTermineEn403() throws Exception {
		// Con el año y las secciones, más pasos quedan desbloqueados (más botones que revisar).
		jdbc.update("INSERT INTO anio_escolar (colegio_id, anio, estado, vigente, inicio_clases, fin_clases, creado_en, "
				+ "creado_por, actualizado_en) VALUES (?, 2026, 'EN_CURSO', TRUE, '2026-03-02', '2026-12-18', NOW(), 'prueba', "
				+ "NOW())", colegio);
		for (Map.Entry<Rol, Usuario> e : personal.entrySet()) {
			ListaVerificacion lista = ContextoColegio.en(colegio, () -> estado.actual());
			for (FilaPaso fila : GuiaPuestaEnMarcha.filas(lista, EnumSet.of(e.getKey()))) {
				String enlace = fila.vista().enlace();
				if (enlace != null) {
					int codigo = mvc.perform(get(enlace).with(UsuariosDePrueba.como(e.getValue()))).andReturn().getResponse()
							.getStatus();
					assertThat(codigo).as("%s abre %s (%s)", e.getKey(), enlace, fila.punto().clave()).isBetween(200, 399);
				}
			}
			String html = mvc.perform(get("/inicio").with(UsuariosDePrueba.como(e.getValue()))).andReturn().getResponse()
					.getContentAsString();
			Matcher botones = Pattern.compile("<a [^>]*href=\"([^\"]+)\"[^>]*data-guia-boton").matcher(html);
			while (botones.find()) {
				int codigo = mvc.perform(get(botones.group(1)).with(UsuariosDePrueba.como(e.getValue()))).andReturn()
						.getResponse().getStatus();
				assertThat(codigo).as("%s: botón %s", e.getKey(), botones.group(1)).isEqualTo(200);
			}
		}
	}

	@Test
	void laPuestaEnMarchaMuestraEstadoResponsableEIr() throws Exception {
		String html = mvc.perform(get("/puesta-en-marcha").with(UsuariosDePrueba.como(personal.get(Rol.PROMOTOR))))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(Pattern.compile("data-paso=\"").matcher(html).results().count()).isEqualTo(17);
		assertThat(Pattern.compile("data-siguiente").matcher(html).results().count()).isEqualTo(1);
		assertThat(html).contains("data-estado-paso").contains("Lo hace: Promotoría").contains("aria-label=\"Ir: ")
				.contains("Lo hace la plataforma").contains("Bloqueado: primero el paso 3")
				// F-D8: las dos firmas, en su paso y con confirmación.
				.contains("Firmar acepto").contains("Firmar: empezamos sin deudas anteriores").contains("popovertarget=\"firma-ose\"")
				// Pasar a producción: deshabilitado con su motivo.
				.contains("aria-disabled=\"true\" aria-describedby=\"motivo-produccion\"").contains("pasos obligatorios.");

		String direccion = mvc.perform(get("/puesta-en-marcha").with(UsuariosDePrueba.como(personal.get(Rol.DIRECTOR))))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(direccion).doesNotContain("Firmar acepto").contains("Lo firma la Promotoría")
				.doesNotContain("data-produccion").contains("Lo hace: Promotoría");
	}

	@Test
	void losEstadosVaciosGuian() throws Exception {
		// Sin año: el año escolar dice qué falta y quién lo hace; las pensiones, «Primero: …».
		mvc.perform(get("/colegio").with(UsuariosDePrueba.como(personal.get(Rol.DIRECTOR)))).andExpect(status().isOk())
				.andExpect(content().string(containsString("Tu colegio todavía no tiene año escolar")))
				.andExpect(content().string(containsString("data-estado-vacio-boton")));
		mvc.perform(get("/colegio").with(UsuariosDePrueba.como(personal.get(Rol.ADMINISTRACION)))).andExpect(status().isOk())
				.andExpect(content().string(containsString("Pídeselo a Promotoría o Dirección.")));
		mvc.perform(get("/pensiones").with(UsuariosDePrueba.como(personal.get(Rol.ADMINISTRACION)))).andExpect(status().isOk())
				.andExpect(content().string(containsString("Primero: Configurar el año escolar")));
		// La cuenta del banco y el saldo inicial.
		mvc.perform(get("/conectores").with(UsuariosDePrueba.como(personal.get(Rol.PROMOTOR)))).andExpect(status().isOk())
				.andExpect(content().string(containsString("Todavía no registraste la cuenta donde entra el dinero")));
		mvc.perform(get("/conectores").with(UsuariosDePrueba.como(personal.get(Rol.ADMINISTRACION)))).andExpect(status().isOk())
				.andExpect(content().string(containsString("Pídeselo a Promotoría.")));
	}

	@Test
	void enviarUnComunicadoEnPreparacionResponde409ConElEnlace() throws Exception {
		mvc.perform(post("/comunicados/nuevo").with(csrf()).with(UsuariosDePrueba.como(personal.get(Rol.DIRECTOR)))
				.param("accion", "enviar").param("titulo", "Reunión de padres").param("cuerpo", "Los esperamos el lunes."))
				.andExpect(status().isConflict())
				.andExpect(content().string(containsString("Tu colegio está en preparación: todavía no salen mensajes")))
				.andExpect(content().string(containsString("role=\"alert\"")))
				.andExpect(content().string(containsString("href=\"/puesta-en-marcha\"")));
	}

	@Test
	void unMensajeDePreparacionDeLaBaseSeTraduceIgual() {
		RuntimeException base = new RuntimeException("could not execute statement", new java.sql.SQLException(
				"cuentasclaras: el colegio todavia no esta en produccion: no se registran pagos", "45000", 1644));
		assertThat(ColegioEnPreparacionException.desdeBase(base)).get()
				.extracting(ColegioEnPreparacionException::getMessage)
				.isEqualTo("Tu colegio está en preparación: todavía no se registran pagos. Cuando la Promotoría lo pase a "
						+ "producción, podrás cobrar.");
		assertThat(ColegioEnPreparacionException.desdeBase(new RuntimeException(new java.sql.SQLException(
				"cuentasclaras: el colegio todavia no esta en produccion: sin pagos en linea")))).isPresent();
		assertThat(ColegioEnPreparacionException.desdeBase(new RuntimeException(new java.sql.SQLException(
				"cuentasclaras: el colegio todavia no esta en produccion: sin recordatorios de cobro ni mensajes a las familias"))))
				.isPresent();
		assertThat(ColegioEnPreparacionException.desdeBase(new RuntimeException("otra cosa"))).isEmpty();
	}

	@Test
	void losTextosDeLaBaseSonLosDeLosTriggers() throws Exception {
		// Si cambia el texto de un trigger de preparación en 03, la traducción se rompe: esta prueba lo detecta.
		String triggers = Files.readString(Path.of("scripts/mysql/03-triggers.sql"));
		assertThat(triggers).contains("cuentasclaras: el colegio todavia no esta en produccion: no se registran pagos")
				.contains("cuentasclaras: el colegio todavia no esta en produccion: sin pagos en linea")
				.contains("cuentasclaras: el colegio todavia no esta en produccion: sin recordatorios de cobro ni mensajes a las "
						+ "familias");
	}
}
