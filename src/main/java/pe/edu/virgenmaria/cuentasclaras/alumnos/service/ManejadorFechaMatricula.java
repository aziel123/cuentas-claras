package pe.edu.virgenmaria.cuentasclaras.alumnos.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Matricula;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.MatriculaRepository;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.DatosSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.ManejadorSolicitud;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.time.LocalDate;

/**
 * Ingreso tardío aprobado por otra persona (auditoría A3): cambia la fecha de la matrícula y publica
 * {@link FechaIngresoCambiada} (cobranza anula las pensiones anteriores al ingreso, en la misma transacción).
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class ManejadorFechaMatricula implements ManejadorSolicitud {

	private final MatriculaRepository matriculas;

	private final RegistroAlumnos registro;

	public ManejadorFechaMatricula(MatriculaRepository matriculas, RegistroAlumnos registro) {
		this.matriculas = matriculas;
		this.registro = registro;
	}

	@Override
	public TipoSolicitud tipo() {
		return TipoSolicitud.FECHA_MATRICULA;
	}

	@Override
	public void aplicar(SolicitudCambio solicitud, String aprobador) {
		Matricula matricula = matriculas.findById(solicitud.getEntidadId())
				.orElseThrow(() -> new ReglaNegocioException("La matrícula de la solicitud no existe."));
		LocalDate fecha = LocalDate.parse(DatosSolicitud.leer(solicitud.getDatos()).get("fecha"));
		registro.cambiarFechaIngreso(matricula, fecha, solicitud.getSolicitadoPor(), aprobador, solicitud.getId());
	}
}
