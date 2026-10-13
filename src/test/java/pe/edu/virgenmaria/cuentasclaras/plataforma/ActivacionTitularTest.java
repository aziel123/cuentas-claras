package pe.edu.virgenmaria.cuentasclaras.plataforma;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EnlacesDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.TokenDeSesionDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.proceso.DespachoMensajes;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.proveedor.BuzonSimulado;
import pe.edu.virgenmaria.cuentasclaras.plataforma.dto.ColegioCreado;
import pe.edu.virgenmaria.cuentasclaras.plataforma.service.AltaColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sprint 8, tanda 1 (sección 3.3, E4): el titular de la primera Promotoría activa su cuenta con el enlace que le llegó,
 * escribiendo el DNI del contrato (no su usuario), y ve quién creó su colegio y su cuenta.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class ActivacionTitularTest {

	private static final String CELULAR = "+51987650040";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private AltaColegio alta;

	@Autowired
	private DespachoMensajes despacho;

	@Autowired
	private BuzonSimulado buzon;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private ColegioRepository colegios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private RelojAjustable reloj;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TokenDeSesionDePrueba tokens;

	private ColegioCreado creado;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		tokens.olvidar();
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		Usuario operador = EscenarioPlataforma.operador(usuarios, codificador, colegios, "operador.activa");
		UsuariosDePrueba.iniciarSesion(operador);
		creado = alta.crear(EscenarioPlataforma.alta("Colegio Activa", EscenarioPlataforma.RUC_C, "titular.activa",
				"45678940", "987650040"), null, null);
		SecurityContextHolder.clearContext();
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		tokens.olvidar();
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void pideElDniDelContratoYMuestraQuienCreoLaCuenta() throws Exception {
		String ruta = EnlacesDePrueba.recibido(despacho, buzon, creado.colegioId(), CELULAR);

		mvc.perform(get(ruta)).andExpect(status().isOk())
				.andExpect(content().string(containsString("Tu DNI (el del contrato)")))
				.andExpect(content().string(containsString("los creó el operador operador.activa")));

		// Con su nombre de usuario (lo que confirma el resto del personal) no basta: es el DNI del contrato.
		mvc.perform(post(ruta).with(csrf()).param("documento", "titular.activa").param("clave", "una clave larga del titular")
				.param("confirmacion", "una clave larga del titular"))
				.andExpect(redirectedUrl(ruta))
				.andExpect(flash().attribute("error", containsString("El DNI no corresponde")));
		assertThat(debeCambiarClave()).isTrue();

		mvc.perform(post(ruta).with(csrf()).param("documento", "45678940").param("clave", "una clave larga del titular")
				.param("confirmacion", "una clave larga del titular"))
				.andExpect(redirectedUrl("/login?cuenta-activada"));
		assertThat(debeCambiarClave()).isFalse();
		assertThat(jdbc.queryForList("SELECT accion FROM evento_auditoria WHERE colegio_id = ?", String.class,
				creado.colegioId())).contains("ACCESO_PERSONAL_ACTIVADO");
	}

	private boolean debeCambiarClave() {
		return Boolean.TRUE.equals(jdbc.queryForObject("SELECT debe_cambiar_clave FROM usuario WHERE colegio_id = ?",
				Boolean.class, creado.colegioId()));
	}
}
