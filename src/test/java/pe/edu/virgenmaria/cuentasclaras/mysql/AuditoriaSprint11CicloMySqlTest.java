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
import pe.edu.virgenmaria.cuentasclaras.comun.basedatos.FuenteDatosEnrutada;
import pe.edu.virgenmaria.cuentasclaras.comun.cripto.DerivadorSecreto;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.CuentasDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.TokenDeSesionDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.plataforma.dto.ColegioCreado;
import pe.edu.virgenmaria.cuentasclaras.plataforma.service.AltaColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.CrearUsuarioRequest;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.FacultadTitular;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioUsuarios;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 11, tanda 1 (secciones 3.12, 3.15 y 7 del diseño) contra MySQL 8 real, con los permisos mínimos de 02 y los
 * triggers de 03: el colegio nuevo, del alta al cobro, con el equipo que arma el titular con su facultad (con su firma y su
 * código: trg_uso_facultad_titular_nace y trg_usuario_rol_alta), el año escolar creado por el titular con su firma
 * (trg_anio_escolar_nace), el plan que aprueba Dirección y el cierre que aprueba Dirección; y lo que la base rechaza.
 * Corre después de la fase 2 del job mysql ({@code CC_PRUEBA_MYSQL=true}). Usa nombres únicos y no limpia.
 */
@SpringBootTest
@ActiveProfiles({ "test", "mysql" })
@EnabledIfEnvironmentVariable(named = "CC_PRUEBA_MYSQL", matches = "true")
class AuditoriaSprint11CicloMySqlTest {

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
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private ServicioCierreCaja cierre;

	@Autowired
	private BandejaAprobaciones bandeja;

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

	/** El colegio nuevo y su titular, con el correo de control registrado y su cuenta activada. */
	private record Nuevo(long colegio, Usuario titular) {
	}

	private Nuevo colegioNuevo(String ruc, String dni, String celular) {
		UsuariosDePrueba.iniciarSesion(EscenarioPlataforma.operador(usuarios, codificador, colegios, "operador.s11"));
		ColegioCreado creado = alta.crear(EscenarioPlataforma.alta("Colegio S11 " + sufijo + " " + celular, ruc,
				"titular." + sufijo + "." + celular, dni, celular), null, null);
		assertThat(creado.completo()).as(creado.pendiente()).isTrue();
		alta.registrarCorreoControl(creado.colegioId(), "contador." + sufijo + "." + celular + "@estudio.pe");
		SecurityContextHolder.clearContext();
		Usuario titular = ContextoColegio.en(creado.colegioId(), () -> usuarios.findByNombreUsuario(creado.titularUsuario()))
				.orElseThrow();
		// El titular «activa» su cuenta: elige su clave por la ruta de identidad y abre su sesión.
		pe.edu.virgenmaria.cuentasclaras.comun.basedatos.RutaConexion.identidad(() -> ContextoColegio.en(creado.colegioId(),
				() -> {
					Usuario t = usuarios.findById(titular.getId()).orElseThrow();
					t.cambiarClave(codificador.encode(UsuariosDePrueba.CLAVE), java.time.LocalDateTime.now(java.time.Clock
							.system(pe.edu.virgenmaria.cuentasclaras.comun.config.ConfiguracionTiempo.ZONA_LIMA)), false);
					return usuarios.saveAndFlush(t);
				}));
		Usuario activado = ContextoColegio.en(creado.colegioId(), () -> usuarios.findById(titular.getId())).orElseThrow();
		ingresar(activado);
		return new Nuevo(creado.colegioId(), activado);
	}

	private void ingresar(Usuario usuario) {
		UsuariosDePrueba.iniciarSesion(usuario);
		new TransactionTemplate(transacciones).executeWithoutResult(t -> tokens.actual());
	}

