package pe.edu.virgenmaria.cuentasclaras.academico.libreta.dto;

import java.util.List;

/**
 * Sprint 10, tanda 3 (sección 3.7): las libretas de los hijos de la familia en el portal. TODAS las versiones, con su fecha
 * y qué cambió: nada desaparece. Lo mismo para una familia con deuda que para una al día (INDECOPI).
 */
public record PortalLibretasVista(List<Hijo> hijos) {

	public record Hijo(Long alumnoId, String nombre, List<Periodo> periodos) {
	}

	public record Periodo(String nombre, int anio, List<Version> versiones) {
	}

	/** Una versión: la vigente se descarga; las anteriores, también (para comparar). */
	public record Version(Long id, int numero, String emitida, String motivo, String cambios, boolean vigente) {
	}
}
