package pe.edu.virgenmaria.cuentasclaras.matricula.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Avance de la campaña de renovación (pantalla 9): contadores y una fila por alumno. {@code reservadas}: matrículas
 * reservadas esperando el pago de la matrícula; {@code activadas}: ya pagadas y activas.
 */
public record CampanaRenovacion(Long anioId, int anio, boolean planificado, long total, long porResponder,
		long confirmadas, long reservadas, long activadas, long noContinuan, long vencidas, List<Fila> filas,
		List<OpcionSeccion> secciones) {

	public record Fila(Long id, String alumno, String grado, String seccion, Long seccionId, String estado,
			String variante, boolean conDeuda, BigDecimal deuda, LocalDate venceEn, String canal, boolean propuesta) {
	}

	public record OpcionSeccion(Long id, String etiqueta) {
	}
}