	private Usuario cuenta(long colegio, String nombre) {
		Usuario u = ContextoColegio.en(colegio, () -> usuarios.findByNombreUsuario(nombre)).orElseThrow();
		// Activa su cuenta (su clave), como el enlace.
		pe.edu.virgenmaria.cuentasclaras.comun.basedatos.RutaConexion.identidad(() -> ContextoColegio.en(colegio, () -> {
			Usuario x = usuarios.findById(u.getId()).orElseThrow();
			x.cambiarClave(codificador.encode(UsuariosDePrueba.CLAVE), java.time.LocalDateTime.now(java.time.Clock.system(
					pe.edu.virgenmaria.cuentasclaras.comun.config.ConfiguracionTiempo.ZONA_LIMA)), false);
			return usuarios.saveAndFlush(x);
		}));
		return ContextoColegio.en(colegio, () -> usuarios.findById(u.getId())).orElseThrow();
	}

	private JdbcTemplate sistema() {
		return new JdbcTemplate(((FuenteDatosEnrutada) fuenteDatos).sistema());
	}

	/**
	 * Sección 3.15: del alta al cobro con los permisos mínimos. El titular arma su equipo con su facultad, Administración
	 * propone el plan, el titular no lo aprueba (A5) y Dirección sí; Caja cobra y cierra; Dirección aprueba el cierre.
	 */
	@Test
	void delAltaAlCobroConElEquipoQueArmaElTitular() {
		Nuevo n = colegioNuevo(EscenarioPlataforma.RUC_B, "4" + String.format("%07d", Math.floorMod(sufijo.hashCode(),
				10_000_000)), "987661001");
		long colegio = n.colegio();
		Usuario titular = n.titular();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM anio_escolar WHERE colegio_id = ?", Long.class, colegio))
				.isZero();

		// El año, con la firma del titular (trg_anio_escolar_nace).
		Long anio = estructura.crearAnio(new CrearAnioEscolarRequest(2027, LocalDate.of(2027, 3, 1),
				LocalDate.of(2027, 12, 17), false));
		assertThat(jdbc.queryForObject("SELECT creado_por FROM anio_escolar WHERE id = ?", String.class, anio))
				.isEqualTo(titular.getNombreUsuario());

		// Dirección, con la facultad y el código (trg_uso_facultad_titular_nace y trg_usuario_rol_alta).
		ingresar(titular);
		FacultadTitular.CodigoPedido pedido = facultad.pedirCodigo();
		String codigo = derivador.codigoFacultad(colegio, pedido.clave());
		ingresar(titular);
		facultad.crearCuentaDirectiva(new CrearUsuarioRequest("Diana Dirección", "dir." + sufijo, null, "987661002",
				EnumSet.of(Rol.DIRECTOR)), codigo, pedido);
		assertThat(jdbc.queryForObject("SELECT r.rol FROM usuario u JOIN usuario_rol r ON r.usuario_id = u.id "
				+ "WHERE u.nombre_usuario = ?", String.class, "dir." + sufijo)).isEqualTo("DIRECTOR");
		assertThat(jdbc.queryForObject("SELECT creado_por FROM usuario WHERE nombre_usuario = ?", String.class,
				"dir." + sufijo)).isEqualTo(titular.getNombreUsuario());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM uso_facultad_titular WHERE colegio_id = ?", Long.class, colegio))
				.isEqualTo(1L);

		ingresar(titular);
		servicioUsuarios.crear(new CrearUsuarioRequest("Ana Admin", "adm." + sufijo, null, "987661003",
				Set.of(Rol.ADMINISTRACION)));
		ingresar(titular);
		servicioUsuarios.crear(new CrearUsuarioRequest("Carla Caja", "caja." + sufijo, null, "987661004", Set.of(Rol.CAJA)));
		Usuario direccion = cuenta(colegio, "dir." + sufijo);
		Usuario admin = cuenta(colegio, "adm." + sufijo);
		Usuario caja = cuenta(colegio, "caja." + sufijo);

		// Administración: sección, alumno y plan.
		ingresar(admin);
		Long seccion = estructura.crearSeccion(anio, new CrearSeccionRequest(Grado.PRIMARIA_6, "A"));
		ingresar(admin);
		estructura.crearSeccion(anio, new CrearSeccionRequest(Grado.SECUNDARIA_1, "A"));
		ingresar(admin);
		String dni = String.format("7%07d", Math.floorMod(("a" + sufijo).hashCode(), 10_000_000));
		String dniApoderado = String.format("4%07d", Math.floorMod(("b" + sufijo).hashCode(), 10_000_000));
		var alumno = alumnos.registrar(EscenarioEscolar.conApoderadoNuevo(dni, "Kepler", "Ruiz", "Mateo",
				LocalDate.of(2015, 6, 14), dniApoderado, "Ruiz", "Soto", "Rosa", "98766" + String.format("%04d",
						Math.floorMod(sufijo.hashCode(), 10_000)), null, seccion));
		ingresar(admin);
		Long plan = planes.crearBorrador(anio, Nivel.PRIMARIA, EscenarioCobranza.plan(2027, "450", "350", null));
		ingresar(admin);
		planes.enviar(plan);
		// El colegio ofrece también Secundaria: su plan lo propone Administración y lo aprueba Dirección.
		ingresar(admin);
		Long planSecundaria = planes.crearBorrador(anio, Nivel.SECUNDARIA, EscenarioCobranza.plan(2027, "500", "350", null));
		ingresar(admin);
		planes.enviar(planSecundaria);

		// El titular no aprueba (preparó la cuenta de Administración); Dirección sí.
		ingresar(titular);
		assertThatThrownBy(() -> planes.aprobar(plan, planes.obtener(plan).version())).hasMessageContaining("participaste");
		ingresar(direccion);
		planes.aprobar(plan, planes.obtener(plan).version());
		ingresar(direccion);
		planes.aprobar(planSecundaria, planes.obtener(planSecundaria).version());
		assertThat(jdbc.queryForObject("SELECT estado FROM plan_pension WHERE id = ?", String.class, plan))
				.isEqualTo("APROBADO");
		Long cuota = EscenarioCaja.cuota(jdbc, alumno.alumnoId(), "PEN-2027-03");

		// A producción con la firma del titular (trg_colegio_cambio: también exige el año).
		cuentasDePrueba.pasarAProduccion(colegio, titular);
		assertThat(jdbc.queryForObject("SELECT estado FROM colegio WHERE id = ?", String.class, colegio))
				.isEqualTo("PRODUCCION");

		// Caja cobra y cierra con faltante.
		ingresar(caja);
		cobro.cobrar(EscenarioCaja.efectivo(alumno.familiaId(), List.of(cuota), "450.00", "500.00"));
		ingresar(caja);
		cierre.contar(new ConteoRequest(new BigDecimal("400.00"), null));
		ingresar(caja);
		cierre.recontar(new ReconteoRequest(new BigDecimal("400.00"), null, "Volví a contar dos veces y faltan cincuenta"));
		Long cierreId = jdbc.queryForObject("SELECT id FROM cierre_caja WHERE colegio_id = ?", Long.class, colegio);
		Long solicitud = ContextoColegio.en(colegio, () -> EscenarioAprobaciones.pendiente(jdbc, "cierre_caja", cierreId));

		// El titular no aprueba el cierre de la cajera que creó; Dirección sí.
		ingresar(titular);
		assertThatThrownBy(() -> bandeja.aprobar(solicitud, "Conversé con la cajera: repuso el faltante"))
				.hasMessageContaining("cuenta que creaste");
		ingresar(direccion);
		bandeja.aprobar(solicitud, "Conversé con la cajera: repuso el faltante");
		assertThat(jdbc.queryForObject("SELECT revisado_por FROM cierre_caja WHERE id = ?", String.class, cierreId))
				.isEqualTo("dir." + sufijo);
	}

