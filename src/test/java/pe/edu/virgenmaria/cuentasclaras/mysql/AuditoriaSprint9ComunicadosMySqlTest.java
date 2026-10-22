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
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.ProponerAsignacionRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.model.ConfiguracionAcademica;
import pe.edu.virgenmaria.cuentasclaras.academico.model.TipoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.academico.service.ConfiguracionesAcademicas;
import pe.edu.virgenmaria.cuentasclaras.academico.service.Asignaciones;
import pe.edu.virgenmaria.cuentasclaras.academico.siagie.CodigoModularRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.siagie.NominaSiagie;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.EstadoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.CrearSeccionRequest;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.basedatos.FuenteDatosEnrutada;
import pe.edu.virgenmaria.cuentasclaras.comun.basedatos.RutaConexion;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.CuentasDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.ContenidoSinCobranza;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.proceso.AvisosComunicados;
import pe.edu.virgenmaria.cuentasclaras.comunicados.PdfsDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comunicados.dto.AdjuntoValidado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.dto.BorradorComunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.AlcanceComunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.EmisorRol;
import pe.edu.virgenmaria.cuentasclaras.comunicados.service.EnvioComunicados;
import pe.edu.virgenmaria.cuentasclaras.comunicados.service.HuellaComunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.service.LecturaFamilia;
import pe.edu.virgenmaria.cuentasclaras.plataforma.service.AltaColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.CambiarRolesRequest;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioUsuarios;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.FirmaSesion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.SesionAbierta;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.TokenDeSesion;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Sprint 9, tanda 3 (sección 13.2 y los hallazgos B1 a B15 de la auditoría en papel; brief de la tanda 3, sección 9)
 * contra MySQL 8 real, con los permisos mínimos de 02 y los triggers de 03. Con la clave de {@code cc_app}: nadie envía
 * como Dirección sin serlo, sin su sesión ni su firma; el docente, solo a su sección; un comunicado no se edita (1143) ni
 * se retira sin firma, y su retiro no se reescribe; la huella la recalcula la base (B2) y es única; los caracteres
 * invisibles y la cobranza no entran (B4); un PDF que requiere revisión no sale directo (B3) ni lo aprueba el mismo
 * órgano; la fe de erratas llega a todos los del original (B1) y la de un retirado, solo Dirección (B9); la lista de
 * destinatarios no se reabre; la familia confirma con su sesión; la nómina, Dirección con su sesión (B14); y el resumen
 * de comunicados no habla de cobranza y empieza con el colegio (B6, B7). Y el flujo con los permisos mínimos.
 * <p>
 * Usa UN colegio para toda la clase: cada prueba escribe su propio contenido. Las clases de MySQL anteriores del job ya
 * llenan los 25 colegios de la instalación (decisión 138): solo esta clase sube el tope a 30 para su colegio; el tope de
 * 25 lo prueba {@code TopeInstalacionQaSprint8Test}.
 */
@SpringBootTest(properties = "cuentasclaras.plataforma.max-colegios=30")
@ActiveProfiles({ "test", "mysql" })
@EnabledIfEnvironmentVariable(named = "CC_PRUEBA_MYSQL", matches = "true")
class AuditoriaSprint9ComunicadosMySqlTest {

	private static final String TEXTO = "Estimadas familias: el viernes celebraremos el Día del Logro en el patio principal.";

	private static final String MOTIVO = "Se envió con la fecha equivocada a la sección";

	private static final String INSERTAR = "INSERT INTO comunicado (colegio_id, anio_escolar_id, tipo, corrige_id, "
			+ "emisor_usuario_id, emisor_rol, emisor_etiqueta, alcance, nivel, grado, seccion_id, titulo, cuerpo, urgente, "
			+ "requiere_confirmacion, adjunto_id, huella_contenido, estado, enviado_en, destinatarios, sesion_id, creado_en, "
			+ "creado_por, actualizado_en) VALUES (?, ?, ?, ?, ?, ?, 'Etiqueta de prueba', ?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?, ?, "
			+ "%s, ?, ?, NOW(6), ?, NOW(6))";

	private final String sufijo = Long.toString(System.nanoTime(), 36);

	@Autowired
	private EnvioComunicados envio;

	@Autowired
	private LecturaFamilia lectura;

	@Autowired
	private NominaSiagie nomina;

	@Autowired
	private AvisosComunicados avisos;

	@Autowired
	private Asignaciones asignaciones;

	@Autowired
	private ConfiguracionesAcademicas configuraciones;

	@Autowired
	private BandejaAprobaciones bandeja;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private FirmaSesion firma;

	@Autowired
	private TokenDeSesion tokens;

	@Autowired
	private AltaColegio alta;

	@Autowired
	private ServicioUsuarios servicioUsuarios;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private ColegioRepository colegios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private CuentasDePrueba cuentasDePrueba;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private javax.sql.DataSource fuenteDatos;

	@Autowired
	private PlatformTransactionManager transacciones;

	/** El colegio de la clase: en producción, con una sección con tutor(a), auxiliar y dos familias, y otra con una. */
	private record Escuela(EscuelaConDocentesMySql.Escuela e, long seccion, long otraSeccion, Long familiaA, Long familiaB,
			Long familiaC, Long apoderadoA, Long apoderadoC, Usuario auxiliar) {

		long colegio() {
			return e.colegioId();
		}

		Usuario directora() {
			return e.equipo().directora();
		}

		Usuario promotora() {
			return e.equipo().promotora();
		}

		Usuario docente() {
			return e.docente();
		}
	}

