package pe.edu.virgenmaria.cuentasclaras.academico;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.ListaSeccion;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.ProponerAsignacionRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.ResultadoCopia;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.SeccionDocente;
import pe.edu.virgenmaria.cuentasclaras.academico.model.TipoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.academico.proceso.FinAsignaciones;
import pe.edu.virgenmaria.cuentasclaras.academico.service.Asignaciones;
import pe.edu.virgenmaria.cuentasclaras.academico.service.ConsultaAcademica;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.AutoaprobacionSolicitudException;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.CrearSeccionRequest;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.TokenDeSesionDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.dto.PuntoLista;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service.EstadoPuestaEnMarcha;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 9, tanda 1 (sección 3.2; E3, E4, E32 y E46): dar acceso a una sección lo deciden dos personas y quitarlo, una.
 * Administración propone; otra persona de Dirección o Promotoría aprueba con su firma (nunca quien propuso ni el docente
 * asignado, aunque tenga el rol de Dirección); al aprobar, el docente ve los nombres de los alumnos de SU sección y de
 * ninguna otra; al terminar (Dirección con su firma, o sistema.academico) deja de verla. Todo queda en la bitácora con el
 * antes y el después, y el colegio B no ve ni toca las asignaciones del A.
 */
@PruebaIntegracion
class AsignacionesTest {

	private static final String MOTIVO = "Carga horaria aprobada por Dirección";

	@Autowired
	private Asignaciones asignaciones;

	@Autowired
	private ConsultaAcademica consulta;

	@Autowired
	private BandejaAprobaciones bandeja;

	@Autowired
	private FinAsignaciones fin;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private EstadoPuestaEnMarcha puestaEnMarcha;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private TokenDeSesionDePrueba tokens;

	@Autowired
	private JdbcTemplate jdbc;

	private EscenarioEscolar.Estructura escuela;

	private Usuario docente;

