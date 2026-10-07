package pe.edu.virgenmaria.cuentasclaras.matricula.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Matricula;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision.Gravedad;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertasRevision;
import pe.edu.virgenmaria.cuentasclaras.matricula.model.EstadoRenovacion;
import pe.edu.virgenmaria.cuentasclaras.matricula.repository.MatriculasReservadasRepository;
import pe.edu.virgenmaria.cuentasclaras.matricula.repository.RenovacionMatriculaRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Alertas de la matrícula del año siguiente para «Para revisar» de Promotoría (sprint 5, sección 13):
 * <ul>
 *   <li>ATENCIÓN: matrícula ACTIVA cuya cuota de matrícula volvió a estar por pagar (se anuló el pago). No se desactiva
 *       sola (decisión 57): se resuelve con las aprobaciones existentes.</li>
 *   <li>ATENCIÓN: renovaciones confirmadas que el sistema aún no pudo reservar.</li>
 * </ul>
 */
@Service
@PreAuthorize("hasRole('PROMOTOR')")
@Transactional(readOnly = true)
public class AlertasMatricula implements AlertasRevision {

	static final String MODULO = "Matrícula";

	private final MatriculasReservadasRepository reservadas;

	private final RenovacionMatriculaRepository renovaciones;

	public AlertasMatricula(MatriculasReservadasRepository reservadas, RenovacionMatriculaRepository renovaciones) {
		this.reservadas = reservadas;
		this.renovaciones = renovaciones;
	}

	@Override
	public List<AlertaRevision> alertas() {
		List<AlertaRevision> alertas = new ArrayList<>();
		List<Matricula> porPagar = reservadas.activasConMatriculaPorPagar();
		if (!porPagar.isEmpty()) {
			alertas.add(new AlertaRevision(Gravedad.ATENCION, MODULO, porPagar.size() + " matrícula(s) activa(s) con la "
					+ "cuota de matrícula otra vez por pagar (se anuló su pago): "
					+ porPagar.stream().limit(3).map(m -> m.getAlumno().nombreCompleto()).collect(Collectors.joining(", "))
					+ ". No se desactivan solas: revísalas con Administración.", "/matricula-2027"));
		}
		int sinReservar = renovaciones.findByEstadoOrderByIdAsc(EstadoRenovacion.CONFIRMADA).size();
		if (sinReservar > 0) {
			alertas.add(new AlertaRevision(Gravedad.ATENCION, MODULO, sinReservar + " renovación(es) confirmada(s) sin "
					+ "matrícula reservada todavía.", "/matricula-2027"));
		}
		return alertas;
	}
}
