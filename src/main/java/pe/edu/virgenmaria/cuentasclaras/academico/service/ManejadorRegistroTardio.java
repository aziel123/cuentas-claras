package pe.edu.virgenmaria.cuentasclaras.academico.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.repository.RegistroAsistenciaRepository;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.ManejadorSolicitud;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.SeccionRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Sprint 9, tanda 2 (decisión 146, E10): aprueba la solicitud {@code REGISTRO_ASISTENCIA_TARDIO} en la bandeja. No
 * registra nada: desde ese momento el docente que la pidió puede guardar la hoja de ESE día (trg_registro_asistencia_nace
 * exige SU solicitud aprobada y firmada, de esa sección y esa fecha). Las faltas de ese día se avisan a las familias como
 * corrección. Vuelve a validar que el día sigue sin registro y dentro de los 15 días de la base.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class ManejadorRegistroTardio implements ManejadorSolicitud {

	private final RegistroAsistenciaRepository registros;

	private final SeccionRepository secciones;

	private final DocentesDeSeccion docentes;

	private final AuditoriaService auditoria;

	private final Clock reloj;

	public ManejadorRegistroTardio(RegistroAsistenciaRepository registros, SeccionRepository secciones,
			DocentesDeSeccion docentes, AuditoriaService auditoria, Clock reloj) {
		this.registros = registros;
		this.secciones = secciones;
		this.docentes = docentes;
		this.auditoria = auditoria;
		this.reloj = reloj;
	}

	@Override
	public TipoSolicitud tipo() {
		return TipoSolicitud.REGISTRO_ASISTENCIA_TARDIO;
	}

	@Override
	public String entidad() {
		return ServicioAsistencia.ENTIDAD_SECCION;
	}

	@Override
	public void aplicar(SolicitudCambio solicitud, String aprobador) {
		LocalDate fecha = ServicioAsistencia.fechaDe(solicitud)
				.orElseThrow(() -> new ReglaNegocioException("La solicitud no tiene fecha: recházala."));
		if (fecha.isBefore(LocalDate.now(reloj).minusDays(ServicioAsistencia.MAX_DIAS_TARDIO))) {
			throw new ReglaNegocioException("Pasaron más de " + ServicioAsistencia.MAX_DIAS_TARDIO + " días: ese día ya no "
					+ "se puede registrar. Recházala.");
		}
		if (registros.findBySeccionIdAndFecha(solicitud.getEntidadId(), fecha).isPresent()) {
			throw new ReglaNegocioException("Ese día ya tiene asistencia registrada: recházala.");
		}
		if (!docentes.es(solicitud.getSolicitadoPor(), solicitud.getEntidadId())) {
			throw new ReglaNegocioException("Quien lo pidió ya no es docente de la sección: rechaza la solicitud.");
		}
		auditoria.registrar(AccionAuditoria.SOLICITUD_APROBADA, ServicioAsistencia.ENTIDAD_SECCION,
				solicitud.getEntidadId().toString(), null, fecha.toString(), "Aprobó que "
						+ solicitud.getSolicitadoPor() + " registre la asistencia del " + fecha + " (solicitud "
						+ solicitud.getId() + "). Las faltas de ese día se avisan a las familias.");
	}

	@Override
	public List<String> detalle(SolicitudCambio solicitud) {
		List<String> lineas = new ArrayList<>();
		secciones.findById(solicitud.getEntidadId()).ifPresent(s -> lineas.add("Sección: " + s.etiqueta()));
		ServicioAsistencia.fechaDe(solicitud).ifPresent(f -> lineas.add("Día: " + DiaDeClases.texto(f) + " (" + f + ")"));
		return lineas;
	}

	@Override
	public String advertencia(SolicitudCambio solicitud) {
		return "Al aprobar, el docente registra ese día desde su hoja (lo que anotó en papel). Pídele la hoja de papel.";
	}
}