	private Usuario otroDocente;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		tokens.olvidar();
		EscenarioCobranza.como(EscenarioCobranza.ADMINISTRACION);
		escuela = EscenarioEscolar.crearEstructura(estructura);
		alumnos.registrar(EscenarioEscolar.mateoConRosa(escuela.primaria5A2026()));
		alumnos.registrar(EscenarioEscolar.valeriaConRosaRegistrada(escuela.primaria5A2026()));
		SecurityContextHolder.clearContext();
		docente = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "ana.docente", UsuariosDePrueba.CLAVE, false,
				Rol.DOCENTE);
		otroDocente = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "luis.docente", UsuariosDePrueba.CLAVE, false,
				Rol.DOCENTE);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		tokens.olvidar();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	private Long proponer(Long seccion, Usuario quien, TipoAsignacion tipo, String areas) {
		EscenarioCobranza.como(EscenarioCobranza.ADMINISTRACION);
		try {
			return asignaciones.proponer(new ProponerAsignacionRequest(seccion, quien.getId(), tipo, areas, MOTIVO));
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	private Long solicitudDe(Long asignacion) {
		return jdbc.queryForObject("SELECT solicitud_id FROM asignacion_docente WHERE id = ?", Long.class, asignacion);
	}

	private void aprobar(Long asignacion) {
		EscenarioAprobaciones.aprueba(EscenarioCobranza.DIRECCION, bandeja, jdbc, "asignacion_docente", asignacion);
		SecurityContextHolder.clearContext();
	}

	private String estado(Long asignacion) {
		return jdbc.queryForObject("SELECT estado FROM asignacion_docente WHERE id = ?", String.class, asignacion);
	}

	private List<SeccionDocente> seccionesDe(Usuario quien) {
		UsuariosDePrueba.iniciarSesion(quien);
		try {
			return consulta.inicio().mias();
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	private ListaSeccion listaComo(Usuario quien, Long seccion) {
		UsuariosDePrueba.iniciarSesion(quien);
		try {
			return consulta.lista(seccion);
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	@Test
	void administracionProponeDireccionApruebaYElDocenteVeSoloSuSeccion() {
		Long id = proponer(escuela.primaria5A2026(), docente, TipoAsignacion.TUTOR, null);
		assertThat(estado(id)).isEqualTo("PROPUESTA");
		// E3: una propuesta sin aprobar no da acceso.
		assertThat(seccionesDe(docente)).isEmpty();
		assertThatThrownBy(() -> listaComo(docente, escuela.primaria5A2026())).isInstanceOf(RecursoNoEncontradoException.class);

		aprobar(id);

		assertThat(estado(id)).isEqualTo("VIGENTE");
		assertThat(seccionesDe(docente)).singleElement().satisfies(s -> {
			assertThat(s.seccionId()).isEqualTo(escuela.primaria5A2026());
			assertThat(s.funcion()).isEqualTo("Tutor(a)");
			assertThat(s.tutor()).isTrue();
		});
		ListaSeccion lista = listaComo(docente, escuela.primaria5A2026());
		assertThat(lista.alumnos()).extracting(a -> a.numero() + " " + a.apellidos() + ", " + a.nombres())
				.containsExactly("1 Quispe Huamán, Mateo", "2 Quispe Huamán, Valeria");
		assertThat(lista.tutor()).isEqualTo(docente.getNombreCompleto());
		// E32: otra sección del mismo colegio, y un docente de otra sección, responden 404.
		assertThatThrownBy(() -> listaComo(docente, escuela.primaria5B2026())).isInstanceOf(RecursoNoEncontradoException.class);
		assertThatThrownBy(() -> listaComo(otroDocente, escuela.primaria5A2026()))
				.isInstanceOf(RecursoNoEncontradoException.class);
		// La bitácora: propuesta y aprobada, con el antes y el después.
		Map<String, Object> propuesta = EscenarioEscolar.ultimoEvento(jdbc, "ASIGNACION_DOCENTE_PROPUESTA");
		assertThat(propuesta.get("VALOR_NUEVO")).isEqualTo("PROPUESTA");
		assertThat(propuesta.get("ENTIDAD_ID")).isEqualTo(id.toString());
		Map<String, Object> aprobada = EscenarioEscolar.ultimoEvento(jdbc, "ASIGNACION_DOCENTE_APROBADA");
		assertThat(aprobada.get("VALOR_ANTERIOR")).isEqualTo("PROPUESTA");
		assertThat(aprobada.get("VALOR_NUEVO")).isEqualTo("VIGENTE");
		assertThat(jdbc.queryForObject("SELECT resuelto_por FROM asignacion_docente WHERE id = ?", String.class, id))
				.isEqualTo("director");
	}

	/** E4: quien propone no aprueba, y el docente asignado tampoco (aunque tenga el rol de Dirección). */
	@Test
	void quienProponeNoApruebaYElDocenteTampoco() {
		Usuario directorQueEnsena = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "director.docente",
				UsuariosDePrueba.CLAVE, false, Rol.DIRECTOR, Rol.DOCENTE);
		Long id = proponer(escuela.primaria5A2026(), directorQueEnsena, TipoAsignacion.DOCENTE, "Matemática");

		UsuariosDePrueba.iniciarSesion(directorQueEnsena);
		assertThatThrownBy(() -> bandeja.aprobar(solicitudDe(id), null)).isInstanceOf(AutoaprobacionSolicitudException.class);
		SecurityContextHolder.clearContext();
		UsuariosDePrueba.iniciarSesion(EscenarioCobranza.ADMINISTRACION);
		assertThatThrownBy(() -> bandeja.aprobar(solicitudDe(id), null)).isInstanceOf(AccessDeniedException.class);
		SecurityContextHolder.clearContext();
		assertThat(estado(id)).isEqualTo("PROPUESTA");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'AUTOAPROBACION_RECHAZADA'",
				Long.class)).isEqualTo(1L);

		// Una Dirección que propone tampoco aprueba lo suyo.
		UsuariosDePrueba.iniciarSesion(EscenarioCobranza.DIRECCION);
		Long suya = asignaciones.proponer(new ProponerAsignacionRequest(escuela.primaria5B2026(), docente.getId(),
				TipoAsignacion.TUTOR, null, MOTIVO));
		assertThatThrownBy(() -> bandeja.aprobar(solicitudDe(suya), null)).isInstanceOf(AutoaprobacionSolicitudException.class);
		SecurityContextHolder.clearContext();

		aprobar(id);
		assertThat(estado(id)).isEqualTo("VIGENTE");
		assertThat(seccionesDe(directorQueEnsena)).singleElement()
				.satisfies(s -> assertThat(s.funcion()).isEqualTo("Docente de área: Matemática"));
	}

	@Test
	void rechazadaNoDaAcceso() {
		Long id = proponer(escuela.primaria5A2026(), docente, TipoAsignacion.AUXILIAR, null);
		UsuariosDePrueba.iniciarSesion(EscenarioCobranza.DIRECCION);
		bandeja.rechazar(solicitudDe(id), "No trabaja en esta sección este año");
		SecurityContextHolder.clearContext();

		assertThat(estado(id)).isEqualTo("RECHAZADA");
		assertThat(seccionesDe(docente)).isEmpty();
		assertThat(EscenarioEscolar.ultimoEvento(jdbc, "ASIGNACION_DOCENTE_RECHAZADA").get("VALOR_NUEVO"))
				.isEqualTo("RECHAZADA");
	}

	/** E3 y E32: al terminar (Dirección con su firma) pierde la sección; lo terminado no vuelve. */
	@Test
	void alTerminarPierdeLaSeccion() {
		Long id = proponer(escuela.primaria5A2026(), docente, TipoAsignacion.TUTOR, null);
		aprobar(id);
		assertThat(seccionesDe(docente)).hasSize(1);

		UsuariosDePrueba.iniciarSesion(EscenarioCobranza.ADMINISTRACION);
		assertThatThrownBy(() -> asignaciones.terminar(id, "Cambió de colegio a mitad de año"))
				.isInstanceOf(AccessDeniedException.class);
		UsuariosDePrueba.iniciarSesion(EscenarioCobranza.DIRECCION);
		assertThatThrownBy(() -> asignaciones.terminar(id, "corto")).isInstanceOf(ReglaNegocioException.class);
		asignaciones.terminar(id, "Cambió de colegio a mitad de año");
		assertThatThrownBy(() -> asignaciones.terminar(id, "Cambió de colegio a mitad de año"))
				.isInstanceOf(ReglaNegocioException.class);
		SecurityContextHolder.clearContext();

		assertThat(estado(id)).isEqualTo("TERMINADA");
		assertThat(seccionesDe(docente)).isEmpty();
		assertThatThrownBy(() -> listaComo(docente, escuela.primaria5A2026())).isInstanceOf(RecursoNoEncontradoException.class);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM firma_operacion WHERE clave = ?", Long.class,
				"asignacion_docente:" + id + ":TERMINADA")).isEqualTo(1L);
		Map<String, Object> evento = EscenarioEscolar.ultimoEvento(jdbc, "ASIGNACION_DOCENTE_TERMINADA");
		assertThat(evento.get("VALOR_ANTERIOR")).isEqualTo("VIGENTE");
		assertThat(evento.get("VALOR_NUEVO")).isEqualTo("TERMINADA");
		assertThat((String) evento.get("DETALLE")).contains("Cambió de colegio");
		// La sección queda libre para un tutor nuevo.
		Long nuevo = proponer(escuela.primaria5A2026(), otroDocente, TipoAsignacion.TUTOR, null);
		aprobar(nuevo);
		assertThat(seccionesDe(otroDocente)).hasSize(1);
	}

	@Test
	void unSoloTutorPorSeccionYNadaDuplicado() {
		Long id = proponer(escuela.primaria5A2026(), docente, TipoAsignacion.TUTOR, null);
		assertThatThrownBy(() -> proponer(escuela.primaria5A2026(), docente, TipoAsignacion.TUTOR, null))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("por aprobarse");
		Long otro = proponer(escuela.primaria5A2026(), otroDocente, TipoAsignacion.TUTOR, null);
		aprobar(id);
		org.assertj.core.api.Assertions.assertThatCode(() -> proponer(escuela.primaria5A2026(), otroDocente,
				TipoAsignacion.DOCENTE, "Inglés")).doesNotThrowAnyException();
		// La segunda propuesta de tutor ya no se puede aprobar: la sección tiene tutor vigente.
		UsuariosDePrueba.iniciarSesion(EscenarioCobranza.DIRECCION);
		assertThatThrownBy(() -> bandeja.aprobar(solicitudDe(otro), null)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("ya tiene tutor");
		SecurityContextHolder.clearContext();
		assertThat(estado(otro)).isEqualTo("PROPUESTA");
		assertThatThrownBy(() -> proponer(escuela.primaria5A2026(), otroDocente, TipoAsignacion.AUXILIAR, "Matemática"))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("auxiliar");
	}

	/** Solo una cuenta activa del personal con el rol DOCENTE; y nunca una que combine Docente con Caja (E46). */
	@Test
	void soloSeAsignaUnDocenteActivoQueNoVeDeudas() {
		Usuario caja = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "caja.sin.docente", UsuariosDePrueba.CLAVE, false,
				Rol.CAJA);
		assertThatThrownBy(() -> proponer(escuela.primaria5A2026(), caja, TipoAsignacion.DOCENTE, null))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("rol Docente");
		// Una cuenta de antes de V32 con DOCENTE y CAJA (en H2 no hay trigger: se fabrica a mano).
		jdbc.update("INSERT INTO usuario_rol (usuario_id, rol) VALUES (?, 'DOCENTE')", caja.getId());
		assertThatThrownBy(() -> proponer(escuela.primaria5A2026(), caja, TipoAsignacion.DOCENTE, null))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Caja o Administración");
		// La puesta en marcha la muestra como pendiente (decisión 141).
		UsuariosDePrueba.iniciarSesion(EscenarioCobranza.PROMOTORIA);
		PuntoLista equipo = puestaEnMarcha.actual().puntos().stream().filter(p -> p.clave().equals(EstadoPuestaEnMarcha.EQUIPO))
				.findFirst().orElseThrow();
		SecurityContextHolder.clearContext();
		assertThat(equipo.hecho()).isFalse();
		assertThat(equipo.conteo()).contains("1 cuenta(s) combinan Docente con Caja o Administración");
	}

	/** sistema.academico termina la asignación de una cuenta desactivada (con cc_sistema en MySQL). */
	@Test
	void elSistemaTerminaLaDeUnaCuentaDesactivada() {
		Long id = proponer(escuela.primaria5A2026(), docente, TipoAsignacion.TUTOR, null);
		aprobar(id);
		assertThat(fin.terminar(1L)).isZero();
		jdbc.update("UPDATE usuario SET activo = FALSE, desactivado_en = CURRENT_TIMESTAMP, desactivado_por = 'promotor' "
				+ "WHERE id = ?", docente.getId());

		assertThat(fin.terminar(1L)).isEqualTo(1);

		assertThat(estado(id)).isEqualTo("TERMINADA");
		assertThat(jdbc.queryForObject("SELECT terminada_por FROM asignacion_docente WHERE id = ?", String.class, id))
				.isEqualTo("sistema.academico");
		assertThat((String) EscenarioEscolar.ultimoEvento(jdbc, "ASIGNACION_DOCENTE_TERMINADA").get("DETALLE"))
				.contains("se desactivó");
	}

	@Test
	void proponerLasDelAnioAnterior() {
		Long id = proponer(escuela.primaria5A2026(), docente, TipoAsignacion.TUTOR, null);
		aprobar(id);
		Long matematica = proponer(escuela.primaria5A2026(), otroDocente, TipoAsignacion.DOCENTE, "Matemática");
		aprobar(matematica);
		// 2027 tiene 5.° Primaria A (la de 2026 se copia ahí); 6.° A no estaba en 2026.
		EscenarioCobranza.como(EscenarioCobranza.ADMINISTRACION);
		Long quinto2027 = estructura.crearSeccion(escuela.anio2027(), new CrearSeccionRequest(Grado.PRIMARIA_5, "A"));
		ResultadoCopia copia = asignaciones.proponerLasDelAnioAnterior(escuela.anio2027(), MOTIVO);
		ResultadoCopia otraVez = asignaciones.proponerLasDelAnioAnterior(escuela.anio2027(), MOTIVO);
		SecurityContextHolder.clearContext();

		assertThat(copia.propuestas()).isEqualTo(2);
		assertThat(otraVez.propuestas()).isZero();
		assertThat(otraVez.saltadas()).isEqualTo(2);
		assertThat(jdbc.queryForList("SELECT tipo FROM asignacion_docente WHERE seccion_id = ? AND estado = 'PROPUESTA' "
				+ "ORDER BY tipo", String.class, quinto2027)).containsExactly("DOCENTE", "TUTOR");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM solicitud_cambio WHERE tipo = 'ASIGNACION_DOCENTE' AND "
				+ "estado = 'PENDIENTE'", Long.class)).isEqualTo(2L);
	}

	/** Aislamiento: el colegio B no ve las secciones ni las asignaciones del A (404), y su vista no las lista. */
	@Test
	void elColegioBNoVeNiTocaLasDelA() {
		Long id = proponer(escuela.primaria5A2026(), docente, TipoAsignacion.TUTOR, null);
		aprobar(id);
		jdbc.update("INSERT INTO colegio (nombre) VALUES ('Colegio de Prueba B')");
		long colegioB = jdbc.queryForObject("SELECT MAX(id) FROM colegio", Long.class);
		UsuarioDeB personas = new UsuarioDeB(colegioB);

		UsuariosDePrueba.iniciarSesion(personas.direccion());
		assertThatThrownBy(() -> consulta.lista(escuela.primaria5A2026())).isInstanceOf(RecursoNoEncontradoException.class);
		assertThatThrownBy(() -> asignaciones.terminar(id, "Intento desde otro colegio")).isInstanceOf(
				RecursoNoEncontradoException.class);
		assertThatThrownBy(() -> asignaciones.vista(escuela.anio2026())).isInstanceOf(RecursoNoEncontradoException.class);
		assertThat(asignaciones.vista(null).secciones()).isEmpty();
		SecurityContextHolder.clearContext();
		UsuariosDePrueba.iniciarSesion(personas.administracion());
		assertThatThrownBy(() -> asignaciones.proponer(new ProponerAsignacionRequest(escuela.primaria5A2026(),
				docente.getId(), TipoAsignacion.DOCENTE, null, MOTIVO))).isInstanceOf(RecursoNoEncontradoException.class);
		SecurityContextHolder.clearContext();
		assertThat(estado(id)).isEqualTo("VIGENTE");
		assertThat(ContextoColegio.en(colegioB, () -> jdbc.queryForObject(
				"SELECT COUNT(*) FROM asignacion_docente WHERE colegio_id = ?", Long.class, colegioB))).isZero();
	}

	/** Dirección y Administración del colegio B (inventadas: no necesitan cuenta para leer o fallar). */
	private record UsuarioDeB(long colegio) {

		pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado direccion() {
			return UsuariosDePrueba.autenticado(colegio, 901L, "director.b", "Director B", false,
					java.util.EnumSet.of(Rol.DIRECTOR));
		}

		pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado administracion() {
			return UsuariosDePrueba.autenticado(colegio, 902L, "administracion.b", "Administración B", false,
					java.util.EnumSet.of(Rol.ADMINISTRACION));
		}
	}
}
