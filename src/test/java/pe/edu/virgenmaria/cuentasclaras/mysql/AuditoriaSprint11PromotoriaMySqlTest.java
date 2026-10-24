package pe.edu.virgenmaria.cuentasclaras.mysql;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.EstadoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.proceso.AplicacionSolicitudesEnEspera;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.CrearAnioEscolarRequest;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.FeriadoRequest;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioFeriados;
import pe.edu.virgenmaria.cuentasclaras.comun.basedatos.FuenteDatosEnrutada;
import pe.edu.virgenmaria.cuentasclaras.comun.basedatos.RutaConexion;
import pe.edu.virgenmaria.cuentasclaras.comun.config.ConfiguracionTiempo;
import pe.edu.virgenmaria.cuentasclaras.comun.cripto.DerivadorSecreto;
import pe.edu.virgenmaria.cuentasclaras.comun.cripto.Sha256;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.CodigosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.CuentasDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.DbaMySql;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.TokenDeSesionDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.conectores.dto.PropuestaConectorRequest;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.ModoConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.ProveedorConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.TipoConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.proceso.AplicacionConectoresEnEspera;
import pe.edu.virgenmaria.cuentasclaras.conectores.service.CuentaInterbancaria;
import pe.edu.virgenmaria.cuentasclaras.conectores.service.EsperaConectores;
import pe.edu.virgenmaria.cuentasclaras.conectores.service.PropuestaConector;
import pe.edu.virgenmaria.cuentasclaras.plataforma.dto.ColegioCreado;
import pe.edu.virgenmaria.cuentasclaras.plataforma.service.AltaColegio;
import pe.edu.virgenmaria.cuentasclaras.plataforma.service.FrenosOperador;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.CambiarRolesRequest;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.CrearUsuarioRequest;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.CodigoPromotoria;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.FacultadTitular;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioUsuarios;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.FirmaSesion;

import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 11, tanda 1c (secciones 3.16 y 3.16b) contra MySQL 8 real, con los permisos mínimos de 02 y los triggers de 03: la
 * Promotoría aplica directo (con su firma y, en lo sensible, con su código atado a la acción), los conectores de dinero
 * esperan 24 horas (con freno y el socio con 30 días en el rol), quitar la Promotoría espera, y lo que la base rechaza:
 * {@code aplica_desde} y {@code frenado_en} solo los pone la base (A1c-6), la directa de otra persona o sin firma o sin
 * código, y lo que no cambia para los demás roles. Las esperas se prueban bajando {@code espera_directa_horas} como DBA
 * (y se restaura), nunca cambiando {@code aplica_desde}. Usa nombres únicos y no limpia.
 */
@SpringBootTest(properties = "cuentasclaras.plataforma.max-colegios=80")
@ActiveProfiles({ "test", "mysql" })
@EnabledIfEnvironmentVariable(named = "CC_PRUEBA_MYSQL", matches = "true")
class AuditoriaSprint11PromotoriaMySqlTest {

	private static final String MOTIVO = "Lo decidió la Promotoría en la reunión de hoy";

	private final String sufijo = Long.toString(System.nanoTime(), 36);

	@Autowired
	private AltaColegio alta;

	@Autowired
	private FacultadTitular facultad;

	@Autowired
	private ServicioUsuarios servicioUsuarios;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private ServicioFeriados feriados;

	@Autowired
	private BandejaAprobaciones bandeja;

	@Autowired
	private PropuestaConector propuesta;

	@Autowired
	private EsperaConectores espera;

	@Autowired
	private AplicacionConectoresEnEspera conectoresEnEspera;

	@Autowired
	private AplicacionSolicitudesEnEspera solicitudesEnEspera;

	@Autowired
	private FrenosOperador frenos;

	@Autowired
	private FirmaSesion firma;

	@Autowired
	private CuentasDePrueba cuentasDePrueba;

	@Autowired
	private DerivadorSecreto derivador;

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
	private JdbcTemplate jdbc;

