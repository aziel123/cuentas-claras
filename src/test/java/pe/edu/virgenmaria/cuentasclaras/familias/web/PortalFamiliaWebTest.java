package pe.edu.virgenmaria.cuentasclaras.familias.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAccesoApoderados;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioAnulacionPagos;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.TipoDescuento;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioAnulacionCuotas;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioDescuentos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRenovacion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.matricula.service.ServicioCampanaRenovacion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.efectivo;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.DIRECCION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;

/**
 * Sprint 5, tanda 2: el portal de familias para el celular (pantallas 1 a 6). Muestra TODO (también lo anulado y lo
 * descontado, con el rol de quien lo aprobó), la familia sale de la sesión (otra familia: 404) y las pantallas cumplen
 * la CSP (sin estilos ni scripts en línea) y caben en un celular.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class PortalFamiliaWebTest {

	private static final List<String> PANTALLAS = List.of("/familia", "/familia/estado-de-cuenta",
			"/familia/comprobantes", "/familia/mensajes", "/familia/algo-no-cuadra");

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private ServicioAnulacionPagos anulaciones;

	@Autowired
	private ServicioAnulacionCuotas anulacionCuotas;

	@Autowired
	private ServicioDescuentos descuentos;

	@Autowired
	private BandejaAprobaciones bandeja;

	@Autowired
	private ServicioAccesoApoderados accesos;

	@Autowired
	private ServicioCampanaRenovacion campana;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	private static UsuarioAutenticado apoderado(long id, String usuario, Long apoderadoId) {
		return new UsuarioAutenticado(id, 1L, usuario, usuario, null, true, false, false, EnumSet.of(Rol.APODERADO),
				apoderadoId);
	}

	private EscenarioCaja.Familias familias() {
		EscenarioCaja.Familias f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		UsuariosDePrueba.guardar(usuarios, codificador, 1L, "director", UsuariosDePrueba.CLAVE, false, Rol.DIRECTOR);
		SecurityContextHolder.clearContext();
		return f;
	}

	@Test
	void estadoDeCuentaMuestraAnuladasYDescuentosConQuienAprobo() throws Exception {
		EscenarioCaja.Familias f = familias();
		// Un pago anulado, una cuota anulada y un descuento: todo aprobado por Dirección.
		como(EscenarioCaja.CAJA);
		Long pago = cobro.cobrar(efectivo(f.quispe(), List.of(cuota(jdbc, f.mateo(), "PEN-2027-03")), "450.00",
				"450.00"));
		anulaciones.solicitarDevolucion(pago, EscenarioAprobaciones.MOTIVO_ANULACION);
		EscenarioAprobaciones.aprueba(DIRECCION, bandeja, jdbc, "pago", pago);
		Long abril = cuota(jdbc, f.mateo(), "PEN-2027-04");
		como(ADMINISTRACION);
		anulacionCuotas.solicitar(abril, "Se generó dos veces por un error de configuración");
		EscenarioAprobaciones.aprueba(DIRECCION, bandeja, jdbc, "cuota", abril);
		como(ADMINISTRACION);
		Long descuento = descuentos.solicitar(EscenarioAprobaciones.descuento(f.valeria(), TipoDescuento.HERMANOS, "10",
				List.of(cuota(jdbc, f.valeria(), "PEN-2027-05"))));
		EscenarioAprobaciones.aprueba(DIRECCION, bandeja, jdbc, "descuento", descuento);
		SecurityContextHolder.clearContext();

		UsuarioAutenticado rosa = apoderado(40L, "rosa.familia", f.rosa());
		mvc.perform(get("/familia/estado-de-cuenta").with(UsuariosDePrueba.como(rosa))).andExpect(status().isOk())
				.andExpect(content().string(containsString("data-pantalla=\"familia-estado-de-cuenta\"")))
				.andExpect(content().string(containsString("Anulado. Motivo: " + EscenarioAprobaciones.MOTIVO_ANULACION
						+ ". Aprobó: Dirección")))
				.andExpect(content().string(containsString("Anulada. Motivo: Se generó dos veces por un error de "
						+ "configuración. Aprobó: Dirección")))
				.andExpect(content().string(containsString("aprobó: Dirección")))
				.andExpect(content().string(containsString("Mateo Quispe")))
				.andExpect(content().string(containsString("Valeria Quispe")))
				// Nunca el usuario de quien aprobó.
				.andExpect(content().string(not(containsString("director"))));
		mvc.perform(get("/familia/comprobantes").with(UsuariosDePrueba.como(rosa))).andExpect(status().isOk())
				.andExpect(content().string(containsString("Ver o guardar B001-")))
				.andExpect(content().string(containsString("Anulado")));
	}

	@Test
	void otraFamiliaRecibe404() throws Exception {
		EscenarioRenovacion.Datos d = EscenarioRenovacion.preparar(estructura, alumnos, planes, jdbc, true);
		como(ADMINISTRACION);
		campana.abrir(d.anio2027(), EscenarioRenovacion.RESPONDEN_HASTA);
		SecurityContextHolder.clearContext();
		Long renovacion = EscenarioRenovacion.renovacionDe(jdbc, d.mateo());

		mvc.perform(get("/familia/matricula/" + renovacion).with(UsuariosDePrueba.como(d.rosaEnLinea())))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Mateo continúa en 2027 en 6.° Primaria, sección B.")))
				.andExpect(content().string(containsString("Matrícula S/ 300.00, vence el 28/02/2027.")));
		mvc.perform(get("/familia/matricula/" + renovacion).with(UsuariosDePrueba.como(d.pedroEnLinea())))
				.andExpect(status().isNotFound());
		mvc.perform(post("/familia/matricula/" + renovacion).with(csrf()).param("continua", "true")
				.with(UsuariosDePrueba.como(d.pedroEnLinea()))).andExpect(status().isNotFound());
		assertThat(jdbc.queryForObject("SELECT estado FROM renovacion_matricula WHERE id = ?", String.class, renovacion))
				.isEqualTo("PROPUESTA");

		// Rosa confirma desde el celular: listo para pagar la matrícula.
		mvc.perform(post("/familia/matricula/" + renovacion).with(csrf()).param("continua", "true")
				.with(UsuariosDePrueba.como(d.rosaEnLinea()))).andExpect(redirectedUrl("/familia/matricula/" + renovacion));
		mvc.perform(get("/familia").with(UsuariosDePrueba.como(d.rosaEnLinea()))).andExpect(status().isOk())
				.andExpect(content().string(containsString("Matrícula 2027")));
		assertThat(jdbc.queryForObject("SELECT estado FROM renovacion_matricula WHERE id = ?", String.class, renovacion))
				.isEqualTo("MATRICULADA");
	}

	@Test
	void mensajesDeActivacionSinContenido() throws Exception {
		EscenarioCaja.Familias f = familias();
		como(ADMINISTRACION);
		accesos.darAcceso(f.rosa());
		SecurityContextHolder.clearContext();
		mvc.perform(get("/familia/mensajes").with(UsuariosDePrueba.como(apoderado(40L, "rosa.familia", f.rosa()))))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Enlace de acceso enviado")))
				.andExpect(content().string(not(containsString("/activar/"))))
				.andExpect(content().string(not(containsString("use este enlace"))));
	}

	@Test
	void sinEstilosNiScriptsEnLinea() throws Exception {
		EscenarioCaja.Familias f = familias();
		UsuarioAutenticado rosa = apoderado(40L, "rosa.familia", f.rosa());
		Pattern prohibido = Pattern.compile("style=|<style|<script(?![^>]*\\bsrc=)|\\son[a-z]+=", Pattern.CASE_INSENSITIVE);
		for (String pantalla : PANTALLAS) {
			String html = mvc.perform(get(pantalla).with(UsuariosDePrueba.como(rosa))).andExpect(status().isOk())
					.andReturn().getResponse().getContentAsString();
			assertThat(prohibido.matcher(html).find()).as(pantalla).isFalse();
		}
		String bandejaHtml = mvc.perform(get("/avisos-familias").with(UsuariosDePrueba.como(EscenarioCobranza.PROMOTORIA)))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(prohibido.matcher(bandejaHtml).find()).as("/avisos-familias").isFalse();
		String campanaHtml = mvc.perform(get("/matricula-2027").with(UsuariosDePrueba.como(ADMINISTRACION)))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(prohibido.matcher(campanaHtml).find()).as("/matricula-2027").isFalse();
	}

	/**
	 * Sin navegador en las pruebas, el «snapshot de 360 px» se comprueba por construcción: las pantallas del portal no
	 * usan tablas (en celular serían scroll horizontal), los textos largos se parten, la página declara el viewport del
	 * celular y la hoja de estilos no fija anchos mayores a 360 px en los bloques del portal.
	 */
	@Test
	void cabeEnCelularSinScrollHorizontal() throws Exception {
		EscenarioCaja.Familias f = familias();
		UsuarioAutenticado rosa = apoderado(40L, "rosa.familia", f.rosa());
		for (String pantalla : PANTALLAS) {
			String html = mvc.perform(get(pantalla).with(UsuariosDePrueba.como(rosa))).andExpect(status().isOk())
					.andReturn().getResponse().getContentAsString();
			assertThat(html).as(pantalla).contains("name=\"viewport\" content=\"width=device-width, initial-scale=1\"")
					.doesNotContain("<table");
		}
		String css = Files.readString(Path.of("src/main/resources/static/css/app.css"));
		assertThat(css).contains(".texto-largo { overflow-wrap: anywhere; }").contains(".cuotas-familia {");
		String portal = css.substring(css.indexOf("/* Sprint 5 · tanda 2: portal de familias"));
		assertThat(Pattern.compile("(min-)?width:\\s*(3[6-9]\\d|[4-9]\\d\\d|\\d{4,})px").matcher(portal).find())
				.as("ancho fijo mayor a 360 px en el portal").isFalse();
	}

	@Test
	void elPersonalNoEntraAlPortalNiLaFamiliaALaBandeja() throws Exception {
		for (UsuarioAutenticado quien : List.of(EscenarioCaja.CAJA, ADMINISTRACION, EscenarioCobranza.PROMOTORIA)) {
			mvc.perform(get("/familia/estado-de-cuenta").with(UsuariosDePrueba.como(quien)))
					.andExpect(status().isForbidden());
		}
		mvc.perform(get("/avisos-familias").with(UsuariosDePrueba.como(Rol.APODERADO))).andExpect(status().isForbidden());
	}

	static String leer(Path ruta) throws IOException {
		return Files.readString(ruta);
	}
}
