package pe.edu.virgenmaria.cuentasclaras.alumnos.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.ApoderadoRepository;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.DatosSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.ManejadorSolicitud;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

/**
 * Cambio de celular o correo de un apoderado aprobado por otra persona (auditoría A4). Sprint 4: avisará también al
 * contacto anterior.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class ManejadorContactoApoderado implements ManejadorSolicitud {

	private final ApoderadoRepository apoderados;

	private final RegistroAlumnos registro;

	public ManejadorContactoApoderado(ApoderadoRepository apoderados, RegistroAlumnos registro) {
		this.apoderados = apoderados;
		this.registro = registro;
	}

	@Override
	public TipoSolicitud tipo() {
		return TipoSolicitud.CAMBIO_CONTACTO_APODERADO;
	}

	@Override
	public void aplicar(SolicitudCambio solicitud, String aprobador) {
		Apoderado apoderado = apoderados.findById(solicitud.getEntidadId())
				.orElseThrow(() -> new ReglaNegocioException("El apoderado de la solicitud no existe."));
		registro.cambiarContactoAprobado(apoderado, DatosSolicitud.leer(solicitud.getDatos()), solicitud.getMotivo(),
				solicitud.getSolicitadoPor(), aprobador);
	}
}
