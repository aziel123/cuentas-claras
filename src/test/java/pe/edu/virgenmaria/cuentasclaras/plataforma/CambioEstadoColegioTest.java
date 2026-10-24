package pe.edu.virgenmaria.cuentasclaras.plataforma;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.MotivoCambioEstado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.TipoCambioEstado;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.EstadoDelColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.TokenDeSesionDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.plataforma.dto.CambioEstadoVista;
import pe.edu.virgenmaria.cuentasclaras.plataforma.proceso.AplicacionCambiosEstado;
import pe.edu.virgenmaria.cuentasclaras.plataforma.proceso.RevisionEstadosColegio;
import pe.edu.virgenmaria.cuentasclaras.plataforma.service.AltaColegio;
import pe.edu.virgenmaria.cuentasclaras.plataforma.service.CambiosEstadoColegio;
import pe.edu.virgenmaria.cuentasclaras.plataforma.service.ColegiosDeLaPlataforma;
import pe.edu.virgenmaria.cuentasclaras.plataforma.service.ObjecionesTitular;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sprint 11, tanda 1 (secciones 3.3 a 3.8, F2, F7, F10, AP-6, AP-8 y AP-10): dos operadores deciden el estado del colegio y
 * la facultad de su titular. Uno propone y OTRO aprueba; la baja sin cuentas activadas es inmediata y con cuentas, a los 7
 * días; la suspensión rige a los 30 días, espera la objeción del titular y, al aplicarse, cierra las sesiones del personal;
 * el personal ya no entra y las familias quedan en modo consulta. En H2 la suspensión está encendida
 * ({@code cuentasclaras.ciclo.suspension-disponible}); en MySQL la base la rechaza sin la exportación (AP-8).
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class CambioEstadoColegioTest {

	private static final String MOTIVO = "El colegio pidió darse de baja: era una instalación de prueba.";

	@Autowired
	private AltaColegio alta;

	@Autowired
	private CambiosEstadoColegio cambios;

	@Autowired
	private ObjecionesTitular objeciones;

	@Autowired
	private AplicacionCambiosEstado aplicacion;

	@Autowired
	private RevisionEstadosColegio revision;

	@Autowired
	private ColegiosDeLaPlataforma lista;

	@Autowired
	private EstadoDelColegio estadoDelColegio;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private ColegioRepository colegios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private TokenDeSesionDePrueba tokens;

	@Autowired
	private PlatformTransactionManager transacciones;

	@Autowired
	private RelojAjustable reloj;

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	private Usuario ana;

	private Usuario beto;

	@BeforeEach
	void preparar() {
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
		tokens.olvidar();
		ana = EscenarioPlataforma.operador(usuarios, codificador, colegios, "operador.ana");
		beto = EscenarioPlataforma.operador(usuarios, codificador, colegios, "operador.beto");
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		tokens.olvidar();
		LimpiezaBaseDatos.limpiar(jdbc);
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
	}

	@Test
	void f2LaBajaLaProponeUnoYLaApruebaOtroYSinCuentasActivadasEsInmediata() {
		long colegio = altaColegio("Colegio Prueba Baja", EscenarioPlataforma.RUC_A, "titular.baja", "45678931", "987650011");

		como(ana);
		Long id = cambios.proponer(colegio, TipoCambioEstado.BAJA, MotivoCambioEstado.PRUEBA, MOTIVO, null);
		assertThat(estado(colegio)).isEqualTo("PREPARACION");
		assertThatThrownBy(() -> cambios.aprobar(colegio, id)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("otro operador");
		assertThatThrownBy(() -> cambios.rechazar(colegio, id)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("otro operador");
		// La bandeja oculta «Aprobar» a quien propuso.
		assertThat(cambios.bandeja()).filteredOn(v -> v.id().equals(id)).singleElement()
				.extracting(CambioEstadoVista::puedeAprobar).isEqualTo(false);

		como(beto);
		assertThat(cambios.bandeja()).filteredOn(v -> v.id().equals(id)).singleElement()
				.extracting(CambioEstadoVista::puedeAprobar).isEqualTo(true);
		cambios.aprobar(colegio, id);

		assertThat(estado(colegio)).isEqualTo("BAJA");
		Map<String, Object> cambio = jdbc.queryForMap("SELECT * FROM cambio_estado_colegio WHERE id = ?", id);
		assertThat(cambio).containsEntry("estado", "APLICADO").containsEntry("propuesto_por", "operador.ana")
				.containsEntry("aprobado_por", "operador.beto");
		assertThat(cambio.get("pendiente")).isNull();
		assertThat(jdbc.queryForObject("SELECT cambio_estado_id FROM colegio WHERE id = ?", Long.class, colegio)).isEqualTo(id);
		// Auditoría en la bitácora del colegio y en la de la plataforma; avisos al colegio en la misma transacción.
		assertThat(jdbc.queryForList("SELECT accion FROM evento_auditoria WHERE colegio_id = ?", String.class, colegio))
				.contains("CAMBIO_ESTADO_COLEGIO_PROPUESTO", "CAMBIO_ESTADO_COLEGIO_APROBADO", "COLEGIO_DADO_DE_BAJA");
		assertThat(jdbc.queryForList("SELECT accion FROM evento_auditoria WHERE colegio_id = ?", String.class,
				EscenarioPlataforma.plataformaId(colegios))).contains("CAMBIO_ESTADO_COLEGIO_PROPUESTO",
						"CAMBIO_ESTADO_COLEGIO_APROBADO");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mensaje WHERE colegio_id = ?", Long.class, colegio))
				.isPositive();
		// Sale de la lista principal y aparece en «Dados de baja».
		assertThat(lista.todos(false)).noneMatch(c -> c.id().equals(colegio));
		assertThat(lista.todos(true)).anyMatch(c -> c.id().equals(colegio));
	}

	@Test
	void a6LaBajaConUnaCuentaActivadaEsALos7Dias() {
		long colegio = altaColegio("Colegio Activado", EscenarioPlataforma.RUC_B, "titular.activado", "45678932", "987650012");
		ingresar(activar(colegio, "titular.activado"));

		como(ana);
		Long id = cambios.proponer(colegio, TipoCambioEstado.BAJA, MotivoCambioEstado.NO_ARRANCO, MOTIVO, null);
		como(beto);
		cambios.aprobar(colegio, id);

		Map<String, Object> cambio = jdbc.queryForMap("SELECT estado, aprobado_en, efectiva_desde FROM cambio_estado_colegio "
				+ "WHERE id = ?", id);
		assertThat(cambio).containsEntry("estado", "APROBADO");
		assertThat(Duration.between(((java.sql.Timestamp) cambio.get("aprobado_en")).toLocalDateTime(),
				((java.sql.Timestamp) cambio.get("efectiva_desde")).toLocalDateTime())).isEqualTo(Duration.ofDays(7));
		assertThat(estado(colegio)).isEqualTo("PREPARACION");
		// La franja ámbar del personal dice la fecha; a las familias no les dice nada mientras opera.
		assertThat(franja(colegio, false)).hasValueSatisfying(t -> assertThat(t).contains("dado de baja"));
		assertThat(franja(colegio, true)).isEmpty();

		// A los 7 días la aplica sistema.plataforma.
		reloj.mover(Duration.ofDays(7).plusMinutes(1));
		assertThat(aplicacion.enColegio(colegio)).isEqualTo(1);
		assertThat(estado(colegio)).isEqualTo("BAJA");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sesion_usuario WHERE colegio_id = ? AND motivo_cierre = "
				+ "'COLEGIO_SUSPENDIDO'", Long.class, colegio)).isPositive();
	}

	@Test
	void unMotivoCortoYUnSegundoPendienteDelMismoAmbitoSeRechazan() {
		long colegio = altaColegio("Colegio Motivos", EscenarioPlataforma.RUC_C, "titular.motivos", "45678933", "987650013");
		como(ana);
		assertThatThrownBy(() -> cambios.proponer(colegio, TipoCambioEstado.BAJA, MotivoCambioEstado.PRUEBA, "corto", null))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("20");
		assertThatThrownBy(() -> cambios.proponer(colegio, TipoCambioEstado.BAJA, MotivoCambioEstado.FALTA_PAGO, MOTIVO, null))
				.isInstanceOf(ReglaNegocioException.class);
		cambios.proponer(colegio, TipoCambioEstado.BAJA, MotivoCambioEstado.PRUEBA, MOTIVO, null);
		assertThatThrownBy(() -> cambios.proponer(colegio, TipoCambioEstado.BAJA, MotivoCambioEstado.DUPLICADO, MOTIVO, null))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("pendiente");
		// Otro ámbito (la facultad) no espera a que se resuelva el estado.
		Long bloqueo = cambios.proponer(colegio, TipoCambioEstado.BLOQUEO_FACULTAD, MotivoCambioEstado.SEGURIDAD,
				"El titular reportó que perdió su celular con la sesión abierta.", null);
		assertThat(bloqueo).isNotNull();
		// Quien propuso puede anular (no aprobar).
		cambios.anular(colegio, bloqueo);
		assertThat(jdbc.queryForObject("SELECT estado FROM cambio_estado_colegio WHERE id = ?", String.class, bloqueo))
				.isEqualTo("ANULADO");
	}

	@Test
	void unaPersonaDelColegioNoProponeNiApruebaCambiosDeEstado() {
		long colegio = altaColegio("Colegio Sin Permiso", EscenarioPlataforma.RUC_D, "titular.permiso", "45678934", "987650014");
		Usuario titular = activar(colegio, "titular.permiso");
		como(titular);
		assertThatThrownBy(() -> cambios.proponer(colegio, TipoCambioEstado.BAJA, MotivoCambioEstado.PRUEBA, MOTIVO, null))
				.isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> cambios.bandeja()).isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void a2ElBloqueoDeLaFacultadLoAplicaLaAprobacionYLaRevisionDiariaDetectaUnEstadoSinSuCambio() {
		long colegio = altaColegio("Colegio Facultad", EscenarioPlataforma.RUC_A, "titular.facultad", "45678935", "987650015");
		como(ana);
		Long id = cambios.proponer(colegio, TipoCambioEstado.BLOQUEO_FACULTAD, MotivoCambioEstado.SEGURIDAD,
				"El titular reportó que perdió su celular con la sesión abierta.", null);
		como(beto);
		cambios.aprobar(colegio, id);
		assertThat(jdbc.queryForMap("SELECT facultad_bloqueada, facultad_cambio_id FROM colegio WHERE id = ?", colegio))
				.containsEntry("facultad_bloqueada", true).containsEntry("facultad_cambio_id", id);
		assertThat(jdbc.queryForList("SELECT accion FROM evento_auditoria WHERE colegio_id = ?", String.class, colegio))
				.contains("FACULTAD_BLOQUEADA");
		// Con la facultad bloqueada, el titular ya no objeta.
		como(activar(colegio, "titular.facultad"));
		assertThat(ContextoColegio.en(colegio, () -> objeciones.puedeObjetar())).isFalse();

		// F7: en orden, sin hallazgos; si el DBA pasa el colegio a BAJA a mano (con un cambio que no es su baja), alerta.
		SecurityContextHolder.clearContext();
		assertThat(revision.revisar(colegio)).isEmpty();
		jdbc.update("UPDATE colegio SET estado = 'BAJA', cambio_estado_id = ? WHERE id = ?", id, colegio);
		assertThat(revision.revisar(colegio)).singleElement().asString().contains("sin su cambio");
		assertThat(jdbc.queryForList("SELECT accion FROM evento_auditoria WHERE colegio_id = ?", String.class, colegio))
				.contains("ESTADO_COLEGIO_SIN_CAMBIO");
	}

	@Test
	void laSuspensionRigeALos30DiasEsperaLaObjecionYCierraLasSesionesDelPersonal() throws Exception {
		long colegio = altaColegio("Colegio Suspendido", EscenarioPlataforma.RUC_B, "titular.suspension", "45678936",
				"987650016");
		jdbc.update("UPDATE colegio SET estado = 'PRODUCCION', produccion_desde = ?, produccion_por = 'prueba' WHERE id = ?",
				LocalDateTime.now(reloj), colegio);
		Usuario titular = activar(colegio, "titular.suspension");
		ingresar(titular);

		como(ana);
		assertThatThrownBy(() -> cambios.proponer(colegio, TipoCambioEstado.SUSPENSION, MotivoCambioEstado.FALTA_PAGO,
				"Tres meses sin pagar la suscripción, con aviso escrito.", null))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("referencia");
		Long id = cambios.proponer(colegio, TipoCambioEstado.SUSPENSION, MotivoCambioEstado.FALTA_PAGO,
				"Tres meses sin pagar la suscripción, con aviso escrito.", "Carta notarial 2026-118");
		como(beto);
		cambios.aprobar(colegio, id);
		LocalDate hoy = LocalDate.now(reloj);
		assertThat(jdbc.queryForObject("SELECT efectiva_desde FROM cambio_estado_colegio WHERE id = ?",
				java.sql.Timestamp.class, id).toLocalDateTime()).isEqualTo(hoy.plusDays(31).atStartOfDay());
		assertThat(franja(colegio, false)).hasValueSatisfying(t -> assertThat(t).contains("en pausa"));

		// AP-10: el titular objeta con su firma; sin responder, la suspensión no se aplica aunque llegue la fecha.
		como(titular);
		ContextoColegio.en(colegio, () -> {
			objeciones.objetar(id, "Ya pagamos la deuda por transferencia el lunes, adjunto el voucher al correo.");
			return null;
		});
		assertThat(jdbc.queryForObject("SELECT objetado_por FROM cambio_estado_colegio WHERE id = ?", String.class, id))
				.isEqualTo("titular.suspension");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM firma_operacion WHERE colegio_id = ? AND clave = ?", Long.class,
				colegio, "cambio_estado_colegio:" + id + ":OBJETADO")).isEqualTo(1L);
		SecurityContextHolder.clearContext();
		reloj.mover(Duration.ofDays(32));
		assertThat(aplicacion.enColegio(colegio)).isZero();
		assertThat(estado(colegio)).isEqualTo("PRODUCCION");

		// La objeción la responde un operador que no propuso.
		como(ana);
		assertThatThrownBy(() -> cambios.resolverObjecion(colegio, id, "Revisamos el banco y el pago no aparece todavía."))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("otro operador");
		como(beto);
		cambios.resolverObjecion(colegio, id, "Revisamos el banco y el pago no aparece todavía.");
		SecurityContextHolder.clearContext();

		assertThat(aplicacion.enColegio(colegio)).isEqualTo(1);
		assertThat(estado(colegio)).isEqualTo("SUSPENDIDO");
		assertThat(jdbc.queryForObject("SELECT suspendido_en FROM colegio WHERE id = ?", java.sql.Timestamp.class, colegio))
				.isNotNull();
		// F10: la sesión del titular (personal) quedó cerrada; la revisión diaria lo encuentra en orden.
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sesion_usuario WHERE usuario_id = ? AND motivo_cierre = "
				+ "'COLEGIO_SUSPENDIDO'", Long.class, titular.getId())).isPositive();
		assertThat(revision.revisar(colegio)).isEmpty();

		// El personal sale en el pedido siguiente; la familia ve (GET) pero no escribe (POST 403).
		mvc.perform(get("/inicio").with(UsuariosDePrueba.como(titular)))
				.andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?error"));
		UsuarioAutenticado familia = new UsuarioAutenticado(990L, colegio, "familia.suspendida", "Familia Suspendida", null,
				true, false, false, EnumSet.of(Rol.APODERADO), 1L);
		mvc.perform(post("/familia/preferencias").with(csrf()).with(UsuariosDePrueba.como(familia)))
				.andExpect(status().isForbidden());
		assertThat(franja(colegio, true)).hasValueSatisfying(t -> assertThat(t).contains("modo consulta"));
		// Sale de los recorridos de los procesos que operan (no de la bitácora).
		assertThat(jdbc.queryForList("SELECT accion FROM evento_auditoria WHERE colegio_id = ?", String.class, colegio))
				.contains("CAMBIO_ESTADO_OBJETADO", "OBJECION_RESUELTA", "COLEGIO_SUSPENDIDO");
	}

	@Test
	void elTitularObjetaSoloLoSuyoYQuienNoEsTitularNoObjeta() {
		long colegio = altaColegio("Colegio Objecion", EscenarioPlataforma.RUC_C, "titular.objecion", "45678937",
				"987650017");
		Usuario titular = activar(colegio, "titular.objecion");
		como(ana);
		Long id = cambios.proponer(colegio, TipoCambioEstado.BAJA, MotivoCambioEstado.PEDIDO_COLEGIO, MOTIVO, null);

		// Otra Promotoría del colegio (no el titular) no objeta.
		Usuario otra = ContextoColegio.en(colegio, () -> UsuariosDePrueba.guardar(usuarios, codificador, colegio,
				"otra.promotoria", UsuariosDePrueba.CLAVE, false, Rol.PROMOTOR));
		como(otra);
		assertThatThrownBy(() -> ContextoColegio.en(colegio, () -> {
			objeciones.objetar(id, "No estoy de acuerdo con la baja del colegio, seguimos.");
			return null;
		})).isInstanceOf(AccessDeniedException.class);

		// El titular, sí (una vez); en su pantalla ve el cambio con la objeción.
		como(titular);
		ContextoColegio.en(colegio, () -> {
			objeciones.objetar(id, "No estoy de acuerdo con la baja del colegio, seguimos.");
			return null;
		});
		assertThatThrownBy(() -> ContextoColegio.en(colegio, () -> {
			objeciones.objetar(id, "Otra objeción más sobre la misma baja del colegio.");
			return null;
		})).isInstanceOf(ReglaNegocioException.class);
		List<pe.edu.virgenmaria.cuentasclaras.plataforma.dto.CambioDelColegioVista> vistas = ContextoColegio.en(colegio,
				() -> objeciones.delColegio());
		assertThat(vistas).singleElement().satisfies(v -> {
			assertThat(v.objecion()).contains("No estoy de acuerdo");
			assertThat(v.puedeObjetar()).isFalse();
		});

		// Aislamiento: el titular de OTRO colegio no ve ni objeta el cambio (404).
		long otroColegio = altaColegio("Colegio Vecino", EscenarioPlataforma.RUC_D, "titular.vecino", "45678938",
				"987650018");
		Usuario vecino = activar(otroColegio, "titular.vecino");
		como(vecino);
		assertThat(ContextoColegio.en(otroColegio, () -> objeciones.delColegio())).isEmpty();
		assertThatThrownBy(() -> ContextoColegio.en(otroColegio, () -> {
			objeciones.objetar(id, "Intento objetar el cambio de otro colegio distinto.");
			return null;
		})).isInstanceOf(pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException.class);
	}

	// ---------------------------------------------------------------------------------------------------------------

	private long altaColegio(String nombre, String ruc, String titular, String dni, String celular) {
		como(ana);
		long id = alta.crear(EscenarioPlataforma.alta(nombre, ruc, titular, dni, celular), null, null).colegioId();
		SecurityContextHolder.clearContext();
		return id;
	}

	/** La franja como la arma la pantalla: con el colegio de la sesión en contexto. */
	private java.util.Optional<String> franja(long colegio, boolean familia) {
		return ContextoColegio.en(colegio, () -> estadoDelColegio.franja(colegio, familia));
	}

	private String estado(long colegio) {
		return jdbc.queryForObject("SELECT estado FROM colegio WHERE id = ?", String.class, colegio);
	}

	private void como(Usuario usuario) {
		SecurityContextHolder.clearContext();
		UsuariosDePrueba.iniciarSesion(usuario);
	}

	/** La persona elige su clave (como con su enlace de activación): desde aquí su sesión tiene sus roles. */
	private Usuario activar(long colegio, String nombre) {
		return ContextoColegio.en(colegio, () -> new TransactionTemplate(transacciones).execute(t -> {
			Usuario u = usuarios.findByNombreUsuario(nombre).orElseThrow();
			u.cambiarClave(codificador.encode(UsuariosDePrueba.CLAVE), LocalDateTime.now(reloj), false);
			return usuarios.saveAndFlush(u);
		}));
	}

	/** Una sesión abierta en la base (en H2 la abre la firma de prueba): la cuenta queda activada. */
	private void ingresar(Usuario usuario) {
		como(usuario);
		new TransactionTemplate(transacciones).executeWithoutResult(t -> tokens.actual());
		SecurityContextHolder.clearContext();
	}
}
