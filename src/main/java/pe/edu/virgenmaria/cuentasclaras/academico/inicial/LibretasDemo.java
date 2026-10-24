package pe.edu.virgenmaria.cuentasclaras.academico.inicial;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.academico.model.TipoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;

import java.time.LocalDate;
import java.util.List;

/**
 * Sprint 10, tanda 3 (sección 3.12, decisión 187): las notas y las libretas del colegio de un visitante de la demo, hechas
 * por sus personas con los mismos servicios del colegio ({@link EjemploAcademico}, que comparte con el arranque de
 * desarrollo). Dirección guarda la evaluación por bimestres; Administración completa el área (Matemática) de la
 * asignación de la cuenta «docente» en 4.° Primaria A y Dirección la aprueba; el I bimestre se abre, la docente pone las
 * notas y Dirección lo cierra; sistema.libretas emite las libretas (de todas las secciones: también la del hijo de la
 * cuenta «apoderado»); la docente pide una corrección que aprueba Promotoría y sale la versión 2; el II bimestre queda
 * abierto. La exportación para el SIAGIE la saca Dirección desde la pantalla, con la marca «DEMO · DATOS FICTICIOS». Solo
 * si la fecha de hoy deja un I bimestre de al menos 3 semanas ya terminado; si algo falla, la demo sigue sin libretas
 * (queda en el log).
 */
@Component
@Profile("demo")
public class LibretasDemo {

	/** Lo que califica la cuenta «docente» de la demo: Matemática de 4.° Primaria A. */
	static final List<EjemploAcademico.Calificable> CALIFICABLES = List.of(
			new EjemploAcademico.Calificable(Grado.PRIMARIA_4, "A", TipoAsignacion.DOCENTE, List.of("MAT")));

	private final EjemploAcademico ejemplo;

	public LibretasDemo(EjemploAcademico ejemplo) {
		this.ejemplo = ejemplo;
	}

	public void cargar(long colegioId, String promotoria, String direccion, String administracion, String docente, int anio,
			LocalDate hoy) {
		ejemplo.calificar(colegioId, new EjemploAcademico.Personas(promotoria, direccion, administracion, docente), anio, hoy,
				CALIFICABLES, null);
	}
}
