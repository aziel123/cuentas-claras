package pe.edu.virgenmaria.cuentasclaras.academico.notas.service;

import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.EstadoPeriodo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.PeriodoEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.academico.repository.AsignacionDocenteRepository;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.EstadoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.repository.SolicitudCambioRepository;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.DatosSolicitud;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Seccion;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Sprint 10, tanda 2 (sección 3.6, E23): la nota tardía aprobada del docente en sesión para esa sección, área y periodo:
 * {@code CALIFICACION_TARDIA} (entidad {@code asignacion_docente}: su asignación en la sección; datos: periodo y área), que
 * pidió él, APROBADA hace {@value #HORAS} horas o menos, con el periodo CERRADO y todavía sin su lote. Si no, 404 o un
 * mensaje claro. En MySQL lo exige trg_lote_calificacion_nace.
 */
@Component
class TardiasAprobadas {

	static final int HORAS = 48;

	static final String ENTIDAD = "asignacion_docente";

	private final SolicitudCambioRepository solicitudes;

	private final AsignacionDocenteRepository asignaciones;

	private final pe.edu.virgenmaria.cuentasclaras.academico.notas.repository.LoteCalificacionRepository lotes;

	private final Clock reloj;

	TardiasAprobadas(SolicitudCambioRepository solicitudes, AsignacionDocenteRepository asignaciones,
			pe.edu.virgenmaria.cuentasclaras.academico.notas.repository.LoteCalificacionRepository lotes, Clock reloj) {
		this.solicitudes = solicitudes;
		this.asignaciones = asignaciones;
		this.lotes = lotes;
		this.reloj = reloj;
	}

	/** Las del docente en sesión, aprobadas hace 48 horas o menos y todavía sin su lote. */
	List<pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.MisNotasVista.TardiaPorRegistrar> porRegistrar() {
		String yo = Personas.actual().getUsername();
		LocalDateTime desde = LocalDateTime.now(reloj).minusHours(HORAS);
		return solicitudes.findByTipoOrderByIdDesc(TipoSolicitud.CALIFICACION_TARDIA).stream()
				.filter(s -> s.getEstado() == EstadoSolicitud.APROBADA && yo.equals(s.getSolicitadoPor())
						&& s.getResueltoEn() != null && !s.getResueltoEn().isBefore(desde)
						&& !lotes.existsBySolicitudId(s.getId()))
				.flatMap(s -> asignaciones.findById(s.getEntidadId()).map(a -> {
					Map<String, String> datos = DatosSolicitud.leer(s.getDatos());
					return new pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.MisNotasVista.TardiaPorRegistrar(
							s.getId(), a.getSeccionId(), Long.valueOf(datos.get("area_id")),
							Long.valueOf(datos.get("periodo_id")), s.getResumen());
				}).stream())
				.toList();
	}

	SolicitudCambio de(long solicitudId, Seccion seccion, long areaId, PeriodoEvaluacion periodo) {
		String yo = Personas.actual().getUsername();
		SolicitudCambio solicitud = solicitudes.findById(solicitudId)
				.filter(s -> s.getTipo() == TipoSolicitud.CALIFICACION_TARDIA && ENTIDAD.equals(s.getEntidad())
						&& yo.equals(s.getSolicitadoPor()))
				.filter(s -> asignaciones.findById(s.getEntidadId()).map(a -> a.getSeccionId().equals(seccion.getId()))
						.orElse(false))
				.orElseThrow(() -> new RecursoNoEncontradoException("Solicitud no encontrada"));
		Map<String, String> datos = DatosSolicitud.leer(solicitud.getDatos());
		if (!Objects.equals(datos.get("periodo_id"), periodo.getId().toString())
				|| !Objects.equals(datos.get("area_id"), Long.toString(areaId))) {
			throw new RecursoNoEncontradoException("Solicitud no encontrada");
		}
		if (solicitud.getEstado() != EstadoSolicitud.APROBADA) {
			throw new ReglaNegocioException("La nota tardía todavía no está aprobada (o se rechazó).");
		}
		if (periodo.getEstado() != EstadoPeriodo.CERRADO) {
			throw new ReglaNegocioException("El " + periodo.getNombre() + " está abierto: registra en la hoja de siempre.");
		}
		if (solicitud.getResueltoEn() == null || solicitud.getResueltoEn().isBefore(LocalDateTime.now(reloj).minusHours(HORAS))) {
			throw new ReglaNegocioException("Pasaron más de " + HORAS + " horas desde que se aprobó: la nota tardía venció.");
		}
		if (lotes.existsBySolicitudId(solicitud.getId())) {
			throw new ReglaNegocioException("Esa nota tardía ya se registró.");
		}
		return solicitud;
	}
}
