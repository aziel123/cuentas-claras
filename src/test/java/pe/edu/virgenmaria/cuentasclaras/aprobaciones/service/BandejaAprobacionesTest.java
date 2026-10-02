package pe.edu.virgenmaria.cuentasclaras.aprobaciones.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.ApoderadoCorregido;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.ApoderadoRequest;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.CambiarResponsableRequest;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.RegistroResultado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.RetirarAlumnoRequest;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Parentesco;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.TipoDocumento;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioFamilias;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.dto.SolicitudVista;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.Estructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.DIRECCION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.DIRECCION_Y_ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.PROMOTORIA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.MOTIVO;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.ultimoEvento;

/**
 * Auditoría antifraude del sprint 2 (A4, A5, A6): retiros, contactos y responsables de pago los pide una persona y los
 * aprueba otra. Cada prueba reproduce el ataque: sin la corrección, una sola persona aplicaba el cambio.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class BandejaAprobacionesTest {

	private static final LocalDate HOY = LocalDate.of(2026, 10, 2);

	@Autowired
	private BandejaAprobaciones bandeja;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioFamilias familias;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private List<ManejadorSolicitud> manejadores;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private PlatformTransactionManager transacciones;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private Clock reloj;

	private Estructura escuela;

	@BeforeEach
	void preparar() {
		((RelojAjustable) reloj).fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
		como(ADMINISTRACION);
		escuela = EscenarioEscolar.crearEstructura(estructura);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void cadaTipoDeSolicitudTieneUnSoloManejador() {
		Map<TipoSolicitud, Long> porTipo = new TransactionTemplate(transacciones).execute(estado -> manejadores.stream()
				.collect(java.util.stream.Collectors.groupingBy(ManejadorSolicitud::tipo,
						java.util.stream.Collectors.counting())));
		assertThat(porTipo).hasSize(TipoSolicitud.values().length).allSatisfy((tipo, cantidad) ->
				assertThat(cantidad).as(tipo.name()).isEqualTo(1L));
	}

	/** A6: antes, Administración retiraba sola al alumno (y con él, todo lo que debía de ahí en adelante). */
	@Test
	void retiroQuedaPendienteHastaQueOtraPersonaLoApruebe() {
		RegistroResultado mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(escuela.primaria5A2026()));

		alumnos.retirar(mateo.alumnoId(), new RetirarAlumnoRequest(HOY, "Se mudó a Arequipa con su familia"));

		assertThat(jdbc.queryForObject("SELECT estado FROM alumno WHERE id = ?", String.class, mateo.alumnoId()))
				.isEqualTo("ACTIVO");
		assertThat(jdbc.queryForObject("SELECT estado FROM matricula WHERE alumno_id = ?", String.class,
				mateo.alumnoId())).isEqualTo("ACTIVA");
		Map<String, Object> solicitud = jdbc.queryForMap("SELECT * FROM solicitud_cambio");
		assertThat(solicitud).containsEntry("tipo", "RETIRO_ALUMNO").containsEntry("estado", "PENDIENTE")
				.containsEntry("solicitado_por", "administracion").containsEntry("entidad_id", mateo.alumnoId());
		assertThat((String) ultimoEvento(jdbc, "SOLICITUD_CREADA").get("valor_nuevo"))
				.contains("Retirar a Mateo Quispe Huamán desde el 02/10/2026");
		assertThat(alumnos.obtenerFicha(mateo.alumnoId()).solicitudesPendientes()).singleElement()
				.asString().contains("Retiro de alumno", "pedida por administracion");
		// Una sola pendiente por alumno.
		assertThatThrownBy(() -> alumnos.retirar(mateo.alumnoId(), new RetirarAlumnoRequest(HOY, MOTIVO)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("pendiente");

		// Administración no resuelve solicitudes.
		assertThatThrownBy(bandeja::bandeja).isInstanceOf(AccessDeniedException.class);

		como(DIRECCION);
		Long id = idDe(solicitud);
		bandeja.aprobar(id, null);

		assertThat(jdbc.queryForMap("SELECT * FROM alumno WHERE id = ?", mateo.alumnoId()))
				.containsEntry("estado", "RETIRADO").containsEntry("retirado_por", "administracion");
		assertThat(jdbc.queryForObject("SELECT estado FROM matricula WHERE alumno_id = ?", String.class,
				mateo.alumnoId())).isEqualTo("RETIRADA");
		assertThat((String) ultimoEvento(jdbc, "ALUMNO_RETIRADO").get("detalle"))
				.contains("Pedido por administracion, aprobado por director", "Arequipa");
		assertThat(jdbc.queryForMap("SELECT * FROM solicitud_cambio WHERE id = ?", id))
				.containsEntry("estado", "APROBADA").containsEntry("resuelto_por", "director")
				.containsEntry("pendiente", null);
		assertThat(ultimoEvento(jdbc, "SOLICITUD_APROBADA")).containsEntry("nombre_usuario", "director");
		assertThat(bandeja.bandeja().resueltas()).singleElement().extracting(SolicitudVista::estado)
				.isEqualTo("APROBADA");
	}

	@Test
	void quienPideNoApruebaNiRechazaYElIntentoQuedaAuditado() {
		RegistroResultado mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(escuela.primaria5A2026()));
		// El subdirector también es de Administración: puede pedir el retiro...
		como(DIRECCION_Y_ADMINISTRACION);
		alumnos.retirar(mateo.alumnoId(), new RetirarAlumnoRequest(HOY, MOTIVO));
		Long id = jdbc.queryForObject("SELECT id FROM solicitud_cambio", Long.class);

		// ...pero no aprobarlo él mismo, ni rechazarlo.
		assertThat(bandeja.bandeja().pendientes()).singleElement().extracting(SolicitudVista::puedeResolver)
				.isEqualTo(false);
		assertThatThrownBy(() -> bandeja.aprobar(id, null)).isInstanceOf(AutoaprobacionSolicitudException.class)
				.hasMessageContaining("debe hacerlo otra persona");
		assertThatThrownBy(() -> bandeja.rechazar(id, MOTIVO)).isInstanceOf(AutoaprobacionSolicitudException.class);

		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'AUTOAPROBACION_RECHAZADA'")).isEqualTo(2);
		assertThat(jdbc.queryForObject("SELECT estado FROM solicitud_cambio", String.class)).isEqualTo("PENDIENTE");
		assertThat(jdbc.queryForObject("SELECT estado FROM alumno WHERE id = ?", String.class, mateo.alumnoId()))
				.isEqualTo("ACTIVO");

		como(PROMOTORIA);
		bandeja.aprobar(id, "Confirmado con la familia por teléfono");
		assertThat(jdbc.queryForObject("SELECT estado FROM alumno WHERE id = ?", String.class, mateo.alumnoId()))
				.isEqualTo("RETIRADO");
	}

	/** A5: quien creó la cuenta del solicitante en los últimos 30 días podría estar usándola. */
	@Test
	void quienCreoLaCuentaDelSolicitanteNoApruebaSuSolicitud() {
		UsuariosDePrueba.guardar(usuarios, codificador, 1L, "administracion", UsuariosDePrueba.CLAVE, false,
				Rol.ADMINISTRACION);
		jdbc.update("UPDATE usuario SET creado_por = 'director', creado_en = ? WHERE nombre_usuario = 'administracion'",
				java.sql.Timestamp.valueOf("2026-09-20 10:00:00"));
		RegistroResultado mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(escuela.primaria5A2026()));
		alumnos.retirar(mateo.alumnoId(), new RetirarAlumnoRequest(HOY, MOTIVO));
		Long id = jdbc.queryForObject("SELECT id FROM solicitud_cambio", Long.class);

		como(DIRECCION);
		assertThatThrownBy(() -> bandeja.aprobar(id, null)).isInstanceOf(AutoaprobacionSolicitudException.class)
				.hasMessageContaining("cuenta que creaste");

		// Pasados 30 días ya no cuenta como participante.
		jdbc.update("UPDATE usuario SET creado_en = ? WHERE nombre_usuario = 'administracion'",
				java.sql.Timestamp.valueOf("2026-08-01 10:00:00"));
		bandeja.aprobar(id, null);
		assertThat(jdbc.queryForObject("SELECT estado FROM alumno WHERE id = ?", String.class, mateo.alumnoId()))
				.isEqualTo("RETIRADO");
	}

	@Test
	void laBaseRechazaQueQuienPideResuelva() {
		RegistroResultado mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(escuela.primaria5A2026()));
		alumnos.retirar(mateo.alumnoId(), new RetirarAlumnoRequest(HOY, MOTIVO));

		assertThatThrownBy(() -> jdbc.update("UPDATE solicitud_cambio SET estado = 'APROBADA', pendiente = NULL, "
				+ "resuelto_por = solicitado_por, resuelto_en = creado_en")).isInstanceOf(DataIntegrityViolationException.class);
		// Rechazo sin comentario, tampoco.
		assertThatThrownBy(() -> jdbc.update("UPDATE solicitud_cambio SET estado = 'RECHAZADA', pendiente = NULL, "
				+ "resuelto_por = 'director', resuelto_en = creado_en")).isInstanceOf(DataIntegrityViolationException.class);
		// Dos pendientes del mismo tipo para el mismo alumno, tampoco.
		assertThatThrownBy(() -> jdbc.update("INSERT INTO solicitud_cambio (colegio_id, tipo, entidad, entidad_id, "
				+ "resumen, datos, motivo, estado, pendiente, solicitado_por, creado_en, creado_por, actualizado_en) "
				+ "SELECT colegio_id, tipo, entidad, entidad_id, resumen, datos, motivo, estado, pendiente, "
				+ "solicitado_por, creado_en, creado_por, actualizado_en FROM solicitud_cambio"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void rechazarExigeMotivoYNoCambiaNada() {
		RegistroResultado mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(escuela.primaria5A2026()));
		alumnos.retirar(mateo.alumnoId(), new RetirarAlumnoRequest(HOY, MOTIVO));
		Long id = jdbc.queryForObject("SELECT id FROM solicitud_cambio", Long.class);

		como(PROMOTORIA);
		assertThatThrownBy(() -> bandeja.rechazar(id, "no")).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("entre 10 y 500");
		assertThatThrownBy(() -> bandeja.rechazar(id, "=HYPERLINK(\"http://x\")")).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("no puede empezar con «=»");
		bandeja.rechazar(id, "La familia dijo que el alumno sigue en el colegio");

		assertThat(jdbc.queryForObject("SELECT estado FROM alumno WHERE id = ?", String.class, mateo.alumnoId()))
				.isEqualTo("ACTIVO");
		assertThat(jdbc.queryForMap("SELECT estado, comentario FROM solicitud_cambio")).containsEntry("estado", "RECHAZADA")
				.containsEntry("comentario", "La familia dijo que el alumno sigue en el colegio");
		assertThat(ultimoEvento(jdbc, "SOLICITUD_RECHAZADA")).containsEntry("nombre_usuario", "promotor");
		assertThatThrownBy(() -> bandeja.aprobar(id, null)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("ya fue rechazada por promotor");
		// Resuelta la anterior, se puede volver a pedir.
		como(ADMINISTRACION);
		alumnos.retirar(mateo.alumnoId(), new RetirarAlumnoRequest(HOY, MOTIVO));
		assertThat(contar(jdbc, "solicitud_cambio")).isEqualTo(2);
	}

	/** A6: una fecha de retiro anterior a la matrícula evitaba todas las cuotas del año. */
	@Test
	void retiroAnteriorALaMatriculaEsRechazado() {
		RegistroResultado mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(escuela.primaria5A2026()));

		assertThatThrownBy(() -> alumnos.retirar(mateo.alumnoId(), new RetirarAlumnoRequest(LocalDate.of(2026, 2, 15),
				MOTIVO))).isInstanceOf(ReglaNegocioException.class)
				.hasMessage("La fecha de retiro (15/02/2026) es anterior a la matrícula de Mateo Quispe Huamán en 2026 "
						+ "(02/03/2026).");
		assertThatThrownBy(() -> alumnos.retirar(mateo.alumnoId(), new RetirarAlumnoRequest(HOY.plusDays(1), MOTIVO)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("futura");
		assertThat(contar(jdbc, "solicitud_cambio")).isZero();

		// También al aprobar: se vuelve a validar (la base pudo cambiar entre la solicitud y la aprobación).
		alumnos.retirar(mateo.alumnoId(), new RetirarAlumnoRequest(LocalDate.of(2026, 3, 2), MOTIVO));
		jdbc.update("UPDATE matricula SET fecha_matricula = '2026-04-01'");
		como(DIRECCION);
		Long id = jdbc.queryForObject("SELECT id FROM solicitud_cambio", Long.class);
		assertThatThrownBy(() -> bandeja.aprobar(id, null)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("es anterior a la matrícula");
		assertThat(jdbc.queryForObject("SELECT estado FROM solicitud_cambio", String.class)).isEqualTo("PENDIENTE");
	}

	/** A4: cambiar el celular desviaba los avisos de pago lejos del padre real. */
	@Test
	void cambioDeCelularQuedaComoSolicitudYNoSeAplicaHastaAprobarse() {
		RegistroResultado mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(escuela.primaria5A2026()));
		Long rosa = jdbc.queryForObject("SELECT responsable_pago_id FROM alumno WHERE id = ?", Long.class,
				mateo.alumnoId());

		ApoderadoCorregido resultado = familias.actualizarApoderado(rosa, new ApoderadoRequest(TipoDocumento.DNI,
				"45678912", "Huamán", "Ccori", "Rosa", Parentesco.TUTOR_LEGAL, "999 888 777", EscenarioEscolar.CORREO_ROSA,
				"Perdió su celular; dio el número nuevo por teléfono"));

		assertThat(resultado.datosCorregidos()).isTrue();
		assertThat(resultado.contactoSolicitado()).isTrue();
		assertThat(resultado.aviso()).contains("lo aprueba otra persona");
		// El parentesco se corrige al momento; el celular NO.
		assertThat(jdbc.queryForMap("SELECT parentesco, telefono_whatsapp FROM apoderado WHERE id = ?", rosa))
				.containsEntry("parentesco", "TUTOR_LEGAL").containsEntry("telefono_whatsapp", "+51987654321");
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'APODERADO_CONTACTO_CAMBIADO'")).isZero();
		Map<String, Object> solicitud = jdbc.queryForMap("SELECT * FROM solicitud_cambio");
		assertThat(solicitud).containsEntry("tipo", "CAMBIO_CONTACTO_APODERADO").containsEntry("entidad", "apoderado");
		// El resumen va enmascarado; los números completos solo en los datos de la solicitud.
		assertThat((String) solicitud.get("resumen")).contains("+51 *** *** 321", "+51 *** *** 777")
				.doesNotContain("987654321", "999888777");
		assertThat(familias.obtener(mateo.familiaId()).solicitudesPendientes()).singleElement().asString()
				.contains("Cambio de celular o correo");

		como(DIRECCION);
		bandeja.aprobar(idDe(solicitud), null);

		assertThat(jdbc.queryForObject("SELECT telefono_whatsapp FROM apoderado WHERE id = ?", String.class, rosa))
				.isEqualTo("+51999888777");
		Map<String, Object> evento = ultimoEvento(jdbc, "APODERADO_CONTACTO_CAMBIADO");
		assertThat(evento).containsEntry("valor_anterior", "WhatsApp +51 *** *** 321")
				.containsEntry("valor_nuevo", "WhatsApp +51 *** *** 777");
		assertThat((String) evento.get("detalle")).contains("Pedido por administracion, aprobado por director");
	}

	@Test
	void contactoQueCambioDesdeLaSolicitudNoSeAplica() {
		RegistroResultado mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(escuela.primaria5A2026()));
		Long rosa = jdbc.queryForObject("SELECT responsable_pago_id FROM alumno WHERE id = ?", Long.class,
				mateo.alumnoId());
		familias.actualizarApoderado(rosa, new ApoderadoRequest(TipoDocumento.DNI, "45678912", "Huamán", "Ccori", "Rosa",
				Parentesco.MADRE, "999888777", EscenarioEscolar.CORREO_ROSA, MOTIVO));
		jdbc.update("UPDATE apoderado SET telefono_whatsapp = '+51911111111' WHERE id = ?", rosa);

		como(DIRECCION);
		Long id = jdbc.queryForObject("SELECT id FROM solicitud_cambio", Long.class);
		assertThatThrownBy(() -> bandeja.aprobar(id, null)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("cambió desde que se pidió");
		assertThat(jdbc.queryForObject("SELECT telefono_whatsapp FROM apoderado WHERE id = ?", String.class, rosa))
				.isEqualTo("+51911111111");
	}

	/** A4: el responsable de pago decide a quién se cobra y quién recibe los avisos. */
	@Test
	void cambioDeResponsableQuedaComoSolicitud() {
		RegistroResultado mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(escuela.primaria5A2026()));
		Long rosa = jdbc.queryForObject("SELECT responsable_pago_id FROM alumno WHERE id = ?", Long.class,
				mateo.alumnoId());
		Long juan = familias.agregarApoderado(mateo.familiaId(), EscenarioEscolar.apoderado("40112233", "Quispe",
				"Juan", Parentesco.PADRE, "976540129", null, null));

		alumnos.cambiarResponsablePago(mateo.alumnoId(), new CambiarResponsableRequest(juan, null, MOTIVO));

		assertThat(jdbc.queryForObject("SELECT responsable_pago_id FROM alumno WHERE id = ?", Long.class,
				mateo.alumnoId())).isEqualTo(rosa);
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'RESPONSABLE_PAGO_CAMBIADO'")).isZero();
		assertThatThrownBy(() -> alumnos.cambiarResponsablePago(mateo.alumnoId(),
				new CambiarResponsableRequest(rosa, null, MOTIVO))).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("ya es el responsable de pago");

		como(PROMOTORIA);
		bandeja.aprobar(jdbc.queryForObject("SELECT id FROM solicitud_cambio", Long.class), null);
		assertThat(jdbc.queryForObject("SELECT responsable_pago_id FROM alumno WHERE id = ?", Long.class,
				mateo.alumnoId())).isEqualTo(juan);
		assertThat((String) ultimoEvento(jdbc, "RESPONSABLE_PAGO_CAMBIADO").get("detalle"))
				.contains("Pedido por administracion, aprobado por promotor");
	}

	/** B1: Promotoría (solo lectura) ve el documento, el celular y el correo enmascarados. */
	@Test
	void promotoriaVeLosDatosPersonalesEnmascarados() {
		RegistroResultado mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(escuela.primaria5A2026()));

		como(PROMOTORIA);
		var ficha = alumnos.obtenerFicha(mateo.alumnoId());
		assertThat(ficha.cabecera().documento()).isEqualTo("DNI ****1236");
		assertThat(ficha.numeroDocumento()).isEqualTo("****1236");
		assertThat(ficha.responsable().documento()).isEqualTo("DNI ****8912");
		assertThat(ficha.responsable().telefonoWhatsapp()).isEqualTo("+51 *** *** 321");
		assertThat(ficha.responsable().correo()).isEqualTo("r***@gmail.com");
		assertThat(familias.obtener(mateo.familiaId()).apoderados().getFirst().telefonoWhatsapp())
				.isEqualTo("+51 *** *** 321");

		// Quien además corrige datos (Dirección o Administración) los ve completos.
		como(DIRECCION);
		assertThat(alumnos.obtenerFicha(mateo.alumnoId()).responsable().correo()).isEqualTo(EscenarioEscolar.CORREO_ROSA);
	}

	private static Long idDe(Map<String, Object> fila) {
		return ((Number) fila.get("id")).longValue();
	}
}
