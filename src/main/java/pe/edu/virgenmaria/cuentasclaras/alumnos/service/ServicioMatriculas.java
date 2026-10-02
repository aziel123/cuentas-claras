package pe.edu.virgenmaria.cuentasclaras.alumnos.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.CambiarSeccionRequest;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.MatricularRequest;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.MatriculaResultado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Alumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Matricula;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.ReglasDatosPersonales;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.AlumnoRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.MatriculaRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Seccion;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.SeccionRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Motivo;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Matrículas: una por alumno y año, en una sección activa de ese año. Registrar una matrícula publica
 * {@link MatriculaRegistrada} en la misma transacción (la tanda 3 genera ahí el cronograma de cuotas).
 * Solo Dirección y Administración.
 */
@Service
@PreAuthorize("hasAnyRole('DIRECTOR','ADMINISTRACION')")
public class ServicioMatriculas {

	private final AlumnoRepository alumnos;

	private final MatriculaRepository matriculas;

	private final SeccionRepository secciones;

	private final RegistroAlumnos registro;

	private final AuditoriaService auditoria;

	private final ObjectProvider<ConsultaCuotasMatricula> consultaCuotas;

	private final Clock reloj;

	public ServicioMatriculas(AlumnoRepository alumnos, MatriculaRepository matriculas, SeccionRepository secciones,
			RegistroAlumnos registro, AuditoriaService auditoria, ObjectProvider<ConsultaCuotasMatricula> consultaCuotas,
			Clock reloj) {
		this.alumnos = alumnos;
		this.matriculas = matriculas;
		this.secciones = secciones;
		this.registro = registro;
		this.auditoria = auditoria;
		this.consultaCuotas = consultaCuotas;
		this.reloj = reloj;
	}

	/** Sin fecha: hoy, o el inicio de clases si ya pasó. Devuelve un aviso si la edad no corresponde al grado. */
	@Transactional
	public MatriculaResultado matricular(Long alumnoId, MatricularRequest solicitud) {
		Alumno alumno = alumnos.findById(alumnoId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Alumno no encontrado"));
		if (solicitud.seccionId() == null) {
			throw new ReglaNegocioException("Elige la sección.");
		}
		Seccion seccion = buscarSeccion(solicitud.seccionId());
		LocalDate fecha = solicitud.fecha() != null ? solicitud.fecha()
				: seccion.getAnioEscolar().fechaMatriculaPorDefecto(LocalDate.now(reloj));
		Matricula matricula = registro.matricular(alumno, seccion, fecha);
		String advertencia = ReglasDatosPersonales.advertenciaEdad(alumno.getFechaNacimiento(), seccion.getGrado(),
				seccion.getAnioEscolar().getAnio()).orElse(null);
		return new MatriculaResultado(matricula.getId(), alumno.getId(), advertencia);
	}

	/**
	 * Mueve al alumno a otra sección del mismo año. A otro nivel solo si la matrícula aún no tiene cuotas (cambiaría su
	 * pensión).
	 *
	 * @return id del alumno
	 */
	@Transactional
	public Long cambiarSeccion(Long matriculaId, CambiarSeccionRequest solicitud) {
		Matricula matricula = matriculas.findById(matriculaId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Matrícula no encontrada"));
		String motivo = Motivo.exigir(solicitud.motivo());
		if (solicitud.seccionId() == null) {
			throw new ReglaNegocioException("Elige la sección.");
		}
		Seccion nueva = buscarSeccion(solicitud.seccionId());
		String anterior = matricula.getSeccion().etiqueta();
		boolean tieneCuotas = consultaCuotas.getIfAvailable(() -> id -> false).tieneCuotas(matriculaId);
		matricula.cambiarSeccion(nueva, !tieneCuotas);
		auditoria.registrar(AccionAuditoria.MATRICULA_SECCION_CAMBIADA, "matricula", matriculaId.toString(), anterior,
				nueva.etiqueta(), "Alumno " + matricula.getAlumno().nombreCompleto() + ", año "
						+ matricula.getAnioEscolar().getAnio() + ". Motivo: " + motivo);
		return matricula.getAlumno().getId();
	}

	private Seccion buscarSeccion(Long id) {
		return secciones.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Sección no encontrada"));
	}
}