	@Autowired
	private javax.sql.DataSource fuenteDatos;

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
	}

	private record Nuevo(long colegio, Usuario titular, String ruc, String razonSocial) {
	}

	private Nuevo colegioNuevo(String ruc, String semilla, String celular) {
		UsuariosDePrueba.iniciarSesion(EscenarioPlataforma.operador(usuarios, codificador, colegios, "operador.s11c"));
		String dni = "4" + String.format("%07d", Math.floorMod((semilla + sufijo).hashCode(), 10_000_000));
		ColegioCreado creado = alta.crear(EscenarioPlataforma.alta("Colegio S11c " + sufijo + " " + celular, ruc,
				"t" + celular.substring(5) + "." + sufijo, dni, celular), null, null);
		assertThat(creado.completo()).as(creado.pendiente()).isTrue();
		alta.registrarCorreoControl(creado.colegioId(), "contador." + sufijo + "." + celular + "@estudio.pe");
		SecurityContextHolder.clearContext();
		Usuario titular = cuenta(creado.colegioId(), creado.titularUsuario());
		ingresar(titular);
		String razon = jdbc.queryForObject("SELECT razon_social FROM colegio WHERE id = ?", String.class,
				creado.colegioId());
		return new Nuevo(creado.colegioId(), titular, ruc, razon);
	}

	private void ingresar(Usuario usuario) {
		UsuariosDePrueba.iniciarSesion(usuario);
		new TransactionTemplate(transacciones).executeWithoutResult(t -> tokens.actual());
	}

	/** Activa la cuenta (su clave, como el enlace) y la devuelve como está en la base. */
	private Usuario cuenta(long colegio, String nombre) {
		Usuario u = ContextoColegio.en(colegio, () -> usuarios.findByNombreUsuario(nombre)).orElseThrow();
		RutaConexion.identidad(() -> ContextoColegio.en(colegio, () -> {
			Usuario x = usuarios.findById(u.getId()).orElseThrow();
			x.cambiarClave(codificador.encode(UsuariosDePrueba.CLAVE), LocalDateTime.now(Clock.system(
					ConfiguracionTiempo.ZONA_LIMA)), false);
			return usuarios.saveAndFlush(x);
		}));
		return ContextoColegio.en(colegio, () -> usuarios.findById(u.getId())).orElseThrow();
	}

	private <T> T como(Usuario quien, Supplier<T> accion) {
		ingresar(quien);
		try {
			return accion.get();
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	private void como(Usuario quien, Runnable accion) {
		como(quien, () -> {
			accion.run();
			return null;
		});
	}

	private JdbcTemplate sistema() {
		return new JdbcTemplate(((FuenteDatosEnrutada) fuenteDatos).sistema());
	}

	/** Con la facultad del titular (y su código), una cuenta directiva nueva, ya activada. */
	private Usuario directiva(Nuevo n, String nombre, String celular, Rol rol) {
		ingresar(n.titular());
		CodigoPromotoria.Pedido pedido = facultad.pedirCodigo(rol, nombre);
		String codigo = CodigosDePrueba.codigo(jdbc, derivador, n.colegio(), pedido.mensajeId());
		ingresar(n.titular());
		facultad.crearCuentaDirectiva(new CrearUsuarioRequest("Cuenta " + nombre, nombre, null, celular, EnumSet.of(rol)),
				codigo, pedido);
		return cuenta(n.colegio(), nombre);
	}

	private Usuario docente(Nuevo n, String nombre, String celular) {
		ingresar(n.titular());
		servicioUsuarios.crear(new CrearUsuarioRequest("Docente " + nombre, nombre, null, celular, Set.of(Rol.DOCENTE)));
		return cuenta(n.colegio(), nombre);
	}

	/** El titular pide los roles nuevos de la cuenta; devuelve la solicitud (pendiente: lo de roles es sensible). */
	private Long pedirRoles(Nuevo n, Usuario cuenta, Set<Rol> roles) {
		como(n.titular(), () -> servicioUsuarios.cambiarRoles(cuenta.getId(), new CambiarRolesRequest(roles, MOTIVO)));
		return ContextoColegio.en(n.colegio(), () -> EscenarioAprobaciones.pendiente(jdbc, "usuario", cuenta.getId()));
	}

	private CodigoPromotoria.Escrito escrito(long colegio, CodigoPromotoria.Pedido pedido) {
		return CodigosDePrueba.escrito(jdbc, derivador, colegio, pedido);
	}

	/** «Aprobar y aplicar» de quien la pidió, con su código correcto. */
	private void aplicarConCodigo(Usuario quien, long colegio, Long solicitud) {
		CodigoPromotoria.Pedido pedido = como(quien, () -> bandeja.pedirCodigo(solicitud));
		como(quien, () -> bandeja.aprobar(solicitud, null, false, List.of(), escrito(colegio, pedido)));
	}

	private PropuestaConectorRequest cuentaBancaria(Nuevo n, String numero) {
		String entidad = CuentaInterbancaria.ENTIDADES.getOrDefault(ProveedorConector.BCP, "002");
		String doce = ("000000000000" + numero).substring(("000000000000" + numero).length() - 12);
		return new PropuestaConectorRequest(ProveedorConector.BCP, ModoConector.REAL, null, null, null, null, null, null,
				null, null, null, numero, CuentaInterbancaria.con(entidad, "191", doce), n.razonSocial(), n.ruc(), null, null,
				null, null, null, "Cuenta del colegio para depositar el efectivo");
	}

	/** El titular propone, prueba y aprueba con su código la cuenta bancaria: queda EN_ESPERA. Devuelve el conector. */
	private Long cuentaEnEspera(Nuevo n, String numero) {
		Long id = como(n.titular(), () -> propuesta.proponer(TipoConector.CUENTA_BANCARIA, cuentaBancaria(n, numero)));
		como(n.titular(), () -> propuesta.probar(id));
		Long solicitud = jdbc.queryForObject("SELECT solicitud_id FROM conector_colegio WHERE id = ?", Long.class, id);
		aplicarConCodigo(n.titular(), n.colegio(), solicitud);
		return id;
	}

	private void espera(int horas) {
		DbaMySql.exigir().update("UPDATE configuracion_bd SET valor = ? WHERE clave = 'espera_directa_horas'",
				Integer.toString(horas));
	}

	/** A1c-6: aplica_desde (y frenado_en) los escribe solo la base: ni cc_app ni cc_sistema (1143), ni el DBA (1644). */
	@Test
	void aplicaDesdeManipuladoFalla() {
		Nuevo n = colegioNuevo("20600000111", "a", "987671001");
		Usuario docente = docente(n, "doc.a." + sufijo, "987671002");
		// (Dar DIRECTOR sería la «primera Dirección», que no pasa por solicitud.)
		Long solicitud = pedirRoles(n, docente, EnumSet.of(Rol.PROMOTOR));
		Long conector = como(n.titular(), () -> propuesta.proponer(TipoConector.CUENTA_BANCARIA,
				cuentaBancaria(n, "1912345678901")));
		for (String tabla : List.of("solicitud_cambio", "conector_colegio")) {
			for (String columna : List.of("aplica_desde", "frenado_en")) {
				String sql = "UPDATE " + tabla + " SET " + columna + " = NOW(6) WHERE id = ?";
				Long id = tabla.equals("solicitud_cambio") ? solicitud : conector;
				assertThat(codigoAl(() -> jdbc.update(sql, id))).as("cc_app " + tabla + "." + columna).isEqualTo(1143);
				assertThat(codigoAl(() -> sistema().update(sql, id))).as("cc_sistema " + tabla + "." + columna)
						.isEqualTo(1143);
				assertThat(codigoAl(() -> DbaMySql.exigir().update(sql, id))).as("DBA " + tabla + "." + columna)
						.isEqualTo(1644);
			}
		}
		// Aplicada con su código, la espera que puso la base tampoco se mueve.
		Usuario socia = directiva(n, "soc.a." + sufijo, "987671003", Rol.PROMOTOR);
		Long quitar = pedirRoles(n, socia, EnumSet.of(Rol.DOCENTE));
		aplicarConCodigo(n.titular(), n.colegio(), quitar);
		LocalDateTime desde = jdbc.queryForObject("SELECT aplica_desde FROM solicitud_cambio WHERE id = ?",
				LocalDateTime.class, quitar);
		assertThat(desde).as("la espera la pone la base").isAfter(LocalDateTime.now(Clock.system(ConfiguracionTiempo
				.ZONA_LIMA)).plusHours(23));
		assertThat(codigoAl(() -> DbaMySql.exigir().update("UPDATE solicitud_cambio SET aplica_desde = NOW(6) WHERE id = ?",
				quitar))).isEqualTo(1644);
	}

	/**
	 * f16b ajustada (decisión 220): en producción, quitar la única Dirección es una directa con código y avisos, sin
	 * espera (trg_solicitud_cambio_resuelta con el código; trg_usuario_rol_baja con cc_solicitud_firmada). Y el código va
	 * atado a la acción: el de otra solicitud no sirve aunque la firma sea buena (A1c-5).
	 */
	@Test
	void f16bQuitarLaUnicaDireccionEnProduccionEsDirectaConCodigoYElCodigoVaAtado() {
		Nuevo n = colegioNuevo("20600000129", "b", "987672001");
		Usuario direccion = directiva(n, "dir.b." + sufijo, "987672002", Rol.DIRECTOR);
		Usuario docente = docente(n, "doc.b." + sufijo, "987672003");
		ingresar(n.titular());
		servicioUsuarios.crear(new CrearUsuarioRequest("Caja " + sufijo, "caja.b." + sufijo, null, "987672004",
				Set.of(Rol.CAJA)));
		cuenta(n.colegio(), "caja.b." + sufijo);
		ingresar(n.titular());
		estructura.crearAnio(new CrearAnioEscolarRequest(2027, LocalDate.of(2027, 3, 1), LocalDate.of(2027, 12, 17), false));
		cuentasDePrueba.pasarAProduccion(n.colegio(), n.titular());

		Long quitar = pedirRoles(n, direccion, EnumSet.of(Rol.DOCENTE));
		Long dar = pedirRoles(n, docente, EnumSet.of(Rol.DIRECTOR));
		// La firma de la directa de «dar», con el código de «quitar»: la base lo rechaza (A1c-5).
		CodigoPromotoria.Pedido deQuitar = como(n.titular(), () -> bandeja.pedirCodigo(quitar));
		String escritoQuitar = Sha256.hex(escrito(n.colegio(), deQuitar).codigo());
		como(n.titular(), () -> new TransactionTemplate(transacciones).executeWithoutResult(t -> firma.firmar(
				ClaveFirma.solicitud(dar, EstadoSolicitud.APROBADA))));
		String resolver = "UPDATE solicitud_cambio SET estado = 'APROBADA', pendiente = NULL, resuelto_por = ?, "
				+ "resuelto_en = NOW(6), directa = TRUE, codigo_mensaje_id = ?, codigo_escrito_sha256 = ? WHERE id = ?";
		assertThat(codigoAl(() -> sistema().update(resolver, n.titular().getNombreUsuario(), deQuitar.mensajeId(),
				escritoQuitar, dar))).isEqualTo(1644);
		// Sin código, tampoco.
		assertThat(codigoAl(() -> sistema().update("UPDATE solicitud_cambio SET estado = 'APROBADA', pendiente = NULL, "
				+ "resuelto_por = ?, resuelto_en = NOW(6), directa = TRUE WHERE id = ?", n.titular().getNombreUsuario(), dar)))
				.isEqualTo(1644);

		// Con su código, se aplica al instante y sin espera.
		como(n.titular(), () -> bandeja.aprobar(quitar, null, false, List.of(), escrito(n.colegio(), deQuitar)));
		assertThat(jdbc.queryForMap("SELECT estado, directa, aplica_desde FROM solicitud_cambio WHERE id = ?", quitar))
				.containsEntry("estado", "APROBADA").containsEntry("directa", true).containsEntry("aplica_desde", null);
		assertThat(jdbc.queryForList("SELECT rol FROM usuario_rol WHERE usuario_id = ?", String.class, direccion.getId()))
				.containsExactly("DOCENTE");
		// El código ya se usó (UNIQUE o el trigger).
		assertThat(codigoAl(() -> sistema().update(resolver, n.titular().getNombreUsuario(), deQuitar.mensajeId(),
				escritoQuitar, dar))).isIn(1062, 1644);
	}

	/**
	 * Lo que la base rechaza de la directa y lo que no cambia: sin firma, de quien no la pidió (3819), de Administración (que
	 * no es Promotoría), Dirección no resuelve lo suyo, y la Promotoría no es Caja.
	 */
	@Test
	void laDirectaFueraDeSuCaminoFallaYLoDemasNoCambia() {
		Nuevo n = colegioNuevo("20600000137", "c", "987673001");
		Usuario docente = docente(n, "doc.c." + sufijo, "987673002");
		Long dar = pedirRoles(n, docente, EnumSet.of(Rol.PROMOTOR));
		String titular = n.titular().getNombreUsuario();
		// Sin firma (y sin código).
		assertThat(codigoAl(() -> sistema().update("UPDATE solicitud_cambio SET estado = 'APROBADA', pendiente = NULL, "
				+ "resuelto_por = ?, resuelto_en = NOW(6), directa = TRUE WHERE id = ?", titular, dar))).isEqualTo(1644);
		// Directa con quien no la pidió (CHECK o trigger).
		assertThat(codigoAl(() -> sistema().update("UPDATE solicitud_cambio SET estado = 'APROBADA', pendiente = NULL, "
				+ "resuelto_por = 'otra.persona', resuelto_en = NOW(6), directa = TRUE WHERE id = ?", dar))).isIn(3819, 1644);
		// Resuelta por quien la pidió sin directa: el CHECK de siempre.
		assertThat(codigoAl(() -> sistema().update("UPDATE solicitud_cambio SET estado = 'APROBADA', pendiente = NULL, "
				+ "resuelto_por = ?, resuelto_en = NOW(6) WHERE id = ?", titular, dar))).isIn(3819, 1644);
		// Una pendiente no se vuelve directa ni se frena.
		assertThat(codigoAl(() -> sistema().update("UPDATE solicitud_cambio SET directa = TRUE WHERE id = ?", dar)))
				.isIn(3819, 1644);
		assertThat(codigoAl(() -> sistema().update("UPDATE solicitud_cambio SET frenado_por = 'x', motivo_freno = "
				+ "'Un motivo de prueba bastante largo' WHERE id = ?", dar))).isIn(3819, 1644);
		// La Promotoría no es Caja (trg_usuario_rol_alta, sin cambio).
		assertThat(codigoAl(() -> sistema().update("INSERT INTO usuario_rol (usuario_id, rol) VALUES (?, 'CAJA')",
				n.titular().getId()))).isEqualTo(1644);
	}

	/**
	 * Decisión 218 y A1c-2: la cuenta bancaria del titular queda EN_ESPERA 24 horas (la fecha la pone la base); nadie la
	 * pone VIGENTE antes; un socio de paja (Promotoría recién nombrada) no la aprueba al instante; con 30 días en el rol,
	 * con su código, sí.
	 */
	@Test
	void laCuentaBancariaEsperaYElSocioDePajaNoLaApruebaAlInstante() {
		Nuevo n = colegioNuevo("20600000145", "d", "987674001");
		Usuario socia = directiva(n, "soc.d." + sufijo, "987674002", Rol.PROMOTOR);
		Long conector = cuentaEnEspera(n, "1912345678902");
		var fila = jdbc.queryForMap("SELECT estado, directa, aplica_desde FROM conector_colegio WHERE id = ?", conector);
		assertThat(fila).containsEntry("estado", "EN_ESPERA").containsEntry("directa", true);
		assertThat((LocalDateTime) fila.get("aplica_desde")).isAfter(LocalDateTime.now(Clock.system(ConfiguracionTiempo
				.ZONA_LIMA)).plusHours(23));
		// Antes de las 24 horas, nadie la pone VIGENTE (ni sistema.conectores, ni con cc_sistema a mano).
		assertThat(conectoresEnEspera.aplicar(n.colegio())).isZero();
		assertThat(codigoAl(() -> sistema().update("UPDATE conector_colegio SET estado = 'VIGENTE', vigente = TRUE "
				+ "WHERE id = ?", conector))).isEqualTo(1644);

		// El socio de paja: Promotoría desde hoy.
		CodigoPromotoria.Pedido pedido = como(socia, () -> espera.pedirCodigoDelSocio(conector));
		assertThatThrownBy(() -> como(socia, () -> espera.aprobarAhora(conector, escrito(n.colegio(), pedido))))
				.hasMessageContaining("30 días");
		assertThat(jdbc.queryForObject("SELECT estado FROM conector_colegio WHERE id = ?", String.class, conector))
				.isEqualTo("EN_ESPERA");

		// Con 30 días o más en el rol (el DBA mueve usuario_rol.desde, como si hubiera pasado el tiempo), con su código.
		DbaMySql.exigir().update("UPDATE usuario_rol SET desde = NOW(6) - INTERVAL 40 DAY WHERE usuario_id = ? AND "
				+ "rol = 'PROMOTOR'", socia.getId());
		CodigoPromotoria.Pedido otro = como(socia, () -> espera.pedirCodigoDelSocio(conector));
		como(socia, () -> espera.aprobarAhora(conector, escrito(n.colegio(), otro)));
		assertThat(jdbc.queryForMap("SELECT estado, vigente, codigo_mensaje_id FROM conector_colegio WHERE id = ?",
				conector)).containsEntry("estado", "VIGENTE").containsEntry("vigente", true)
				.containsEntry("codigo_mensaje_id", otro.mensajeId());
	}

	/** La espera vencida la aplica sistema.conectores; la frena un operador (frenado_en lo pone la base) y ya no cambia. */
	@Test
	void laEsperaVencidaLaAplicaSistemaConectoresYElFrenoDelOperadorEsFinal() {
		Nuevo n = colegioNuevo("20600000153", "e", "987675001");
		Long vence;
		espera(0);
		try {
			vence = cuentaEnEspera(n, "1912345678903");
		}
		finally {
			espera(24);
		}
		assertThat(conectoresEnEspera.aplicar(n.colegio())).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT estado FROM conector_colegio WHERE id = ?", String.class, vence))
				.isEqualTo("VIGENTE");

		Long frenado = cuentaEnEspera(n, "1912345678904");
		Usuario operador = EscenarioPlataforma.operador(usuarios, codificador, colegios, "operador.s11c");
		como(operador, () -> frenos.frenarConector(n.colegio(), frenado, "El colegio llamó: no reconoce la cuenta nueva"));
		var fila = jdbc.queryForMap("SELECT estado, frenado_por, frenado_en FROM conector_colegio WHERE id = ?", frenado);
		assertThat(fila).containsEntry("estado", "FRENADO").containsEntry("frenado_por", "operador.s11c");
		assertThat(fila.get("frenado_en")).isNotNull();
		assertThat(codigoAl(() -> sistema().update("UPDATE conector_colegio SET estado = 'VIGENTE', vigente = TRUE "
				+ "WHERE id = ?", frenado))).isIn(1644, 3819);
		assertThat(codigoAl(() -> sistema().update("UPDATE conector_colegio SET motivo_freno = 'corto' WHERE id = ?",
				frenado))).isIn(1644, 3819);
	}

	/**
	 * A1c-2: quitarle la Promotoría a la socia espera; con cc_sistema a mano no se aplica antes (cc_solicitud_firmada);
	 * vencida, la aplica sistema.roles.
	 */
	@Test
	void quitarLaPromotoriaEsperaYLaAplicaSistemaRoles() {
		Nuevo n = colegioNuevo("20600000161", "f", "987676001");
		Usuario socia = directiva(n, "soc.f." + sufijo, "987676002", Rol.PROMOTOR);
		Long quitar = pedirRoles(n, socia, EnumSet.of(Rol.DOCENTE));
		aplicarConCodigo(n.titular(), n.colegio(), quitar);
		assertThat(jdbc.queryForList("SELECT rol FROM usuario_rol WHERE usuario_id = ?", String.class, socia.getId()))
				.containsExactly("PROMOTOR");
		assertThat(codigoAl(() -> sistema().update("DELETE FROM usuario_rol WHERE usuario_id = ? AND rol = 'PROMOTOR'",
				socia.getId()))).isEqualTo(1644);
		assertThat(solicitudesEnEspera.aplicar(n.colegio())).isZero();

		// Otra, con la espera en 0 horas (DBA): vencida, la aplica sistema.roles.
		Usuario otra = directiva(n, "soc.f2." + sufijo, "987676003", Rol.PROMOTOR);
		Long quitarOtra = pedirRoles(n, otra, EnumSet.of(Rol.DOCENTE));
		espera(0);
		try {
			aplicarConCodigo(n.titular(), n.colegio(), quitarOtra);
		}
		finally {
			espera(24);
		}
		assertThat(solicitudesEnEspera.aplicar(n.colegio())).isEqualTo(1);
		assertThat(jdbc.queryForList("SELECT rol FROM usuario_rol WHERE usuario_id = ?", String.class, otra.getId()))
				.containsExactly("DOCENTE");
		assertThat(jdbc.queryForList("SELECT rol FROM usuario_rol WHERE usuario_id = ?", String.class, socia.getId()))
				.containsExactly("PROMOTOR");
	}

	/** La directa de un paso: el plan (directa en plan_pension) y el feriado; la directa no se marca después. */
	@Test
	void planYFeriadoDirectosPasan() {
		Nuevo n = colegioNuevo("20600000170", "g", "987677001");
		ingresar(n.titular());
		Long anio = estructura.crearAnio(new CrearAnioEscolarRequest(2027, LocalDate.of(2027, 3, 1),
				LocalDate.of(2027, 12, 17), false));
		Long plan = como(n.titular(), () -> {
			Long id = planes.crearBorrador(anio, Nivel.PRIMARIA, EscenarioCobranza.plan(2027, "450", "350", null));
			planes.enviar(id);
			planes.aprobar(id, planes.obtener(id).version());
			return id;
		});
		assertThat(jdbc.queryForMap("SELECT estado, directa, creado_por, aprobado_por FROM plan_pension WHERE id = ?", plan))
				.containsEntry("estado", "APROBADO").containsEntry("directa", true)
				.containsEntry("aprobado_por", n.titular().getNombreUsuario());
		assertThat(codigoAl(() -> jdbc.update("UPDATE plan_pension SET directa = NULL WHERE id = ?", plan))).isEqualTo(1644);

		LocalDate dia = LocalDate.now(Clock.system(ConfiguracionTiempo.ZONA_LIMA)).plusDays(10);
		while (dia.getDayOfWeek() == java.time.DayOfWeek.SUNDAY
				|| pe.edu.virgenmaria.cuentasclaras.comun.fecha.FeriadosNacionales.es(dia)) {
			dia = dia.plusDays(1);
		}
		LocalDate fecha = dia;
		como(n.titular(), () -> feriados.registrar(new FeriadoRequest(fecha, "Aniversario del colegio")));
		assertThat(jdbc.queryForMap("SELECT pendiente, aprobado_por FROM feriado WHERE colegio_id = ? AND fecha = ?",
				n.colegio(), fecha)).containsEntry("pendiente", false)
				.containsEntry("aprobado_por", n.titular().getNombreUsuario());
	}

	private static int codigoAl(Runnable sentencia) {
		try {
			sentencia.run();
			return 0;
		}
		catch (DataAccessException e) {
			Throwable causa = e;
			while (causa != null && !(causa instanceof SQLException)) {
				causa = causa.getCause();
			}
			return causa instanceof SQLException sql ? sql.getErrorCode() : -1;
		}
	}
}
