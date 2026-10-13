package pe.edu.virgenmaria.cuentasclaras.auditoriaseguridad;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ColegioService;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.config.PropiedadesPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EnlacesDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.TokenDeSesionDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.proceso.DespachoMensajes;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.proveedor.BuzonSimulado;
import pe.edu.virgenmaria.cuentasclaras.panel.proceso.ResumenDiarioTarea;
import pe.edu.virgenmaria.cuentasclaras.pasarela.dto.PagoEnLineaRequest;
import pe.edu.virgenmaria.cuentasclaras.pasarela.dto.SeleccionPagoRequest;
import pe.edu.virgenmaria.cuentasclaras.pasarela.proceso.RecepcionAvisos;
import pe.edu.virgenmaria.cuentasclaras.pasarela.service.ServicioPagoEnLinea;
import pe.edu.virgenmaria.cuentasclaras.pasarela.simulada.PasarelaSimulada.Accion;
import pe.edu.virgenmaria.cuentasclaras.pasarela.simulada.SimuladorPagos;
import pe.edu.virgenmaria.cuentasclaras.plataforma.dto.ColegioCreado;
import pe.edu.virgenmaria.cuentasclaras.plataforma.dto.ContactoTitularRequest;
import pe.edu.virgenmaria.cuentasclaras.plataforma.dto.FichaColegioRequest;
import pe.edu.virgenmaria.cuentasclaras.plataforma.inicial.InicializadorOperadores;
import pe.edu.virgenmaria.cuentasclaras.plataforma.service.AltaColegio;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service.CorreoDeControl;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.GeneradorClaveTemporal;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.identidad.EjecucionIdentidad;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.SesionesFirmadas;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.efectivo;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;

