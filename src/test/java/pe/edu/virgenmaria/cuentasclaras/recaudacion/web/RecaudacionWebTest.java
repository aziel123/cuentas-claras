package pe.edu.virgenmaria.cuentasclaras.recaudacion.web;

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
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.Archivo;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.dto.VistaPreviaRecaudacion;

import java.time.Clock;
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
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.PROMOTORIA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.archivo;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.estadoLote;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.version;

/**
 * Sprint 4, tanda 2: el flujo web de la recaudación (subir → revisar → registrar → confirmar a ciegas) y quién entra a
 * cada paso. Ninguna pantalla muestra el total antes de la confirmación a ciegas.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class RecaudacionWebTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private Clock reloj;

	@Autowired
	private JdbcTemplate jdbc;

	private Familias f;

	@BeforeEach
	void preparar() {
		((RelojAjustable) reloj).fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
		f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		SecurityContextHolder.clearContext();
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	private Archivo banco() {
		return archivo().pago(f.mateo(), null, "350.00", "BCP90001").pago(f.valeria(), null, "1250.75", "BCP90002");
	}

	@Test
	void subirRevisarRegistrarYConfirmarACiegas() throws Exception {
		Archivo banco = banco();
		MockHttpSession sesion = new MockHttpSession();
		mvc.perform(get("/recaudacion").with(UsuariosDePrueba.como(ADMINISTRACION))).andExpect(status().isOk())
				.andExpect(content().string(containsString("enctype=\"multipart/form-data\"")));

		mvc.perform(multipart("/recaudacion/vista-previa").file(new MockMultipartFile("archivo", "banco.csv", "text/csv",
				banco.csv())).with(UsuariosDePrueba.como(ADMINISTRACION)).with(csrf()).session(sesion))
				.andExpect(redirectedUrl("/recaudacion/vista-previa"));
		VistaPreviaRecaudacion previa = (VistaPreviaRecaudacion) sesion.getAttribute(RecaudacionController.CLAVE_SESION);
		assertThat(previa).isNotNull();
		mvc.perform(get("/recaudacion/vista-previa").with(UsuariosDePrueba.como(ADMINISTRACION)).session(sesion))
				.andExpect(status().isOk()).andExpect(content().string(containsString("Revisa antes de registrar")))
				.andExpect(content().string(not(containsString("1,600.75"))));

		MvcResult registro = mvc.perform(post("/recaudacion/lotes").param("token", previa.token().toString())
				.with(UsuariosDePrueba.como(ADMINISTRACION)).with(csrf()).session(sesion)).andReturn();
		String destino = registro.getResponse().getRedirectedUrl();
		Matcher numero = Pattern.compile("/recaudacion/lotes/(\\d+)").matcher(destino);
		assertThat(numero.matches()).as(destino).isTrue();
		Long lote = Long.valueOf(numero.group(1));
		assertThat(sesion.getAttribute(RecaudacionController.CLAVE_SESION)).isNull();

		// Quien confirma no ve el total en ninguna pantalla mientras está por confirmar.
		for (String ruta : new String[] { "/recaudacion", "/recaudacion/lotes/" + lote,
				"/recaudacion/lotes/" + lote + "/confirmar" }) {
			mvc.perform(get(ruta).with(UsuariosDePrueba.como(PROMOTORIA))).andExpect(status().isOk())
					.andExpect(content().string(not(containsString("1,600.75"))))
					.andExpect(content().string(not(containsString("1600.75"))));
		}
		mvc.perform(get("/recaudacion/lotes/" + lote + "/archivo").with(UsuariosDePrueba.como(PROMOTORIA)))
				.andExpect(status().is4xxClientError());

		mvc.perform(post("/recaudacion/lotes/" + lote + "/confirmar").param("version", version(jdbc, lote).toString())
				.param("totalVisto", "1,600.75").with(UsuariosDePrueba.como(PROMOTORIA)).with(csrf()))
				.andExpect(redirectedUrl("/recaudacion/lotes/" + lote + "/confirmar"))
				.andExpect(flash().attribute("error", containsString("punto decimal")));
		mvc.perform(post("/recaudacion/lotes/" + lote + "/confirmar").param("version", version(jdbc, lote).toString())
				.param("totalVisto", "1600.00").with(UsuariosDePrueba.como(PROMOTORIA)).with(csrf()))
				.andExpect(redirectedUrl("/recaudacion/lotes/" + lote + "/confirmar"))
				.andExpect(flash().attribute("error", containsString("No coincide")));
		mvc.perform(post("/recaudacion/lotes/" + lote + "/confirmar").param("version", version(jdbc, lote).toString())
				.param("totalVisto", "1600.75").with(UsuariosDePrueba.como(PROMOTORIA)).with(csrf()))
				.andExpect(redirectedUrl("/recaudacion/lotes/" + lote))
				.andExpect(flash().attribute("exito", containsString("Coincide")));

		assertThat(estadoLote(jdbc, lote)).isEqualTo("APLICADO");
		assertThat(contar(jdbc, "pago WHERE origen = 'RECAUDACION'")).isEqualTo(2);
		mvc.perform(get("/recaudacion/lotes/" + lote).with(UsuariosDePrueba.como(PROMOTORIA))).andExpect(status().isOk())
				.andExpect(content().string(containsString("1,600.75")))
				.andExpect(content().string(containsString("B001-")));
	}

	/** Segregación por rol en las rutas: Caja no entra; Administración no confirma; Promotoría no sube. */
	@Test
	void cadaRolSoloHaceSuParte() throws Exception {
		mvc.perform(get("/recaudacion").with(UsuariosDePrueba.como(CAJA))).andExpect(status().isForbidden());
		mvc.perform(multipart("/recaudacion/vista-previa").file(new MockMultipartFile("archivo", "banco.csv", "text/csv",
				banco().csv())).with(UsuariosDePrueba.como(PROMOTORIA)).with(csrf())).andExpect(status().isForbidden());
		mvc.perform(get("/recaudacion/base-deudas").with(UsuariosDePrueba.como(PROMOTORIA)))
				.andExpect(status().isForbidden());
		mvc.perform(get("/recaudacion/lotes/1/confirmar").with(UsuariosDePrueba.como(ADMINISTRACION)))
				.andExpect(status().isForbidden());
		mvc.perform(post("/recaudacion/lotes/1/confirmar").param("version", "0").param("totalVisto", "1.00")
				.with(UsuariosDePrueba.como(ADMINISTRACION)).with(csrf())).andExpect(status().isForbidden());
		mvc.perform(post("/recaudacion/lineas/1/aplicar").param("familiaId", "1").param("cuotaIds", "1")
				.param("motivo", "Pedido desde Promotoría para aplicarlo").with(UsuariosDePrueba.como(PROMOTORIA))
				.with(csrf())).andExpect(status().isForbidden());
		mvc.perform(get("/recaudacion/base-deudas").with(UsuariosDePrueba.como(ADMINISTRACION))).andExpect(status().isOk())
				.andExpect(content().string(containsString("codigo_alumno;referencia_deuda")));
	}

	@Test
	void archivoConErroresMuestraLasLineasYNoSeRegistra() throws Exception {
		MockHttpSession sesion = new MockHttpSession();
		byte[] malo = ("fecha_pago;codigo_alumno;monto;moneda;numero_operacion\n2026-10-01;00000018;450,00;PEN;BCP1\n")
				.getBytes();
		mvc.perform(multipart("/recaudacion/vista-previa").file(new MockMultipartFile("archivo", "banco.csv", "text/csv",
				malo)).with(UsuariosDePrueba.como(ADMINISTRACION)).with(csrf()).session(sesion))
				.andExpect(redirectedUrl("/recaudacion/vista-previa"));

		mvc.perform(get("/recaudacion/vista-previa").with(UsuariosDePrueba.como(ADMINISTRACION)).session(sesion))
				.andExpect(status().isOk()).andExpect(content().string(containsString("Errores del archivo")))
				.andExpect(content().string(not(containsString("Registrar archivo"))));
		mvc.perform(multipart("/recaudacion/vista-previa").file(new MockMultipartFile("archivo", "banco.pdf",
				"application/pdf", "%PDF".getBytes())).with(UsuariosDePrueba.como(ADMINISTRACION)).with(csrf()))
				.andExpect(redirectedUrl("/recaudacion")).andExpect(flash().attribute("error", containsString("CSV")));
		assertThat(contar(jdbc, "lote_recaudacion")).isZero();
	}
}
