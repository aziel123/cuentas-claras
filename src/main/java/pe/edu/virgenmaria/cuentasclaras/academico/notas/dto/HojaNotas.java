package pe.edu.virgenmaria.cuentasclaras.academico.notas.dto;

import java.util.List;

/**
 * Sprint 10, tanda 2 (sección 3.5): la hoja de notas de una sección, un área y un periodo. Solo nombres de los alumnos
 * (decisión 142): sin documentos, contactos, deudas ni pagos, y sin el estado de la matrícula (C1: la RESERVADA recibe nota
 * igual que las demás; lo revisa {@code DtoDocenteSinDatosTest}). {@code editable}: si se puede guardar ahora (periodo
 * abierto, o cerrado con la nota tardía aprobada); {@code aviso}: por qué no (por ejemplo, la asignación sin áreas del
 * catálogo).
 */
public record HojaNotas(Long seccionId, String seccion, Long areaId, String area, Long periodoId, String periodo,
		String estadoPeriodo, List<OpcionPeriodo> periodos, boolean editable, boolean tardia, Long solicitudId, String aviso,
		int conclusionMax, boolean conclusionSiempre, List<ColumnaCompetencia> competencias, List<String> exigenConclusion,
		List<FilaAlumno> filas, int sinNota) {

	/** Una competencia (columna). */
	public record ColumnaCompetencia(Long id, String codigo, String nombre) {
	}

	/** Un periodo del selector. */
	public record OpcionPeriodo(Long id, String nombre, String estado) {
	}

	/** Un alumno (fila) con su celda en cada competencia. */
	public record FilaAlumno(int numero, Long matriculaId, String apellidos, String nombres, boolean exonerado,
			List<Celda> celdas) {
	}

	/** La nota vigente de una celda ({@code nivel} null: sin nota). */
	public record Celda(Long competenciaId, String nivel, String conclusion, boolean conNota) {

		public String clave(Long matriculaId) {
			return matriculaId + "-" + competenciaId;
		}
	}
}
