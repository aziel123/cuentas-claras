package pe.edu.virgenmaria.cuentasclaras.conciliacion.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.Extracto;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.dto.VistaPreviaExtracto;

import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.CAJA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.digital;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.DIRECCION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.PROMOTORIA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.estadoExtracto;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.extracto;

/**
 * Sprint 4, tanda 3: el flujo web de la conciliación (Promotoría registra la cuenta → Administración sube y revisa el
 * extracto → Promotoría escribe a ciegas el saldo → la pantalla muestra SOLO las diferencias, con el Yape inventado en
 * rojo) y quién entra a cada paso. Ninguna pantalla muestra el saldo final antes de la confirmación a ciegas.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class ConciliacionWebTest {

	static final Instant LUNES = Instant.parse("2026-10-05T14:00:00Z");

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private RelojAjustable reloj;

	@Autowired
	private JdbcTemplate jdbc;

	private Familias f;

	@BeforeEach
	void preparar() {
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
		f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		SecurityContextHolder.clearContext();
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void subirConfirmarACiegasYVerSoloLasDiferencias() throws Exception {
		como(CAJA);
		cobro.cobrar(digital(f.quispe(), List.of(cuota(jdbc, f.valeria(), "PEN-2027-03")), MedioPago.YAPE, "YP111222",
				"450.00"));
		cobro.cobrar(digital(f.quispe(), List.of(cuota(jdbc, f.mateo(), "PEN-2027-03")), MedioPago.YAPE, "YP999888",
				"450.00"));
		SecurityContextHolder.clearContext();

		// Promotoría registra la cuenta del colegio.
		mvc.perform(post("/conciliacion/cuentas").param("banco", "BCP").param("numero", "191-2345678-0-12")
				.param("alias", "BCP soles cobranza").with(UsuariosDePrueba.como(PROMOTORIA)).with(csrf()))
				.andExpect(redirectedUrl("/conciliacion/cuentas")).andExpect(flash().attribute("exito",
						containsString("registraste la cuenta")));
		Long cuenta = jdbc.queryForObject("SELECT id FROM cuenta_bancaria", Long.class);

		reloj.fijar(LUNES);
		Extracto banco = extracto("12345.67").abono("2026-10-02", "YAPE DE ROSA QUISPE", "YP111222", "450.00")
				.abono("2026-10-02", "INTERESES GANADOS", "", "1.23");
		String saldo = banco.saldoFinal().toPlainString();
		assertThat(saldo).isEqualTo("12796.90");

		MockHttpSession sesion = new MockHttpSession();
		mvc.perform(get("/conciliacion/extractos").with(UsuariosDePrueba.como(ADMINISTRACION))).andExpect(status().isOk())
				.andExpect(content().string(containsString("enctype=\"multipart/form-data\"")));
		mvc.perform(multipart("/conciliacion/extractos/vista-previa").file(new MockMultipartFile("archivo",
				"extracto.csv", "text/csv", banco.csv())).with(UsuariosDePrueba.como(ADMINISTRACION)).with(csrf())
				.session(sesion)).andExpect(redirectedUrl("/conciliacion/extractos/vista-previa"));
		VistaPreviaExtracto previa = (VistaPreviaExtracto) sesion.getAttribute(ConciliacionController.CLAVE_SESION);
		assertThat(previa).isNotNull();
		mvc.perform(get("/conciliacion/extractos/vista-previa").with(UsuariosDePrueba.como(ADMINISTRACION))
				.session(sesion)).andExpect(status().isOk())
				.andExpect(content().string(containsString("Primer extracto de la cuenta")))
				.andExpect(content().string(not(containsString("12,796.90"))))
				.andExpect(content().string(not(containsString("12796.90"))));

		MvcResult registro = mvc.perform(post("/conciliacion/extractos").param("token", previa.token().toString())
				.with(UsuariosDePrueba.como(ADMINISTRACION)).with(csrf()).session(sesion)).andReturn();
		Matcher numero = Pattern.compile("/conciliacion/extractos/(\\d+)")
				.matcher(String.valueOf(registro.getResponse().getRedirectedUrl()));
		assertThat(numero.matches()).as(registro.getResponse().getRedirectedUrl()).isTrue();
		Long id = Long.valueOf(numero.group(1));
		assertThat(sesion.getAttribute(ConciliacionController.CLAVE_SESION)).isNull();

		// Quien confirma no ve el saldo en ninguna pantalla mientras está por confirmar.
		for (String ruta : new String[] { "/conciliacion", "/conciliacion/extractos", "/conciliacion/extractos/" + id,
				"/conciliacion/cuentas/" + cuenta + "/confirmar" }) {
			mvc.perform(get(ruta).with(UsuariosDePrueba.como(PROMOTORIA))).andExpect(status().isOk())
					.andExpect(content().string(not(containsString("12,796.90"))))
					.andExpect(content().string(not(containsString("12796.90"))));
		}
		// Administración no confirma (quien sube no escribe el saldo).
		mvc.perform(get("/conciliacion/cuentas/" + cuenta + "/confirmar").with(UsuariosDePrueba.como(ADMINISTRACION)))
				.andExpect(status().isForbidden());

		String version = String.valueOf(jdbc.queryForObject("SELECT version FROM extracto_bancario WHERE id = ?",
				Long.class, id));
		mvc.perform(post("/conciliacion/cuentas/" + cuenta + "/confirmar").param("extracto", id.toString())
				.param("version", version).param("saldoVisto", "12,796.90").with(UsuariosDePrueba.como(PROMOTORIA))
				.with(csrf())).andExpect(redirectedUrl("/conciliacion/cuentas/" + cuenta + "/confirmar"))
				.andExpect(flash().attribute("error", containsString("punto decimal")));
		mvc.perform(post("/conciliacion/cuentas/" + cuenta + "/confirmar").param("extracto", id.toString())
				.param("version", version).param("saldoVisto", saldo).with(UsuariosDePrueba.como(PROMOTORIA))
				.with(csrf())).andExpect(redirectedUrl("/conciliacion"))
				.andExpect(flash().attribute("exito", containsString("Coincide")));
		assertThat(estadoExtracto(jdbc, id)).isEqualTo("CONFIRMADO");

		// Solo las diferencias: el Yape inventado en rojo (con quién lo cobró) y los intereses sin pareja; el Yape real no.
		mvc.perform(get("/conciliacion").with(UsuariosDePrueba.como(PROMOTORIA))).andExpect(status().isOk())
				.andExpect(content().string(containsString("Debía estar en el banco y no está (1)")))
				.andExpect(content().string(containsString("YP999888")))
				.andExpect(content().string(containsString("cobró caja")))
				.andExpect(content().string(containsString("INTERESES GANADOS")))
				.andExpect(content().string(not(containsString("YP111222"))));
		// En el inicio de Promotoría, la alerta crítica.
		mvc.perform(get("/inicio").with(UsuariosDePrueba.como(PROMOTORIA))).andExpect(status().isOk())
				.andExpect(content().string(containsString("(operación YP999888) NO aparece en el banco")));
		// Confirmado, el detalle del extracto ya muestra los saldos.
		mvc.perform(get("/conciliacion/extractos/" + id).with(UsuariosDePrueba.como(DIRECCION)))
				.andExpect(status().isOk()).andExpect(content().string(containsString("12,796.90")));

		// Administración explica los intereses (con nota) desde la misma pantalla.
		Long intereses = jdbc.queryForObject("SELECT id FROM movimiento_bancario WHERE descripcion = 'INTERESES GANADOS'",
				Long.class);
		mvc.perform(post("/conciliacion/movimientos/" + intereses + "/explicar").param("categoria", "INTERESES")
				.param("nota", "Intereses mensuales de la cuenta de cobranza").with(UsuariosDePrueba.como(ADMINISTRACION))
				.with(csrf())).andExpect(redirectedUrl("/conciliacion"))
				.andExpect(flash().attribute("exito", containsString("explicaste")));
		mvc.perform(get("/conciliacion").with(UsuariosDePrueba.como(ADMINISTRACION))).andExpect(status().isOk())
				.andExpect(content().string(not(containsString("INTERESES GANADOS"))))
				.andExpect(content().string(containsString("YP999888")));
	}

	/** Segregación por rol en las rutas: Caja, Docente y Apoderado no entran; Promotoría no sube; Dirección solo mira. */
	@Test
	void cadaRolSoloHaceSuParte() throws Exception {
		mvc.perform(get("/conciliacion").with(UsuariosDePrueba.como(CAJA))).andExpect(status().isForbidden());
		mvc.perform(get("/conciliacion").with(UsuariosDePrueba.como(DIRECCION))).andExpect(status().isOk());
		mvc.perform(multipart("/conciliacion/extractos/vista-previa").file(new MockMultipartFile("archivo",
				"extracto.csv", "text/csv", extracto("1.00").abono("2026-10-01", "X", "", "1.00").csv()))
				.with(UsuariosDePrueba.como(PROMOTORIA)).with(csrf())).andExpect(status().isForbidden());
		mvc.perform(post("/conciliacion/cuentas").param("banco", "BCP").param("numero", "191-2345678-0-12")
				.param("alias", "BCP").with(UsuariosDePrueba.como(ADMINISTRACION)).with(csrf()))
				.andExpect(status().isForbidden());
		mvc.perform(post("/conciliacion/partidas/1/confirmar").with(UsuariosDePrueba.como(PROMOTORIA)).with(csrf()))
				.andExpect(status().isForbidden());
		mvc.perform(post("/conciliacion/movimientos/1/emparejar").param("objeto", "PAGO:1")
				.param("nota", "Es el Yape de la familia").with(UsuariosDePrueba.como(DIRECCION)).with(csrf()))
				.andExpect(status().isForbidden());
		// Un archivo que no es CSV ni XLSX no se lee.
		mvc.perform(multipart("/conciliacion/extractos/vista-previa").file(new MockMultipartFile("archivo",
				"extracto.pdf", "application/pdf", "%PDF".getBytes())).with(UsuariosDePrueba.como(ADMINISTRACION))
				.with(csrf())).andExpect(redirectedUrl("/conciliacion/extractos"))
				.andExpect(flash().attribute("error", containsString("CSV")));
	}
}