	private static Escuela compartida;

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
	}

	private Escuela escuela() {
		if (compartida != null) {
			return compartida;
		}
		EscuelaConDocentesMySql.Escuela e = EscuelaConDocentesMySql.crear(sufijo + "com", "20600000056", "987654611", alta,
				servicioUsuarios, usuarios, codificador, colegios, estructura, jdbc);
		cuentasDePrueba.pasarAProduccion(e.colegioId(), e.equipo().promotora());
		long seccion = como(e.equipo().administracion(), () -> estructura.crearSeccion(e.anioId(),
				new CrearSeccionRequest(Grado.PRIMARIA_5, "C" + numero(3))));
		UsuariosDePrueba.iniciarSesion(e.equipo().administracion());
		Long mateo = alumnos.registrar(EscenarioEscolar.conApoderadoNuevo("74" + numero(6), "Quispe", "Rojas", "Mateo",
				LocalDate.of(2016, 3, 3), "48" + numero(6), "Rojas", "Paz", "Rosa", null, "rosa.com." + sufijo + "@correo.pe",
				seccion)).alumnoId();
		Long lucas = alumnos.registrar(EscenarioEscolar.conApoderadoNuevo("75" + numero(6), "Flores", "Díaz", "Lucas",
				LocalDate.of(2016, 4, 4), "49" + numero(6), "Flores", "Luna", "Pedro", null, "pedro.com." + sufijo
						+ "@correo.pe", seccion)).alumnoId();
		Long sofia = alumnos.registrar(EscenarioEscolar.conApoderadoNuevo("76" + numero(6), "Vega", "Ruiz", "Sofía",
				LocalDate.of(2016, 5, 5), "40" + numero(6), "Vega", "Soto", "Carmen", null, "carmen.com." + sufijo
						+ "@correo.pe", e.seccionB())).alumnoId();
		SecurityContextHolder.clearContext();
		Usuario auxiliar = UsuariosDePrueba.guardar(usuarios, codificador, e.colegioId(), "aux.com." + sufijo,
				UsuariosDePrueba.CLAVE, false, Rol.DOCENTE);
		asignar(e, seccion, e.docente(), TipoAsignacion.TUTOR);
		asignar(e, seccion, auxiliar, TipoAsignacion.AUXILIAR);
		Long familiaA = familiaDe(mateo);
		Long familiaB = familiaDe(lucas);
		Long familiaC = familiaDe(sofia);
		Long apoderadoA = jdbc.queryForObject("SELECT MIN(id) FROM apoderado WHERE familia_id = ?", Long.class, familiaA);
		Long apoderadoC = jdbc.queryForObject("SELECT MIN(id) FROM apoderado WHERE familia_id = ?", Long.class, familiaC);
		compartida = new Escuela(e, seccion, e.seccionB(), familiaA, familiaB, familiaC, apoderadoA, apoderadoC, auxiliar);
		return compartida;
	}

	private void asignar(EscuelaConDocentesMySql.Escuela e, long seccion, Usuario docente, TipoAsignacion tipo) {
		Long id = como(e.equipo().administracion(), () -> asignaciones.proponer(new ProponerAsignacionRequest(seccion,
				docente.getId(), tipo, null, "Asignación del año aprobada por Dirección")));
		Long solicitud = jdbc.queryForObject("SELECT solicitud_id FROM asignacion_docente WHERE id = ?", Long.class, id);
		como(e.equipo().directora(), () -> {
			bandeja.aprobar(solicitud, null);
			return null;
		});
	}

	private Long familiaDe(Long alumno) {
		return jdbc.queryForObject("SELECT familia_id FROM alumno WHERE id = ?", Long.class, alumno);
	}

	// ------------------------------------------------------------------------------------------------ E16 a E18

	@Test
	void e16DocenteConRolDireccionFalla() {
		Escuela s = escuela();
		assertThat(insertar(Fila.de(s.docente(), "DIRECCION").aSeccion(s.seccion()).titulo("E16 rol " + sufijo)))
				.isEqualTo(1644);
	}

	@Test
	void e16DocenteAlColegioFalla() {
		Escuela s = escuela();
		assertThat(insertar(Fila.de(s.docente(), "DOCENTE").titulo("E16 colegio " + sufijo))).isIn(3819, 1644);
	}

	@Test
	void e16UrgenteDeDocenteFalla() {
		Escuela s = escuela();
		assertThat(insertar(Fila.de(s.docente(), "DOCENTE").aSeccion(s.seccion()).comoUrgente().titulo("E16 urgente " + sufijo)))
				.isIn(3819, 1644);
	}

	@Test
	void e17ComunicadoSinFirmaFalla() {
		Escuela s = escuela();
		assertThat(insertar(Fila.de(s.directora(), "DIRECCION").sinFirma().titulo("E17 sin firma " + sufijo)))
				.isEqualTo(1644);
		// Con la sesión de otra persona (la Administración), tampoco.
		assertThat(insertar(Fila.de(s.directora(), "DIRECCION").conSesionDe(s.e().equipo().administracion())
				.titulo("E17 otra sesión " + sufijo))).isEqualTo(1644);
		// Y el camino legítimo con su firma sí entra.
		assertThat(insertar(Fila.de(s.directora(), "DIRECCION").titulo("E17 legítimo " + sufijo))).isNull();
	}

	@Test
	void e17PorAprobarSinFirmaFalla() {
		Escuela s = escuela();
		assertThat(insertar(Fila.de(s.docente(), "DOCENTE").aSeccion(s.seccion()).porAprobar().conAdjunto(false)
				.sinFirma().titulo("E17 por aprobar " + sufijo))).isEqualTo(1644);
		assertThat(insertar(Fila.de(s.docente(), "DOCENTE").aSeccion(s.seccion()).porAprobar().conAdjunto(false)
				.titulo("E17 por aprobar firmado " + sufijo))).isNull();
	}

	@Test
	void e18SeccionAjenaYAuxiliarFallan() {
		Escuela s = escuela();
		assertThat(insertar(Fila.de(s.docente(), "DOCENTE").aSeccion(s.otraSeccion()).titulo("E18 ajena " + sufijo)))
				.isEqualTo(1644);
		assertThat(insertar(Fila.de(s.auxiliar(), "DOCENTE").aSeccion(s.seccion()).titulo("E18 auxiliar " + sufijo)))
				.isEqualTo(1644);
		assertThat(insertar(Fila.de(s.docente(), "DOCENTE").aSeccion(s.seccion()).titulo("E18 la suya " + sufijo))).isNull();
	}

	// ------------------------------------------------------------------------------------------------ E19 a E29

	@Test
	void e19ElComunicadoNoSeEdita() {
		Escuela s = escuela();
		Long id = enviarLegitimo(s, "E19 " + sufijo);
		for (String columna : new String[] { "titulo", "cuerpo", "adjunto_id", "huella_contenido", "alcance", "emisor_rol",
				"emisor_usuario_id", "sesion_id", "urgente" }) {
			assertThat(codigoAl(() -> jdbc.update("UPDATE comunicado SET " + columna + " = " + columna + " WHERE id = ?",
					id))).as(columna).isEqualTo(1143);
		}
		assertThat(codigoAl(() -> jdbc.update("DELETE FROM comunicado WHERE id = ?", id))).isEqualTo(1142);
		assertThat(codigoAl(() -> jdbc.update("UPDATE comunicado_destinatario SET familia_id = familia_id WHERE "
				+ "comunicado_id = ?", id))).isEqualTo(1143);
	}

	@Test
	void e20FeDeErratasConOtroAlcanceFalla() {
		Escuela s = escuela();
		Long original = enviarLegitimo(s, "E20 original " + sufijo);
		assertThat(insertar(Fila.de(s.directora(), "DIRECCION").feDeErratas(original).titulo("E20 al colegio " + sufijo)))
				.isEqualTo(1644);
	}

	@Test
	void e20FeDeErratasAOtraFamiliaFalla() {
		Escuela s = escuela();
		Long original = enviarLegitimo(s, "E20 familias " + sufijo);
		Fila fe = Fila.de(s.directora(), "DIRECCION").aSeccion(s.seccion()).feDeErratas(original)
				.titulo("E20 fe " + sufijo);
		Integer codigo = enTransaccion(s.directora(), () -> {
			firmar(fe, s);
			jdbc.update(sql(fe), parametros(fe, s, sesionActual()));
			Long id = jdbc.queryForObject("SELECT MAX(id) FROM comunicado WHERE colegio_id = ?", Long.class, s.colegio());
			return codigoAl(() -> jdbc.update("INSERT INTO comunicado_destinatario (colegio_id, comunicado_id, familia_id, "
					+ "alumnos, creado_en, creado_por, actualizado_en) VALUES (?, ?, ?, 1, NOW(6), ?, NOW(6))", s.colegio(),
					id, s.familiaC(), s.directora().getNombreUsuario()));
		});
		assertThat(codigo).isEqualTo(1644);
	}

	@Test
	void e21ComunicadoConDeudaFallaEnLaBase() {
		Escuela s = escuela();
		assertThat(insertar(Fila.de(s.directora(), "DIRECCION").titulo("E21 " + sufijo)
				.cuerpo("Recuerden pagar la pensión de octubre antes del viernes."))).isEqualTo(1644);
		assertThat(insertar(Fila.de(s.directora(), "DIRECCION").titulo("E21 monto " + sufijo)
				.cuerpo("Traigan S/ 20 para la excursión del viernes."))).isEqualTo(1644);
	}

	@Test
	void e22AdjuntoDeOtraPersonaFalla() {
		Escuela s = escuela();
		assertThat(insertar(Fila.de(s.directora(), "DIRECCION").conAdjunto(false).adjuntoDe(s.promotora())
				.titulo("E22 " + sufijo))).isEqualTo(1644);
	}

	@Test
	void e23ConfirmarPorOtraFamiliaFalla() {
		Escuela s = escuela();
		Long id = enviarLegitimo(s, "E23 " + sufijo);
		UsuarioAutenticado carmen = cuentaDe(s, s.apoderadoC(), "c");
		Long sesion = sesionDe(carmen);
		String confirmar = "INSERT INTO confirmacion_lectura (colegio_id, comunicado_id, familia_id, apoderado_id, usuario_id, "
				+ "sesion_id, creado_en, creado_por, actualizado_en) VALUES (?, ?, ?, ?, ?, ?, NOW(6), ?, NOW(6))";
		// La familia C no es destinataria: la FK no la deja (1452).
		assertThat(codigoAl(() -> jdbc.update(confirmar, s.colegio(), id, s.familiaC(), s.apoderadoC(), carmen.usuarioId(),
				sesion, carmen.getUsername()))).isIn(1452, 1644);
		// A nombre de la familia A, con la cuenta de Carmen: 1644.
		assertThat(codigoAl(() -> jdbc.update(confirmar, s.colegio(), id, s.familiaA(), s.apoderadoC(), carmen.usuarioId(),
				sesion, carmen.getUsername()))).isIn(1644, 1452);
	}

	@Test
	void e24ConfirmacionDelPersonalFalla() {
		Escuela s = escuela();
		Long id = enviarLegitimo(s, "E24 " + sufijo);
		Usuario admin = s.e().equipo().administracion();
		Long sesion = sesionDe(admin);
		assertThat(codigoAl(() -> jdbc.update("INSERT INTO confirmacion_lectura (colegio_id, comunicado_id, familia_id, "
				+ "apoderado_id, usuario_id, sesion_id, creado_en, creado_por, actualizado_en) VALUES (?, ?, ?, ?, ?, ?, "
				+ "NOW(6), ?, NOW(6))", s.colegio(), id, s.familiaA(), s.apoderadoA(), admin.getId(), sesion,
				admin.getNombreUsuario()))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE confirmacion_lectura SET version = version WHERE 1 = 0")))
				.isEqualTo(1142);
	}

	@Test
	void e28RetiroSinFirmaFalla() {
		Escuela s = escuela();
		Long id = enviarLegitimo(s, "E28 " + sufijo);
		assertThat(codigoAl(() -> jdbc.update("UPDATE comunicado SET estado = 'RETIRADO', retirado_por = ?, retirado_en = "
				+ "NOW(6), motivo_retiro = ? WHERE id = ?", s.directora().getNombreUsuario(), MOTIVO, id))).isEqualTo(1644);
		assertThat(jdbc.queryForObject("SELECT estado FROM comunicado WHERE id = ?", String.class, id)).isEqualTo("ENVIADO");
	}

	@Test
	void e28RetiroNoSeReescribe() {
		Escuela s = escuela();
		Long id = enviarLegitimo(s, "E28 retiro " + sufijo);
		como(s.directora(), () -> {
			envio.retirar(id, MOTIVO);
			return null;
		});
		assertThat(codigoAl(() -> jdbc.update("UPDATE comunicado SET motivo_retiro = 'Otro motivo que borra el primero' "
				+ "WHERE id = ?", id))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE comunicado SET estado = 'ENVIADO', retirado_por = NULL, retirado_en = "
				+ "NULL, motivo_retiro = NULL WHERE id = ?", id))).isEqualTo(1644);
	}

	@Test
	void e29ComunicadoSinAlcanceFalla() {
		Escuela s = escuela();
		assertThat(insertar(Fila.de(s.directora(), "DIRECCION").alcance("SECCION").titulo("E29 " + sufijo)))
				.isEqualTo(3819);
	}

	@Test
	void e43ReferenciasCruzadasFallan() {
		Escuela s = escuela();
		Long ajena = jdbc.queryForObject("SELECT MIN(id) FROM seccion WHERE colegio_id <> ?", Long.class, s.colegio());
		assertThat(insertar(Fila.de(s.directora(), "DIRECCION").aSeccion(ajena).titulo("E43 " + sufijo))).isIn(1452, 1644);
		Long id = enviarLegitimo(s, "E43 legítimo " + sufijo);
		Long familiaAjena = jdbc.queryForObject("SELECT MIN(id) FROM familia WHERE colegio_id <> ?", Long.class,
				s.colegio());
		assertThat(codigoAl(() -> jdbc.update("INSERT INTO comunicado_destinatario (colegio_id, comunicado_id, familia_id, "
				+ "alumnos, creado_en, creado_por, actualizado_en) VALUES (?, ?, ?, 1, NOW(6), 'x', NOW(6))", s.colegio(), id,
				familiaAjena))).isIn(1452, 1644);
	}

	@Test
	void e45SinProduccionNoHayComunicados() {
		Escuela s = escuela();
		Long preparacion = jdbc.queryForObject("SELECT MIN(id) FROM colegio WHERE estado = 'PREPARACION'", Long.class);
		assumeTrue(preparacion != null, "no hay un colegio en preparación");
		Long sesion = sesionDe(s.directora());
		assertThat(codigoAl(() -> jdbc.update(String.format(INSERTAR, "UTC_TIMESTAMP(6) - INTERVAL 5 HOUR"), preparacion,
				s.e().anioId(), "NORMAL", null, s.directora().getId(), "DIRECCION", "COLEGIO", null, null, null,
				"E45 " + sufijo, TEXTO, false, null, "0".repeat(64), "ENVIADO", null, sesion,
				s.directora().getNombreUsuario()))).isEqualTo(1644);
	}

	@Test
	void docenteNoApruebaSuPdf() {
		Escuela s = escuela();
		Long id = como(s.docente(), () -> envio.enviar(delDocente(s, "PDF del docente " + sufijo), pdf(false)));
		assertThat(jdbc.queryForObject("SELECT estado FROM comunicado WHERE id = ?", String.class, id))
				.isEqualTo("POR_APROBAR");
		Long solicitud = jdbc.queryForObject("SELECT solicitud_id FROM comunicado WHERE id = ?", Long.class, id);
		assertThat(codigoAl(() -> jdbc.update("UPDATE comunicado SET estado = 'ENVIADO', enviado_en = UTC_TIMESTAMP(6) - "
				+ "INTERVAL 5 HOUR WHERE id = ? AND solicitud_id = ?", id, solicitud))).isEqualTo(1644);
		// Dirección lo aprueba en la bandeja (con su firma) y sale.
		como(s.directora(), () -> {
			bandeja.aprobar(solicitud, null);
			return null;
		});
		assertThat(jdbc.queryForObject("SELECT estado FROM comunicado WHERE id = ?", String.class, id)).isEqualTo("ENVIADO");
		assertThat(jdbc.queryForObject("SELECT destinatarios FROM comunicado WHERE id = ?", Integer.class, id)).isEqualTo(2);
	}

	// ------------------------------------------------------------------------------------------------ CHECK con NULL

	@Test
	void losCheckConNullFallan() {
		Escuela s = escuela();
		assertThat(insertar(Fila.de(s.directora(), "DIRECCION").alcance("NIVEL").titulo("Nulo nivel " + sufijo)))
				.as("NIVEL sin nivel").isEqualTo(3819);
		assertThat(insertar(Fila.de(s.directora(), "DIRECCION").alcance("GRADO").titulo("Nulo grado " + sufijo)))
				.as("GRADO sin grado").isEqualTo(3819);
		assertThat(insertar(Fila.de(s.directora(), "DIRECCION").sinEnviadoEn().titulo("Nulo envío " + sufijo)))
				.as("ENVIADO sin enviado_en").isEqualTo(3819);
		assertThat(insertar(Fila.de(s.directora(), "DIRECCION").tipo("FE_DE_ERRATAS").titulo("Nulo corrige " + sufijo)))
				.as("fe de erratas sin original").isIn(3819, 1644);
		assertThat(insertar(Fila.de(s.docente(), "DOCENTE").aSeccion(s.seccion()).porAprobar().conAdjunto(false)
				.destinatarios(1).titulo("Por aprobar con destinatarios " + sufijo))).as("B13").isIn(3819, 1644);
		Long id = enviarLegitimo(s, "Nulo motivo " + sufijo);
		Integer sinMotivo = enTransaccion(s.directora(), () -> {
			firma.firmar(ClaveFirma.comunicadoRetirado(id));
			return codigoAl(() -> jdbc.update("UPDATE comunicado SET estado = 'RETIRADO', retirado_por = ?, retirado_en = "
					+ "NOW(6), motivo_retiro = NULL WHERE id = ?", s.directora().getNombreUsuario(), id));
		});
		assertThat(sinMotivo).as("RETIRADO sin motivo").isEqualTo(3819);
	}

	// ------------------------------------------------------------------------------------------------ B1 a B15

	@Test
	void b1FeDeErratasConMenosDestinatariosFalla() {
		Escuela s = escuela();
		Long original = enviarLegitimo(s, "B1 original " + sufijo);
		Fila fe = Fila.de(s.directora(), "DIRECCION").aSeccion(s.seccion()).feDeErratas(original).titulo("B1 fe " + sufijo);
		Integer codigo = enTransaccion(s.directora(), () -> {
			firmar(fe, s);
			jdbc.update(sql(fe), parametros(fe, s, sesionActual()));
			Long id = jdbc.queryForObject("SELECT MAX(id) FROM comunicado WHERE colegio_id = ?", Long.class, s.colegio());
			jdbc.update("INSERT INTO comunicado_destinatario (colegio_id, comunicado_id, familia_id, alumnos, creado_en, "
					+ "creado_por, actualizado_en) VALUES (?, ?, ?, 1, NOW(6), ?, NOW(6))", s.colegio(), id, s.familiaA(),
					s.directora().getNombreUsuario());
			return codigoAl(() -> jdbc.update("UPDATE comunicado SET destinatarios = 1 WHERE id = ?", id));
		});
		assertThat(codigo).isEqualTo(1644);
	}

	@Test
	void b1DestinatarioDespuesDeCerrarFalla() {
		Escuela s = escuela();
		Long id = enviarLegitimo(s, "B1 cerrada " + sufijo);
		assertThat(jdbc.queryForObject("SELECT destinatarios FROM comunicado WHERE id = ?", Integer.class, id)).isEqualTo(2);
		assertThat(codigoAl(() -> jdbc.update("INSERT INTO comunicado_destinatario (colegio_id, comunicado_id, familia_id, "
				+ "alumnos, creado_en, creado_por, actualizado_en) VALUES (?, ?, ?, 1, NOW(6), ?, NOW(6))", s.colegio(), id,
				s.familiaC(), s.directora().getNombreUsuario()))).isEqualTo(1644);
		// Y la lista cerrada no se reescribe.
		assertThat(codigoAl(() -> jdbc.update("UPDATE comunicado SET destinatarios = 3 WHERE id = ?", id))).isEqualTo(1644);
	}

	@Test
	void b2HuellaQueNoCoincideFalla() {
		Escuela s = escuela();
		String otra = HuellaComunicado.de(AlcanceComunicado.COLEGIO, null, null, null, "Otro título " + sufijo, TEXTO, null);
		assertThat(insertar(Fila.de(s.directora(), "DIRECCION").titulo("B2 " + sufijo).huella(otra))).isEqualTo(1644);
	}

	@Test
	void b2MismaHuellaFalla() {
		Escuela s = escuela();
		Fila fila = Fila.de(s.directora(), "DIRECCION").titulo("B2 misma " + sufijo);
		Long sesion = sesionDe(s.directora());
		Integer primero = enTransaccion(s.directora(), () -> {
			firmar(fila, s);
			return codigoAl(() -> jdbc.update(sql(fila), parametros(fila, s, sesion)));
		}, false);
		assertThat(primero).isNull();
		// La firma de la primera sigue vigente (5 minutos): la base rechaza el duplicado por la huella única.
		assertThat(insertar(fila.sinFirma())).isEqualTo(1062);
	}

	@Test
	void b3PdfConRevisionEnviadoDirectoFalla() {
		Escuela s = escuela();
		assertThat(insertar(Fila.de(s.directora(), "DIRECCION").conAdjunto(true).titulo("B3 directo " + sufijo)))
				.isEqualTo(1644);
		assertThat(insertar(Fila.de(s.directora(), "DIRECCION").conAdjunto(true).porAprobar().titulo("B3 por aprobar "
				+ sufijo))).isNull();
		// Correcciones del sprint 9 (auditoría, 1): TODO comunicado con PDF queda por aprobar, aunque el adjunto diga que no
		// requiere revisión (requiere_revision lo calcula la aplicación: con cc_app se podría escribir en falso).
		assertThat(insertar(Fila.de(s.directora(), "DIRECCION").conAdjunto(false).porAprobar().titulo("B3 sin revisión "
				+ sufijo))).as("Dirección con PDF sin revisión queda por aprobar").isNull();
		assertThat(insertar(Fila.de(s.directora(), "DIRECCION").conAdjunto(false).titulo("B3 directo sin revisión "
				+ sufijo))).as("un PDF «sin revisión» no sale directo").isEqualTo(1644);
		assertThat(insertar(Fila.de(s.promotora(), "PROMOTORIA").conAdjunto(false).titulo("B3 promotoría directo "
				+ sufijo))).as("tampoco el de Promotoría").isEqualTo(1644);
	}

	/** B3: el PDF por aprobar de Dirección no lo aprueba otra persona de Dirección (solo Promotoría). */
	@Test
	void b3DireccionApruebaSuPropioOrganoFalla() {
		Escuela s = escuela();
		Usuario otraDirectora = otraDireccion(s);
		Long id = como(s.directora(), () -> envio.enviar(new BorradorComunicado(EmisorRol.DIRECCION,
				AlcanceComunicado.COLEGIO, null, null, null, "Afiche B3 " + sufijo, TEXTO, false, true), pdf(true)));
		Long solicitud = jdbc.queryForObject("SELECT solicitud_id FROM comunicado WHERE id = ?", Long.class, id);
		// La aplicación no la deja aprobar.
		assertThatThrownBy(() -> como(otraDirectora, () -> {
			bandeja.aprobar(solicitud, null);
			return null;
		})).isInstanceOf(ReglaNegocioException.class);
		// Y saltándose la aplicación, con su firma: la base tampoco (trg_comunicado_estado).
		Integer codigo = enTransaccion(otraDirectora, () -> {
			firma.firmar(ClaveFirma.solicitud(solicitud, EstadoSolicitud.APROBADA));
			jdbc.update("UPDATE solicitud_cambio SET estado = 'APROBADA', pendiente = NULL, resuelto_por = ?, resuelto_en = "
					+ "NOW(6) WHERE id = ?", otraDirectora.getNombreUsuario(), solicitud);
			return codigoAl(() -> jdbc.update("UPDATE comunicado SET estado = 'ENVIADO', enviado_en = UTC_TIMESTAMP(6) - "
					+ "INTERVAL 5 HOUR WHERE id = ?", id));
		});
		assertThat(codigo).isEqualTo(1644);
		// Promotoría, sí.
		como(s.promotora(), () -> {
			bandeja.aprobar(solicitud, null);
			return null;
		});
		assertThat(jdbc.queryForObject("SELECT estado FROM comunicado WHERE id = ?", String.class, id)).isEqualTo("ENVIADO");
	}

	@Test
	void b4AnchoCeroFalla() {
		Escuela s = escuela();
		assertThat(insertar(Fila.de(s.directora(), "DIRECCION").titulo("B4 de​uda " + sufijo))).isEqualTo(1644);
		assertThat(insertar(Fila.de(s.directora(), "DIRECCION").titulo("B4 guion " + sufijo)
				.cuerpo("Reunión de pa­dres el martes por la tarde."))).isEqualTo(1644);
	}

	@Test
	void b6ParametrosSinElColegioFallan() {
		Escuela s = escuela();
		String mensaje = "INSERT INTO mensaje (colegio_id, clave, tipo, canal, destinatario_tipo, familia_id, apoderado_id, "
				+ "destino, plantilla, parametros, entidad, entidad_id, estado, creado_en, creado_por, actualizado_en) VALUES "
				+ "(?, ?, 'COMUNICADOS_RESUMEN', 'CORREO', 'APODERADO', ?, ?, ?, 'cc_comunicados_v2', ?, 'familia', ?, "
				+ "'PENDIENTE', NOW(6), 'sistema.comunicados', NOW(6))";
		String correo = jdbc.queryForObject("SELECT correo FROM apoderado WHERE id = ?", String.class, s.apoderadoA());
		String colegio = jdbc.queryForObject("SELECT nombre FROM colegio WHERE id = ?", String.class, s.colegio());
		// Sin el nombre del colegio delante: 1644.
		assertThat(codigoAl(() -> sistema().update(mensaje, s.colegio(), "ci-b6-" + sufijo, s.familiaA(), s.apoderadoA(),
				correo, "Otro colegio\n2 comunicados nuevos\nDirección", s.familiaA()))).isEqualTo(1644);
		// Con el nombre y una cifra con decimales después: 1644 (E40).
		assertThat(codigoAl(() -> sistema().update(mensaje, s.colegio(), "ci-e40-" + sufijo, s.familiaA(), s.apoderadoA(),
				correo, colegio + "\nDebe S/ 350.00\nDirección", s.familiaA()))).isEqualTo(1644);
	}

	@Test
	void b6ColegioConPalabraDeCobranzaPasa() {
		Escuela s = escuela();
		String nombre = jdbc.queryForObject("SELECT nombre FROM colegio WHERE id = ?", String.class, s.colegio());
		// cc_app no ve el cuerpo del trigger: la misma regla (ContenidoSinCobranzaTest la compara con la de 03) sobre el
		// resto de los parámetros, con un nombre de colegio que calza («Cobranza»).
		String expresion = ContenidoSinCobranza.EXPRESION;
		String parametros = "Colegio Cobranza del Norte\n2 comunicados nuevos\nDirección";
		assertThat(jdbc.queryForObject("SELECT REGEXP_LIKE(SUBSTRING(?, CHAR_LENGTH(?) + 2), ?, 'i')", Integer.class,
				parametros, "Colegio Cobranza del Norte", expresion)).isZero();
		assertThat(jdbc.queryForObject("SELECT REGEXP_LIKE(?, ?, 'i')", Integer.class, parametros, expresion)).isEqualTo(1);
		assertThat(nombre).isNotBlank();
	}

	@Test
	void b7PadresMadresYHoraPasan() {
		String expresion = ContenidoSinCobranza.EXPRESION;
		for (String pasa : new String[] { "Reunión de padres/madres", "a las 7.45 a.m.", "a las 12.30 h", "Pagán" }) {
			assertThat(jdbc.queryForObject("SELECT REGEXP_LIKE(?, ?, 'i')", Integer.class, pasa, expresion)).as(pasa)
					.isZero();
		}
		for (String no : new String[] { "S/ 350", "s/.350", "350.00", "su deuda", "Yape" }) {
			assertThat(jdbc.queryForObject("SELECT REGEXP_LIKE(?, ?, 'i')", Integer.class, no, expresion)).as(no)
					.isEqualTo(1);
		}
	}

	@Test
	void b9FeDeErratasDeRetiradoPorDocenteFalla() {
		Escuela s = escuela();
		Long id = como(s.docente(), () -> envio.enviar(delDocente(s, "B9 del docente " + sufijo), null));
		como(s.directora(), () -> {
			envio.retirar(id, MOTIVO);
			return null;
		});
		assertThat(insertar(Fila.de(s.docente(), "DOCENTE").aSeccion(s.seccion()).feDeErratas(id)
				.titulo("B9 fe del docente " + sufijo))).isEqualTo(1644);
		Long fe = como(s.directora(), () -> envio.enviarFeDeErratas(id, new BorradorComunicado(EmisorRol.DIRECCION,
				AlcanceComunicado.SECCION, null, null, s.seccion(), "B9 aclaración " + sufijo, TEXTO, false, true), null));
		assertThat(jdbc.queryForObject("SELECT estado FROM comunicado WHERE id = ?", String.class, fe)).isEqualTo("ENVIADO");
	}

	@Test
	void b13EnviadoEnFueraDeHoraFalla() {
		Escuela s = escuela();
		assertThat(insertar(Fila.de(s.directora(), "DIRECCION").enviadoEn("UTC_TIMESTAMP(6) - INTERVAL 5 HOUR - INTERVAL "
				+ "1 DAY").titulo("B13 ayer " + sufijo))).isEqualTo(1644);
	}

	@Test
	void b13PorAprobarConEnviadoEnFalla() {
		Escuela s = escuela();
		assertThat(insertar(Fila.de(s.docente(), "DOCENTE").aSeccion(s.seccion()).conAdjunto(false).porAprobar()
				.enviadoEn("UTC_TIMESTAMP(6) - INTERVAL 5 HOUR").titulo("B13 por aprobar " + sufijo))).isEqualTo(3819);
	}

	@Test
	void b14NominaSinDireccionOSinSesionFalla() {
		Escuela s = escuela();
		String exportar = "INSERT INTO exportacion_nomina (colegio_id, anio_escolar_id, seccion_id, perfil, filas, faltantes, "
				+ "codigo, sha256, sesion_id, creado_en, creado_por, actualizado_en) VALUES (?, ?, ?, "
				+ "'SIAGIE_MATRICULA_PROVISIONAL', 2, 0, ?, ?, ?, NOW(6), ?, NOW(6))";
		Usuario admin = s.e().equipo().administracion();
		Long sesionAdmin = sesionDe(admin);
		assertThat(codigoAl(() -> jdbc.update(exportar, s.colegio(), s.e().anioId(), s.seccion(), "NOM-B14A" + numero(3),
				"a".repeat(64), sesionAdmin, admin.getNombreUsuario()))).as("Administración").isEqualTo(1644);
		Long sesionDocente = sesionDe(s.docente());
		assertThat(codigoAl(() -> jdbc.update(exportar, s.colegio(), s.e().anioId(), s.seccion(), "NOM-B14B" + numero(3),
				"a".repeat(64), sesionDocente, s.directora().getNombreUsuario()))).as("con la sesión de otro")
				.isEqualTo(1644);
		// Por la aplicación (Dirección con su sesión), sí; y no se edita.
		como(s.directora(), () -> {
			nomina.registrarCodigoModular(new CodigoModularRequest(Nivel.PRIMARIA, "0" + numero(6), "0"));
			return nomina.exportar(s.seccion());
		});
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM exportacion_nomina WHERE seccion_id = ? AND creado_por = ?",
				Long.class, s.seccion(), s.directora().getNombreUsuario())).isEqualTo(1);
		assertThat(codigoAl(() -> jdbc.update("UPDATE exportacion_nomina SET version = version WHERE 1 = 0")))
				.isEqualTo(1142);
	}

	@Test
	void b15CodigoEnMinusculasFalla() {
		Escuela s = escuela();
		Long alumno = jdbc.queryForObject("SELECT MIN(al.id) FROM alumno al WHERE al.familia_id = ?", Long.class,
				s.familiaA());
		assertThat(codigoAl(() -> jdbc.update("UPDATE alumno SET codigo_estudiante = 'abcd1234' WHERE id = ?", alumno)))
				.isEqualTo(3819);
		assertThat(codigoAl(() -> jdbc.update("UPDATE alumno SET sexo = 'X' WHERE id = ?", alumno))).isEqualTo(3819);
		assertThat(codigoAl(() -> jdbc.update("UPDATE alumno SET codigo_estudiante = 'ABCD1234', sexo = 'F' WHERE id = ?",
				alumno))).isNull();
	}

	@Test
	void e40ResumenConMontoFalla() {
		Escuela s = escuela();
		String colegio = jdbc.queryForObject("SELECT nombre FROM colegio WHERE id = ?", String.class, s.colegio());
		String correo = jdbc.queryForObject("SELECT correo FROM apoderado WHERE id = ?", String.class, s.apoderadoA());
		String mensaje = "INSERT INTO mensaje (colegio_id, clave, tipo, canal, destinatario_tipo, familia_id, apoderado_id, "
				+ "destino, plantilla, parametros, entidad, entidad_id, estado, creado_en, creado_por, actualizado_en) VALUES "
				+ "(?, ?, ?, 'CORREO', 'APODERADO', ?, ?, ?, ?, ?, 'familia', ?, 'PENDIENTE', NOW(6), ?, NOW(6))";
		assertThat(codigoAl(() -> sistema().update(mensaje, s.colegio(), "ci-e40r-" + sufijo, "COMUNICADOS_RESUMEN",
				s.familiaA(), s.apoderadoA(), correo, "cc_comunicados_v2", colegio + "\nSu pensión vence hoy\nDirección",
				s.familiaA(), "sistema.comunicados"))).isEqualTo(1644);
		// Un recordatorio de cobranza con la plantilla de comunicados: 1644.
		assertThat(codigoAl(() -> sistema().update(mensaje, s.colegio(), "ci-e40p-" + sufijo, "RECORDATORIO_VENCIMIENTO",
				s.familiaA(), s.apoderadoA(), correo, "cc_comunicados_v2", colegio + "\nx", s.familiaA(),
				"sistema.mensajeria"))).isEqualTo(1644);
		// Con cc_app (no es el proceso): 1644.
		assertThat(codigoAl(() -> jdbc.update(mensaje, s.colegio(), "ci-e40a-" + sufijo, "COMUNICADOS_RESUMEN", s.familiaA(),
				s.apoderadoA(), correo, "cc_comunicados_v2", colegio + "\n1 comunicado nuevo\nDirección", s.familiaA(),
				"sistema.comunicados"))).isEqualTo(1644);
	}

	@Test
	void e37ComunicadosSinConsentimientoFalla() {
		Escuela s = escuela();
		String colegio = jdbc.queryForObject("SELECT nombre FROM colegio WHERE id = ?", String.class, s.colegio());
		assertThat(codigoAl(() -> sistema().update("INSERT INTO mensaje (colegio_id, clave, tipo, canal, destinatario_tipo, "
				+ "familia_id, apoderado_id, destino, plantilla, parametros, entidad, entidad_id, estado, creado_en, creado_por, "
				+ "actualizado_en) VALUES (?, ?, 'COMUNICADOS_RESUMEN', 'WHATSAPP', 'APODERADO', ?, ?, '+51900000000', "
				+ "'cc_comunicados_v2', ?, 'familia', ?, 'PENDIENTE', NOW(6), 'sistema.comunicados', NOW(6))", s.colegio(),
				"ci-e37-" + sufijo, s.familiaA(), s.apoderadoA(), colegio + "\n1 comunicado nuevo\nDirección",
				s.familiaA()))).isEqualTo(1644);
	}

	// ------------------------------------------------------------------------------------------------ correcciones

	/** Correcciones del sprint 9 (auditoría, 2; QA-S9-5): la frase de INDECOPI y un monto en letras no entran. */
	@Test
	void c2FraseIndecopiYMontoEnLetrasFallan() {
		Escuela s = escuela();
		assertThat(insertar(Fila.de(s.directora(), "DIRECCION").titulo("C2 examen " + sufijo).cuerpo("Los alumnos que no "
				+ "estén al día con la mensualidad no podrán rendir el examen bimestral."))).isEqualTo(1644);
		assertThat(insertar(Fila.de(s.directora(), "DIRECCION").titulo("C2 letras " + sufijo)
				.cuerpo("Para el paseo traigan S/ treinta en un sobre."))).isEqualTo(1644);
		String expresion = ContenidoSinCobranza.EXPRESION;
		for (String no : new String[] { "su saldo", "abonen hoy", "regularicen", "cancelar su deuda", "obligaciones económicas",
				"S/ trescientos" }) {
			assertThat(jdbc.queryForObject("SELECT REGEXP_LIKE(?, ?, 'i')", Integer.class, no, expresion)).as(no).isEqualTo(1);
		}
		for (String pasa : new String[] { "Av. Lima s/n", "padres/madres", "Saldaña y Abondano" }) {
			assertThat(jdbc.queryForObject("SELECT REGEXP_LIKE(?, ?, 'i')", Integer.class, pasa, expresion)).as(pasa)
					.isZero();
		}
	}

	/** Correcciones del sprint 9 (auditoría, 3): la versión de la configuración académica sube de a una. */
	@Test
	void c3ConfiguracionQueSaltaVersionesFalla() {
		Escuela s = escuela();
		como(s.directora(), () -> {
			// Valores distintos de los de por defecto (sin cambios, no se escribe la fila).
			configuraciones.cambiar(new ConfiguracionAcademica.Valores(java.time.LocalTime.of(9, 45),
					java.time.LocalTime.of(10, 40), java.time.LocalTime.of(15, 30), 4, 4, java.time.LocalTime.of(18, 0), 4));
			return null;
		});
		long v = jdbc.queryForObject("SELECT version FROM configuracion_academica WHERE colegio_id = ?", Long.class,
				s.colegio());
		String directora = s.directora().getNombreUsuario();
		String cambiar = "UPDATE configuracion_academica SET hora_aviso = '10:45:00', actualizado_por = ?, actualizado_en = "
				+ "NOW(6), version = ? WHERE colegio_id = ?";
		Integer saltando = enTransaccion(s.directora(), () -> {
			firma.firmar(ClaveFirma.configuracionAcademica(s.colegio(), v + 5));
			return codigoAl(() -> jdbc.update(cambiar, directora, v + 5, s.colegio()));
		});
		assertThat(saltando).as("de " + v + " a " + (v + 5)).isEqualTo(1644);
		Integer igual = enTransaccion(s.directora(), () -> {
			firma.firmar(ClaveFirma.configuracionAcademica(s.colegio(), v + 1));
			return codigoAl(() -> jdbc.update(cambiar.replace("version = ?", "version = version + ?"), directora, 0L,
					s.colegio()));
		});
		assertThat(igual).as("sin subir la versión").isEqualTo(1644);
		Integer deAUna = enTransaccion(s.directora(), () -> {
			firma.firmar(ClaveFirma.configuracionAcademica(s.colegio(), v + 1));
			return codigoAl(() -> jdbc.update(cambiar, directora, v + 1, s.colegio()));
		});
		assertThat(deAUna).as("de a una, con su firma").isNull();
	}

	/**
	 * Correcciones del sprint 9 (auditoría, 4): trg_comunicado_destinatario_cambio. El aviso y el recordatorio los marca
	 * solo cc_sistema, una vez y con un mensaje de comunicados de esa familia; la apertura, una vez y con la hora de la base.
	 */
	@Test
	void c4DestinatarioSoloCambiaComoDebe() {
		Escuela s = escuela();
		Long id = enviarLegitimo(s, "C4 " + sufijo);
		Long destinatario = jdbc.queryForObject("SELECT id FROM comunicado_destinatario WHERE comunicado_id = ? AND "
				+ "familia_id = ?", Long.class, id, s.familiaA());
		String avisar = "UPDATE comunicado_destinatario SET avisado_mensaje_id = ? WHERE id = ?";
		assertThat(codigoAl(() -> jdbc.update(avisar, 999_999_999L, destinatario))).as("con cc_app").isEqualTo(1644);
		assertThat(codigoAl(() -> sistema().update(avisar, 999_999_999L, destinatario))).as("sin su mensaje")
				.isIn(1644, 1452);
		assertThat(codigoAl(() -> jdbc.update("UPDATE comunicado_destinatario SET recordado_mensaje_id = ? WHERE id = ?",
				999_999_999L, destinatario))).as("el recordatorio con cc_app").isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE comunicado_destinatario SET abierto_en = UTC_TIMESTAMP(6) - INTERVAL "
				+ "5 HOUR - INTERVAL 1 DAY WHERE id = ?", destinatario))).as("abierto ayer").isEqualTo(1644);
		// La familia lo abre por la aplicación (con la hora de la base): pasa; y no se vuelve a marcar.
		UsuarioAutenticado rosa = cuentaDe(s, s.apoderadoA(), "c4");
		como(rosa, () -> lectura.abrir(id));
		assertThat(jdbc.queryForObject("SELECT abierto_en IS NOT NULL FROM comunicado_destinatario WHERE id = ?",
				Boolean.class, destinatario)).isTrue();
		assertThat(codigoAl(() -> jdbc.update("UPDATE comunicado_destinatario SET abierto_en = UTC_TIMESTAMP(6) - INTERVAL "
				+ "5 HOUR WHERE id = ?", destinatario))).as("reabrir").isEqualTo(1644);
	}

	// ------------------------------------------------------------------------------------------------ el flujo

	/**
	 * Sprint 9, tanda 3 (sección 7.5): los comunicados con los permisos mínimos. Dirección envía uno a la sección (con su
	 * firma); el docente, uno con PDF que Dirección aprueba en la bandeja; Dirección, uno con PDF de imagen que aprueba
	 * Promotoría; la fe de erratas llega a todos; la familia lo abre y confirma con su sesión; Dirección retira uno; y
	 * sistema.comunicados, con cc_sistema, prepara el resumen del día y anota qué mensaje avisó cada comunicado.
	 */
	@Test
	void flujoComunicadosConPermisosMinimos() {
		Escuela s = escuela();
		Long normal = como(s.directora(), () -> envio.enviar(aLaSeccion(s, "Flujo normal " + sufijo), null));
		assertThat(jdbc.queryForObject("SELECT destinatarios FROM comunicado WHERE id = ?", Integer.class, normal))
				.isEqualTo(2);
		Long delDocente = como(s.docente(), () -> envio.enviar(new BorradorComunicado(EmisorRol.DOCENTE,
				AlcanceComunicado.SECCION, null, null, s.seccion(), "Flujo PDF docente " + sufijo, TEXTO, false, true),
				pdf(false)));
		Long solicitudDocente = jdbc.queryForObject("SELECT solicitud_id FROM comunicado WHERE id = ?", Long.class, delDocente);
		como(s.directora(), () -> {
			bandeja.aprobar(solicitudDocente, null);
			return null;
		});
		Long deImagen = como(s.directora(), () -> envio.enviar(new BorradorComunicado(EmisorRol.DIRECCION,
				AlcanceComunicado.SECCION, null, null, s.seccion(), "Flujo afiche " + sufijo, TEXTO, false, true), pdf(true)));
		Long solicitudImagen = jdbc.queryForObject("SELECT solicitud_id FROM comunicado WHERE id = ?", Long.class, deImagen);
		como(s.promotora(), () -> {
			bandeja.aprobar(solicitudImagen, null);
			return null;
		});
		Long fe = como(s.directora(), () -> envio.enviarFeDeErratas(normal, aLaSeccion(s, "Flujo fe " + sufijo), null));
		for (Long id : new Long[] { delDocente, deImagen, fe }) {
			assertThat(jdbc.queryForObject("SELECT estado FROM comunicado WHERE id = ?", String.class, id))
					.isEqualTo("ENVIADO");
			assertThat(jdbc.queryForObject("SELECT destinatarios FROM comunicado WHERE id = ?", Integer.class, id))
					.isEqualTo(2);
		}
		// La familia lo abre y confirma con su sesión.
		UsuarioAutenticado rosa = cuentaDe(s, s.apoderadoA(), "a");
		como(rosa, () -> lectura.abrir(normal));
		como(rosa, () -> {
			lectura.confirmar(normal);
			return null;
		});
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM confirmacion_lectura WHERE comunicado_id = ? AND familia_id = ?",
				Long.class, normal, s.familiaA())).isEqualTo(1);
		// Dirección retira uno con su firma.
		como(s.directora(), () -> {
			envio.retirar(fe, MOTIVO);
			return null;
		});
		assertThat(jdbc.queryForObject("SELECT estado FROM comunicado WHERE id = ?", String.class, fe)).isEqualTo("RETIRADO");
		// sistema.comunicados (cc_sistema): el resumen del día a las 18:05 de Lima (hora del colegio por defecto: 18:00).
		LocalDate hoy = jdbc.queryForObject("SELECT DATE(UTC_TIMESTAMP() - INTERVAL 5 HOUR)", LocalDate.class);
		var resultado = avisos.enColegio(s.colegio(), LocalDateTime.of(hoy, java.time.LocalTime.of(18, 5)));
		boolean admite = hoy.getDayOfWeek() != java.time.DayOfWeek.SUNDAY
				&& !pe.edu.virgenmaria.cuentasclaras.comun.fecha.FeriadosNacionales.es(hoy);
		if (admite) {
			// La pasada corre con cc_sistema hasta el final (lee comunicados, destinatarios y consentimientos). Los contactos
			// de estas familias no están verificados (se verifican con su enlace, fase 2b), así que no se crea ningún resumen
			// y ningún destinatario queda marcado como avisado: sale en la pasada siguiente, cuando haya contacto. El resumen
			// creado lo prueba AvisosComunicadosTest (H2) y las reglas de trg_mensaje_nace, b6, e37 y e40 de esta clase.
			assertThat(resultado.resumenes()).isZero();
			assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comunicado_destinatario WHERE comunicado_id = ? AND "
					+ "avisado_mensaje_id IS NULL", Long.class, normal)).isEqualTo(2);
		}
	}

	// ------------------------------------------------------------------------------------------------ ayudas

	/** Una fila de comunicado para insertar por SQL (con la clave de cc_app), con valores por defecto válidos. */
	private record Fila(Usuario quien, String rol, String alcance, Long seccion, String nivel, String grado, String titulo,
			String cuerpo, boolean urgente, Boolean revisionAdjunto, Usuario autorAdjunto, String estado, String enviadoEn,
			Integer destinatarios, String tipo, Long corrige, String huellaForzada, boolean firmar, Usuario sesionDe) {

		static Fila de(Usuario quien, String rol) {
			return new Fila(quien, rol, "COLEGIO", null, null, null, "Comunicado de prueba", TEXTO, false, null, null,
					"ENVIADO", "UTC_TIMESTAMP(6) - INTERVAL 5 HOUR", null, "NORMAL", null, null, true, null);
		}

		Fila aSeccion(Long id) {
			return new Fila(quien, rol, "SECCION", id, null, null, titulo, cuerpo, urgente, revisionAdjunto, autorAdjunto,
					estado, enviadoEn, destinatarios, tipo, corrige, huellaForzada, firmar, sesionDe);
		}

		Fila alcance(String nuevo) {
			return new Fila(quien, rol, nuevo, seccion, nivel, grado, titulo, cuerpo, urgente, revisionAdjunto, autorAdjunto,
					estado, enviadoEn, destinatarios, tipo, corrige, huellaForzada, firmar, sesionDe);
		}

		Fila titulo(String nuevo) {
			return new Fila(quien, rol, alcance, seccion, nivel, grado, nuevo, cuerpo, urgente, revisionAdjunto, autorAdjunto,
					estado, enviadoEn, destinatarios, tipo, corrige, huellaForzada, firmar, sesionDe);
		}

		Fila cuerpo(String nuevo) {
			return new Fila(quien, rol, alcance, seccion, nivel, grado, titulo, nuevo, urgente, revisionAdjunto, autorAdjunto,
					estado, enviadoEn, destinatarios, tipo, corrige, huellaForzada, firmar, sesionDe);
		}

		Fila comoUrgente() {
			return new Fila(quien, rol, alcance, seccion, nivel, grado, titulo, cuerpo, true, revisionAdjunto, autorAdjunto,
					estado, enviadoEn, destinatarios, tipo, corrige, huellaForzada, firmar, sesionDe);
		}

		Fila conAdjunto(boolean revision) {
			return new Fila(quien, rol, alcance, seccion, nivel, grado, titulo, cuerpo, urgente, revision, autorAdjunto,
					estado, enviadoEn, destinatarios, tipo, corrige, huellaForzada, firmar, sesionDe);
		}

		Fila adjuntoDe(Usuario autor) {
			return new Fila(quien, rol, alcance, seccion, nivel, grado, titulo, cuerpo, urgente, revisionAdjunto, autor,
					estado, enviadoEn, destinatarios, tipo, corrige, huellaForzada, firmar, sesionDe);
		}

		Fila porAprobar() {
			return new Fila(quien, rol, alcance, seccion, nivel, grado, titulo, cuerpo, urgente, revisionAdjunto, autorAdjunto,
					"POR_APROBAR", null, destinatarios, tipo, corrige, huellaForzada, firmar, sesionDe);
		}

		Fila enviadoEn(String expresion) {
			return new Fila(quien, rol, alcance, seccion, nivel, grado, titulo, cuerpo, urgente, revisionAdjunto, autorAdjunto,
					estado, expresion, destinatarios, tipo, corrige, huellaForzada, firmar, sesionDe);
		}

		Fila sinEnviadoEn() {
			return enviadoEn(null);
		}

		Fila destinatarios(Integer cuantos) {
			return new Fila(quien, rol, alcance, seccion, nivel, grado, titulo, cuerpo, urgente, revisionAdjunto, autorAdjunto,
					estado, enviadoEn, cuantos, tipo, corrige, huellaForzada, firmar, sesionDe);
		}

		Fila tipo(String nuevo) {
			return new Fila(quien, rol, alcance, seccion, nivel, grado, titulo, cuerpo, urgente, revisionAdjunto, autorAdjunto,
					estado, enviadoEn, destinatarios, nuevo, corrige, huellaForzada, firmar, sesionDe);
		}

		Fila feDeErratas(Long original) {
			return new Fila(quien, rol, alcance, seccion, nivel, grado, titulo, cuerpo, urgente, revisionAdjunto, autorAdjunto,
					estado, enviadoEn, destinatarios, "FE_DE_ERRATAS", original, huellaForzada, firmar, sesionDe);
		}

		Fila huella(String forzada) {
			return new Fila(quien, rol, alcance, seccion, nivel, grado, titulo, cuerpo, urgente, revisionAdjunto, autorAdjunto,
					estado, enviadoEn, destinatarios, tipo, corrige, forzada, firmar, sesionDe);
		}

		Fila sinFirma() {
			return new Fila(quien, rol, alcance, seccion, nivel, grado, titulo, cuerpo, urgente, revisionAdjunto, autorAdjunto,
					estado, enviadoEn, destinatarios, tipo, corrige, huellaForzada, false, sesionDe);
		}

		Fila conSesionDe(Usuario otro) {
			return new Fila(quien, rol, alcance, seccion, nivel, grado, titulo, cuerpo, urgente, revisionAdjunto, autorAdjunto,
					estado, enviadoEn, destinatarios, tipo, corrige, huellaForzada, firmar, otro);
		}
	}

	/** Inserta la fila por SQL en una transacción que se revierte (con la firma de quien escribe, si se pide). */
	private Integer insertar(Fila f) {
		Escuela s = escuela();
		Long sesion = sesionDe(f.sesionDe() != null ? f.sesionDe() : f.quien());
		return enTransaccion(f.quien(), () -> {
			Fila conAdjunto = f;
			if (f.revisionAdjunto() != null) {
				conAdjunto = f;
			}
			Long adjunto = f.revisionAdjunto() == null ? null : insertarAdjunto(s, f);
			firmar(f, s);
			return codigoAl(() -> jdbc.update(sql(f), parametros(f, s, sesion, adjunto)));
		});
	}

	private static final byte[] PDF = PdfsDePrueba.normal();

	private static final String SHA_PDF = HuellaComunicado.sha256(PDF);

	private Long insertarAdjunto(Escuela s, Fila f) {
		Usuario autor = f.autorAdjunto() != null ? f.autorAdjunto() : f.quien();
		jdbc.update("INSERT INTO comunicado_adjunto (colegio_id, contenido, bytes, paginas, sha256, sha256_original, "
				+ "requiere_revision, creado_en, creado_por, actualizado_en) VALUES (?, ?, ?, 1, ?, ?, ?, NOW(6), ?, NOW(6))",
				s.colegio(), PDF, PDF.length, SHA_PDF, SHA_PDF, f.revisionAdjunto(), autor.getNombreUsuario());
		return jdbc.queryForObject("SELECT MAX(id) FROM comunicado_adjunto WHERE colegio_id = ?", Long.class, s.colegio());
	}

	private String huellaDe(Fila f) {
		if (f.huellaForzada() != null) {
			return f.huellaForzada();
		}
		return HuellaComunicado.de(AlcanceComunicado.valueOf(f.alcance()), f.nivel() == null ? null : Nivel.valueOf(f.nivel()),
				f.grado() == null ? null : Grado.valueOf(f.grado()), f.seccion(), f.titulo(), f.cuerpo(),
				f.revisionAdjunto() == null ? null : SHA_PDF);
	}

	private void firmar(Fila f, Escuela s) {
		if (f.firmar()) {
			firma.firmar(ClaveFirma.comunicadoEnviar(huellaDe(f)));
		}
	}

	private static String sql(Fila f) {
		return String.format(INSERTAR, f.enviadoEn() == null ? "NULL" : f.enviadoEn());
	}

	private Object[] parametros(Fila f, Escuela s, Long sesion) {
		return parametros(f, s, sesion, null);
	}

	private Object[] parametros(Fila f, Escuela s, Long sesion, Long adjunto) {
		return new Object[] { s.colegio(), s.e().anioId(), f.tipo(), f.corrige(), f.quien().getId(), f.rol(), f.alcance(),
				f.nivel(), f.grado(), f.seccion(), f.titulo(), f.cuerpo(), f.urgente(), adjunto, huellaDe(f), f.estado(),
				f.destinatarios(), sesion, f.quien().getNombreUsuario() };
	}

	private Long enviarLegitimo(Escuela s, String titulo) {
		return como(s.directora(), () -> envio.enviar(aLaSeccion(s, titulo), null));
	}

	private static BorradorComunicado aLaSeccion(Escuela s, String titulo) {
		return new BorradorComunicado(EmisorRol.DIRECCION, AlcanceComunicado.SECCION, null, null, s.seccion(), titulo, TEXTO,
				false, true);
	}

	private static BorradorComunicado delDocente(Escuela s, String titulo) {
		return new BorradorComunicado(EmisorRol.DOCENTE, AlcanceComunicado.SECCION, null, null, s.seccion(), titulo, TEXTO,
				false, true);
	}

	private static AdjuntoValidado pdf(boolean revision) {
		return new AdjuntoValidado(PDF, 1, SHA_PDF, SHA_PDF, revision);
	}

	/** La cuenta del portal de un apoderado (nace enlazada a él, por la ruta de identidad). */
	private UsuarioAutenticado cuentaDe(Escuela s, Long apoderado, String marca) {
		String documento = jdbc.queryForObject("SELECT numero_documento FROM apoderado WHERE id = ?", String.class, apoderado);
		String nombre = "familia.com." + marca + "." + sufijo + "." + apoderado;
		Usuario cuenta = RutaConexion.identidad(() -> ContextoColegio.en(s.colegio(), () -> usuarios.findByApoderadoId(
				apoderado).orElseGet(() -> usuarios.saveAndFlush(Usuario.deApoderado(documento, nombre, null,
						codificador.encode(UsuariosDePrueba.CLAVE), apoderado)))));
		return new UsuarioAutenticado(cuenta.getId(), s.colegio(), cuenta.getNombreUsuario(), nombre, null, true, false,
				false, EnumSet.of(Rol.APODERADO), apoderado);
	}

	/** Otra persona de Dirección: Promotoría le da el rol y la Dirección actual lo aprueba en la bandeja (CAMBIO_ROLES). */
	private Usuario otraDireccion(Escuela s) {
		Usuario cuenta = UsuariosDePrueba.guardar(usuarios, codificador, s.colegio(), "dir2.com." + sufijo,
				UsuariosDePrueba.CLAVE, false, Rol.ADMINISTRACION);
		// Un rol directivo nunca se aplica directo (cambiarRoles devuelve true: quedó pedido): lo aprueba la Dirección actual.
		como(s.promotora(), () -> servicioUsuarios.cambiarRoles(cuenta.getId(), new CambiarRolesRequest(
				EnumSet.of(Rol.DIRECTOR), "Subdirección del colegio para las pruebas de comunicados")));
		Long solicitud = jdbc.queryForObject("SELECT MAX(id) FROM solicitud_cambio WHERE colegio_id = ? AND estado = "
				+ "'PENDIENTE' AND entidad_id = ?", Long.class, s.colegio(), cuenta.getId());
		assertThat(solicitud).as("el cambio de roles quedó pedido").isNotNull();
		como(s.directora(), () -> {
			bandeja.aprobar(solicitud, null);
			return null;
		});
		assertThat(jdbc.queryForList("SELECT rol FROM usuario_rol WHERE usuario_id = ?", String.class, cuenta.getId()))
				.contains("DIRECTOR");
		return ContextoColegio.en(s.colegio(), () -> usuarios.findById(cuenta.getId()).orElseThrow());
	}

	private JdbcTemplate sistema() {
		FuenteDatosEnrutada enrutada = (FuenteDatosEnrutada) fuenteDatos;
		assertThat(enrutada.separadas()).isTrue();
		return new JdbcTemplate(enrutada.sistema());
	}

	private String numero(int digitos) {
		long n = Math.floorMod(System.nanoTime(), (long) Math.pow(10, digitos));
		return String.format("%0" + digitos + "d", n);
	}

	private <T> T como(Usuario quien, Supplier<T> operacion) {
		UsuariosDePrueba.iniciarSesion(quien);
		try {
			return operacion.get();
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	private <T> T como(UsuarioAutenticado quien, Supplier<T> operacion) {
		UsuariosDePrueba.iniciarSesion(quien);
		try {
			return operacion.get();
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	private Long sesionDe(Usuario quien) {
		return como(quien, () -> tokens.actual().map(SesionAbierta::sesionId).orElseThrow());
	}

	private Long sesionDe(UsuarioAutenticado quien) {
		return como(quien, () -> tokens.actual().map(SesionAbierta::sesionId).orElseThrow());
	}

	private Long sesionActual() {
		return tokens.actual().map(SesionAbierta::sesionId).orElseThrow();
	}

	/**
	 * En una transacción con la persona YA en sesión (Hibernate fija el colegio al empezar la transacción), que se revierte
	 * al terminar.
	 */
	private <T> T enTransaccion(Usuario quien, Supplier<T> operacion) {
		return enTransaccion(quien, operacion, true);
	}

	private <T> T enTransaccion(Usuario quien, Supplier<T> operacion, boolean revertir) {
		UsuariosDePrueba.iniciarSesion(quien);
		try {
			TransactionCallback<T> callback = t -> {
				T r = operacion.get();
				if (revertir) {
					t.setRollbackOnly();
				}
				return r;
			};
			return new TransactionTemplate(transacciones).execute(callback);
		}
		finally {
			SecurityContextHolder.clearContext();
		}
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

	@SuppressWarnings("unused")
	private static final ZoneId LIMA = ZoneId.of("America/Lima");
}
