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
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.basedatos.FuenteDatosEnrutada;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.plataforma.dto.ColegioCreado;
import pe.edu.virgenmaria.cuentasclaras.plataforma.dto.ContactoTitularRequest;
import pe.edu.virgenmaria.cuentasclaras.plataforma.service.AltaColegio;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service.PasoAProduccion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.FirmaSesion;

import java.sql.SQLException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 8, tandas 1 y 2 (sección 12, filas que dicen «MySQL») contra MySQL 8 real, con los permisos mínimos de 02 y los
 * triggers de 03: el alta de un colegio por el operador funciona de punta a punta, y con la clave de cc_app o de
 * cc_sistema no se crea un colegio, una primera Promotoría ni un operador por fuera, no se cobra en un colegio en
 * preparación, no se pasa a producción sin la firma de la Promotoría y Dirección y Caja, no se vuelve atrás ni se cambia
 * el RUC, y dos sedes con el mismo RUC no comparten serie. Tanda 2 (E10, E12 y E14): a producción no se pasa sin la lista
 * completa; un conector nace propuesto con la firma de quien propone, entra en vigencia solo con su CAMBIO_CONECTOR aprobada
 * y firmada por otra persona (con una Promotoría de por medio si mueve dinero) y no se edita ni se borra; el flujo completo
 * (proponer, probar, aprobar, reemplazar y rechazar) funciona con los permisos mínimos. Corre después de la fase 2 del job mysql
 * ({@code CC_PRUEBA_MYSQL=true}). Tanda 3 (E25): «faltan filas» por colegio con las claves de cc_respaldo y cc_app. Usa
 * nombres únicos y no limpia.
 */
@SpringBootTest
@ActiveProfiles({ "test", "mysql" })
@EnabledIfEnvironmentVariable(named = "CC_PRUEBA_MYSQL", matches = "true")
class AuditoriaSprint8MySqlTest {

	private final String sufijo = Long.toString(System.nanoTime(), 36);

	@Autowired
	private AltaColegio alta;

	@Autowired
	private PasoAProduccion paso;

	@Autowired
	private FirmaSesion firma;

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioUsuarios servicioUsuarios;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private ColegioRepository colegios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private javax.sql.DataSource fuenteDatos;

