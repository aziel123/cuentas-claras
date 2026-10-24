package pe.edu.virgenmaria.cuentasclaras.academico.libreta.dto;

import java.util.List;

/**
 * Sprint 10, tanda 3 (M9): la pantalla de libretas del colegio. Dirección ve todas las secciones; el tutor, solo las suyas.
 * Sin documentos (el código de estudiante va en el PDF) ni nada de cobranza.
 */
public record LibretasColegioVista(List<Opcion> secciones, List<Opcion> periodos, Long seccionId, Long periodoId,
		String seccion, String periodo, boolean inicial, List<Fila> libretas, int faltan, boolean direccion, int maxPorArchivo) {

	public record Opcion(Long id, String etiqueta) {
	}

	/** Una libreta vigente de la sección. */
	public record Fila(Long id, String alumno, int version, String emitida, String motivo, int entregas) {
	}
}
