package pe.edu.virgenmaria.cuentasclaras.matricula.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.EstadoMatricula;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Matricula;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.MatriculaRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.Cuota;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.PlanPension;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.CuotaRepository;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.PlanPensionRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoAnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Seccion;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.AnioEscolarRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.SeccionRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;
import pe.edu.virgenmaria.cuentasclaras.matricula.dto.CampanaRenovacion;
import pe.edu.virgenmaria.cuentasclaras.matricula.model.CanalRespuesta;
import pe.edu.virgenmaria.cuentasclaras.matricula.model.EstadoRenovacion;
import pe.edu.virgenmaria.cuentasclaras.matricula.model.RenovacionMatricula;
import pe.edu.virgenmaria.cuentasclaras.matricula.repository.MatriculasReservadasRepository;
import pe.edu.virgenmaria.cuentasclaras.matricula.repository.RenovacionMatriculaRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Campaña de renovación de matrícula del año siguiente (sprint 5, tanda 2; decisiones 53 a 56):
 * <ul>
 *   <li>Administración la abre cuando el año está PLANIFICADO, con secciones y con el plan APROBADO de cada nivel. El
 *       sistema propone, a cada alumno ACTIVO con matrícula ACTIVA del año en curso, el grado siguiente y la sección de la
 *       misma letra (si no existe, la primera). 5.° de secundaria no se propone. La deuda es solo informativa.</li>
 *   <li>Dirección marca repitencias o cambia la sección mientras la familia no responde.</li>
 *   <li>Administración registra la respuesta que la familia da en persona: la familia recibe un aviso (G16).</li>
 * </ul>
 * Sin respuesta no hay deuda: la propuesta vence. Al confirmarse, {@code sistema.matricula} reserva la matrícula.
 */
@Service
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR','ADMINISTRACION')")
@Transactional
public class ServicioCampanaRenovacion {

	private static final Set<EstadoRenovacion> RESPONDIDAS = EnumSet.of(EstadoRenovacion.CONFIRMADA,
			EstadoRenovacion.MATRICULADA, EstadoRenovacion.NO_CONTINUA);

	private final RenovacionMatriculaRepository renovaciones;

	private final MatriculasReservadasRepository reservadas;

	private final AnioEscolarRepository anios;

	private final SeccionRepository secciones;

	private final MatriculaRepository matriculas;

	private final PlanPensionRepository planes;

	private final CuotaRepository cuotas;

	private final AuditoriaService auditoria;

	private final ApplicationEventPublisher eventos;

	private final Clock reloj;

	public ServicioCampanaRenovacion(RenovacionMatriculaRepository renovaciones, MatriculasReservadasRepository reservadas,
			AnioEscolarRepository anios, SeccionRepository secciones, MatriculaRepository matriculas,
			PlanPensionRepository planes, CuotaRepository cuotas, AuditoriaService auditoria,
			ApplicationEventPublisher eventos, Clock reloj) {
		this.renovaciones = renovaciones;
		this.reservadas = reservadas;
		this.anios = anios;
		this.secciones = secciones;
		this.matriculas = matriculas;
		this.planes = planes;
		this.cuotas = cuotas;
		this.auditoria = auditoria;
		this.eventos = eventos;
		this.reloj = reloj;
	}

	/** El año planificado más próximo (la campaña de la pantalla), si existe. */
	@Transactional(readOnly = true)
	public Optional<Long> anioPlanificado() {
		return anios.findAllByOrderByAnioDesc().stream().filter(a -> a.getEstado() == EstadoAnioEscolar.PLANIFICADO)
				.min(Comparator.comparingInt(AnioEscolar::getAnio)).map(AnioEscolar::getId);
	}

