package pe.edu.virgenmaria.cuentasclaras.plataforma;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.ConteoRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.ReconteoRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCierreCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.CrearAnioEscolarRequest;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.CrearSeccionRequest;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.cripto.DerivadorSecreto;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPuestaEnMarcha;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.TokenDeSesionDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.plataforma.dto.ColegioCreado;
import pe.edu.virgenmaria.cuentasclaras.plataforma.service.AltaColegio;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service.PasoAProduccion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.CrearUsuarioRequest;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.FacultadTitular;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioUsuarios;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 11, tanda 1 (sección 3.15 del diseño, decisión 211): un colegio nuevo, del alta al cobro, con su equipo armado por
 * el titular y su facultad. El operador da de alta el colegio (sin año escolar); el titular activa su cuenta, crea el año
 * con su firma y, con su facultad (y su código), crea la cuenta de Dirección; después crea Administración y Caja.
 * Administración crea la sección, registra un alumno y propone el plan de pensiones; el titular NO puede aprobarlo (preparó
 * la cuenta de Administración: A5) y Dirección SÍ (la regla no es transitiva). El colegio pasa a producción, Caja cobra en
 * efectivo y cierra con faltante; el titular no aprueba ese cierre (creó la cuenta de la cajera) y Dirección sí.
 */
@PruebaIntegracion
class AltaACobroTest {

	@Autowired
	private AltaColegio alta;

	@Autowired
	private FacultadTitular facultad;

	@Autowired
	private ServicioUsuarios servicioUsuarios;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private PasoAProduccion paso;

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private ServicioCierreCaja cierre;

