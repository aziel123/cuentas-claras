package pe.edu.virgenmaria.cuentasclaras.alumnos.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Alumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.AlumnoRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.ApoderadoRepository;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.DatosSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.ManejadorSolicitud;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.util.Map;

/**
 * Cambio de responsable de pago aprobado por otra persona (auditoría A4). Si el responsable cambió desde la solicitud
 * o el nuevo ya no está activo, no se aplica.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class ManejadorResponsablePago implements ManejadorSolicitud {

	private final AlumnoRepository alumnos;

	private final ApoderadoRepository apoderados;

	private final RegistroAlumnos registro;

	public ManejadorResponsablePago(AlumnoRepository alumnos, ApoderadoRepository apoderados,
			RegistroAlumnos registro) {
		this.alumnos = alumnos;
		this.apoderados = apoderados;
		this.registro = registro;
	}

	@Override
	public TipoSolicitud tipo() {
		return TipoSolicitud.CAMBIO_RESPONSABLE_PAGO;
	}

	@Override
	public void aplicar(SolicitudCambio solicitud, String aprobador) {
		Map<String, String> datos = DatosSolicitud.leer(solicitud.getDatos());
		Alumno alumno = alumnos.findById(solicitud.getEntidadId())
				.orElseThrow(() -> new ReglaNegocioException("El alumno de la solicitud no existe."));
		if (!alumno.activo()) {
			throw new ReglaNegocioException(alumno.nombreCompleto() + " ya no está activo.");
		}
		if (!alumno.getResponsablePago().getId().toString().equals(datos.get("anteriorId"))) {
			throw new ReglaNegocioException("El responsable de pago de " + alumno.nombreCompleto() + " cambió desde que "
					+ "se pidió: rechaza esta solicitud.");
		}
		Apoderado nuevo = apoderados.findById(Long.valueOf(datos.get("nuevoId")))
				.orElseThrow(() -> new ReglaNegocioException("El apoderado de la solicitud no existe."));
		if (!nuevo.isActivo()) {
			throw new ReglaNegocioException(nuevo.nombreCompleto() + " está desactivado: rechaza esta solicitud.");
		}
		registro.cambiarResponsable(alumno, nuevo, solicitud.getMotivo(), solicitud.getSolicitadoPor(), aprobador);
	}
}
