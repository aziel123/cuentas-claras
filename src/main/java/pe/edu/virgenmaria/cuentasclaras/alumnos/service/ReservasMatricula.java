package pe.edu.virgenmaria.cuentasclaras.alumnos.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Alumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Matricula;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.AlumnoRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.MatriculaRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Seccion;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.SeccionRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;

/**
 * Sprint 5, tanda 2: lo que hace {@code sistema.matricula} con las matrículas del año siguiente. Nunca una persona: lo
 * llaman los procesos de {@code matricula.proceso} con el actor de sistema, dentro de su transacción.
 * <ul>
 *   <li>{@link #reservar}: la renovación confirmada crea la matrícula RESERVADA (y su cuota de matrícula).</li>
 *   <li>{@link #activar}: con la cuota de matrícula pagada o exonerada pasa a ACTIVA (y se generan las pensiones).</li>
 *   <li>{@link #desistir}: con la cuota de matrícula anulada con aprobación, la reservada queda retirada.</li>
 * </ul>
 */
@Service
@PreAuthorize("hasRole('SISTEMA_MATRICULA')")
@Transactional(propagation = Propagation.MANDATORY)
public class ReservasMatricula {

	private final AlumnoRepository alumnos;

	private final SeccionRepository secciones;

	private final MatriculaRepository matriculas;

	private final RegistroAlumnos registro;

	public ReservasMatricula(AlumnoRepository alumnos, SeccionRepository secciones, MatriculaRepository matriculas,
			RegistroAlumnos registro) {
		this.alumnos = alumnos;
		this.secciones = secciones;
		this.matriculas = matriculas;
		this.registro = registro;
	}

	/** @return el id de la matrícula RESERVADA (la base rechaza una ACTIVA en un año planificado) */
	public Long reservar(Long alumnoId, Long seccionId) {
		Alumno alumno = alumnos.findById(alumnoId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Alumno no encontrado"));
		Seccion seccion = secciones.findById(seccionId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Sección no encontrada"));
		Matricula matricula = registro.matricular(alumno, seccion, null);
		if (!matricula.reservada()) {
			throw new ReglaNegocioException("El año " + seccion.getAnioEscolar().getAnio() + " ya no está planificado: "
					+ "la matrícula se registra en Alumnos.");
		}
		return matricula.getId();
	}

	public void activar(Long matriculaId) {
		Matricula matricula = matriculas.bloquear(matriculaId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Matrícula no encontrada"));
		if (matricula.reservada()) {
			registro.activar(matricula, ActorSistema.MATRICULA.usuario());
		}
	}

	public void desistir(Long matriculaId, String detalle) {
		Matricula matricula = matriculas.bloquear(matriculaId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Matrícula no encontrada"));
		if (matricula.reservada()) {
			registro.desistir(matricula, detalle);
		}
	}
}
