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
 * Auditoría de seguridad y antifraude del sprint 8 («Lista para vender»), rama auditoria/sprint-8. Cada prueba
 * REPRODUCE un hallazgo: pasa mientras el hallazgo exista (documenta el comportamiento actual). Cuando se corrija, la
 * prueba debe fallar y cambiarse por la que exige el comportamiento correcto (lo dice cada una).
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
	 * HALLAZGO S8-A1. El operador da de alta el colegio con el celular real del titular. Antes de que el titular active su
	 * cuenta: (1) cambia el DNI del titular en la ficha (corregirFicha acepta titular_documento en preparación) y (2)
	 * cambia el contacto del titular a SU propio celular (reenviarEnlaceTitular). El enlace llega al operador, que activa
	 * la cuenta con el DNI que él mismo escribió y queda como Promotoría del colegio. El diseño (sección 3.3 y E4) exigía
	 * avisar al contacto ANTERIOR y al correo institucional del colegio: ninguno de los dos recibe nada.
	 * Corregido cuando: el contacto anterior y el correo institucional reciben un aviso, y el DNI del titular no cambia
	 * mientras exista una primera Promotoría sin activar (o el cambio también avisa).
	 */
	@Test
	void s8a1ElOperadorSeQuedaConLaPromotoriaDelColegioSinAvisoAlTitularNiAlColegio() throws Exception {
		Usuario operador = EscenarioPlataforma.operador(usuarios, codificador, colegios, "operador.toma");
		UsuariosDePrueba.iniciarSesion(operador);
		ColegioCreado creado = alta.crear(EscenarioPlataforma.alta("Colegio Toma", EscenarioPlataforma.RUC_D,
				"titular.toma", "45678941", "987650041"), null, null);
		long colegio = creado.colegioId();
		String correoInstitucional = "contacto@titular-toma.edu.pe";

		// (1) El DNI del titular pasa a ser uno que el operador elige.
		alta.corregirFicha(colegio, new FichaColegioRequest("Colegio Toma", "Asociación Colegio Toma S.A.C.",
				EscenarioPlataforma.RUC_D, "Av. Los Educadores 123, Lima", correoInstitucional,
				EnumSet.of(Nivel.PRIMARIA, Nivel.SECUNDARIA), "CT-titular.toma", "Titular Colegio Toma", "11111111"));
		// (2) El enlace va al celular del operador.
		alta.reenviarEnlaceTitular(colegio, new ContactoTitularRequest("987650099", null));
		SecurityContextHolder.clearContext();

		String ruta = EnlacesDePrueba.recibido(despacho, buzon, colegio, "+51987650099");
		mvc.perform(post(ruta).with(csrf()).param("documento", "11111111").param("clave", "la clave larga del operador")
				.param("confirmacion", "la clave larga del operador")).andExpect(redirectedUrl("/login?cuenta-activada"));

		// El operador ya es la Promotoría activa del colegio (su única cuenta): crea Dirección y Caja, importa alumnos y
		// lo pasa a producción.
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuario u JOIN usuario_rol r ON r.usuario_id = u.id "
				+ "WHERE u.colegio_id = ? AND u.activo AND NOT u.debe_cambiar_clave AND r.rol = 'PROMOTOR' "
				+ "AND u.telefono_whatsapp = '+51987650099'", Long.class, colegio)).isEqualTo(1);
		// Nadie se enteró: al celular del titular real solo le llegó su enlace (ya anulado), nunca un aviso del cambio...
		assertThat(jdbc.queryForList("SELECT DISTINCT tipo FROM mensaje WHERE colegio_id = ? AND destino LIKE ?",
				String.class, colegio, "%987650041")).containsOnly("ACTIVACION_CUENTA");
		// ... y al correo institucional del colegio no le llegó nada.
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mensaje WHERE destino = ?", Long.class, correoInstitucional))
				.isZero();
	}

	// S8-A2. El operador cambia el correo externo de control de un colegio EN PRODUCCIÓN y recibe sus montos.

	/**
	 * HALLAZGO S8-A2. El correo externo de control es el testigo independiente del colegio (recibe la huella de la bitácora
	 * y el resumen diario, con montos). El operador lo cambia en un colegio en PRODUCCIÓN por un correo suyo (solo se
	 * rechaza el correo exacto de su cuenta de operador): ni la Promotoría ni el correo anterior reciben aviso (el diseño,
	 * sección 4, decía «el cambio avisa a la Promotoría y al correo anterior»). Desde esa noche el operador recibe el
	 * resumen del día del colegio, con lo cobrado, el efectivo y la deuda vencida: lo que la decisión 113 le prohíbe ver.
	 * Corregido cuando: en producción el cambio lo aprueba la Promotoría (o al menos la avisa a ella y al correo anterior
	 * antes de surtir efecto).
	 */
	@Test
	void s8a2ElOperadorSeQuedaConElCorreoDeControlDeUnColegioEnProduccionYRecibeSusMontos() {
		Usuario promotora = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "promotora.s8", UsuariosDePrueba.CLAVE,
				false, Rol.PROMOTOR);
		jdbc.update("INSERT INTO configuracion_colegio (colegio_id, clave, valor, creado_en, creado_por) VALUES "
				+ "(1, 'huella_correo_externo', 'contador@estudio.pe', CURRENT_TIMESTAMP, 'operador.inicial'), "
				+ "(1, 'resumen_correo_externo', 'contador@estudio.pe', CURRENT_TIMESTAMP, 'operador.inicial')");
		assertThat(colegioService.enProduccion(1L)).isTrue();
		long mensajesAntes = jdbc.queryForObject("SELECT COUNT(*) FROM mensaje", Long.class);

		Usuario operador = EscenarioPlataforma.operador(usuarios, codificador, colegios, "operador.correo");
		UsuariosDePrueba.iniciarSesion(operador);
		alta.registrarCorreoControl(1L, "operador.personal@correo-propio.pe");
		SecurityContextHolder.clearContext();

		assertThat(jdbc.queryForList("SELECT valor FROM configuracion_colegio WHERE colegio_id = 1", String.class))
				.containsOnly("operador.personal@correo-propio.pe");
		// Sin aviso a la Promotoría ni al contador.
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mensaje", Long.class)).isEqualTo(mensajesAntes);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mensaje WHERE usuario_id = ? OR destino = 'contador@estudio.pe'",
				Long.class, promotora.getId())).isZero();

		// Esa noche, el resumen del día (con montos) sale al correo del operador.
		resumen.enColegio(1L, LocalDate.now(reloj));
		String parametros = jdbc.queryForObject("SELECT parametros FROM mensaje WHERE tipo = 'RESUMEN_DIARIO' AND destino = ?",
				String.class, "operador.personal@correo-propio.pe");
		assertThat(parametros).contains("S/");
	}

	// S8-M1. Un operador que vuelve a CC_OPERADORES recibe el enlace en el correo VIEJO de la cuenta.

	/**
	 * HALLAZGO S8-M1. CC_OPERADORES dice «usuario:correo», pero al reactivar una cuenta que ya existía el inicializador
	 * ignora el correo de la lista y manda el enlace de activación al correo guardado en la cuenta. Si se reusa el usuario
	 * (por ejemplo, «soporte» o «ventas») para otra persona, el enlace le llega a quien se fue de la empresa, que vuelve a
	 * ser operador (y con S8-A1 puede quedarse con un colegio nuevo).
	 * Corregido cuando: el enlace va al correo de la lista (o la reactivación con otro correo se rechaza).
	 */
	@Test
	void s8m1AlReactivarUnOperadorElEnlaceVaAlCorreoAnteriorYNoAlDeLaLista() {
		long plataforma = EscenarioPlataforma.plataformaId(colegios);
		con("soporte.ops:ex.empleado@personal.pe").sincronizar();
		con("otro.ops:otro@proveedor.pe").sincronizar();
		var resultado = con("soporte.ops:nueva.persona@proveedor.pe,otro.ops:otro@proveedor.pe").sincronizar();

		assertThat(resultado.reactivados()).containsExactly("soporte.ops");
		var destinos = jdbc.queryForList("SELECT m.destino FROM mensaje m JOIN usuario u ON u.id = m.usuario_id "
				+ "WHERE u.nombre_usuario = 'soporte.ops' AND m.colegio_id = ? AND m.tipo = 'ACTIVACION_CUENTA' ORDER BY m.id",
				String.class, plataforma);
		assertThat(destinos).hasSize(2).containsOnly("ex.empleado@personal.pe");
	}

	private InicializadorOperadores con(String lista) {
		return new InicializadorOperadores(usuarios, colegioService, codificador, generador, auditoria, identidad, sesiones,
				eventos, new PropiedadesPlataforma("COMPARTIDA", lista, 25, 8000), transacciones, (Clock) reloj);
	}

	// S8-M2. En un colegio en preparación la caja no cobra, pero el pago en línea sí.

	/**
	 * HALLAZGO S8-M2. La regla «en preparación no hay dinero» (sección 3.4, E9) solo está en la caja
	 * (ServicioCobro.cobrar). El apoderado de un colegio en preparación (el portal se puede activar antes de producción)
	 * crea su orden de pago en línea y la pasarela la cobra; en H2 el sistema incluso registra el pago y su boleta. En
	 * MySQL, trg_pago_registro rechaza el pago DESPUÉS de que la pasarela ya cobró a la familia: dinero cobrado sin pago,
	 * sin boleta y sin aviso. Igual para la recaudación bancaria (RegistroPagosAutomaticos).
	 * Corregido cuando: crearOrden (y la aplicación de recaudación) exigen el colegio en producción.
	 */
	@Test
	void s8m2EnUnColegioEnPreparacionLaCajaNoCobraPeroElPagoEnLineaSi() {
		Familias f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		jdbc.update("UPDATE colegio SET estado = 'PREPARACION', produccion_desde = NULL, produccion_por = NULL WHERE id = 1");
		assertThat(colegioService.enProduccion(1L)).isFalse();
		Long marzo = cuota(jdbc, f.mateo(), "PEN-2027-03");

		// La caja: rechazado (el control existe).
		como(EscenarioCaja.CAJA);
		assertThatThrownBy(() -> cobro.cobrar(efectivo(f.quispe(), List.of(marzo), "450.00", "450.00")))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no se registran pagos");

		// El portal: la orden se crea y la pasarela cobra.
		como(new UsuarioAutenticado(40L, 1L, "rosa.familia", "Rosa", null, true, false, false, EnumSet.of(Rol.APODERADO),
				f.rosa()));
		BigDecimal total = pagoEnLinea.revisar(new SeleccionPagoRequest(List.of(marzo))).total();
		String referencia = pagoEnLinea.crearOrden(new PagoEnLineaRequest(UUID.randomUUID(), List.of(marzo), total, false));
		assertThat(simulador.simular(referencia, Accion.YAPE)).isEqualTo(RecepcionAvisos.Resultado.ACEPTADO);

		assertThat(jdbc.queryForObject("SELECT estado FROM colegio WHERE id = 1", String.class)).isEqualTo("PREPARACION");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pago WHERE origen = 'PASARELA' AND colegio_id = 1", Long.class))
				.as("pago en línea registrado en un colegio en preparación").isEqualTo(1);
	}
}