	@Transactional(readOnly = true)
	public CampanaRenovacion campana(Long anioDestinoId) {
		AnioEscolar anio = anios.findById(anioDestinoId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Año escolar no encontrado"));
		List<RenovacionMatricula> todas = renovaciones.delAnio(anioDestinoId);
		Map<EstadoRenovacion, Long> conteo = new EnumMap<>(EstadoRenovacion.class);
		todas.forEach(r -> conteo.merge(r.getEstado(), 1L, Long::sum));
		List<CampanaRenovacion.Fila> filas = todas.stream().map(r -> new CampanaRenovacion.Fila(r.getId(),
				r.getAlumno().nombreCompleto(), r.getGradoDestino().etiqueta(), r.getSeccionDestino().getNombre(),
				r.getSeccionDestino().getId(), r.getEstado().etiqueta(), r.getEstado().variante(), r.conDeuda(),
				r.getDeudaAlProponer(), r.getVenceEn(), r.getCanalRespuesta() == null ? null
						: r.getCanalRespuesta() == CanalRespuesta.PORTAL ? "Portal" : "En persona",
				r.getEstado() == EstadoRenovacion.PROPUESTA)).toList();
		List<CampanaRenovacion.OpcionSeccion> opciones = secciones.findByAnioEscolarIdOrderByGradoAscNombreAsc(anioDestinoId)
				.stream().filter(Seccion::isActiva)
				.map(s -> new CampanaRenovacion.OpcionSeccion(s.getId(), s.etiqueta())).toList();
		return new CampanaRenovacion(anio.getId(), anio.getAnio(), anio.getEstado() == EstadoAnioEscolar.PLANIFICADO,
				todas.size(), conteo.getOrDefault(EstadoRenovacion.PROPUESTA, 0L),
				conteo.getOrDefault(EstadoRenovacion.CONFIRMADA, 0L) + conteo.getOrDefault(EstadoRenovacion.MATRICULADA, 0L),
				reservadas.reservadasDelAnio(anioDestinoId), reservadas.activadasDelAnio(anioDestinoId),
				conteo.getOrDefault(EstadoRenovacion.NO_CONTINUA, 0L), conteo.getOrDefault(EstadoRenovacion.VENCIDA, 0L),
				filas, opciones);
	}

	/**
	 * Abre (o completa) la campaña: crea la propuesta de cada alumno que continúa. Es idempotente: un alumno que ya tiene
	 * su propuesta no se vuelve a proponer.
	 *
	 * @return cuántas propuestas nuevas se crearon
	 */
	@PreAuthorize("hasRole('ADMINISTRACION')")
	public int abrir(Long anioDestinoId, LocalDate venceEn) {
		AnioEscolar destino = anios.bloquear(anioDestinoId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Año escolar no encontrado"));
		LocalDate hoy = LocalDate.now(reloj);
		if (destino.getEstado() != EstadoAnioEscolar.PLANIFICADO) {
			throw new ReglaNegocioException("La renovación se abre para un año planificado; " + destino.getAnio()
					+ " no lo está.");
		}
		if (venceEn == null || venceEn.isBefore(hoy) || venceEn.isAfter(destino.getInicioClases())) {
			throw new ReglaNegocioException("Elige la fecha límite para responder: desde hoy y hasta el inicio de clases ("
					+ Calendario.formatear(destino.getInicioClases()) + ").");
		}
		AnioEscolar origen = anios.findByEstado(EstadoAnioEscolar.EN_CURSO)
				.orElseThrow(() -> new ReglaNegocioException("No hay un año en curso del que renovar matrículas."));
		List<Matricula> continuan = matriculas.findByAnioEscolarIdAndEstado(origen.getId(), EstadoMatricula.ACTIVA)
				.stream().filter(m -> m.getAlumno().activo()).filter(m -> m.getSeccion().getGrado().siguiente().isPresent())
				.sorted(Comparator.comparing(Matricula::getId)).toList();
		Map<Grado, List<Seccion>> porGrado = secciones.findByAnioEscolarIdOrderByGradoAscNombreAsc(destino.getId()).stream()
				.filter(Seccion::isActiva).collect(Collectors.groupingBy(Seccion::getGrado));
		// Cada grado destino necesita secciones y cada nivel su plan APROBADO (la cuota de matrícula sale de ahí).
		List<String> faltan = new ArrayList<>();
		Set<Nivel> niveles = EnumSet.noneOf(Nivel.class);
		for (Matricula m : continuan) {
			Grado siguiente = m.getSeccion().getGrado().siguiente().orElseThrow();
			niveles.add(siguiente.nivel());
			if (!porGrado.containsKey(siguiente) && !faltan.contains("secciones de " + siguiente.etiqueta())) {
				faltan.add("secciones de " + siguiente.etiqueta());
			}
		}
		for (Nivel nivel : niveles) {
			boolean aprobado = planes.findByAnioEscolarIdAndNivelAndVigenteTrue(destino.getId(), nivel)
					.filter(PlanPension::aprobado).isPresent();
			if (!aprobado) {
				faltan.add("el plan de pensiones " + destino.getAnio() + " de " + nivel.etiqueta() + " aprobado");
			}
		}
		if (!faltan.isEmpty()) {
			throw new ReglaNegocioException("Antes de abrir la renovación " + destino.getAnio() + " falta: "
					+ String.join(", ", faltan) + ".");
		}
		List<Long> nuevas = new ArrayList<>();
		for (Matricula m : continuan) {
			if (renovaciones.existsByAlumnoIdAndAnioDestinoId(m.getAlumno().getId(), destino.getId())) {
				continue;
			}
			Grado siguiente = m.getSeccion().getGrado().siguiente().orElseThrow();
			List<Seccion> opciones = porGrado.get(siguiente);
			Seccion seccion = opciones.stream().filter(s -> s.getNombre().equals(m.getSeccion().getNombre())).findFirst()
					.orElse(opciones.get(0));
			RenovacionMatricula propuesta = renovaciones.saveAndFlush(RenovacionMatricula.propuesta(m, seccion,
					deudaVencida(m.getAlumno().getId(), hoy), venceEn));
			nuevas.add(propuesta.getId());
		}
		auditoria.registrar(AccionAuditoria.RENOVACION_CAMPANA_ABIERTA, "anio_escolar", destino.getId().toString(), null,
				nuevas.size() + " propuestas; responden hasta el " + Calendario.formatear(venceEn),
				"Renovación " + destino.getAnio() + " desde " + origen.getAnio() + ": " + continuan.size()
						+ " alumnos continúan (5.° de secundaria no se propone).");
		if (!nuevas.isEmpty()) {
			eventos.publishEvent(new RenovacionesAbiertas(List.copyOf(nuevas)));
		}
		return nuevas.size();
	}

	/** Dirección: repitencia o cambio de sección, solo en PROPUESTA (en MySQL lo exige el trigger). */
	@PreAuthorize("hasRole('DIRECTOR')")
	public void cambiarDestino(Long renovacionId, Long seccionId) {
		RenovacionMatricula renovacion = renovaciones.bloquear(renovacionId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Renovación no encontrada"));
		Seccion seccion = Optional.ofNullable(seccionId).flatMap(secciones::findById)
				.orElseThrow(() -> new ReglaNegocioException("Elige la sección."));
		String anterior = renovacion.getSeccionDestino().etiqueta();
		renovacion.cambiarDestino(seccion);
		renovaciones.saveAndFlush(renovacion);
		auditoria.registrar(AccionAuditoria.RENOVACION_DESTINO_CAMBIADO, "renovacion_matricula", renovacionId.toString(),
				anterior, seccion.etiqueta(), "Alumno " + renovacion.getAlumno().nombreCompleto() + ", año "
						+ renovacion.getAnioDestino().getAnio() + ".");
	}

	/**
	 * Administración registra la respuesta que la familia dio en persona. La familia recibe un aviso («si no la pediste,
	 * avísanos»): Administración no puede renovar en silencio (G16).
	 */
	@PreAuthorize("hasRole('ADMINISTRACION')")
	public void registrarPresencial(Long renovacionId, boolean continua) {
		RenovacionMatricula renovacion = renovaciones.bloquear(renovacionId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Renovación no encontrada"));
		renovacion.responder(continua, CanalRespuesta.PRESENCIAL, SesionActual.usuario(), LocalDateTime.now(reloj));
		renovaciones.saveAndFlush(renovacion);
		auditoria.registrar(AccionAuditoria.RENOVACION_PRESENCIAL, "renovacion_matricula", renovacionId.toString(),
				"PROPUESTA", renovacion.getEstado().name(), "Alumno " + renovacion.getAlumno().nombreCompleto() + ": "
						+ (continua ? "continúa" : "no continuará") + " en " + renovacion.getAnioDestino().getAnio()
						+ " (respuesta en persona; se avisó a la familia).");
		eventos.publishEvent(new RenovacionRespondida(renovacionId, true, continua));
		if (continua) {
			eventos.publishEvent(new RenovacionConfirmada(ContextoColegio.actual(), renovacionId));
		}
	}

	/** Saldos vencidos (de cualquier año) del alumno al abrir la campaña: solo informativo (decisión 55). */
	private BigDecimal deudaVencida(Long alumnoId, LocalDate hoy) {
		return Dinero.sumar(cuotas.porPagarDeAlumnos(List.of(alumnoId)).stream().filter(c -> c.vencidaAl(hoy))
				.map(Cuota::saldo).toList());
	}

	static boolean respondida(EstadoRenovacion estado) {
		return RESPONDIDAS.contains(estado);
	}
}
