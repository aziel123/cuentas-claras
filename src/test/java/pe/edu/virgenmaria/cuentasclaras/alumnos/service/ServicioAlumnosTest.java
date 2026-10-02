package pe.edu.virgenmaria.cuentasclaras.alumnos.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.OtraPersona;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.ActualizarAlumnoRequest;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.CambiarResponsableRequest;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.FichaAlumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.RegistroResultado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.RetirarAlumnoRequest;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.DatoInvalidoException;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Parentesco;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.TipoDocumento;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.Estructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.MOTIVO;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.todaLaBitacora;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.ultimoEvento;

/** Registro y cuidado de alumnos, apoderados y familias, sobre la base real y con la seguridad por método. */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class ServicioAlumnosTest {

	@Autowired
	private BandejaAprobaciones bandeja;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioFamilias familias;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private JdbcTemplate jdbc;

	private Estructura escuela;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		UsuariosDePrueba.iniciarSesion(UsuariosDePrueba.autenticado(Rol.ADMINISTRACION));
		escuela = EscenarioEscolar.crearEstructura(estructura);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void registrarConApoderadoNuevoCreaLaFamilia() {
		RegistroResultado resultado = alumnos.registrar(EscenarioEscolar.mateoConRosa(escuela.primaria5A2026()));

		Map<String, Object> alumno = jdbc.queryForMap("SELECT * FROM alumno WHERE id = ?", resultado.alumnoId());
		assertThat(alumno).containsEntry("numero_documento", "78451236").containsEntry("estado", "ACTIVO")
				.containsEntry("nombre_busqueda", "QUISPE HUAMAN MATEO").containsEntry("colegio_id", 1L);
		assertThat(jdbc.queryForObject("SELECT nombre FROM familia WHERE id = ?", String.class, resultado.familiaId()))
				.isEqualTo("Familia Quispe Huamán");
		Map<String, Object> apoderado = jdbc.queryForMap("SELECT * FROM apoderado WHERE id = ?",
				alumno.get("responsable_pago_id"));
		assertThat(apoderado).containsEntry("familia_id", resultado.familiaId())
				.containsEntry("telefono_whatsapp", "+51987654321").containsEntry("correo", "rosa.huaman@gmail.com");
		assertThat(jdbc.queryForMap("SELECT * FROM matricula WHERE alumno_id = ?", resultado.alumnoId()))
				.containsEntry("seccion_id", escuela.primaria5A2026()).containsEntry("estado", "ACTIVA")
				// El año ya empezó: la fecha por defecto es el inicio de clases.
				.containsEntry("fecha_matricula", java.sql.Date.valueOf("2026-03-02"));
		assertThat(jdbc.queryForList("SELECT accion FROM evento_auditoria ORDER BY secuencia", String.class))
				.containsSubsequence("FAMILIA_CREADA", "APODERADO_REGISTRADO", "ALUMNO_REGISTRADO", "MATRICULA_REGISTRADA");
		assertThat(resultado.advertencia()).isNull();

		FichaAlumno ficha = alumnos.obtenerFicha(resultado.alumnoId());
		assertThat(ficha.responsable().nombreCompleto()).isEqualTo("Rosa Huamán Ccori");
		assertThat(ficha.responsable().responsableDe()).containsExactly("Mateo Quispe Huamán");
		assertThat(ficha.cabecera().seccionActual()).isEqualTo("5.° Primaria A · 2026");
		assertThat(ficha.seccionesParaMatricular()).extracting(s -> s.etiquetaConAnio())
				.containsExactly("2027 · 6.° Primaria A");
	}

	@Test
	void hermanoConElMismoApoderadoCompartenFamilia() {
		RegistroResultado mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(escuela.primaria5A2026()));
		RegistroResultado valeria = alumnos.registrar(EscenarioEscolar.valeriaConRosaRegistrada(escuela.primaria2B2026()));

		assertThat(valeria.familiaId()).isEqualTo(mateo.familiaId());
		assertThat(contar(jdbc, "familia")).isEqualTo(1);
		assertThat(contar(jdbc, "apoderado")).isEqualTo(1);
		assertThat(alumnos.obtenerFicha(mateo.alumnoId()).hermanos()).extracting(h -> h.nombreCompleto())
				.containsExactly("Valeria Quispe Huamán");
		assertThat(familias.obtener(mateo.familiaId()).apoderados().getFirst().responsableDe())
				.containsExactly("Mateo Quispe Huamán", "Valeria Quispe Huamán");
	}

	@Test
	void documentoDuplicadoMuestraMensajeClaro() {
		alumnos.registrar(EscenarioEscolar.mateoConRosa(escuela.primaria5A2026()));
		long familiasAntes = contar(jdbc, "familia");
		long eventosAntes = contar(jdbc, "evento_auditoria");

		// Mismo DNI de alumno, con otra apoderada nueva: no queda NADA a medias (ni familia ni apoderada).
		assertThatThrownBy(() -> alumnos.registrar(EscenarioEscolar.conApoderadoNuevo("78451236", "Otro", null, "Niño",
				EscenarioEscolar.NACIMIENTO_MATEO, "11223344", "Pérez", null, "Ana", "912345678", null, null)))
				.isInstanceOf(ReglaNegocioException.class)
				.hasMessage("Ya hay un alumno registrado con DNI 78451236: Mateo Quispe Huamán. Búscalo en la lista de alumnos.");
		// Mismo DNI de apoderada nueva.
		assertThatThrownBy(() -> alumnos.registrar(EscenarioEscolar.conApoderadoNuevo("11112222", "Otro", null, "Niño",
				EscenarioEscolar.NACIMIENTO_MATEO, EscenarioEscolar.DNI_ROSA, "Huamán", null, "Rosa", "912345678", null,
				null)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Ya hay un apoderado registrado con DNI 45678912");

		assertThat(contar(jdbc, "familia")).isEqualTo(familiasAntes);
		assertThat(contar(jdbc, "apoderado")).isEqualTo(1);
		assertThat(contar(jdbc, "alumno")).isEqualTo(1);
		assertThat(contar(jdbc, "evento_auditoria")).isEqualTo(eventosAntes);
	}

	@Test
	void responsableDePagoDebeSerDeLaFamiliaTambienEnLaBase() {
		RegistroResultado mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(null));
		RegistroResultado otro = alumnos.registrar(EscenarioEscolar.conApoderadoNuevo("75330981", "Flores", "Rojas",
				"Sebastián", LocalDate.of(2013, 8, 21), "41235678", "Flores", "Díaz", "Pedro", "912345678", null, null));
		Long pedro = jdbc.queryForObject("SELECT responsable_pago_id FROM alumno WHERE id = ?", Long.class, otro.alumnoId());

		// Responsable de OTRA familia sin mover al alumno: la FK (responsable_pago_id, familia_id) lo impide.
		assertThatThrownBy(() -> jdbc.update("UPDATE alumno SET responsable_pago_id = ? WHERE id = ?", pedro,
				mateo.alumnoId())).isInstanceOf(DataIntegrityViolationException.class);
		// Y mover al alumno de familia sin cambiar su responsable, tampoco.
		assertThatThrownBy(() -> jdbc.update("UPDATE alumno SET familia_id = ? WHERE id = ?", otro.familiaId(),
				mateo.alumnoId())).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void cambiarResponsableAOtraFamiliaMueveAlAlumnoYQuedaResaltado() {
		RegistroResultado mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(null));
		RegistroResultado otro = alumnos.registrar(EscenarioEscolar.conApoderadoNuevo("75330981", "Flores", "Rojas",
				"Sebastián", LocalDate.of(2013, 8, 21), "41235678", "Flores", "Díaz", "Pedro", "912345678", null, null));

		alumnos.cambiarResponsablePago(mateo.alumnoId(), new CambiarResponsableRequest(null, " 41235678 ",
				"La tutela pasó al padre por resolución judicial"));
		OtraPersona.apruebaLoPendiente(bandeja, jdbc);

		assertThat(jdbc.queryForMap("SELECT familia_id, responsable_pago_id FROM alumno WHERE id = ?", mateo.alumnoId()))
				.containsEntry("familia_id", otro.familiaId());
		Map<String, Object> evento = ultimoEvento(jdbc, "RESPONSABLE_PAGO_CAMBIADO");
		assertThat((String) evento.get("valor_anterior")).contains("Rosa Huamán Ccori", "Familia Quispe Huamán");
		assertThat((String) evento.get("valor_nuevo")).contains("Pedro Flores Díaz", "Familia Flores Rojas");
		assertThat((String) evento.get("detalle")).contains("Pasó a otra familia", "resolución judicial");
		assertThat(AccionAuditoria.RESPONSABLE_PAGO_CAMBIADO.requiereAtencion()).isTrue();
		assertThat(AccionAuditoria.siempreRevisar()).contains(AccionAuditoria.RESPONSABLE_PAGO_CAMBIADO);
	}

	@Test
	void cambiarResponsableExigeMotivo() {
		RegistroResultado mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(null));
		Long segundo = familias.agregarApoderado(mateo.familiaId(), EscenarioEscolar.apoderado("40112233", "Quispe",
				"Juan", Parentesco.PADRE, "976540129", null, null));

		assertThatThrownBy(() -> alumnos.cambiarResponsablePago(mateo.alumnoId(),
				new CambiarResponsableRequest(segundo, null, "corto")))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("entre 10 y 500");
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'RESPONSABLE_PAGO_CAMBIADO'")).isZero();

		alumnos.cambiarResponsablePago(mateo.alumnoId(), new CambiarResponsableRequest(segundo, null, MOTIVO));
		OtraPersona.apruebaLoPendiente(bandeja, jdbc);
		assertThat(jdbc.queryForObject("SELECT responsable_pago_id FROM alumno WHERE id = ?", Long.class,
				mateo.alumnoId())).isEqualTo(segundo);
		assertThat((String) ultimoEvento(jdbc, "RESPONSABLE_PAGO_CAMBIADO").get("detalle"))
				.doesNotContain("otra familia");
	}

	@Test
	void retirarExigeMotivoYRetiraLaMatricula() {
		RegistroResultado mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(escuela.primaria5A2026()));
		LocalDate hoy = LocalDate.of(2026, 10, 2);

		assertThatThrownBy(() -> alumnos.retirar(mateo.alumnoId(), new RetirarAlumnoRequest(hoy, "corto")))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("entre 10 y 500");
		assertThatThrownBy(() -> alumnos.retirar(mateo.alumnoId(), new RetirarAlumnoRequest(hoy.plusDays(1), MOTIVO)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("futura");
		assertThat(jdbc.queryForObject("SELECT estado FROM alumno WHERE id = ?", String.class, mateo.alumnoId()))
				.isEqualTo("ACTIVO");

		alumnos.retirar(mateo.alumnoId(), new RetirarAlumnoRequest(hoy, "Se mudó a Arequipa con su familia"));
		OtraPersona.apruebaLoPendiente(bandeja, jdbc);

		assertThat(jdbc.queryForMap("SELECT * FROM alumno WHERE id = ?", mateo.alumnoId()))
				.containsEntry("estado", "RETIRADO").containsEntry("retirado_por", "usuario.prueba")
				.containsEntry("motivo_retiro", "Se mudó a Arequipa con su familia");
		assertThat(jdbc.queryForMap("SELECT * FROM matricula WHERE alumno_id = ?", mateo.alumnoId()))
				.containsEntry("estado", "RETIRADA").containsEntry("retirada_en", java.sql.Date.valueOf(hoy));
		assertThat((String) ultimoEvento(jdbc, "ALUMNO_RETIRADO").get("detalle"))
				.contains("5.° Primaria A 2026", "Arequipa");
		// Ya retirado: no se vuelve a retirar ni se le cambia el responsable.
		assertThatThrownBy(() -> alumnos.retirar(mateo.alumnoId(), new RetirarAlumnoRequest(hoy, MOTIVO)))
				.isInstanceOf(ReglaNegocioException.class);
	}

	@Test
	void apoderadoSinTelefonoNiCorreoEsRechazado() {
		assertThatThrownBy(() -> alumnos.registrar(EscenarioEscolar.conApoderadoNuevo("78451236", "Quispe", "Huamán",
				"Mateo", EscenarioEscolar.NACIMIENTO_MATEO, "45678912", "Huamán", "Ccori", "Rosa", " ", "", null)))
				.isInstanceOf(DatoInvalidoException.class)
				.hasMessageContaining("celular para WhatsApp o el correo")
				.extracting(e -> ((DatoInvalidoException) e).campo()).isEqualTo("apoderadoTelefonoWhatsapp");
		assertThat(contar(jdbc, "familia")).isZero();

		// En la base: CHECK ck_apoderado_contacto.
		RegistroResultado mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(null));
		assertThatThrownBy(() -> jdbc.update("UPDATE apoderado SET telefono_whatsapp = NULL, correo = NULL "
				+ "WHERE familia_id = ?", mateo.familiaId())).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void noSeDesactivaAlApoderadoResponsableDePago() {
		RegistroResultado mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(null));
		Long rosa = jdbc.queryForObject("SELECT responsable_pago_id FROM alumno WHERE id = ?", Long.class,
				mateo.alumnoId());

		assertThatThrownBy(() -> familias.desactivarApoderado(rosa, MOTIVO))
				.isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("es responsable de pago de Mateo Quispe Huamán");

		Long juan = familias.agregarApoderado(mateo.familiaId(), EscenarioEscolar.apoderado("40112233", "Quispe",
				"Juan", Parentesco.PADRE, "976540129", null, null));
		familias.desactivarApoderado(juan, MOTIVO);
		assertThat(jdbc.queryForObject("SELECT activo FROM apoderado WHERE id = ?", Boolean.class, juan)).isFalse();
		assertThat(jdbc.queryForObject("SELECT activo FROM apoderado WHERE id = ?", Boolean.class, rosa)).isTrue();
		// Un apoderado desactivado no puede ser responsable de pago.
		assertThatThrownBy(() -> alumnos.cambiarResponsablePago(mateo.alumnoId(),
				new CambiarResponsableRequest(juan, null, MOTIVO)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("desactivado");
	}

	@Test
	void cambioDeCelularDelApoderadoQuedaAuditadoYResaltado() {
		RegistroResultado mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(null));
		Long rosa = jdbc.queryForObject("SELECT responsable_pago_id FROM alumno WHERE id = ?", Long.class,
				mateo.alumnoId());

		assertThatThrownBy(() -> familias.actualizarApoderado(rosa, EscenarioEscolar.apoderado("45678912", "Huamán",
				"Rosa", Parentesco.MADRE, "999888777", EscenarioEscolar.CORREO_ROSA, "")))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("motivo");

		familias.actualizarApoderado(rosa, new pe.edu.virgenmaria.cuentasclaras.alumnos.dto.ApoderadoRequest(
				TipoDocumento.DNI, "45678912", "Huamán", "Ccori", "Rosa", Parentesco.MADRE, "999 888 777",
				EscenarioEscolar.CORREO_ROSA, "Perdió su celular; confirmó el número nuevo en persona"));
		// Auditoría A4: el contacto se cambia cuando otra persona aprueba la solicitud.
		OtraPersona.apruebaLoPendiente(bandeja, jdbc);

		Map<String, Object> evento = ultimoEvento(jdbc, "APODERADO_CONTACTO_CAMBIADO");
		assertThat(evento).containsEntry("valor_anterior", "WhatsApp +51 *** *** 321")
				.containsEntry("valor_nuevo", "WhatsApp +51 *** *** 777");
		assertThat((String) evento.get("detalle")).contains("Rosa Huamán Ccori", "celular", "en persona");
		assertThat(AccionAuditoria.APODERADO_CONTACTO_CAMBIADO.requiereAtencion()).isTrue();

		// Corregir solo el parentesco no se resalta.
		familias.actualizarApoderado(rosa, new pe.edu.virgenmaria.cuentasclaras.alumnos.dto.ApoderadoRequest(
				TipoDocumento.DNI, "45678912", "Huamán", "Ccori", "Rosa", Parentesco.TUTOR_LEGAL, "999888777",
				EscenarioEscolar.CORREO_ROSA, "Presentó la resolución de tutela"));
		assertThat(ultimoEvento(jdbc, "APODERADO_ACTUALIZADO")).containsEntry("valor_anterior", "Madre")
				.containsEntry("valor_nuevo", "Tutor legal");
		assertThat(AccionAuditoria.APODERADO_ACTUALIZADO.requiereAtencion()).isFalse();
	}

	@Test
	void auditoriaEnmascaraDocumentoTelefonoYCorreo() {
		RegistroResultado mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(escuela.primaria5A2026()));
		alumnos.actualizar(mateo.alumnoId(), new ActualizarAlumnoRequest(TipoDocumento.DNI, "78451237", "Quispe",
				"Huamán", "Mateo", EscenarioEscolar.NACIMIENTO_MATEO, "El DNI estaba mal escrito en la ficha"));

		String bitacora = todaLaBitacora(jdbc);
		assertThat(bitacora).doesNotContain("78451236", "78451237", "45678912", "987654321", "rosa.huaman@gmail.com",
				"huaman@");
		assertThat(bitacora).contains("DNI ****1236", "DNI ****1237", "DNI ****8912", "+51 *** *** 321",
				"r***@gmail.com");
		assertThat(ultimoEvento(jdbc, "ALUMNO_ACTUALIZADO")).containsEntry("valor_anterior", "DNI ****1236")
				.containsEntry("valor_nuevo", "DNI ****1237");
	}

	@Test
	void auditoriaSoloGuardaElAnioDeNacimiento() {
		RegistroResultado mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(null));
		alumnos.actualizar(mateo.alumnoId(), new ActualizarAlumnoRequest(TipoDocumento.DNI, "78451236", "Quispe",
				"Huamán", "Mateo", LocalDate.of(2015, 7, 20), "La fecha de nacimiento estaba mal"));

		String bitacora = todaLaBitacora(jdbc);
		assertThat(bitacora).doesNotContain("2015-06-14", "14/06/2015", "2015-07-20", "20/07/2015", "14/06", "20/07");
		assertThat((String) ultimoEvento(jdbc, "ALUMNO_REGISTRADO").get("valor_nuevo")).contains("nació en 2015");
		assertThat(ultimoEvento(jdbc, "ALUMNO_ACTUALIZADO")).containsEntry("valor_nuevo", "nació en 2015");
	}

	@Test
	void promotoriaVeLaFichaPeroNoRegistraNiCambia() {
		RegistroResultado mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(null));
		UsuariosDePrueba.iniciarSesion(UsuariosDePrueba.autenticado(Rol.PROMOTOR));

		assertThat(alumnos.obtenerFicha(mateo.alumnoId()).cabecera().nombreCompleto()).isEqualTo("Mateo Quispe Huamán");
		assertThatThrownBy(() -> alumnos.registrar(EscenarioEscolar.valeriaConRosaRegistrada(null)))
				.isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> alumnos.retirar(mateo.alumnoId(), new RetirarAlumnoRequest(LocalDate.of(2026, 10, 1),
				MOTIVO))).isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> familias.agregarApoderado(mateo.familiaId(), EscenarioEscolar.apoderado("40112233",
				"Quispe", "Juan", Parentesco.PADRE, "976540129", null, null))).isInstanceOf(AccessDeniedException.class);
	}
}
