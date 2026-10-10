package pe.edu.virgenmaria.cuentasclaras.operacion.log;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAccesoApoderados;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EnlacesDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.proceso.DespachoMensajes;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.proveedor.BuzonSimulado;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Sprint 7, sección 10.2 (E36): un escenario completo (cobro con su aviso por WhatsApp simulado, acceso de un apoderado
 * con su enlace de activación, la página de error de una excepción con un DNI y un error de MySQL simulado) deja logs
 * que, con el formato de prod y piloto (JSON ECS enmascarado), no llevan ningún DNI, celular, correo ni token.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class LogsSinDatosPersonalesTest {

	private static final Pattern DNI_SUELTO = Pattern.compile("(?<![0-9])[0-9]{8}(?![0-9])");

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private ServicioAccesoApoderados acceso;

	@Autowired
	private DespachoMensajes despacho;

	@Autowired
	private BuzonSimulado buzon;

	@Autowired
	private RelojAjustable reloj;

	@Autowired
	private MockMvc mvc;

	private final ListAppender<ILoggingEvent> capturados = new ListAppender<>();

	private EscenarioCaja.Familias f;

	@BeforeEach
	void preparar() {
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
		buzon.vaciar();
		f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		capturados.start();
		raiz().addAppender(capturados);
	}

	@AfterEach
	void limpiar() {
		raiz().detachAppender(capturados);
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void unEscenarioCompletoNoDejaDatosPersonalesEnLosLogs() throws Exception {
		UsuariosDePrueba.iniciarSesion(EscenarioCaja.CAJA);
		cobro.cobrar(EscenarioCaja.efectivo(f.quispe(), List.of(EscenarioCaja.cuota(jdbc, f.mateo(), "PEN-2027-03")),
				"450.00", "500.00"));
		UsuariosDePrueba.iniciarSesion(EscenarioCobranza.ADMINISTRACION);
		acceso.darAcceso(f.rosa());
		SecurityContextHolder.clearContext();
		String ruta = EnlacesDePrueba.recibido(despacho, buzon, 1L, "+51" + EscenarioEscolar.CELULAR_ROSA);
		String token = ruta.substring(ruta.lastIndexOf('/') + 1);
		mvc.perform(get(ruta));
		String duplicado = "Duplicate entry '1-" + EscenarioEscolar.DNI_ROSA + "' for key 'uk_apoderado_documento'";
		mvc.perform(get("/error").accept(org.springframework.http.MediaType.TEXT_HTML)
				.requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
				.requestAttr(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException(duplicado)));
		LoggerFactory.getLogger("pe.edu.virgenmaria.cuentasclaras.prueba").error("Error de MySQL al registrar a "
				+ EscenarioEscolar.CORREO_ROSA, new DataIntegrityViolationException(duplicado));

		String crudo = capturados.list.stream().map(e -> e.getFormattedMessage()
				+ (e.getThrowableProxy() == null ? "" : e.getThrowableProxy().getMessage())).collect(Collectors.joining("\n"));
		String comoEnProd = capturados.list.stream().map(LogsSinDatosPersonalesTest::comoEnProd)
				.collect(Collectors.joining());

		// Control: sin el enmascarado, el error de MySQL sí llevaba el DNI y el correo.
		assertThat(crudo).contains(EscenarioEscolar.DNI_ROSA).contains(EscenarioEscolar.CORREO_ROSA);
		assertThat(capturados.list).isNotEmpty();
		assertThat(comoEnProd).doesNotContain(EscenarioEscolar.DNI_ROSA, EscenarioEscolar.DNI_MATEO,
				EscenarioEscolar.DNI_VALERIA, EscenarioCaja.DNI_SEBASTIAN, EscenarioCaja.DNI_PEDRO,
				EscenarioEscolar.CELULAR_ROSA, EscenarioEscolar.CORREO_ROSA, token)
				.contains("Duplicate entry '********' for key 'uk_apoderado_documento'");
		assertThat(DNI_SUELTO.matcher(comoEnProd).find()).as("ningún número de 8 dígitos").isFalse();
	}

	private static Logger raiz() {
		return ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
	}

	/** La línea que escribiría prod: JSON ECS con el enmascarado de {@code application-prod.yaml}. */
	private static String comoEnProd(ILoggingEvent evento) {
		LoggerContext contexto = new LoggerContext();
		Environment entorno = new MockEnvironment().withProperty("logging.structured.json.customizer",
				EnmascaradoLogs.class.getName());
		contexto.putObject(Environment.class.getName(), entorno);
		StructuredLogEncoder codificador = new StructuredLogEncoder();
		codificador.setContext(contexto);
		codificador.setFormat("ecs");
		codificador.start();
		return new String(codificador.encode(evento), StandardCharsets.UTF_8);
	}
}
