package pe.edu.virgenmaria.cuentasclaras.academico.inicial;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.AreaCurricular;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.ConfiguracionEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.TipoPeriodo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository.AreaCurricularRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository.CurriculoVersionRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.service.ConfiguracionesEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.service.Periodos;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.AlumnoEnHoja;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.HojaRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.MarcaPedida;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.ProponerAsignacionRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.RegistroTardioRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.proceso.EmisionLibretas;
import pe.edu.virgenmaria.cuentasclaras.academico.model.EstadoAsistencia;
import pe.edu.virgenmaria.cuentasclaras.academico.model.TipoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.HojaNotas;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.NotaPedida;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.NotasRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.service.AvanceRegistro;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.service.CorreccionesNota;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.service.RegistroNotas;
import pe.edu.virgenmaria.cuentasclaras.academico.service.Asignaciones;
import pe.edu.virgenmaria.cuentasclaras.academico.service.ServicioAsistencia;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.TipoDocumento;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.AlumnoRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.MatriculaRepository;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.EstadoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.repository.SolicitudCambioRepository;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Seccion;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.AnioEscolarRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.SeccionRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.DiasHabiles;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.PersonaDemo;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Lo académico de ejemplo, hecho por las personas del colegio con los mismos servicios (firmas, segregación y bitácora):
 * lo usan la demo pública ({@link LibretasDemo}, perfil {@code demo}) y el arranque de desarrollo
 * ({@link AcademicoDemoDev}, perfil {@code dev}). Nada aquí escribe directo en la base.
 * <ul>
 *   <li>{@link #calificar}: Dirección guarda la evaluación del año por bimestres; Administración propone las asignaciones
 *       de la cuenta docente con sus áreas del catálogo y Dirección las aprueba; el I bimestre se abre, la docente pone
 *       las notas de todas sus hojas y Dirección lo cierra; {@code sistema.libretas} emite las libretas (de todas las
 *       secciones); la docente pide una corrección que aprueba Promotoría (versión 2) y el II bimestre queda abierto.</li>
 *   <li>{@link #asistencia}: la docente registra días pasados que anotó en papel (registro tardío que aprueba Dirección)
 *       y, si hoy hay clases, la hoja de hoy de una sección.</li>
 * </ul>
 * Si algo falla, queda en el log y quien llama sigue sin esa parte.
 */
@Component
@Profile({ "dev", "demo" })
public class EjemploAcademico {

	private static final Logger LOG = LoggerFactory.getLogger(EjemploAcademico.class);

	static final String MOTIVO = "Áreas del catálogo de la carga horaria (ejemplo de la demo)";

	static final String MOTIVO_CORRECCION = "El examen recalificado muestra que logró lo esperado (ejemplo de la demo)";

	static final String MOTIVO_TARDIO = "La asistencia de ese día se anotó en papel (ejemplo de la demo)";

	static final String CONCLUSION = "Avanza con apoyo en esta competencia; seguirá practicando en casa (ejemplo de la demo).";

	/** Las personas que actúan: cada una con su rol, y nunca la misma para proponer y aprobar. */
	public record Personas(String promotoria, String direccion, String administracion, String docente) {
	}

	/** Una asignación de la cuenta docente: la sección (grado y nombre), qué hace y los códigos de sus áreas del catálogo. */
	public record Calificable(Grado grado, String seccion, TipoAsignacion tipo, List<String> areas) {

		public Calificable {
			areas = List.copyOf(areas);
		}
	}

	/** Las fechas del I bimestre (cerrado) y del II (abierto), o {@code null} si hoy no da para un I bimestre terminado. */
	public record Bimestres(LocalDate inicioI, LocalDate finI, LocalDate inicioII, LocalDate finII) {
	}

	/** Lo que quedó de {@link #calificar}: el periodo abierto (II bimestre) y las hojas que la docente califica en él. */
	public record Calificado(Long periodoAbierto, int hojas, int notas, boolean conVersion2) {
	}

	private record Hoja(Long seccionId, Long areaId) {
	}

	private final ConfiguracionesEvaluacion configuraciones;

	private final Periodos periodos;

	private final Asignaciones asignaciones;

	private final BandejaAprobaciones bandeja;

	private final SolicitudCambioRepository solicitudes;

	private final RegistroNotas registro;

	private final AvanceRegistro avance;

	private final CorreccionesNota correcciones;

	private final EmisionLibretas emision;

	private final ServicioAsistencia asistencia;

	private final AnioEscolarRepository anios;

	private final SeccionRepository secciones;

	private final UsuarioRepository usuarios;

	private final AlumnoRepository alumnos;

	private final MatriculaRepository matriculas;

	private final CurriculoVersionRepository versiones;

	private final AreaCurricularRepository areas;

	private final DiasHabiles calendario;

	private final PersonaDemo personas;

	private final TransactionTemplate lectura;

	public EjemploAcademico(ConfiguracionesEvaluacion configuraciones, Periodos periodos, Asignaciones asignaciones,
			BandejaAprobaciones bandeja, SolicitudCambioRepository solicitudes, RegistroNotas registro, AvanceRegistro avance,
			CorreccionesNota correcciones, EmisionLibretas emision, ServicioAsistencia asistencia, AnioEscolarRepository anios,
			SeccionRepository secciones, UsuarioRepository usuarios, AlumnoRepository alumnos, MatriculaRepository matriculas,
			CurriculoVersionRepository versiones, AreaCurricularRepository areas, DiasHabiles calendario,
			PersonaDemo personas, PlatformTransactionManager transacciones) {
		this.configuraciones = configuraciones;
		this.periodos = periodos;
		this.asignaciones = asignaciones;
		this.bandeja = bandeja;
		this.solicitudes = solicitudes;
		this.registro = registro;
		this.avance = avance;
		this.correcciones = correcciones;
		this.emision = emision;
		this.asistencia = asistencia;
		this.anios = anios;
		this.secciones = secciones;
		this.usuarios = usuarios;
		this.alumnos = alumnos;
		this.matriculas = matriculas;
		this.versiones = versiones;
		this.areas = areas;
		this.calendario = calendario;
		this.personas = personas;
		this.lectura = new TransactionTemplate(transacciones);
		this.lectura.setReadOnly(true);
	}

	public static Bimestres bimestres(LocalDate inicioClases, LocalDate finClases, LocalDate hoy) {
		LocalDate finI = hoy.minusDays(8);
		LocalDate inicioI = inicioClases.isAfter(hoy.minusDays(70)) ? inicioClases : hoy.minusDays(70);
		LocalDate inicioII = finI.plusDays(1);
		LocalDate finII = finClases.isBefore(hoy.plusDays(50)) ? finClases : hoy.plusDays(50);
		if (inicioI.plusDays(21).isAfter(finI) || !finII.isAfter(hoy)) {
			return null;
		}
		return new Bimestres(inicioI, finI, inicioII, finII);
	}

	/**
	 * La evaluación del año, las asignaciones con áreas del catálogo y, si hoy deja un I bimestre terminado, sus notas, el
	 * cierre, las libretas (con una versión 2) y el II bimestre abierto.
	 *
	 * @param dniCorreccion el alumno de la corrección (versión 2 de su libreta); {@code null}: el primero con nota B
	 * @return lo que quedó, o {@code null} si no hubo bimestres (o algo falló: queda en el log)
	 */
	public Calificado calificar(long colegioId, Personas p, int anio, LocalDate hoy, List<Calificable> calificables,
			String dniCorreccion) {
		try {
			AnioEscolar delAnio = leer(colegioId, () -> anios.findByAnio(anio).orElseThrow());
			Long anioId = delAnio.getId();
			Long version = leer(colegioId, () -> versiones.findByCodigo("CNEB-2016.1").orElseThrow().getId());
			personas.como(colegioId, p.direccion(), () -> configuraciones.guardar(anioId, new ConfiguracionEvaluacion.Valores(
					version, TipoPeriodo.BIMESTRE, 500, false, 7, "Castellano", "DRE Lima Metropolitana", "UGEL 03")));
			Long docenteId = leer(colegioId, () -> usuarios.findByNombreUsuario(p.docente()).orElseThrow().getId());
			List<Hoja> hojas = new ArrayList<>();
			for (Calificable c : calificables) {
				Seccion seccion = leer(colegioId, () -> secciones.findByAnioEscolarIdAndGradoAndNombre(anioId, c.grado(),
						c.seccion()).orElseThrow());
				List<Long> deLaAsignacion = leer(colegioId, () -> areas.findByVersionIdAndNivelOrderByOrdenAsc(version,
						c.grado().nivel()).stream().filter(a -> c.areas().contains(a.getCodigo()))
						.map(AreaCurricular::getId).toList());
				if (deLaAsignacion.size() != c.areas().size()) {
					throw new NoSuchElementException("Áreas " + c.areas() + " incompletas en el catálogo de "
							+ c.grado().nivel());
				}
				// Una vigente con las mismas claves (la del sprint 9, en texto) se reemplaza al aprobar esta.
				Long asignacion = personas.como(colegioId, p.administracion(), () -> asignaciones.proponer(
						ProponerAsignacionRequest.conAreas(seccion.getId(), docenteId, c.tipo(), MOTIVO, deLaAsignacion)));
				aprobar(colegioId, p.direccion(), pendiente(colegioId, TipoSolicitud.ASIGNACION_DOCENTE, asignacion));
				deLaAsignacion.forEach(area -> hojas.add(new Hoja(seccion.getId(), area)));
				if (c.tipo() == TipoAsignacion.TUTOR) {
					// Las competencias transversales no se asignan: las califica el tutor(a).
					leer(colegioId, () -> areas.findByVersionIdAndNivelOrderByOrdenAsc(version, c.grado().nivel()).stream()
							.filter(AreaCurricular::isTransversal).map(AreaCurricular::getId).toList())
							.forEach(area -> hojas.add(new Hoja(seccion.getId(), area)));
				}
			}
			Bimestres fechas = bimestres(delAnio.getInicioClases(), delAnio.getFinClases(), hoy);
			if (fechas == null) {
				LOG.info("El colegio {} no tiene libretas de ejemplo: hoy ({}) no deja un I bimestre terminado.", colegioId,
						hoy);
				return null;
			}
			// El I bimestre: se abre, la docente pone las notas de todas sus hojas y Dirección lo cierra.
			Long primero = personas.como(colegioId, p.direccion(), () -> periodos.programar(anioId, fechas.inicioI(),
					fechas.finI()));
			personas.como(colegioId, p.direccion(), () -> {
				periodos.abrir(primero);
				return null;
			});
			int notas = 0;
			for (Hoja h : hojas) {
				notas += personas.como(colegioId, p.docente(), () -> calificarHoja(h, primero));
			}
			personas.como(colegioId, p.direccion(), () -> {
				avance.cerrar(primero);
				return null;
			});
			// sistema.libretas emite las libretas del I bimestre (todas las secciones, versión 1).
			emitir(colegioId);
			// La docente pide una corrección (B → A) y Promotoría la aprueba: la versión 2 con su aviso.
			Long matricula = dniCorreccion == null ? null : leer(colegioId, () -> alumnos
					.findByDocumentoTipoAndDocumentoNumero(TipoDocumento.DNI, dniCorreccion)
					.flatMap(a -> matriculas.findByAlumnoIdAndAnioEscolarId(a.getId(), anioId))
					.map(m -> m.getId()).orElse(null));
			Long nota = personas.como(colegioId, p.docente(), () -> notaB(hojas, primero, matricula));
			if (nota != null) {
				Long correccion = personas.como(colegioId, p.docente(), () -> correcciones.pedir(nota, "A", null,
						MOTIVO_CORRECCION));
				aprobar(colegioId, p.promotoria(), correccion);
				emitir(colegioId);
			}
			// El II bimestre, abierto: la hoja de la docente lista para calificar.
			Long segundo = personas.como(colegioId, p.direccion(), () -> periodos.programar(anioId, fechas.inicioII(),
					fechas.finII()));
			personas.como(colegioId, p.direccion(), () -> {
				periodos.abrir(segundo);
				return null;
			});
			return new Calificado(segundo, hojas.size(), notas, nota != null);
		}
		catch (ReglaNegocioException | IllegalStateException | NoSuchElementException e) {
			LOG.warn("No se cargaron las notas y libretas de ejemplo del colegio {}: {}", colegioId, e.getMessage());
			return null;
		}
	}

	/**
	 * La asistencia de ejemplo de las secciones de la docente: los últimos días hábiles que anotó en papel (cada uno con su
	 * registro tardío, que aprueba Dirección) y, si hoy hay clases, la hoja de hoy de {@code deHoy}.
	 *
	 * @return cuántos días de sección quedaron registrados
	 */
	public int asistencia(long colegioId, Personas p, int anio, LocalDate hoy, List<Calificable> calificables,
			int diasPasados, Calificable deHoy) {
		try {
			Long anioId = leer(colegioId, () -> anios.findByAnio(anio).orElseThrow().getId());
			Set<Long> seccionesDocente = new LinkedHashSet<>();
			for (Calificable c : calificables) {
				seccionesDocente.add(seccion(colegioId, anioId, c));
			}
			List<LocalDate> dias = new ArrayList<>();
			for (LocalDate d = calendario.anteriorDiaHabil(hoy); dias.size() < diasPasados;
					d = calendario.anteriorDiaHabil(d)) {
				dias.add(0, d);
			}
			int registrados = 0;
			for (Long seccion : seccionesDocente) {
				for (int i = 0; i < dias.size(); i++) {
					LocalDate dia = dias.get(i);
					int desfase = i;
					try {
						Long solicitud = personas.como(colegioId, p.docente(), () -> asistencia.pedirRegistroTardio(seccion,
								new RegistroTardioRequest(dia, MOTIVO_TARDIO)));
						aprobar(colegioId, p.direccion(), solicitud);
						personas.como(colegioId, p.docente(), () -> asistencia.guardar(seccion, new HojaRequest(
								marcas(asistencia.hojaTardia(seccion, solicitud).alumnos(), desfase), solicitud)));
						registrados++;
					}
					catch (ReglaNegocioException e) {
						LOG.info("Sin asistencia de ejemplo del {} en la sección {}: {}", dia, seccion, e.getMessage());
					}
				}
			}
			if (deHoy != null && calendario.esHabil(hoy)) {
				Long seccion = seccion(colegioId, anioId, deHoy);
				try {
					personas.como(colegioId, p.docente(), () -> asistencia.guardar(seccion, new HojaRequest(
							marcas(asistencia.hoja(seccion).alumnos(), dias.size()), null)));
					registrados++;
				}
				catch (ReglaNegocioException e) {
					LOG.info("Sin asistencia de ejemplo de hoy en la sección {}: {}", seccion, e.getMessage());
				}
			}
			return registrados;
		}
		catch (IllegalStateException | NoSuchElementException e) {
			LOG.warn("No se cargó la asistencia de ejemplo del colegio {}: {}", colegioId, e.getMessage());
			return 0;
		}
	}

	/** Las notas de una hoja (niveles variados, con la conclusión donde la norma la exige). @return cuántas guardó */
	private int calificarHoja(Hoja h, Long periodo) {
		HojaNotas hoja = registro.hoja(h.seccionId(), h.areaId(), periodo, null);
		List<NotaPedida> notas = new ArrayList<>();
		String[] niveles = { "A", "AD", "A", "B" };
		for (HojaNotas.FilaAlumno fila : hoja.filas()) {
			if (fila.exonerado()) {
				continue;
			}
			for (HojaNotas.ColumnaCompetencia c : hoja.competencias()) {
				String nivel = niveles[(fila.numero() + c.id().intValue()) % niveles.length];
				boolean conConclusion = hoja.conclusionSiempre() || hoja.exigenConclusion().contains(nivel);
				notas.add(new NotaPedida(fila.matriculaId(), c.id(), nivel, conConclusion ? CONCLUSION : null));
			}
		}
		registro.guardar(h.seccionId(), h.areaId(), periodo, new NotasRequest(notas, null));
		return notas.size();
	}

	/** La primera nota B de las hojas (de esa matrícula, si se pidió y la tiene; si no, de cualquiera). */
	private Long notaB(List<Hoja> hojas, Long periodo, Long matricula) {
		Long cualquiera = null;
		for (Hoja h : hojas) {
			for (HojaNotas.FilaAlumno fila : registro.hoja(h.seccionId(), h.areaId(), periodo, null).filas()) {
				for (HojaNotas.Celda celda : fila.celdas()) {
					if ("B".equals(celda.nivel()) && celda.calificacionId() != null) {
						if (matricula == null || matricula.equals(fila.matriculaId())) {
							return celda.calificacionId();
						}
						cualquiera = cualquiera == null ? celda.calificacionId() : cualquiera;
					}
				}
			}
		}
		return cualquiera;
	}

	/** Casi todos presentes; según el día, alguien llega tarde y alguien falta. */
	static List<MarcaPedida> marcas(List<AlumnoEnHoja> filas, int dia) {
		List<MarcaPedida> marcas = new ArrayList<>();
		for (AlumnoEnHoja fila : filas) {
			int turno = (fila.numero() + dia) % 5;
			EstadoAsistencia estado = turno == 0 ? EstadoAsistencia.TARDE
					: turno == 3 && fila.numero() % 2 == 0 ? EstadoAsistencia.FALTA : EstadoAsistencia.PRESENTE;
			marcas.add(new MarcaPedida(fila.alumnoId(), estado, estado == EstadoAsistencia.TARDE ? LocalTime.of(8, 12)
					: null));
		}
		return marcas;
	}

	/** La pasada de sistema.libretas; si chocó con la programada (cada 5 minutos), la siguiente retoma lo pendiente. */
	private void emitir(long colegioId) {
		try {
			emision.enColegio(colegioId);
		}
		catch (IllegalStateException e) {
			LOG.info("La emisión de libretas de ejemplo se repite: {}", e.getMessage());
			emision.enColegio(colegioId);
		}
	}

	private Long seccion(long colegioId, Long anioId, Calificable c) {
		return leer(colegioId, () -> secciones.findByAnioEscolarIdAndGradoAndNombre(anioId, c.grado(), c.seccion())
				.map(Seccion::getId).orElseThrow());
	}

	private Long pendiente(long colegioId, TipoSolicitud tipo, Long entidad) {
		return leer(colegioId, () -> solicitudes.findByEstadoOrderByIdAsc(EstadoSolicitud.PENDIENTE).stream()
				.filter(s -> s.getTipo() == tipo && Objects.equals(entidad, s.getEntidadId())).findFirst().orElseThrow()
				.getId());
	}

	private void aprobar(long colegioId, String quien, Long solicitud) {
		personas.como(colegioId, quien, () -> {
			bandeja.aprobar(solicitud, null);
			return null;
		});
	}

	private <T> T leer(long colegioId, Supplier<T> consulta) {
		return ContextoColegio.en(colegioId, () -> lectura.execute(t -> consulta.get()));
	}
}
