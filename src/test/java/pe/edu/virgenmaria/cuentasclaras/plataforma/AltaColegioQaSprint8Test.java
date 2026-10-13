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
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
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
import pe.edu.virgenmaria.cuentasclaras.plataforma.dto.ContactoTitularRequest;
import pe.edu.virgenmaria.cuentasclaras.plataforma.dto.FichaColegioRequest;
import pe.edu.virgenmaria.cuentasclaras.plataforma.service.AltaColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioUsuarios;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.identidad.AltaTitular.DatosTitular;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/**
 * QA del sprint 8 (secciones 3.3 y 3.5; E3, E4, E17 y E29): el alta de un colegio por el operador con datos inválidos,
 * logo hostil, «Completar alta» repetido, la primera Promotoría una sola vez y la activación del titular con su DNI.
 *
 * <p>Escenarios (Dado / Cuando / Entonces):
 * <ul>
 *   <li>Dado un operador, cuando el alta trae un logo hostil (SVG con script), entonces el colegio no queda sin su año
 *       escolar (QA-S8-1).</li>
 *   <li>Dado un colegio con su titular, cuando el operador pulsa «Completar alta» otra vez, entonces se rechaza sin firmar
 *       de nuevo ni crear otra cuenta.</li>
 *   <li>Dado un colegio que el DBA creó sin ficha, cuando el operador completa el alta, entonces se exige antes el DNI del
 *       titular (si no, la activación no pide DNI) (QA-S8-2).</li>
 *   <li>Dado un titular sin activar, cuando el operador cambia su contacto o su DNI, entonces se avisa al contacto anterior
 *       y al correo institucional (S8-A1 de la auditoría).</li>
 *   <li>Dado un titular que ya activó su cuenta y luego otra Promotoría le restablece el acceso, cuando el operador quiere
 *       «reenviar el enlace» a otro celular, entonces se rechaza: ya no es una cuenta sin activar (QA-S8-3).</li>
 * </ul>
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class AltaColegioQaSprint8Test {

	private static final String SVG_CON_SCRIPT = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>";

	@Autowired
	private AltaColegio alta;

	@Autowired
	private ServicioUsuarios servicioUsuarios;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private ColegioRepository colegios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private DespachoMensajes despacho;

	@Autowired
	private BuzonSimulado buzon;

	@Autowired
	private RelojAjustable reloj;

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TokenDeSesionDePrueba tokens;

	private Usuario operador;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		tokens.olvidar();
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		operador = EscenarioPlataforma.operador(usuarios, codificador, colegios, "operador.qa8");
		UsuariosDePrueba.iniciarSesion(operador);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		tokens.olvidar();
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	private ColegioCreado altaCompleta(String nombre, String ruc, String usuario, String dni, String celular) {
		ColegioCreado creado = alta.crear(EscenarioPlataforma.alta(nombre, ruc, usuario, dni, celular), null, null);
		assertThat(creado.completo()).as(creado.pendiente()).isTrue();
		return creado;
	}

	private long contar(String sql, Object... parametros) {
		return jdbc.queryForObject(sql, Long.class, parametros);
	}

	// ------------------------------------------------------------------ datos inválidos y logo hostil

	@Test
	void debeRechazarUnRucConLetrasSinCrearNingunColegio() {
		long antes = contar("SELECT COUNT(*) FROM colegio");

		assertThatThrownBy(() -> alta.crear(EscenarioPlataforma.alta("Colegio Letras", "2060000001A", "titular.letras",
				"45670001", "987650101"), null, null)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("11 dígitos");
		assertThat(contar("SELECT COUNT(*) FROM colegio")).isEqualTo(antes);
		assertThat(contar("SELECT COUNT(*) FROM firma_operacion WHERE clave LIKE 'primera_promotoria:%'")).isZero();
	}

	@Test
	void debeRechazarUnRucConPrefijoQueNoEsDeColegioAunqueSeaValidoEnSunat() {
		// 15, 16 y 17 son prefijos válidos de SUNAT, pero el colegio es persona jurídica (20) o natural con negocio (10).
		assertThatThrownBy(() -> alta.crear(EscenarioPlataforma.alta("Colegio Prefijo", "15000000001", "titular.prefijo",
				"45670002", "987650102"), null, null)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("empezar con 10 o 20");
	}

	@Test
	void debeRechazarQueElUsuarioDelTitularYaExistaEnLaPlataformaSinCrearElColegio() {
		altaCompleta("Colegio Primero", EscenarioPlataforma.RUC_A, "titular.repetido8", "45670003", "987650103");
		long antes = contar("SELECT COUNT(*) FROM colegio");

		assertThatThrownBy(() -> alta.crear(EscenarioPlataforma.alta("Colegio Segundo", EscenarioPlataforma.RUC_B,
				"titular.repetido8", "45670004", "987650104"), null, null)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("Ya existe un usuario");
		assertThat(contar("SELECT COUNT(*) FROM colegio")).isEqualTo(antes);
	}

	/**
	 * QA-S8-1 (corregido): el logo se valida ANTES de crear nada. Un logo hostil ya no deja un colegio a medias sin su año
	 * escolar: el alta se rechaza entera, sin colegio, sin firma y sin cuenta, y el operador la repite con otro archivo.
	 */
	@Test
	void debeRechazarElLogoHostilAntesDeCrearNadaParaNoDejarElColegioSinSuAnioEscolar() {
		long colegiosAntes = contar("SELECT COUNT(*) FROM colegio");
		long aniosAntes = contar("SELECT COUNT(*) FROM anio_escolar");

		assertThatThrownBy(() -> alta.crear(EscenarioPlataforma.alta("Colegio Logo Hostil", EscenarioPlataforma.RUC_C,
				"titular.logohostil", "45670005", "987650105"), SVG_CON_SCRIPT.getBytes(StandardCharsets.UTF_8), "logo.png"))
				.isInstanceOf(ReglaNegocioException.class);

		assertThat(contar("SELECT COUNT(*) FROM colegio")).isEqualTo(colegiosAntes);
		assertThat(contar("SELECT COUNT(*) FROM anio_escolar")).isEqualTo(aniosAntes);
		assertThat(contar("SELECT COUNT(*) FROM firma_operacion WHERE clave LIKE 'primera_promotoria:%'")).isZero();
		assertThat(contar("SELECT COUNT(*) FROM usuario WHERE nombre_usuario = 'titular.logohostil'")).isZero();
	}

	// ------------------------------------------------------------------ «Completar alta» y primera Promotoría

	@Test
	void debeRechazarCompletarAltaRepetidoSinFirmarDeNuevoNiCrearOtraCuenta() {
		ColegioCreado creado = altaCompleta("Colegio Completo", EscenarioPlataforma.RUC_A, "titular.completo", "45670006",
				"987650106");
		long firmas = contar("SELECT COUNT(*) FROM firma_operacion WHERE clave LIKE ?",
				"primera_promotoria:" + creado.colegioId() + ":%");

		for (int intento = 0; intento < 2; intento++) {
			assertThatThrownBy(() -> alta.completarAlta(creado.colegioId(), new DatosTitular("intruso.completo",
					"Otra Persona", "987650107", null))).isInstanceOf(ReglaNegocioException.class)
					.hasMessageContaining("primera Promotoría");
		}
		assertThat(contar("SELECT COUNT(*) FROM firma_operacion WHERE clave LIKE ?",
				"primera_promotoria:" + creado.colegioId() + ":%")).isEqualTo(firmas).isEqualTo(1L);
		assertThat(contar("SELECT COUNT(*) FROM usuario WHERE colegio_id = ?", creado.colegioId())).isEqualTo(1L);
		assertThat(contar("SELECT COUNT(*) FROM primera_promotoria WHERE colegio_id = ?", creado.colegioId())).isEqualTo(1L);
	}

	@Test
	void debeRechazarCompletarAltaEnUnColegioEnProduccion() {
		assertThatThrownBy(() -> alta.completarAlta(1L, new DatosTitular("titular.produccion", "Titular", "987650108",
				null))).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("producción");
		assertThat(contar("SELECT COUNT(*) FROM usuario WHERE nombre_usuario = 'titular.produccion'")).isZero();
	}

	@Test
	void debeResponderNoEncontradoAlCompletarElAltaDeLaPlataforma() {
		long plataforma = EscenarioPlataforma.plataformaId(colegios);

		assertThatThrownBy(() -> alta.completarAlta(plataforma, new DatosTitular("titular.plataforma", "Titular",
				"987650109", null))).isInstanceOf(RecursoNoEncontradoException.class);
	}

	@Test
	void debeExigirElDniDelTitularAntesDeCompletarElAltaDeUnColegioSinFicha() {
		jdbc.update("INSERT INTO colegio (nombre) VALUES ('Colegio Creado por el DBA')");
		long colegio = contar("SELECT MAX(id) FROM colegio WHERE tipo = 'COLEGIO'");

		assertThatThrownBy(() -> alta.completarAlta(colegio, new DatosTitular("titular.dba", "Titular del DBA",
				"987650110", null))).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("DNI");
		assertThat(contar("SELECT COUNT(*) FROM usuario WHERE colegio_id = ?", colegio)).isZero();
	}

	// ------------------------------------------------------------------ el contacto y el DNI del titular

	/** S8-A1: el cambio del contacto del titular lo propone este operador y lo aplica OTRO (con su propia sesión). */
	private void cambiarContacto(long colegio, String celular) {
		alta.proponerContactoTitular(colegio, new ContactoTitularRequest(celular, null,
				"El titular llamó: su celular del contrato tenía un dígito mal escrito"));
		Long cambio = contar("SELECT MAX(id) FROM cambio_contacto_titular WHERE colegio_id = ?", colegio);
		UsuariosDePrueba.iniciarSesion(EscenarioPlataforma.operador(usuarios, codificador, colegios, "operador2.qa8"));
		try {
			alta.aplicarContactoTitular(colegio, cambio);
		}
		finally {
			UsuariosDePrueba.iniciarSesion(operador);
		}
	}

	@Test
	void debeAvisarAlCorreoInstitucionalCuandoElOperadorCorrigeElContactoDelTitular() {
		ColegioCreado creado = altaCompleta("Colegio Aviso", EscenarioPlataforma.RUC_A, "titular.aviso", "45670011",
				"987650111");
		String institucional = jdbc.queryForObject("SELECT correo_institucional FROM colegio WHERE id = ?", String.class,
				creado.colegioId());

		cambiarContacto(creado.colegioId(), "987650112");

		assertThat(contar("SELECT COUNT(*) FROM mensaje WHERE colegio_id = ? AND LOWER(destino) = LOWER(?)",
				creado.colegioId(), institucional)).as("aviso al correo institucional").isGreaterThan(0L);
		assertThat(jdbc.queryForObject("SELECT telefono_whatsapp FROM usuario WHERE colegio_id = ?", String.class,
				creado.colegioId())).isEqualTo("+51987650112");
	}

	@Test
	void debeAvisarAlContactoAnteriorCuandoElOperadorCorrigeElContactoDelTitular() {
		ColegioCreado creado = altaCompleta("Colegio Anterior", EscenarioPlataforma.RUC_B, "titular.anterior", "45670013",
				"987650113");
		long antes = contar("SELECT COUNT(*) FROM mensaje WHERE colegio_id = ? AND destino = '+51987650113'",
				creado.colegioId());

		cambiarContacto(creado.colegioId(), "987650114");

		assertThat(contar("SELECT COUNT(*) FROM mensaje WHERE colegio_id = ? AND destino = '+51987650113'",
				creado.colegioId())).as("aviso al celular anterior").isGreaterThan(antes);
		assertThat(contar("SELECT COUNT(*) FROM mensaje WHERE colegio_id = ? AND destino = '+51987650113' "
				+ "AND tipo = 'AVISO_ALTA'", creado.colegioId())).isEqualTo(1L);
	}

	/** S8-A1 (corregido): un solo operador no cambia el contacto del titular; quien lo propone no lo aplica. */
	@Test
	void debeRechazarQueElMismoOperadorApliqueElContactoQuePropuso() {
		ColegioCreado creado = altaCompleta("Colegio Solo", EscenarioPlataforma.RUC_D, "titular.solo", "45670017",
				"987650119");
		alta.proponerContactoTitular(creado.colegioId(), new ContactoTitularRequest("987650199", null,
				"Celular del contrato mal escrito según el titular"));
		Long cambio = contar("SELECT MAX(id) FROM cambio_contacto_titular WHERE colegio_id = ?", creado.colegioId());

		assertThatThrownBy(() -> alta.aplicarContactoTitular(creado.colegioId(), cambio))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("otro operador");
		assertThat(jdbc.queryForObject("SELECT telefono_whatsapp FROM usuario WHERE colegio_id = ?", String.class,
				creado.colegioId())).isEqualTo("+51987650119");
	}

	/**
	 * S8-A1 (corregido): el DNI del titular no cambia después de crear su cuenta (la QA esperaba al menos un aviso; la
	 * corrección pedida es más estricta: el cambio se rechaza).
	 */
	@Test
	void debeRechazarQueElOperadorCambieElDniDelTitularAntesDeLaActivacion() {
		ColegioCreado creado = altaCompleta("Colegio Dni", EscenarioPlataforma.RUC_C, "titular.dni", "45670015",
				"987650115");
		String institucional = jdbc.queryForObject("SELECT correo_institucional FROM colegio WHERE id = ?", String.class,
				creado.colegioId());

		assertThatThrownBy(() -> alta.corregirFicha(creado.colegioId(), new FichaColegioRequest("Colegio Dni",
				"Asociación Colegio Dni S.A.C.", EscenarioPlataforma.RUC_C, "Av. Los Educadores 123, Lima", institucional,
				EnumSet.of(Nivel.PRIMARIA, Nivel.SECUNDARIA), "CT-titular.dni", "Titular Colegio Dni", "09999999")))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("DNI del titular no cambia");

		assertThat(jdbc.queryForObject("SELECT titular_documento FROM colegio WHERE id = ?", String.class,
				creado.colegioId())).isEqualTo("45670015");
	}

	@Test
	void debeRechazarQueElOperadorRedirijaElEnlaceDeUnTitularQueYaActivoSuCuenta() throws Exception {
		ColegioCreado creado = altaCompleta("Colegio Activado", EscenarioPlataforma.RUC_D, "titular.activado", "45670016",
				"987650116");
		SecurityContextHolder.clearContext();
		// El titular activa su cuenta con su enlace y el DNI del contrato.
		String ruta = EnlacesDePrueba.recibido(despacho, buzon, creado.colegioId(), "+51987650116");
		mvc.perform(post(ruta).with(csrf()).param("documento", "45670016").param("clave", "una clave larga del titular")
				.param("confirmacion", "una clave larga del titular")).andExpect(redirectedUrl("/login?cuenta-activada"));
		Long titular = contar("SELECT id FROM usuario WHERE nombre_usuario = 'titular.activado'");
		// Una segunda Promotoría del colegio le restablece el acceso (perdió su celular).
		Usuario segunda = UsuariosDePrueba.guardar(usuarios, codificador, creado.colegioId(), "promotora2.activado",
				UsuariosDePrueba.CLAVE, false, Rol.PROMOTOR);
		UsuariosDePrueba.iniciarSesion(segunda);
		servicioUsuarios.restablecerClave(titular, "Perdió su celular y pidió un enlace nuevo");
		SecurityContextHolder.clearContext();

		UsuariosDePrueba.iniciarSesion(operador);
		// QA-S8-3: «sin activar» ya no sale de debe_cambiar_clave (que el restablecimiento vuelve a pedir).
		assertThatThrownBy(() -> alta.proponerContactoTitular(creado.colegioId(), new ContactoTitularRequest("987650117",
				null, "Pidió cambiar su celular después de activar"))).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("ya activó");
		assertThatThrownBy(() -> alta.reenviarEnlaceTitular(creado.colegioId())).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("ya activó");
		assertThat(jdbc.queryForObject("SELECT telefono_whatsapp FROM usuario WHERE id = ?", String.class, titular))
				.isEqualTo("+51987650116");
	}

	// ------------------------------------------------------------------ activación con el DNI

	@Test
	void debeRechazarLaActivacionDelTitularConElDniDeOtroTitular() throws Exception {
		ColegioCreado creado = altaCompleta("Colegio Otro Dni", EscenarioPlataforma.RUC_A, "titular.otrodni", "45670018",
				"987650118");
		SecurityContextHolder.clearContext();
		String ruta = EnlacesDePrueba.recibido(despacho, buzon, creado.colegioId(), "+51987650118");

		mvc.perform(post(ruta).with(csrf()).param("documento", "45670019").param("clave", "una clave larga del titular")
				.param("confirmacion", "una clave larga del titular")).andExpect(redirectedUrl(ruta))
				.andExpect(flash().attribute("error", containsString("El DNI no corresponde")));
		assertThat(jdbc.queryForObject("SELECT debe_cambiar_clave FROM usuario WHERE colegio_id = ?", Boolean.class,
				creado.colegioId())).isTrue();
	}

	@Test
	void debeRechazarLaActivacionDelTitularConUnEnlaceDeMasDeCuarentaYOchoHoras() throws Exception {
		ColegioCreado creado = altaCompleta("Colegio Vencido", EscenarioPlataforma.RUC_B, "titular.vencido", "45670020",
				"987650120");
		SecurityContextHolder.clearContext();
		String ruta = EnlacesDePrueba.recibido(despacho, buzon, creado.colegioId(), "+51987650120");
		reloj.fijar(ConfiguracionRelojAjustable.INICIO.plusSeconds(48 * 3600 + 60));

		mvc.perform(post(ruta).with(csrf()).param("documento", "45670020").param("clave", "una clave larga del titular")
				.param("confirmacion", "una clave larga del titular")).andExpect(redirectedUrl(ruta))
				.andExpect(flash().attribute("error", containsString("ya no sirve")));
		assertThat(jdbc.queryForObject("SELECT debe_cambiar_clave FROM usuario WHERE colegio_id = ?", Boolean.class,
				creado.colegioId())).isTrue();
	}
}
