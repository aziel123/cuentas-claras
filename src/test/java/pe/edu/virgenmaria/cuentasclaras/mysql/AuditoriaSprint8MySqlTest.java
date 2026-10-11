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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 8, tanda 1 (sección 12, filas que dicen «MySQL») contra MySQL 8 real, con los permisos mínimos de 02 y los
 * triggers de 03: el alta de un colegio por el operador funciona de punta a punta, y con la clave de cc_app o de
 * cc_sistema no se crea un colegio, una primera Promotoría ni un operador por fuera, no se cobra en un colegio en
 * preparación, no se pasa a producción sin la firma de la Promotoría y Dirección y Caja, no se vuelve atrás ni se cambia
 * el RUC, y dos sedes con el mismo RUC no comparten serie. Corre después de la fase 2 del job mysql
 * ({@code CC_PRUEBA_MYSQL=true}). Usa nombres únicos y no limpia.
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

		// Corregir el contacto del titular antes de la activación: con la firma del operador (trg_usuario_contacto).
		alta.reenviarEnlaceTitular(creado.colegioId(), new ContactoTitularRequest("987651002", null));
		assertThat(jdbc.queryForObject("SELECT telefono_whatsapp FROM usuario WHERE colegio_id = ?", String.class,
				creado.colegioId())).isEqualTo("+51987651002");
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
		UsuariosDePrueba.iniciarSesion(promotora);
		paso.pasar();
		assertThat(jdbc.queryForObject("SELECT estado FROM colegio WHERE id = ?", String.class, creado.colegioId()))
				.isEqualTo("PRODUCCION");
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