	/**
	 * Lo que la base rechaza de la facultad: un uso por quien no es el titular (F13), sin código, con un código ya usado
	 * (UNIQUE), con el número saltado, sobre sí mismo (CHECK), cc_app no lo escribe ni lo edita (1142), un DIRECTOR sin su uso
	 * (trg_usuario_rol_alta) y la Promotoría del titular no se quita ni su cuenta se desactiva.
	 */
	@Test
	void laBaseRechazaLaFacultadFueraDeSuCamino() {
		Nuevo n = colegioNuevo(EscenarioPlataforma.RUC_C, "4" + String.format("%07d", Math.floorMod(("c" + sufijo).hashCode(),
				10_000_000)), "987662001");
		long colegio = n.colegio();
		Usuario titular = n.titular();
		FacultadTitular.CodigoPedido pedido = facultad.pedirCodigo();
		ingresar(titular);
		servicioUsuarios.crear(new CrearUsuarioRequest("Pedro Docente", "doc." + sufijo, null, "987662002",
				Set.of(Rol.DOCENTE)));
		Long docente = jdbc.queryForObject("SELECT id FROM usuario WHERE nombre_usuario = ?", Long.class, "doc." + sufijo);
		String insertar = "INSERT INTO uso_facultad_titular (colegio_id, numero, titular_id, titular, usuario_id, rol, accion, "
				+ "cuenta_nueva, mensaje_id, creado_en, creado_por, actualizado_en) VALUES (?, ?, ?, ?, ?, 'DIRECTOR', 'DAR', "
				+ "FALSE, ?, NOW(6), ?, NOW(6))";
		// F13: a nombre de otra cuenta (no es la del titular).
		assertThat(codigoAl(() -> sistema().update(insertar, colegio, 1, docente, "doc." + sufijo, titular.getId(),
				pedido.mensajeId(), "doc." + sufijo))).isEqualTo(1644);
		// Sin la firma del titular (y con el número saltado).
		assertThat(codigoAl(() -> sistema().update(insertar, colegio, 2, titular.getId(), titular.getNombreUsuario(),
				docente, pedido.mensajeId(), titular.getNombreUsuario()))).isEqualTo(1644);
		// F14: sobre sí mismo (el trigger o el CHECK).
		assertThat(codigoAl(() -> sistema().update(insertar, colegio, 1, titular.getId(), titular.getNombreUsuario(),
				titular.getId(), pedido.mensajeId(), titular.getNombreUsuario()))).isIn(1644, 3819);
		// cc_app no lo escribe, no lo edita ni lo borra.
		assertThat(codigoAl(() -> jdbc.update(insertar, colegio, 1, titular.getId(), titular.getNombreUsuario(), docente,
				pedido.mensajeId(), titular.getNombreUsuario()))).isEqualTo(1142);
		assertThat(codigoAl(() -> jdbc.update("UPDATE uso_facultad_titular SET rol = rol WHERE 1 = 0"))).isEqualTo(1142);
		assertThat(codigoAl(() -> jdbc.update("DELETE FROM uso_facultad_titular WHERE 1 = 0"))).isEqualTo(1142);
		// Un DIRECTOR sin su uso de la facultad ni su CAMBIO_ROLES.
		assertThat(codigoAl(() -> sistema().update("INSERT INTO usuario_rol (usuario_id, rol) VALUES (?, 'DIRECTOR')",
				docente))).isEqualTo(1644);
		// La Promotoría del titular no se quita y su cuenta no se desactiva.
		assertThat(codigoAl(() -> sistema().update("DELETE FROM usuario_rol WHERE usuario_id = ? AND rol = 'PROMOTOR'",
				titular.getId()))).isEqualTo(1644);
		assertThat(codigoAl(() -> sistema().update("UPDATE usuario SET activo = FALSE WHERE id = ?", titular.getId())))
				.isEqualTo(1644);

		// Con la aplicación: el uso legítimo pasa y su código no se reusa (UNIQUE de mensaje_id).
		ingresar(titular);
		facultad.dar(docente, Rol.DIRECTOR, derivador.codigoFacultad(colegio, pedido.clave()), pedido);
		assertThat(jdbc.queryForList("SELECT rol FROM usuario_rol WHERE usuario_id = ?", String.class, docente))
				.contains("DIRECTOR", "DOCENTE");
		ingresar(titular);
		Long uso = jdbc.queryForObject("SELECT id FROM uso_facultad_titular WHERE colegio_id = ?", Long.class, colegio);
		assertThat(codigoAl(() -> sistema().update("INSERT INTO uso_facultad_titular (colegio_id, numero, titular_id, "
				+ "titular, usuario_id, rol, accion, cuenta_nueva, mensaje_id, creado_en, creado_por, actualizado_en) SELECT "
				+ "colegio_id, numero + 1, titular_id, titular, usuario_id, 'PROMOTOR', accion, cuenta_nueva, mensaje_id, "
				+ "NOW(6), creado_por, NOW(6) FROM uso_facultad_titular WHERE id = ?", uso))).isIn(1062, 1644);
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