/**
 * Auditoría de seguridad y antifraude del sprint 8 («Lista para vender»). Cada prueba reproducía un hallazgo; con las
 * correcciones del sprint 8 (docs/arquitectura/sprint-8-correcciones.md) se INVIRTIERON: ahora exigen el comportamiento
 * correcto y fallan si el hallazgo vuelve.
 * Solo H2 (sin triggers): los hallazgos son de la aplicación. Donde MySQL cambia el resultado, lo dice la prueba.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class AuditoriaSprint8Test {

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
	private ColegioService colegioService;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private RelojAjustable reloj;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TokenDeSesionDePrueba tokens;

	@Autowired
	private ResumenDiarioTarea resumen;

	@Autowired
	private GeneradorClaveTemporal generador;

	@Autowired
	private AuditoriaService auditoria;

	@Autowired
	private EjecucionIdentidad identidad;

	@Autowired
	private SesionesFirmadas sesiones;

	@Autowired
	private ApplicationEventPublisher eventos;

	@Autowired
	private PlatformTransactionManager transacciones;

	@Autowired
	private ServicioPagoEnLinea pagoEnLinea;

	@Autowired
	private SimuladorPagos simulador;

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private CorreoDeControl correoDeControl;

	@BeforeEach
	void preparar() {
		SecurityContextHolder.clearContext();
		tokens.olvidar();
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		tokens.olvidar();
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	// S8-A1. El operador se queda con la primera Promotoría de un colegio, sin que nadie se entere.

	/**
	 * S8-A1 (CORREGIDO; antes reproducía el hallazgo). El operador intenta quedarse con la primera Promotoría: (1) cambiar el
	 * DNI del titular en la ficha se rechaza (el DNI no cambia después de crear su cuenta); (2) cambiar el contacto del
	 * titular a su propio celular él solo, tampoco: lo propone y lo debe aplicar OTRO operador; (3) cuando otro operador lo
	 * aplica, en la misma transacción se avisa al celular ANTERIOR del titular, al correo institucional y al correo externo
	 * de control. Y aunque el enlace llegue a otro celular, la activación sigue pidiendo el DNI del contrato.
	 */
	@Test
	void s8a1ElOperadorYaNoSeQuedaConLaPromotoriaNiCambiaElContactoEnSilencio() throws Exception {
		Usuario operador = EscenarioPlataforma.operador(usuarios, codificador, colegios, "operador.toma");
		UsuariosDePrueba.iniciarSesion(operador);
		ColegioCreado creado = alta.crear(EscenarioPlataforma.alta("Colegio Toma", EscenarioPlataforma.RUC_D,
				"titular.toma", "45678941", "987650041"), null, null);
		long colegio = creado.colegioId();
		String correoInstitucional = jdbc.queryForObject("SELECT correo_institucional FROM colegio WHERE id = ?",
				String.class, colegio);
		alta.registrarCorreoControl(colegio, "contador.toma@estudio.pe");

		// (1) El DNI del titular no cambia.
		assertThatThrownBy(() -> alta.corregirFicha(colegio, new FichaColegioRequest("Colegio Toma",
				"Asociación Colegio Toma S.A.C.", EscenarioPlataforma.RUC_D, "Av. Los Educadores 123, Lima",
				correoInstitucional, EnumSet.of(Nivel.PRIMARIA, Nivel.SECUNDARIA), "CT-titular.toma", "Titular Colegio Toma",
				"11111111"))).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("DNI del titular no cambia");
		assertThat(jdbc.queryForObject("SELECT titular_documento FROM colegio WHERE id = ?", String.class, colegio))
				.isEqualTo("45678941");

		// (2) Él solo no redirige el enlace: lo propone, pero no lo puede aplicar.
		Long cambio = alta.proponerContactoTitular(colegio, new ContactoTitularRequest("987650099", null,
				"El titular pidió cambiar su celular (llamada)"));
		assertThatThrownBy(() -> alta.aplicarContactoTitular(colegio, cambio)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("otro operador");
		assertThat(jdbc.queryForObject("SELECT telefono_whatsapp FROM usuario WHERE colegio_id = ?", String.class, colegio))
				.isEqualTo("+51987650041");

		// (3) Con un segundo operador sí, y todos se enteran en la misma transacción.
		UsuariosDePrueba.iniciarSesion(EscenarioPlataforma.operador(usuarios, codificador, colegios, "operador.segundo"));
		alta.aplicarContactoTitular(colegio, cambio);
		SecurityContextHolder.clearContext();
		assertThat(jdbc.queryForList("SELECT DISTINCT tipo FROM mensaje WHERE colegio_id = ? AND destino LIKE ?",
				String.class, colegio, "%987650041")).contains("ACTIVACION_CUENTA", "AVISO_ALTA");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mensaje WHERE destino = ? AND tipo = 'AVISO_ALTA'", Long.class,
				correoInstitucional)).isPositive();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mensaje WHERE destino = 'contador.toma@estudio.pe' "
				+ "AND tipo = 'AVISO_ALTA'", Long.class)).isPositive();

		// El enlace llega al celular nuevo, pero sin el DNI del contrato no se activa la cuenta.
		String ruta = EnlacesDePrueba.recibido(despacho, buzon, colegio, "+51987650099");
		mvc.perform(post(ruta).with(csrf()).param("documento", "11111111").param("clave", "la clave larga del operador")
				.param("confirmacion", "la clave larga del operador")).andExpect(redirectedUrl(ruta));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuario u JOIN usuario_rol r ON r.usuario_id = u.id "
				+ "WHERE u.colegio_id = ? AND NOT u.debe_cambiar_clave AND r.rol = 'PROMOTOR'", Long.class, colegio)).isZero();
	}

	// S8-A2. El operador cambia el correo externo de control de un colegio EN PRODUCCIÓN y recibe sus montos.

	/**
	 * S8-A2 (CORREGIDO; antes reproducía el hallazgo). En un colegio en PRODUCCIÓN el operador solo PROPONE el correo
	 * externo de control: el correo anterior y toda la Promotoría reciben el aviso antes de que surta efecto, el resumen de
	 * esa noche sigue yendo al contador, y el correo cambia recién cuando una Promotoría del colegio lo aprueba con su firma.
	 */
	@Test
	void s8a2ElOperadorSoloProponeElCorreoDeControlYLaPromotoriaLoAprueba() {
		Usuario promotora = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "promotora.s8", UsuariosDePrueba.CLAVE,
				false, Rol.PROMOTOR);
		jdbc.update("INSERT INTO configuracion_colegio (colegio_id, clave, valor, creado_en, creado_por) VALUES "
				+ "(1, 'huella_correo_externo', 'contador@estudio.pe', CURRENT_TIMESTAMP, 'operador.inicial'), "
				+ "(1, 'resumen_correo_externo', 'contador@estudio.pe', CURRENT_TIMESTAMP, 'operador.inicial')");
		assertThat(colegioService.enProduccion(1L)).isTrue();

		Usuario operador = EscenarioPlataforma.operador(usuarios, codificador, colegios, "operador.correo");
		UsuariosDePrueba.iniciarSesion(operador);
		assertThatThrownBy(() -> alta.registrarCorreoControl(1L, "operador.personal@correo-propio.pe"))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("motivo");
		boolean registrado = alta.registrarCorreoControl(1L, "operador.personal@correo-propio.pe",
				"Adenda del contrato: el colegio cambió de contador");
		SecurityContextHolder.clearContext();

		assertThat(registrado).isFalse();
		assertThat(jdbc.queryForList("SELECT valor FROM configuracion_colegio WHERE colegio_id = 1", String.class))
				.containsOnly("contador@estudio.pe");
		// El aviso, antes de que surta efecto: a la Promotoría y al contador.
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mensaje WHERE tipo = 'CORREO_CONTROL' AND usuario_id = ?",
				Long.class, promotora.getId())).isPositive();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mensaje WHERE tipo = 'CORREO_CONTROL' "
				+ "AND destino = 'contador@estudio.pe'", Long.class)).isPositive();

		// Esa noche, el resumen sigue yendo al contador (no al operador).
		resumen.enColegio(1L, LocalDate.now(reloj));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mensaje WHERE tipo = 'RESUMEN_DIARIO' AND destino = ?",
				Long.class, "operador.personal@correo-propio.pe")).isZero();

		// Solo la Promotoría (con su firma) lo pone en vigencia.
		Long propuesta = jdbc.queryForObject("SELECT id FROM cambio_correo_control WHERE colegio_id = 1", Long.class);
		UsuariosDePrueba.iniciarSesion(promotora);
		correoDeControl.aprobar(propuesta);
		SecurityContextHolder.clearContext();
		assertThat(jdbc.queryForList("SELECT valor FROM configuracion_colegio WHERE colegio_id = 1", String.class))
				.containsOnly("operador.personal@correo-propio.pe");
		assertThat(jdbc.queryForObject("SELECT actualizado_por FROM configuracion_colegio WHERE colegio_id = 1 "
				+ "AND clave = 'huella_correo_externo'", String.class)).isEqualTo("promotora.s8");
	}

	// S8-M1. Un operador que vuelve a CC_OPERADORES recibe el enlace en el correo VIEJO de la cuenta.

	/**
	 * S8-M1 (CORREGIDO; antes reproducía el hallazgo). Al reactivar una cuenta, su correo pasa a ser el de CC_OPERADORES
	 * (con su evento en la bitácora) y el enlace nuevo va ahí, nunca al correo de quien tuvo antes ese usuario.
	 */
	@Test
	void s8m1AlReactivarUnOperadorElEnlaceVaAlCorreoDeLaLista() {
		long plataforma = EscenarioPlataforma.plataformaId(colegios);
		con("soporte.ops:ex.empleado@personal.pe").sincronizar();
		con("otro.ops:otro@proveedor.pe").sincronizar();
		var resultado = con("soporte.ops:nueva.persona@proveedor.pe,otro.ops:otro@proveedor.pe").sincronizar();

		assertThat(resultado.reactivados()).containsExactly("soporte.ops");
		var destinos = jdbc.queryForList("SELECT m.destino FROM mensaje m JOIN usuario u ON u.id = m.usuario_id "
				+ "WHERE u.nombre_usuario = 'soporte.ops' AND m.colegio_id = ? AND m.tipo = 'ACTIVACION_CUENTA' ORDER BY m.id",
				String.class, plataforma);
		assertThat(destinos).containsExactly("ex.empleado@personal.pe", "nueva.persona@proveedor.pe");
		assertThat(jdbc.queryForObject("SELECT correo FROM usuario WHERE nombre_usuario = 'soporte.ops'", String.class))
				.isEqualTo("nueva.persona@proveedor.pe");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'OPERADOR_CORREO_ACTUALIZADO'",
				Long.class)).isEqualTo(1L);
	}

	/** S8-M1: el correo de un operador ACTIVO no cambia por la lista: no arranca (se quita y se vuelve a agregar). */
	@Test
	void s8m1ElCorreoDeUnOperadorActivoNoCambiaPorLaLista() {
		con("soporte.ops:soporte@proveedor.pe").sincronizar();

		assertThatThrownBy(() -> con("soporte.ops:otra.persona@personal.pe").sincronizar())
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("quita al operador");
		assertThat(jdbc.queryForObject("SELECT correo FROM usuario WHERE nombre_usuario = 'soporte.ops'", String.class))
				.isEqualTo("soporte@proveedor.pe");
	}

	private InicializadorOperadores con(String lista) {
		return new InicializadorOperadores(usuarios, colegioService, codificador, generador, auditoria, identidad, sesiones,
				eventos, new PropiedadesPlataforma("COMPARTIDA", lista, 25, 8000), transacciones, (Clock) reloj);
	}

	// S8-M2. En un colegio en preparación la caja no cobra, pero el pago en línea sí.

	/**
	 * S8-M2 (CORREGIDO; antes reproducía el hallazgo). En un colegio en preparación tampoco hay pago en línea: crearOrden
	 * lo rechaza antes de ir a la pasarela (nada se cobra a la familia), como la caja.
	 */
	@Test
	void s8m2EnUnColegioEnPreparacionTampocoHayPagoEnLinea() {
		Familias f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		jdbc.update("UPDATE colegio SET estado = 'PREPARACION', produccion_desde = NULL, produccion_por = NULL WHERE id = 1");
		assertThat(colegioService.enProduccion(1L)).isFalse();
		Long marzo = cuota(jdbc, f.mateo(), "PEN-2027-03");

		// La caja: rechazado.
		como(EscenarioCaja.CAJA);
		assertThatThrownBy(() -> cobro.cobrar(efectivo(f.quispe(), List.of(marzo), "450.00", "450.00")))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no se registran pagos");

		// El portal: también rechazado, sin orden ni pago.
		como(new UsuarioAutenticado(40L, 1L, "rosa.familia", "Rosa", null, true, false, false, EnumSet.of(Rol.APODERADO),
				f.rosa()));
		BigDecimal total = pagoEnLinea.revisar(new SeleccionPagoRequest(List.of(marzo))).total();
		assertThatThrownBy(() -> pagoEnLinea.crearOrden(new PagoEnLineaRequest(UUID.randomUUID(), List.of(marzo), total,
				false))).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no recibe pagos en línea");

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orden_pago WHERE colegio_id = 1", Long.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pago WHERE origen = 'PASARELA' AND colegio_id = 1", Long.class))
				.isZero();
	}
}
