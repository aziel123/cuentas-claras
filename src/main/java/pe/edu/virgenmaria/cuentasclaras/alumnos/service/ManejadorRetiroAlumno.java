package pe.edu.virgenmaria.cuentasclaras.alumnos.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Alumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.AlumnoRepository;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.DatosSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.ManejadorSolicitud;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.time.LocalDate;

/** Retiro aprobado por otra persona (auditoría A6): vuelve a validar la fecha y retira al alumno y sus matrículas. */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class ManejadorRetiroAlumno implements ManejadorSolicitud {

	private final AlumnoRepository alumnos;

	private final RegistroAlumnos registro;

	public ManejadorRetiroAlumno(AlumnoRepository alumnos, RegistroAlumnos registro) {
		this.alumnos = alumnos;
		this.registro = registro;
	}

	@Override
	public TipoSolicitud tipo() {
		return TipoSolicitud.RETIRO_ALUMNO;
	}

	@Override
	public void aplicar(SolicitudCambio solicitud, String aprobador) {
		Alumno alumno = alumnos.findById(solicitud.getEntidadId())
				.orElseThrow(() -> new ReglaNegocioException("El alumno de la solicitud no existe."));
		LocalDate fecha = LocalDate.parse(DatosSolicitud.leer(solicitud.getDatos()).get("fecha"));
		registro.retirar(alumno, fecha, solicitud.getMotivo(), solicitud.getSolicitadoPor(), aprobador);
	}
}