	@Autowired
	private PlatformTransactionManager transacciones;

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.conectores.service.PropuestaConector propuesta;

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones bandeja;

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.comun.prueba.CuentasDePrueba cuentasDePrueba;

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.comun.prueba.TokenDeSesionDePrueba tokens;

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
	}

	private JdbcTemplate sistema() {
		FuenteDatosEnrutada enrutada = (FuenteDatosEnrutada) fuenteDatos;
		assertThat(enrutada.separadas()).isTrue();
		return new JdbcTemplate(enrutada.sistema());
	}

	private Usuario operador() {
		return EscenarioPlataforma.operador(usuarios, codificador, colegios, "operador.s8");
	}

	private long plataforma() {
		return EscenarioPlataforma.plataformaId(colegios);
	}

	/** Un colegio dado de alta por el operador (con las cuatro transacciones del alta), en preparación. */
	private ColegioCreado colegioNuevo(String ruc, String celular) {
		UsuariosDePrueba.iniciarSesion(operador());
		ColegioCreado creado = alta.crear(EscenarioPlataforma.alta("Colegio S8 " + sufijo, ruc, "titular." + sufijo,
				"4" + String.format("%07d", Math.floorMod(sufijo.hashCode(), 10_000_000)), celular), null, null);
		assertThat(creado.completo()).as(creado.pendiente()).isTrue();
		return creado;
	}

	/** El alta de punta a punta con los permisos mínimos: colegio, firma, titular, enlace, año y bitácora. */
	@Test
	void elAltaFuncionaConLosPermisosMinimosYDejaSuRastro() {
		ColegioCreado creado = colegioNuevo(EscenarioPlataforma.RUC_C, "987651001");

		assertThat(jdbc.queryForObject("SELECT estado FROM colegio WHERE id = ?", String.class, creado.colegioId()))
				.isEqualTo("PREPARACION");
		assertThat(jdbc.queryForObject("SELECT r.rol FROM usuario u JOIN usuario_rol r ON r.usuario_id = u.id "
				+ "WHERE u.colegio_id = ?", String.class, creado.colegioId())).isEqualTo("PROMOTOR");
		assertThat(jdbc.queryForObject("SELECT creado_por FROM primera_promotoria WHERE colegio_id = ?", String.class,
				creado.colegioId())).isEqualTo("operador.s8");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM anio_escolar WHERE colegio_id = ?", Long.class,
				creado.colegioId())).isEqualTo(1L);
		assertThat(jdbc.queryForList("SELECT accion FROM evento_auditoria WHERE colegio_id = ?", String.class,
				creado.colegioId())).contains("PRIMERA_PROMOTORIA_CREADA", "COLEGIO_CREADO");

		// Correcciones del sprint 8 (S8-A1): el contacto del titular lo propone un operador y lo aplica OTRO, con aviso al
		// celular anterior y al correo institucional (trg_cambio_contacto_titular_*, trg_usuario_contacto, trg_mensaje_nace).
		Long cambio = alta.proponerContactoTitular(creado.colegioId(), new ContactoTitularRequest("987651002", null,
				"El titular corrigió su celular por videollamada"));
		UsuariosDePrueba.iniciarSesion(EscenarioPlataforma.operador(usuarios, codificador, colegios, "operador.s8b"));
		alta.aplicarContactoTitular(creado.colegioId(), cambio);
		UsuariosDePrueba.iniciarSesion(operador());
		assertThat(jdbc.queryForObject("SELECT telefono_whatsapp FROM usuario WHERE colegio_id = ?", String.class,
				creado.colegioId())).isEqualTo("+51987651002");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mensaje WHERE colegio_id = ? AND tipo = 'AVISO_ALTA' "
				+ "AND destino IN ('+51987651001', ?)", Long.class, creado.colegioId(), jdbc.queryForObject(
						"SELECT correo_institucional FROM colegio WHERE id = ?", String.class, creado.colegioId())))
				.isEqualTo(2L);
		// Sin la firma, cc_sistema no cambia ese contacto (1644).
		assertThat(codigoAl(() -> sistema().update("UPDATE usuario SET telefono_whatsapp = '+51987651003' WHERE colegio_id = ?",
				creado.colegioId()))).isEqualTo(1644);

		// El correo externo de control, con la firma del operador.
		alta.registrarCorreoControl(creado.colegioId(), "contador." + sufijo + "@estudio.pe");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM configuracion_colegio WHERE colegio_id = ?", Long.class,
				creado.colegioId())).isEqualTo(2L);
		alta.registrarCorreoControl(creado.colegioId(), "contador2." + sufijo + "@estudio.pe");
	}

	/** E5: con la clave de cc_app no se crea un colegio, una primera Promotoría ni el correo de control. */
	@Test
	void e5CcAppNoCreaColegiosNiPromotorias() {
		assertThat(codigoAl(() -> jdbc.update("INSERT INTO colegio (nombre, tipo, estado, activo, creado_por, actualizado_en) "
				+ "VALUES ('Intruso', 'COLEGIO', 'PREPARACION', TRUE, 'operador.s8', NOW(6))"))).isEqualTo(1142);
		assertThat(codigoAl(() -> jdbc.update("INSERT INTO primera_promotoria (colegio_id, usuario_id, operador_id, "
				+ "plataforma_id, creado_en, creado_por, actualizado_en) VALUES (1, 1, 1, ?, NOW(6), 'operador.s8', NOW(6))",
				plataforma()))).isEqualTo(1142);
		assertThat(codigoAl(() -> jdbc.update("INSERT INTO configuracion_colegio (colegio_id, clave, valor, creado_en, "
				+ "creado_por) VALUES (1, 'huella_correo_externo', 'yo@x.pe', NOW(6), 'operador.s8')"))).isEqualTo(1142);
		assertThat(codigoAl(() -> jdbc.update("UPDATE colegio SET ruc = ruc WHERE 1 = 0"))).isEqualTo(1143);
		assertThat(codigoAl(() -> jdbc.update("UPDATE colegio SET tipo = tipo WHERE 1 = 0"))).isEqualTo(1143);
		assertThat(codigoAl(() -> jdbc.update("DELETE FROM colegio WHERE 1 = 0"))).isEqualTo(1142);
		assertThat(codigoAl(() -> jdbc.update("UPDATE colegio_logo SET contenido = contenido WHERE 1 = 0"))).isEqualTo(1143);
	}

	/**
	 * E3 y E6: con cc_sistema, el operador solo crea la PRIMERA cuenta de un colegio en preparación, la primera Promotoría
	 * exige su firma y el primer PROMOTOR exige esa fila.
	 */
	@Test
	void e3YE6ElOperadorSoloCreaLaPrimeraCuentaYConSuFirma() {
		ColegioCreado creado = colegioNuevo(EscenarioPlataforma.RUC_D, "987651010");
		// E3: una segunda cuenta creada por el operador en ese colegio (1644).
		assertThat(codigoAl(() -> sistema().update("INSERT INTO usuario (colegio_id, nombre_usuario, nombre_completo, "
				+ "clave_hash, activo, debe_cambiar_clave, intentos_fallidos, creado_en, creado_por, actualizado_en) VALUES "
				+ "(?, ?, 'Otra cuenta', 'x', TRUE, TRUE, 0, NOW(6), 'operador.s8', NOW(6))", creado.colegioId(),
				"otra." + sufijo))).isEqualTo(1644);
		// Una segunda primera Promotoría (UNIQUE o trigger).
		Long titular = jdbc.queryForObject("SELECT id FROM usuario WHERE colegio_id = ?", Long.class, creado.colegioId());
		assertThat(codigoAl(() -> sistema().update("INSERT INTO primera_promotoria (colegio_id, usuario_id, operador_id, "
				+ "plataforma_id, creado_en, creado_por, actualizado_en) VALUES (?, ?, ?, ?, NOW(6), 'operador.s8', NOW(6))",
				creado.colegioId(), titular, operador().getId(), plataforma()))).isIn(1062, 1644);

		// E6: un colegio nuevo por SQL (como el operador) con su primera cuenta, pero sin la firma: no hay primera Promotoría
		// ni primer PROMOTOR.
		JdbcTemplate sistema = sistema();
		sistema.update("INSERT INTO colegio (nombre, tipo, estado, activo, creado_por, actualizado_en) VALUES (?, 'COLEGIO', "
				+ "'PREPARACION', TRUE, 'operador.s8', NOW(6))", "Colegio sin firma " + sufijo);
		Long sinFirma = jdbc.queryForObject("SELECT id FROM colegio WHERE nombre = ?", Long.class, "Colegio sin firma " + sufijo);
		sistema.update("INSERT INTO usuario (colegio_id, nombre_usuario, nombre_completo, clave_hash, activo, "
				+ "debe_cambiar_clave, intentos_fallidos, creado_en, creado_por, actualizado_en) VALUES (?, ?, 'Titular', 'x', "
				+ "TRUE, TRUE, 0, NOW(6), 'operador.s8', NOW(6))", sinFirma, "sinfirma." + sufijo);
		Long cuenta = jdbc.queryForObject("SELECT id FROM usuario WHERE nombre_usuario = ?", Long.class, "sinfirma." + sufijo);
		assertThat(codigoAl(() -> sistema.update("INSERT INTO primera_promotoria (colegio_id, usuario_id, operador_id, "
				+ "plataforma_id, creado_en, creado_por, actualizado_en) VALUES (?, ?, ?, ?, NOW(6), 'operador.s8', NOW(6))",
				sinFirma, cuenta, operador().getId(), plataforma()))).isEqualTo(1644);
		assertThat(codigoAl(() -> sistema.update("INSERT INTO usuario_rol (usuario_id, rol) VALUES (?, 'PROMOTOR')", cuenta)))
				.isEqualTo(1644);
	}

	/**
	 * E29: la firma del operador para la primera Promotoría de un colegio no sirve para la de otro (la clave lleva el
	 * colegio).
	 */
	@Test
	void e29FirmaDelOperadorSoloSirveParaSuColegio() {
		Usuario operador = operador();
		JdbcTemplate sistema = sistema();
		for (String nombre : new String[] { "Colegio firmado " + sufijo, "Colegio ajeno " + sufijo }) {
			sistema.update("INSERT INTO colegio (nombre, tipo, estado, activo, creado_por, actualizado_en) VALUES (?, "
					+ "'COLEGIO', 'PREPARACION', TRUE, 'operador.s8', NOW(6))", nombre);
		}
		Long firmado = jdbc.queryForObject("SELECT id FROM colegio WHERE nombre = ?", Long.class, "Colegio firmado " + sufijo);
		Long ajeno = jdbc.queryForObject("SELECT id FROM colegio WHERE nombre = ?", Long.class, "Colegio ajeno " + sufijo);
		sistema.update("INSERT INTO usuario (colegio_id, nombre_usuario, nombre_completo, clave_hash, activo, "
				+ "debe_cambiar_clave, intentos_fallidos, creado_en, creado_por, actualizado_en) VALUES (?, ?, 'Titular', 'x', "
				+ "TRUE, TRUE, 0, NOW(6), 'operador.s8', NOW(6))", ajeno, "ajeno." + sufijo);
		Long cuenta = jdbc.queryForObject("SELECT id FROM usuario WHERE nombre_usuario = ?", Long.class, "ajeno." + sufijo);
		// El operador firma la primera Promotoría del colegio «firmado» (intento 1)...
		UsuariosDePrueba.iniciarSesion(operador);
		new TransactionTemplate(transacciones).executeWithoutResult(t -> firma.firmar(
				pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma.primeraPromotoria(firmado, 1)));
		SecurityContextHolder.clearContext();
		// ...y esa firma no crea la del colegio «ajeno».
		assertThat(codigoAl(() -> sistema.update("INSERT INTO primera_promotoria (colegio_id, usuario_id, operador_id, "
				+ "plataforma_id, creado_en, creado_por, actualizado_en) VALUES (?, ?, ?, ?, NOW(6), 'operador.s8', NOW(6))",
				ajeno, cuenta, operador.getId(), plataforma()))).isEqualTo(1644);
	}

	/** E7: el rol de operador solo en el colegio de plataforma, solo, y en la plataforma ningún rol de colegio. */
	@Test
	void e7RolDeOperadorSoloEnLaPlataformaYSolo() {
		JdbcTemplate sistema = sistema();
		Usuario docente = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "docente.e7." + sufijo, UsuariosDePrueba.CLAVE,
				false, Rol.DOCENTE);
		assertThat(codigoAl(() -> sistema.update("INSERT INTO usuario_rol (usuario_id, rol) VALUES (?, 'OPERADOR_PLATAFORMA')",
				docente.getId()))).isEqualTo(1644);
		assertThat(codigoAl(() -> sistema.update("INSERT INTO usuario_rol (usuario_id, rol) VALUES (?, 'PROMOTOR')",
				operador().getId()))).isEqualTo(1644);
		assertThat(codigoAl(() -> sistema.update("INSERT INTO usuario (colegio_id, nombre_usuario, nombre_completo, "
				+ "clave_hash, activo, debe_cambiar_clave, intentos_fallidos, creado_en, creado_por, actualizado_en) VALUES "
				+ "(?, ?, 'Intruso', 'x', TRUE, TRUE, 0, NOW(6), 'cualquiera', NOW(6))", plataforma(), "intruso." + sufijo)))
				.isEqualTo(1644);
	}

	/** E9 y E30: en un colegio en preparación no se registra un pago ni sale un recordatorio de cobro. */
	@Test
	void e9YE30SinProduccionNoHayPagosNiRecordatorios() {
		ColegioCreado creado = colegioNuevo(EscenarioPlataforma.RUC_C, "987651020");
		String pago = "INSERT INTO pago (colegio_id, familia_id, caja_diaria_id, cajero, fecha, comprobante_id, medio, total, "
				+ "recibido, vuelto, origen, clave_idempotencia, estado, creado_en, creado_por, actualizado_en) VALUES (?, 0, 0, "
				+ "'caja', CURRENT_DATE, 0, 'EFECTIVO', 1, 1, 0, 'CAJA', ?, 'VIGENTE', NOW(6), 'caja', NOW(6))";
		assertThat(mensajeDe(() -> jdbc.update(pago, creado.colegioId(), "e9-app-" + sufijo)))
				.contains("todavia no esta en produccion");
		assertThat(mensajeDe(() -> sistema().update(pago, creado.colegioId(), "e9-sis-" + sufijo)))
				.contains("todavia no esta en produccion");
		assertThat(mensajeDe(() -> jdbc.update("INSERT INTO mensaje (colegio_id, clave, tipo, canal, destinatario_tipo, "
				+ "destino, plantilla, parametros, estado, creado_en, creado_por, actualizado_en) VALUES (?, ?, "
				+ "'RECORDATORIO_VENCIMIENTO', 'CORREO', 'APODERADO', 'x@y.pe', 'RECORDATORIO', '', 'PENDIENTE', NOW(6), 'x', "
				+ "NOW(6))", creado.colegioId(), "e30-" + sufijo))).contains("sin recordatorios de cobro");
		// Correcciones del sprint 8 (QA-S8-4): ningún mensaje a las familias en preparación (ni un aviso de pago).
		assertThat(mensajeDe(() -> jdbc.update("INSERT INTO mensaje (colegio_id, clave, tipo, canal, destinatario_tipo, "
				+ "destino, plantilla, parametros, estado, creado_en, creado_por, actualizado_en) VALUES (?, ?, "
				+ "'PAGO_REGISTRADO', 'CORREO', 'APODERADO', 'x@y.pe', 'PAGO_REGISTRADO', '', 'PENDIENTE', NOW(6), 'x', "
				+ "NOW(6))", creado.colegioId(), "qa-s8-4-" + sufijo))).contains("mensajes a las familias");
		// S8-M2: tampoco una orden de pago en línea.
		assertThat(mensajeDe(() -> jdbc.update("INSERT INTO orden_pago (colegio_id, referencia, familia_id, apoderado_id, "
				+ "proveedor, monto, moneda, comprobante_tipo, clave_idempotencia, vence_en, estado, creado_en, creado_por, "
				+ "actualizado_en) VALUES (?, ?, 0, 0, 'CULQI', 1, 'PEN', 'BOLETA', ?, NOW(6), 'CREADA', NOW(6), 'x', "
				+ "NOW(6))", creado.colegioId(), "S8M2" + sufijo, "s8-m2-" + sufijo))).contains("sin pagos en linea");
	}

	/**
	 * E10: a producción no lo pasan cc_app sin la firma de la Promotoría ni sin Dirección y Caja, ni el operador (cc_sistema);
	 * la Promotoría con su firma y su equipo, sí.
	 */
	@Test
	void e10ProduccionSoloLaPromotoriaConSuFirma() {
		ColegioCreado creado = colegioNuevo(EscenarioPlataforma.RUC_D, "987651030");
		String titular = jdbc.queryForObject("SELECT nombre_usuario FROM usuario WHERE colegio_id = ?", String.class,
				creado.colegioId());
		String aProduccion = "UPDATE colegio SET estado = 'PRODUCCION', produccion_desde = NOW(6), produccion_por = ? "
				+ "WHERE id = ?";
		assertThat(codigoAl(() -> jdbc.update(aProduccion, titular, creado.colegioId()))).isEqualTo(1644);
		// El operador (cc_sistema) no tiene GRANT del estado: 1143.
		assertThat(codigoAl(() -> sistema().update(aProduccion, titular, creado.colegioId()))).isIn(1143, 1644);

		// Con su cuenta activada, Dirección y Caja: la Promotoría lo pasa con su firma.
		Usuario promotora = activar(creado.colegioId(), titular);
		// Las cuentas siguientes no las crea el operador (trg_usuario_nace): sin su sesión.
		SecurityContextHolder.clearContext();
		UsuariosDePrueba.guardar(usuarios, codificador, creado.colegioId(), "caja." + sufijo, UsuariosDePrueba.CLAVE, false,
				Rol.CAJA);
		Usuario docente = UsuariosDePrueba.guardar(usuarios, codificador, creado.colegioId(), "dir." + sufijo,
				UsuariosDePrueba.CLAVE, false, Rol.DOCENTE);
		// La primera Dirección del colegio (la excepción de arranque, una sola vez: primera_direccion).
		UsuariosDePrueba.iniciarSesion(promotora);
		servicioUsuarios.cambiarRoles(docente.getId(), new pe.edu.virgenmaria.cuentasclaras.seguridad.dto.CambiarRolesRequest(
				java.util.EnumSet.of(Rol.DIRECTOR), "Primera Dirección del colegio nuevo"));
		// Tanda 2: la aplicación exige la lista de verificación completa (aquí faltan pensiones, alumnos, conectores...).
		UsuariosDePrueba.iniciarSesion(promotora);
		org.assertj.core.api.Assertions.assertThatThrownBy(paso::pasar)
				.isInstanceOf(pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException.class)
				.hasMessageContaining("completa la lista de verificación");
		assertThat(jdbc.queryForObject("SELECT estado FROM colegio WHERE id = ?", String.class, creado.colegioId()))
				.isEqualTo("PREPARACION");
		// La base exige su parte (la firma de la Promotoría, Dirección y Caja): con ellas, pasa.
		cuentasDePrueba.pasarAProduccion(creado.colegioId(), promotora);
		assertThat(jdbc.queryForObject("SELECT estado FROM colegio WHERE id = ?", String.class, creado.colegioId()))
				.isEqualTo("PRODUCCION");
	}

	// ------------------------------------------------------------------ tanda 2: conectores (E12 y E14)

	private static pe.edu.virgenmaria.cuentasclaras.conectores.dto.PropuestaConectorRequest recaudacion(String abono) {
		return new pe.edu.virgenmaria.cuentasclaras.conectores.dto.PropuestaConectorRequest(
				pe.edu.virgenmaria.cuentasclaras.conectores.model.ProveedorConector.GENERICO,
				pe.edu.virgenmaria.cuentasclaras.conectores.model.ModoConector.REAL, null, null, null, null, null, null, null,
				null, null, null, null, "SIN_BASE", "RECAUDACION CC", abono, null, null,
				"Recaudación por código de alumno en el banco");
	}

	private Long proponer(Usuario quien, pe.edu.virgenmaria.cuentasclaras.conectores.model.TipoConector tipo,
			pe.edu.virgenmaria.cuentasclaras.conectores.dto.PropuestaConectorRequest pedido) {
		UsuariosDePrueba.iniciarSesion(quien);
		try {
			Long id = propuesta.proponer(tipo, pedido);
			UsuariosDePrueba.iniciarSesion(quien);
			propuesta.probar(id);
			return id;
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	private Long solicitudDe(Long conector) {
		return jdbc.queryForObject("SELECT solicitud_id FROM conector_colegio WHERE id = ?", Long.class, conector);
	}

	private String estadoConector(Long conector) {
		return jdbc.queryForObject("SELECT estado FROM conector_colegio WHERE id = ?", String.class, conector);
	}

	/**
	 * E12 con los permisos mínimos: Administración propone (con su firma), prueba la conexión, y la aprueba la Promotoría
	 * (Dirección sola no puede: mueve dinero); la segunda reemplaza a la primera; un rechazo lleva su firma. Avisa a la
	 * Promotoría y al correo externo de control (trg_mensaje_nace lo admite). Un secreto de pasarela se guarda cifrado.
	 */
	@Test
	void e12CambioDeConectorConPermisosMinimos() {
		ColegioConEquipoMySql.Equipo equipo = ColegioConEquipoMySql.crear(sufijo, EscenarioPlataforma.RUC_A, "987652001", alta,
				servicioUsuarios, usuarios, codificador, colegios);
		var tipo = pe.edu.virgenmaria.cuentasclaras.conectores.model.TipoConector.RECAUDACION;

		Long primera = proponer(equipo.administracion(), tipo, recaudacion("POR_LOTE"));
		assertThat(estadoConector(primera)).isEqualTo("PROPUESTO");
		assertThat(jdbc.queryForObject("SELECT prueba_ok FROM conector_colegio WHERE id = ?", Boolean.class, primera)).isTrue();
		// Dirección sola no la aprueba (la aplicación; la base también: e12SinPromotoriaLaBaseNoLoPoneEnVigencia).
		UsuariosDePrueba.iniciarSesion(equipo.directora());
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> bandeja.aprobar(solicitudDe(primera), null))
				.hasMessageContaining("Promotoría");
		UsuariosDePrueba.iniciarSesion(equipo.promotora());
		bandeja.aprobar(solicitudDe(primera), null);
		SecurityContextHolder.clearContext();
		assertThat(estadoConector(primera)).isEqualTo("VIGENTE");
		assertThat(jdbc.queryForList("SELECT destinatario_tipo FROM mensaje WHERE tipo = 'CONECTOR_VIGENTE' AND entidad_id = ?",
				String.class, primera)).contains("USUARIO", "EXTERNO");

		Long segunda = proponer(equipo.administracion(), tipo, recaudacion("POR_PAGO"));
		UsuariosDePrueba.iniciarSesion(equipo.promotora());
		bandeja.aprobar(solicitudDe(segunda), null);
		SecurityContextHolder.clearContext();
		assertThat(estadoConector(primera)).isEqualTo("REEMPLAZADO");
		assertThat(estadoConector(segunda)).isEqualTo("VIGENTE");

		// Un secreto de pasarela: cifrado (VARBINARY) y su propuesta rechazada con la firma de quien rechaza.
		Long pasarela = proponer(equipo.administracion(), pe.edu.virgenmaria.cuentasclaras.conectores.model.TipoConector.PASARELA,
				new pe.edu.virgenmaria.cuentasclaras.conectores.dto.PropuestaConectorRequest(
						pe.edu.virgenmaria.cuentasclaras.conectores.model.ProveedorConector.SIMULADO,
						pe.edu.virgenmaria.cuentasclaras.conectores.model.ModoConector.SIMULADO, null, null, null, null, null,
						null, null, java.util.List.of("YAPE"), "1500.00", null, null, null, null, null, null,
						"secreto-de-avisos-mysql-0123456789", "Pasarela simulada para mostrar el pago en línea"));
		byte[] cifrado = jdbc.queryForObject("SELECT secreto_cifrado FROM conector_colegio WHERE id = ?", byte[].class, pasarela);
		assertThat(new String(cifrado, java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("secreto-de-avisos");
		UsuariosDePrueba.iniciarSesion(equipo.directora());
		bandeja.rechazar(solicitudDe(pasarela), "Todavía no contratamos la pasarela del colegio");
		SecurityContextHolder.clearContext();
		assertThat(estadoConector(pasarela)).isEqualTo("RECHAZADO");
	}

	/**
	 * E12 en la base: un conector no nace VIGENTE ni a nombre de otra persona (sin su firma), un PROPUESTO no entra en
	 * vigencia sin su solicitud aprobada y firmada, sin una Promotoría de por medio (aunque Dirección haya firmado), un
	 * VIGENTE no se reemplaza solo ni cambia, y un rechazo exige la firma de quien rechaza.
	 */
	@Test
	void e12ConectorVigenteSinSolicitudFirmadaFalla() {
		ColegioConEquipoMySql.Equipo equipo = ColegioConEquipoMySql.crear(sufijo, EscenarioPlataforma.RUC_B, "987652011", alta,
				servicioUsuarios, usuarios, codificador, colegios);
		long colegio = equipo.colegioId();
		String insertar = "INSERT INTO conector_colegio (colegio_id, tipo, proveedor, modo, configuracion, clave_publica, "
				+ "estado, vigente, propuesto_por, resuelto_por, resuelto_en, prueba_ok, creado_en, creado_por, actualizado_en) "
				+ "VALUES (?, 'CUENTA_BANCARIA', 'BCP', 'REAL', '{}', ?, ?, ?, ?, ?, ?, ?, NOW(6), ?, NOW(6))";
		// Nace VIGENTE: 1644. Nace PROPUESTO a nombre de la Promotoría sin su firma: 1644.
		assertThat(codigoAl(() -> jdbc.update(insertar, colegio, java.util.UUID.randomUUID().toString(), "VIGENTE", true,
				equipo.administracion().getNombreUsuario(), equipo.promotora().getNombreUsuario(),
				java.time.LocalDateTime.now(), true, equipo.administracion().getNombreUsuario()))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update(insertar, colegio, java.util.UUID.randomUUID().toString(), "PROPUESTO", null,
				equipo.promotora().getNombreUsuario(), null, null, null, equipo.promotora().getNombreUsuario())))
				.isEqualTo(1644);

		var tipo = pe.edu.virgenmaria.cuentasclaras.conectores.model.TipoConector.RECAUDACION;
		Long propuesto = proponer(equipo.administracion(), tipo, recaudacion("POR_LOTE"));
		String aVigente = "UPDATE conector_colegio SET estado = 'VIGENTE', vigente = TRUE, resuelto_por = ?, "
				+ "resuelto_en = NOW(6) WHERE id = ?";
		// Sin la solicitud aprobada: 1644 (con cc_app y con cc_sistema).
		assertThat(codigoAl(() -> jdbc.update(aVigente, equipo.promotora().getNombreUsuario(), propuesto))).isEqualTo(1644);
		assertThat(codigoAl(() -> sistema().update(aVigente, equipo.promotora().getNombreUsuario(), propuesto)))
				.isIn(1143, 1644);
		// Con la solicitud aprobada y firmada por Dirección, pero sin Promotoría de por medio (mueve dinero): 1644.
		Long solicitud = solicitudDe(propuesto);
		// La sesión de la base de la directora se abre ANTES (por la ruta de identidad, en su propia transacción).
		UsuariosDePrueba.iniciarSesion(equipo.directora());
		tokens.actual();
		Integer sinPromotoria = new TransactionTemplate(transacciones).execute(t -> {
			UsuariosDePrueba.iniciarSesion(equipo.directora());
			firma.firmar(pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma.solicitud(solicitud,
					pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.EstadoSolicitud.APROBADA));
			jdbc.update("UPDATE solicitud_cambio SET estado = 'APROBADA', pendiente = NULL, resuelto_por = ?, "
					+ "resuelto_en = NOW(6) WHERE id = ?", equipo.directora().getNombreUsuario(), solicitud);
			Integer codigo = codigoAl(() -> jdbc.update(aVigente, equipo.directora().getNombreUsuario(), propuesto));
			t.setRollbackOnly();
			return codigo;
		});
		SecurityContextHolder.clearContext();
		assertThat(sinPromotoria).isEqualTo(1644);
		assertThat(estadoConector(propuesto)).isEqualTo("PROPUESTO");
		// Enlazar otra solicitud o rechazar sin firma: 1644.
		assertThat(codigoAl(() -> jdbc.update("UPDATE conector_colegio SET solicitud_id = (SELECT MAX(id) FROM solicitud_cambio "
				+ "WHERE colegio_id <> ?) WHERE id = ?", colegio, propuesto))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE conector_colegio SET estado = 'RECHAZADO', resuelto_por = ?, "
				+ "resuelto_en = NOW(6) WHERE id = ?", equipo.directora().getNombreUsuario(), propuesto))).isEqualTo(1644);
		// La prueba de conexión de un PROPUESTO sí se puede registrar (con cc_app).
		jdbc.update("UPDATE conector_colegio SET prueba_detalle = 'Probado otra vez' WHERE id = ?", propuesto);

		// Un VIGENTE no cambia ni se reemplaza solo.
		UsuariosDePrueba.iniciarSesion(equipo.promotora());
		bandeja.aprobar(solicitud, null);
		SecurityContextHolder.clearContext();
		assertThat(estadoConector(propuesto)).isEqualTo("VIGENTE");
		assertThat(codigoAl(() -> jdbc.update("UPDATE conector_colegio SET resuelto_por = 'otra.persona' WHERE id = ?",
				propuesto))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE conector_colegio SET estado = 'REEMPLAZADO', vigente = NULL WHERE id = ?",
				propuesto))).isEqualTo(1644);
	}

	/** E14: un conector no se borra (1142) ni cambia su configuración, su secreto o quién lo propuso (1143). */
	@Test
	void e14ElConectorNoSeEdita() {
		assertThat(codigoAl(() -> jdbc.update("DELETE FROM conector_colegio WHERE 1 = 0"))).isEqualTo(1142);
		for (String columna : new String[] { "secreto_cifrado", "configuracion", "propuesto_por", "tipo", "proveedor", "modo",
				"clave_publica", "clave_version", "secreto_pista" }) {
			assertThat(codigoAl(() -> jdbc.update("UPDATE conector_colegio SET " + columna + " = " + columna + " WHERE 1 = 0")))
					.as(columna).isEqualTo(1143);
			assertThat(codigoAl(() -> sistema().update("UPDATE conector_colegio SET " + columna + " = " + columna
					+ " WHERE 1 = 0"))).as("cc_sistema " + columna).isEqualTo(1143);
		}
		assertThat(codigoAl(() -> sistema().update("DELETE FROM conector_colegio WHERE 1 = 0"))).isEqualTo(1142);
	}

	/** E11: un colegio en producción no vuelve a preparación ni cambia su RUC (ni la Promotoría ni el operador). */
	@Test
	void e11ProduccionNoVuelveAtrasNiCambiaRuc() {
		assertThat(jdbc.queryForObject("SELECT estado FROM colegio WHERE id = 1", String.class)).isEqualTo("PRODUCCION");
		assertThat(codigoAl(() -> jdbc.update("UPDATE colegio SET estado = 'PREPARACION', produccion_desde = NULL, "
				+ "produccion_por = NULL WHERE id = 1"))).isEqualTo(1644);
		assertThat(codigoAl(() -> sistema().update("UPDATE colegio SET ruc = '20600000048' WHERE id = 1"))).isEqualTo(1644);
		assertThat(codigoAl(() -> sistema().update("UPDATE colegio SET nombre = 'Otro nombre' WHERE id = 1"))).isEqualTo(1644);
	}

	/** E16: dos sedes con el mismo RUC no usan la misma serie de comprobantes. */
	@Test
	void e16MismaSerieConElMismoRucFalla() {
		String ruc = jdbc.queryForObject("SELECT ruc FROM colegio WHERE id = 1", String.class);
		ColegioCreado sede = colegioNuevo(ruc, "987651040");
		String serie = "INSERT INTO serie_comprobante (colegio_id, tipo, serie, proveedor, ultimo_numero, creado_en, "
				+ "creado_por, actualizado_en) VALUES (?, 'BOLETA', ?, 'SIMULADO', 0, NOW(6), 'prueba', NOW(6))";
		String nombre = "B" + String.format("%03d", Math.floorMod(sufijo.hashCode(), 1000));
		jdbc.update(serie, 1L, nombre);
		assertThat(mensajeDe(() -> jdbc.update(serie, sede.colegioId(), nombre))).contains("mismo RUC");
	}

	/** El titular activa su cuenta (la clave de las pruebas), por la ruta de identidad, como la activación. */
	/**
	 * Sprint 8, tanda 3 (V30, E25): «faltan filas» por colegio con las claves reales de cc_respaldo y cc_app. Un FALTAN_FILAS
	 * dice de qué colegios (sin colegios o con uno que no existe, 1644); mientras la falta de un colegio siga abierta, un
	 * IGUAL se rechaza (1644); la Promotoría de OTRO colegio no la resuelve ni con la firma de su sesión (1644) y la del
	 * colegio afectado sí; resuelta, el IGUAL se acepta. Deja la base sin alertas abiertas.
	 */
	@Test
	void e25FaltanFilasPorColegio() throws Exception {
		String clave = System.getenv("CC_MYSQL_CLAVE_RESPALDO");
		String url = System.getenv("CC_MYSQL_URL");
		org.junit.jupiter.api.Assumptions.assumeTrue(clave != null && url != null, "sin la clave de cc_respaldo del job");
		long colegioA = 1L;
		Long colegioB = jdbc.queryForObject("SELECT MIN(u.colegio_id) FROM usuario u JOIN usuario_rol r ON r.usuario_id = "
				+ "u.id JOIN colegio c ON c.id = u.colegio_id WHERE c.tipo = 'COLEGIO' AND c.estado = 'PRODUCCION' AND "
				+ "u.colegio_id <> 1 AND u.activo AND u.apoderado_id IS NULL AND r.rol = 'PROMOTOR'", Long.class);
		assertThat(colegioB).as("otro colegio en producción con Promotoría").isNotNull();
		try (java.sql.Connection respaldo = java.sql.DriverManager.getConnection(url, "cc_respaldo", clave)) {
			assertThat(registroRespaldo(respaldo, "FALTAN_FILAS", null)).as("sin colegios").contains("de que colegios");
			assertThat(registroRespaldo(respaldo, "FALTAN_FILAS", "[" + colegioB + ",999999]"))
					.as("un colegio que no existe").contains("de que colegios");
			assertThat(registroRespaldo(respaldo, "FALTAN_FILAS", "[" + plataforma() + "]")).as("la plataforma")
					.contains("de que colegios");
			assertThat(registroRespaldo(respaldo, "FALTAN_FILAS", "[" + colegioB + "]")).isEqualTo("(sin error)");
			assertThat(registroRespaldo(respaldo, "IGUAL", null)).as("con la falta del B abierta")
					.contains("filas faltantes sin resolver");
			Long id = jdbc.queryForObject("SELECT MAX(id) FROM respaldo", Long.class);

			// La Promotoría del colegio A (no afectado), con la firma de su sesión: 1644.
			Usuario promotoraA = promotoriaDe(colegioA);
			Usuario promotoraB = promotoriaDe(colegioB);
			UsuariosDePrueba.iniciarSesion(promotoraA);
			String mensaje = mensajeDe(() -> new TransactionTemplate(transacciones).executeWithoutResult(t -> {
				firma.firmar(pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma.respaldoResuelto(id,
						colegioA));
				jdbc.update("INSERT INTO resolucion_respaldo (respaldo_id, colegio_id, usuario_id, motivo, creado_en, "
						+ "creado_por) VALUES (?, ?, ?, 'Revisado con el responsable tecnico', NOW(6), ?)", id, colegioA,
						promotoraA.getId(), promotoraA.getNombreUsuario());
			}));
			SecurityContextHolder.clearContext();
			assertThat(mensaje).contains("filas faltantes de ese colegio");

			// La del B, por el servicio: sí.
			UsuariosDePrueba.iniciarSesion(promotoraB);
			resolucionesRespaldo.resolver("Revisado con el responsable técnico (prueba E25 del job mysql)");
			SecurityContextHolder.clearContext();
			assertThat(jdbc.queryForList("SELECT colegio_id FROM resolucion_respaldo WHERE respaldo_id = ?", Long.class,
					id)).containsExactly(colegioB);
			assertThat(registroRespaldo(respaldo, "IGUAL", null)).as("resuelta").isEqualTo("(sin error)");
		}
	}

	// ------------------------------------------------------------------ correcciones del sprint 8 (V31)

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service.CorreoDeControl correoDeControl;

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioMarcaColegio marcaColegio;

	/** Firma {@code clave} con la sesión de {@code quien} (en su colegio), en su propia transacción. */
	private void firmar(Usuario quien, String clave) {
		UsuariosDePrueba.iniciarSesion(quien);
		new TransactionTemplate(transacciones).executeWithoutResult(t -> firma.firmar(clave));
	}

	/**
	 * S8-A1 en la base: el cambio del contacto del titular no lo escribe cc_app (1142); con cc_sistema, no nace sin la firma
	 * del operador (1644), no lo aplica el mismo operador que lo propuso ni otro sin su firma (1644), y sin un cambio APLICADO
	 * la cuenta del titular no cambia de contacto (1644). El DNI del titular no cambia (1644) y la ficha no cambia sin la
	 * firma del operador (S8-M5, 1644).
	 */
	@Test
	void s8a1ElContactoDelTitularExigeDosOperadoresYElDniNoCambia() {
		ColegioCreado creado = colegioNuevo(EscenarioPlataforma.RUC_A, "987651101");
		long colegio = creado.colegioId();
		Long titular = jdbc.queryForObject("SELECT id FROM usuario WHERE colegio_id = ?", Long.class, colegio);
		Usuario operador = operador();
		String insertar = "INSERT INTO cambio_contacto_titular (colegio_id, usuario_id, telefono_anterior, telefono_nuevo, "
				+ "motivo, estado, pendiente, propuesto_por, operador_id, plataforma_id, creado_en, creado_por, actualizado_en) "
				+ "VALUES (?, ?, '+51987651101', '+51987651199', 'Motivo de la prueba de MySQL', 'PROPUESTO', TRUE, "
				+ "'operador.s8', ?, ?, NOW(6), 'operador.s8', NOW(6))";
		assertThat(codigoAl(() -> jdbc.update(insertar, colegio, titular, operador.getId(), plataforma()))).isEqualTo(1142);
		assertThat(codigoAl(() -> sistema().update(insertar, colegio, titular, operador.getId(), plataforma())))
				.isEqualTo(1644);
		// Con la firma del operador (intento 1), nace.
		firmar(operador, pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma
				.cambioContactoTitularPropuesto(colegio, titular, 1));
		sistema().update(insertar, colegio, titular, operador.getId(), plataforma());
		Long cambio = jdbc.queryForObject("SELECT id FROM cambio_contacto_titular WHERE usuario_id = ?", Long.class, titular);
		String aplicar = "UPDATE cambio_contacto_titular SET estado = 'APLICADO', pendiente = NULL, resuelto_por = ?, "
				+ "resuelto_en = NOW(6) WHERE id = ?";
		// El mismo operador, con su firma: 1644 (o el CHECK). Otro operador sin firma: 1644.
		firmar(operador, pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma.cambioContactoTitular(cambio,
				pe.edu.virgenmaria.cuentasclaras.seguridad.model.CambioContactoTitular.Estado.APLICADO));
		assertThat(codigoAl(() -> sistema().update(aplicar, "operador.s8", cambio))).isIn(1644, 3819);
		Usuario otro = EscenarioPlataforma.operador(usuarios, codificador, colegios, "operador.s8b");
		assertThat(codigoAl(() -> sistema().update(aplicar, otro.getNombreUsuario(), cambio))).isEqualTo(1644);
		// Sin un cambio aplicado, la cuenta del titular no cambia de contacto.
		assertThat(codigoAl(() -> sistema().update("UPDATE usuario SET telefono_whatsapp = '+51987651199' WHERE id = ?",
				titular))).isEqualTo(1644);
		// Lo inmutable del cambio no cambia (1143) y no se borra (1142).
		assertThat(codigoAl(() -> sistema().update("UPDATE cambio_contacto_titular SET telefono_nuevo = telefono_nuevo "
				+ "WHERE 1 = 0"))).isEqualTo(1143);
		assertThat(codigoAl(() -> sistema().update("DELETE FROM cambio_contacto_titular WHERE 1 = 0"))).isEqualTo(1142);
		SecurityContextHolder.clearContext();

		// El DNI del titular no cambia (S8-A1) y la ficha no cambia sin la firma del operador (S8-M5).
		assertThat(mensajeDe(() -> sistema().update("UPDATE colegio SET titular_documento = '11111111', actualizado_por = "
				+ "'operador.s8' WHERE id = ?", colegio))).contains("DNI del titular no cambia");
		assertThat(mensajeDe(() -> sistema().update("UPDATE colegio SET nombre = 'Sin firma', actualizado_por = "
				+ "'operador.s8' WHERE id = ?", colegio))).contains("la ficha la cambia el operador con su firma");
		// Con la aplicación (firma del operador), sí.
		UsuariosDePrueba.iniciarSesion(operador);
		String institucional = jdbc.queryForObject("SELECT correo_institucional FROM colegio WHERE id = ?", String.class,
				colegio);
		alta.corregirFicha(colegio, new pe.edu.virgenmaria.cuentasclaras.plataforma.dto.FichaColegioRequest(
				"Colegio S8 corregido " + sufijo, "Asociación corregida S.A.C.", EscenarioPlataforma.RUC_A,
				"Av. Corregida 1, Lima", institucional, java.util.EnumSet.of(
						pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel.PRIMARIA), "CT-corr",
				jdbc.queryForObject("SELECT titular_nombre FROM colegio WHERE id = ?", String.class, colegio),
				jdbc.queryForObject("SELECT titular_documento FROM colegio WHERE id = ?", String.class, colegio)));
		SecurityContextHolder.clearContext();
		assertThat(jdbc.queryForObject("SELECT actualizado_por FROM colegio WHERE id = ?", String.class, colegio))
				.isEqualTo("operador.s8");
	}

	/**
	 * QA-S8-3 y S8-M5 en la base: una cuenta que ya abrió una sesión (activada) no recibe un cambio de contacto del operador
	 * aunque vuelva a «deber cambiar su clave» (1644), y el operador ya no cambia la ficha de ese colegio aunque firme (1644);
	 * la Promotoría sí, con su firma (y sin ella, 1644).
	 */
	@Test
	void qas83YS8m5ElTitularActivadoNoSeTocaYLaFichaLaFirmaLaPromotoria() {
		ColegioCreado creado = colegioNuevo(EscenarioPlataforma.RUC_B, "987651111");
		long colegio = creado.colegioId();
		String nombre = jdbc.queryForObject("SELECT nombre_usuario FROM usuario WHERE colegio_id = ?", String.class, colegio);
		Usuario titular = activar(colegio, nombre);
		// Abre una sesión: la cuenta queda activada (cc_cuenta_activada), aunque después le restablezcan la clave.
		UsuariosDePrueba.iniciarSesion(titular);
		tokens.actual();
		SecurityContextHolder.clearContext();
		sistema().update("UPDATE usuario SET debe_cambiar_clave = TRUE WHERE id = ?", titular.getId());

		Usuario operador = operador();
		firmar(operador, pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma
				.cambioContactoTitularPropuesto(colegio, titular.getId(), 1));
		assertThat(mensajeDe(() -> sistema().update("INSERT INTO cambio_contacto_titular (colegio_id, usuario_id, "
				+ "telefono_anterior, telefono_nuevo, motivo, estado, pendiente, propuesto_por, operador_id, plataforma_id, "
				+ "creado_en, creado_por, actualizado_en) VALUES (?, ?, '+51987651111', '+51987651199', 'Motivo de la prueba', "
				+ "'PROPUESTO', TRUE, 'operador.s8', ?, ?, NOW(6), 'operador.s8', NOW(6))", colegio, titular.getId(),
				operador.getId(), plataforma()))).contains("antes de la activacion");
		// La aplicación también lo rechaza.
		UsuariosDePrueba.iniciarSesion(operador);
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> alta.proponerContactoTitular(colegio,
				new ContactoTitularRequest("987651199", null, "Motivo de la prueba de MySQL")))
				.hasMessageContaining("ya activó");
		// El operador, con su firma de la versión actual, ya no cambia la ficha (el titular activó su cuenta).
		Long version = jdbc.queryForObject("SELECT version FROM colegio WHERE id = ?", Long.class, colegio);
		firmar(operador, pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma.fichaColegio(colegio, version));
		assertThat(mensajeDe(() -> sistema().update("UPDATE colegio SET direccion = 'Av. del operador', actualizado_por = "
				+ "'operador.s8' WHERE id = ?", colegio))).contains("antes de que el titular active su cuenta");
		SecurityContextHolder.clearContext();
		// La Promotoría: sin su firma, 1644; con ella (por la aplicación), sí.
		assertThat(mensajeDe(() -> jdbc.update("UPDATE colegio SET direccion = 'Av. sin firma', actualizado_por = ? "
				+ "WHERE id = ?", nombre, colegio))).contains("la Promotoria del colegio con su firma");
		UsuariosDePrueba.iniciarSesion(titular);
		String institucional = jdbc.queryForObject("SELECT correo_institucional FROM colegio WHERE id = ?", String.class,
				colegio);
		marcaColegio.cambiar(new pe.edu.virgenmaria.cuentasclaras.colegio.dto.MarcaColegioRequest(
				jdbc.queryForObject("SELECT nombre FROM colegio WHERE id = ?", String.class, colegio),
				"Razón social de la Promotoría S.A.C.", "Av. de la Promotoría 9, Lima", institucional), null, null);
		SecurityContextHolder.clearContext();
		assertThat(jdbc.queryForObject("SELECT actualizado_por FROM colegio WHERE id = ?", String.class, colegio))
				.isEqualTo(nombre);
	}

	/**
	 * S8-A2 en la base: en un colegio EN PRODUCCIÓN el operador no cambia el correo de control aunque firme (1644); lo
	 * propone (cambio_correo_control, que cc_app no escribe: 1142) y lo aprueba la Promotoría con su firma; sin ella,
	 * cc_sistema no lo aprueba (1644).
	 */
	@Test
	void s8a2EnProduccionElCorreoDeControlLoApruebaLaPromotoria() {
		ColegioConEquipoMySql.Equipo equipo = ColegioConEquipoMySql.crear(sufijo + "k", EscenarioPlataforma.RUC_C, "987652101",
				alta, servicioUsuarios, usuarios, codificador, colegios);
		long colegio = equipo.colegioId();
		cuentasDePrueba.pasarAProduccion(colegio, equipo.promotora());
		SecurityContextHolder.clearContext();
		String anterior = jdbc.queryForObject("SELECT valor FROM configuracion_colegio WHERE colegio_id = ? AND clave = "
				+ "'huella_correo_externo'", String.class, colegio);
		Long versionFila = jdbc.queryForObject("SELECT version FROM configuracion_colegio WHERE colegio_id = ? AND clave = "
				+ "'huella_correo_externo'", Long.class, colegio);

		// El operador, con su firma de la fila: 1644 (el colegio está en producción).
		Usuario operador = operador();
		firmar(operador, pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma.configuracionColegio(colegio,
				"huella_correo_externo", versionFila + 1));
		assertThat(mensajeDe(() -> sistema().update("UPDATE configuracion_colegio SET valor = 'operador@propio.pe', "
				+ "actualizado_por = 'operador.s8', actualizado_en = NOW(6), version = version + 1 WHERE colegio_id = ? "
				+ "AND clave = 'huella_correo_externo'", colegio))).contains("la cambia el operador con su firma");
		// cc_app no escribe la propuesta.
		assertThat(codigoAl(() -> jdbc.update("INSERT INTO cambio_correo_control (colegio_id, correo_nuevo, motivo, estado, "
				+ "pendiente, propuesto_por, operador_id, plataforma_id, creado_en, creado_por, actualizado_en) VALUES (?, "
				+ "'x@y.pe', 'Motivo de la prueba', 'PROPUESTO', TRUE, 'operador.s8', ?, ?, NOW(6), 'operador.s8', NOW(6))",
				colegio, operador.getId(), plataforma()))).isEqualTo(1142);

		// La aplicación: el operador propone (con aviso), la Promotoría aprueba con su firma.
		UsuariosDePrueba.iniciarSesion(operador);
		assertThat(alta.registrarCorreoControl(colegio, "nuevo.contador." + sufijo + "@estudio.pe",
				"Adenda del contrato: cambio de contador")).isFalse();
		SecurityContextHolder.clearContext();
		Long cambio = jdbc.queryForObject("SELECT id FROM cambio_correo_control WHERE colegio_id = ?", Long.class, colegio);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mensaje WHERE colegio_id = ? AND tipo = 'CORREO_CONTROL' AND "
				+ "entidad = 'cambio_correo_control' AND destino = ?", Long.class, colegio, anterior)).isEqualTo(1L);
		// cc_sistema sin la firma de la Promotoría: 1644.
		assertThat(codigoAl(() -> sistema().update("UPDATE cambio_correo_control SET estado = 'APROBADO', pendiente = NULL, "
				+ "resuelto_por = ?, resuelto_en = NOW(6) WHERE id = ?", equipo.promotora().getNombreUsuario(), cambio)))
				.isEqualTo(1644);
		UsuariosDePrueba.iniciarSesion(equipo.promotora());
		correoDeControl.aprobar(cambio);
		SecurityContextHolder.clearContext();
		assertThat(jdbc.queryForList("SELECT valor FROM configuracion_colegio WHERE colegio_id = ?", String.class, colegio))
				.containsOnly("nuevo.contador." + sufijo + "@estudio.pe");
		assertThat(codigoAl(() -> sistema().update("UPDATE cambio_correo_control SET correo_nuevo = correo_nuevo "
				+ "WHERE 1 = 0"))).isEqualTo(1143);
	}

	/** S8-M4 en la base: un OSE entra en vigencia solo con una Promotoría de por medio (aunque Dirección haya firmado). */
	@Test
	void s8m4ElOseTambienExigeUnaPromotoria() {
		ColegioConEquipoMySql.Equipo equipo = ColegioConEquipoMySql.crear(sufijo + "o", EscenarioPlataforma.RUC_D, "987652111",
				alta, servicioUsuarios, usuarios, codificador, colegios);
		var tipo = pe.edu.virgenmaria.cuentasclaras.conectores.model.TipoConector.OSE;
		String serie = String.format("%03d", Math.floorMod(sufijo.hashCode(), 1000));
		Long propuesto = proponer(equipo.administracion(), tipo, new pe.edu.virgenmaria.cuentasclaras.conectores.dto
				.PropuestaConectorRequest(pe.edu.virgenmaria.cuentasclaras.conectores.model.ProveedorConector.SIMULADO,
						pe.edu.virgenmaria.cuentasclaras.conectores.model.ModoConector.SIMULADO, null, "B" + serie, "F" + serie,
						"BC" + serie.substring(1), "FC" + serie.substring(1), "INAFECTO", null, null, null, null, null, null,
						null, null, null, null, "Comprobante simulado mientras llega el contrato del OSE"));
		Long solicitud = solicitudDe(propuesto);
		UsuariosDePrueba.iniciarSesion(equipo.directora());
		tokens.actual();
		Integer sinPromotoria = new TransactionTemplate(transacciones).execute(t -> {
			UsuariosDePrueba.iniciarSesion(equipo.directora());
			firma.firmar(pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma.solicitud(solicitud,
					pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.EstadoSolicitud.APROBADA));
			jdbc.update("UPDATE solicitud_cambio SET estado = 'APROBADA', pendiente = NULL, resuelto_por = ?, "
					+ "resuelto_en = NOW(6) WHERE id = ?", equipo.directora().getNombreUsuario(), solicitud);
			Integer codigo = codigoAl(() -> jdbc.update("UPDATE conector_colegio SET estado = 'VIGENTE', vigente = TRUE, "
					+ "resuelto_por = ?, resuelto_en = NOW(6) WHERE id = ?", equipo.directora().getNombreUsuario(), propuesto));
			t.setRollbackOnly();
			return codigo;
		});
		SecurityContextHolder.clearContext();
		assertThat(sinPromotoria).isEqualTo(1644);
		// Con la Promotoría (por la bandeja), sí.
		UsuariosDePrueba.iniciarSesion(equipo.promotora());
		bandeja.aprobar(solicitud, null);
		SecurityContextHolder.clearContext();
		assertThat(estadoConector(propuesto)).isEqualTo("VIGENTE");
	}

	/**
	 * S8-M1 en la base: al REACTIVAR una cuenta de la plataforma (un operador que vuelve a CC_OPERADORES), cc_sistema le
	 * pone el correo de la lista; el correo de un operador activo o de una cuenta de un colegio no cambia así (1644).
	 */
	@Test
	void s8m1ElCorreoDelOperadorCambiaSoloAlReactivarlo() {
		Usuario volvio = EscenarioPlataforma.operador(usuarios, codificador, colegios, "operador.m1." + sufijo);
		JdbcTemplate sistema = sistema();
		assertThat(codigoAl(() -> sistema.update("UPDATE usuario SET correo = 'otro@x.pe' WHERE id = ?", volvio.getId())))
				.isEqualTo(1644);
		sistema.update("UPDATE usuario SET activo = FALSE, desactivado_en = NOW(6), desactivado_por = 'sistema.plataforma' "
				+ "WHERE id = ?", volvio.getId());
		sistema.update("UPDATE usuario SET activo = TRUE, desactivado_en = NULL, desactivado_por = NULL, "
				+ "correo = 'nueva.persona@proveedor.pe' WHERE id = ?", volvio.getId());
		assertThat(jdbc.queryForObject("SELECT correo FROM usuario WHERE id = ?", String.class, volvio.getId()))
				.isEqualTo("nueva.persona@proveedor.pe");
		// En un colegio, reactivar no cambia el contacto del personal.
		Usuario docente = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "docente.m1." + sufijo, UsuariosDePrueba.CLAVE,
				false, Rol.DOCENTE);
		sistema.update("UPDATE usuario SET activo = FALSE, desactivado_en = NOW(6), desactivado_por = 'x' WHERE id = ?",
				docente.getId());
		assertThat(codigoAl(() -> sistema.update("UPDATE usuario SET activo = TRUE, desactivado_en = NULL, "
				+ "desactivado_por = NULL, correo = 'otro@x.pe' WHERE id = ?", docente.getId()))).isEqualTo(1644);
	}

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.operacion.service.ResolucionesRespaldo resolucionesRespaldo;

	/** Registra un respaldo como cc_respaldo, con las anclas de la bitácora de este momento; devuelve el error o «(sin error)». */
	private String registroRespaldo(java.sql.Connection respaldo, String comparacion, String colegios) {
		Map<String, Object> ancla = jdbc.queryForMap("SELECT ultima_secuencia, ultimo_hash FROM auditoria_cadena WHERE id = 1");
		String archivo = "cc-19990101-" + String.format("%06d", Math.floorMod(System.nanoTime(), 1_000_000)) + ".sql.gz.age";
		try (java.sql.PreparedStatement sentencia = respaldo.prepareStatement("INSERT INTO respaldo (inicio, fin, archivo, "
				+ "sha256, bytes, version_esquema, secuencia_antes, hash_antes, secuencia_despues, hash_despues, conteos, "
				+ "destino, comparacion, diferencias, colegios_faltan, creado_en, creado_por) VALUES (UTC_TIMESTAMP(6) - "
				+ "INTERVAL 5 HOUR, UTC_TIMESTAMP(6) - INTERVAL 5 HOUR, ?, REPEAT('a', 64), 1, '30', ?, ?, ?, ?, '{}', "
				+ "'cirespaldos', ?, ?, ?, UTC_TIMESTAMP(6) - INTERVAL 5 HOUR, 'cc_respaldo')")) {
			sentencia.setString(1, archivo);
			sentencia.setLong(2, ((Number) ancla.get("ultima_secuencia")).longValue());
			sentencia.setString(3, (String) ancla.get("ultimo_hash"));
			sentencia.setLong(4, ((Number) ancla.get("ultima_secuencia")).longValue());
			sentencia.setString(5, (String) ancla.get("ultimo_hash"));
			sentencia.setString(6, comparacion);
			sentencia.setString(7, "FALTAN_FILAS".equals(comparacion) ? "pago (faltan 1) [colegio x] (prueba E25)" : null);
			sentencia.setString(8, colegios);
			sentencia.executeUpdate();
			return "(sin error)";
		}
		catch (SQLException e) {
			return e.getErrorCode() + " " + e.getMessage();
		}
	}

	private Usuario promotoriaDe(long colegio) {
		Long id = jdbc.queryForObject("SELECT MIN(u.id) FROM usuario u JOIN usuario_rol r ON r.usuario_id = u.id "
				+ "WHERE u.colegio_id = ? AND u.activo AND u.apoderado_id IS NULL AND r.rol = 'PROMOTOR'", Long.class, colegio);
		assertThat(id).as("una Promotoría activa en el colegio " + colegio).isNotNull();
		return pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio.en(colegio,
				() -> usuarios.findById(id).orElseThrow());
	}

	private Usuario activar(long colegioId, String nombre) {
		return pe.edu.virgenmaria.cuentasclaras.comun.basedatos.RutaConexion.identidad(() -> pe.edu.virgenmaria.cuentasclaras
				.comun.multicolegio.ContextoColegio.en(colegioId, () -> {
					Usuario titular = usuarios.findByNombreUsuario(nombre).orElseThrow();
					titular.cambiarClave(codificador.encode(UsuariosDePrueba.CLAVE), java.time.LocalDateTime.now(
							java.time.Clock.system(pe.edu.virgenmaria.cuentasclaras.comun.config.ConfiguracionTiempo.ZONA_LIMA)),
							false);
					return usuarios.saveAndFlush(titular);
				}));
	}

	private static Integer codigoAl(Runnable sentencia) {
		try {
			sentencia.run();
			return null;
		}
		catch (DataAccessException e) {
			for (Throwable t = e; t != null; t = t.getCause()) {
				if (t instanceof SQLException sql) {
					return sql.getErrorCode();
				}
			}
			throw e;
		}
	}

	private static String mensajeDe(Runnable sentencia) {
		try {
			sentencia.run();
			return "(sin error)";
		}
		catch (DataAccessException e) {
			return String.valueOf(e.getMostSpecificCause().getMessage());
		}
	}
}