	@Autowired
	private BandejaAprobaciones bandeja;

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

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		tokens.olvidar();
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		tokens.olvidar();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void delAltaAlCobroConElEquipoQueArmaElTitular() {
		// 1. El operador da de alta el colegio (sin año escolar) y registra el correo externo de control.
		UsuariosDePrueba.iniciarSesion(EscenarioPlataforma.operador(usuarios, codificador, colegios, "operador.alta"));
		ColegioCreado creado = alta.crear(EscenarioPlataforma.alta("Colegio Kepler", EscenarioPlataforma.RUC_A,
				"titular.kepler", "45678921", "987650001"), null, null);
		long colegio = creado.colegioId();
		assertThat(creado.completo()).as(creado.pendiente()).isTrue();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM anio_escolar WHERE colegio_id = ?", Long.class, colegio))
				.isZero();
		alta.registrarCorreoControl(colegio, "contador.kepler@estudio.pe");
		SecurityContextHolder.clearContext();

		// 2. El titular activa su cuenta (su sesión), crea el año con su firma y, con su facultad, la cuenta de Dirección.
		Usuario titular = activar(colegio, "titular.kepler");
		ingresar(titular);
		Long anio = estructura.crearAnio(new CrearAnioEscolarRequest(2027, LocalDate.of(2027, 3, 1),
				LocalDate.of(2027, 12, 17), false));
		assertThat(jdbc.queryForList("SELECT clave FROM firma_operacion WHERE colegio_id = ?", String.class, colegio))
				.contains("anio_escolar:2027:CREADO");

		ingresar(titular);
		FacultadTitular.CodigoPedido pedido = facultad.pedirCodigo();
		assertThat(jdbc.queryForObject("SELECT tipo FROM mensaje WHERE id = ?", String.class, pedido.mensajeId()))
				.isEqualTo("CODIGO_FACULTAD");
		// El mensaje no guarda el código.
		String codigo = derivador.codigoFacultad(colegio, pedido.clave());
		assertThat(jdbc.queryForObject("SELECT parametros FROM mensaje WHERE id = ?", String.class, pedido.mensajeId()))
				.doesNotContain(codigo);
		// Con un código equivocado, no.
		assertThatThrownBy(() -> facultad.crearCuentaDirectiva(direccion(), otro(codigo), pedido))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no coincide");
		ingresar(titular);
		facultad.crearCuentaDirectiva(direccion(), codigo, pedido);
		Usuario direccion = activar(colegio, "testdireccion");
		assertThat(direccion.getRoles()).containsExactly(Rol.DIRECTOR);
		assertThat(direccion.getCreadoPor()).isEqualTo("titular.kepler");
		assertThat(jdbc.queryForMap("SELECT numero, rol, accion, cuenta_nueva, titular FROM uso_facultad_titular "
				+ "WHERE colegio_id = ?", colegio)).containsEntry("rol", "DIRECTOR").containsEntry("accion", "DAR")
				.containsEntry("titular", "titular.kepler");
		assertThat(jdbc.queryForList("SELECT accion FROM evento_auditoria WHERE colegio_id = ?", String.class, colegio))
				.contains("FACULTAD_TITULAR_USADA", "CODIGO_FACULTAD_PEDIDO");
		// El aviso al correo de control y a la cuenta que recibió el rol, en la misma transacción.
		assertThat(jdbc.queryForList("SELECT destino FROM mensaje WHERE colegio_id = ? AND plantilla = "
				+ "'FACULTAD_TITULAR_USADA'", String.class, colegio)).contains("contador.kepler@estudio.pe");
		// El código no se usa dos veces.
		ingresar(titular);
		assertThatThrownBy(() -> facultad.crearCuentaDirectiva(new CrearUsuarioRequest("Otra Dirección", "otradireccion",
				null, "987650099", Set.of(Rol.DIRECTOR)), codigo, pedido)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("ya se usó");

		// Después, Administración y Caja (sin facultad: no son roles directivos).
		ingresar(titular);
		servicioUsuarios.crear(new CrearUsuarioRequest("Ana Admin", "testadmin", null, "987650003",
				Set.of(Rol.ADMINISTRACION)));
		ingresar(titular);
		servicioUsuarios.crear(new CrearUsuarioRequest("Carla Caja", "testcaja", null, "987650004", Set.of(Rol.CAJA)));
		Usuario admin = activar(colegio, "testadmin");
		Usuario caja = activar(colegio, "testcaja");
		assertThat(admin.getCreadoPor()).isEqualTo("titular.kepler");

		// 3. Administración crea la sección, registra un alumno y propone el plan.
		ingresar(admin);
		Long seccion = estructura.crearSeccion(anio, new CrearSeccionRequest(Grado.PRIMARIA_6, "A"));
		ingresar(admin);
		estructura.crearSeccion(anio, new CrearSeccionRequest(Grado.SECUNDARIA_1, "A"));
		ingresar(admin);
		var mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(seccion));
		ingresar(admin);
		Long plan = planes.crearBorrador(anio, Nivel.PRIMARIA, EscenarioCobranza.plan(2027, "450", "350", null));
		ingresar(admin);
		planes.enviar(plan);
		// El colegio ofrece también Secundaria: su plan lo propone Administración y lo aprueba Dirección.
		ingresar(admin);
		Long planSecundaria = planes.crearBorrador(anio, Nivel.SECUNDARIA, EscenarioCobranza.plan(2027, "500", "350", null));
		ingresar(admin);
		planes.enviar(planSecundaria);

		// 4. El titular no lo aprueba (preparó la cuenta de Administración: A5).
		ingresar(titular);
		assertThatThrownBy(() -> planes.aprobar(plan, planes.obtener(plan).version()))
				.hasMessageContaining("participaste");
		// 5. Dirección sí (la regla no es transitiva): el cronograma se genera.
		ingresar(direccion);
		planes.aprobar(plan, planes.obtener(plan).version());
		ingresar(direccion);
		planes.aprobar(planSecundaria, planes.obtener(planSecundaria).version());
		assertThat(jdbc.queryForObject("SELECT estado FROM plan_pension WHERE id = ?", String.class, plan))
				.isEqualTo("APROBADO");
		Long cuota = EscenarioCaja.cuota(jdbc, mateo.alumnoId(), "PEN-2027-03");

		// 6. El resto de la lista (lo que en H2 no depende de una persona) y el paso a producción por el titular.
		EscenarioPuestaEnMarcha.cuentaBancariaVigente(jdbc, colegio, "titular.kepler", "testdireccion");
		EscenarioPuestaEnMarcha.respaldoAlDia(jdbc);
		jdbc.update("UPDATE colegio SET aviso_privacidad_version = '2026-1', aviso_privacidad_aprobado_por = 'testdireccion', "
				+ "aviso_privacidad_aprobado_en = CURRENT_TIMESTAMP WHERE id = ?", colegio);
		jdbc.update("UPDATE mensaje SET estado = 'ENVIADO', proveedor = 'SIMULADO', proveedor_mensaje_id = 'SIM-alta-cobro', "
				+ "enviado_en = CURRENT_TIMESTAMP, intentos = 1 WHERE id = ?", pedido.mensajeId());
		EscenarioEscolar.contactosConfirmados(jdbc);
		ingresar(titular);
		paso.sinSaldoInicial();
		ingresar(titular);
		paso.aceptarOseSimulado();
		ingresar(titular);
		paso.pasar();
		assertThat(jdbc.queryForObject("SELECT estado FROM colegio WHERE id = ?", String.class, colegio))
				.isEqualTo("PRODUCCION");

		// 7 y 8. Caja cobra en efectivo: comprobante y pago.
		ingresar(caja);
		cobro.cobrar(EscenarioCaja.efectivo(mateo.familiaId(), List.of(cuota), "450.00", "500.00"));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pago WHERE colegio_id = ?", Long.class, colegio)).isEqualTo(1L);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comprobante WHERE colegio_id = ?", Long.class, colegio))
				.isEqualTo(1L);

		// 9. Caja cierra a ciegas, con faltante (pide aprobación).
		ingresar(caja);
		cierre.contar(new ConteoRequest(new BigDecimal("400.00"), null));
		ingresar(caja);
		cierre.recontar(new ReconteoRequest(new BigDecimal("400.00"), null, "Volví a contar dos veces y faltan cincuenta"));
		Long cierreId = jdbc.queryForObject("SELECT id FROM cierre_caja WHERE colegio_id = ?", Long.class, colegio);
		Long solicitud = ContextoColegio.en(colegio, () -> EscenarioAprobaciones.pendiente(jdbc, "cierre_caja", cierreId));

		// 10. El titular no aprueba el cierre de la cajera que creó; Dirección sí.
		ingresar(titular);
		assertThatThrownBy(() -> bandeja.aprobar(solicitud, "Conversé con la cajera: repuso el faltante"))
				.hasMessageContaining("cuenta que creaste");
		ingresar(direccion);
		bandeja.aprobar(solicitud, "Conversé con la cajera: repuso el faltante");
		assertThat(jdbc.queryForObject("SELECT revisado_por FROM cierre_caja WHERE id = ?", String.class, cierreId))
				.isEqualTo("testdireccion");
	}

	/** La persona elige su clave (como con su enlace de activación): desde aquí su sesión tiene sus roles. */
	private Usuario activar(long colegio, String nombre) {
		return ContextoColegio.en(colegio, () -> new TransactionTemplate(transacciones).execute(t -> {
			Usuario u = usuarios.findByNombreUsuario(nombre).orElseThrow();
			u.cambiarClave(codificador.encode(UsuariosDePrueba.CLAVE), java.time.LocalDateTime.now(java.time.Clock.system(pe.edu.virgenmaria.cuentasclaras.comun.config.ConfiguracionTiempo.ZONA_LIMA)), false);
			return usuarios.saveAndFlush(u);
		}));
	}

	/** Una cuenta con sesión abierta en la base (en H2 la abre la firma de prueba): la cuenta queda activada. */
	private void ingresar(Usuario usuario) {
		UsuariosDePrueba.iniciarSesion(usuario);
		new TransactionTemplate(transacciones).executeWithoutResult(t -> tokens.actual());
	}

	private static CrearUsuarioRequest direccion() {
		return new CrearUsuarioRequest("Diana Dirección", "testdireccion", null, "987650002", EnumSet.of(Rol.DIRECTOR));
	}

	private static String otro(String codigo) {
		return codigo.equals("000000") ? "111111" : "000000";
	}
}
