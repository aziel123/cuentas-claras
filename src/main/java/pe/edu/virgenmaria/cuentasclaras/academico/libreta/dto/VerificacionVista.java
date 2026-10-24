package pe.edu.virgenmaria.cuentasclaras.academico.libreta.dto;

import java.util.List;

/**
 * Sprint 10, tanda 3 (A4, E16): el resultado de verificar un código impreso. Si existe, si es la versión vigente, si la
 * base sigue coincidiendo con su huella y los NIVELES de esa versión por competencia (sin conclusiones) para compararlos
 * con el papel.
 */
public record VerificacionVista(String codigo, boolean existe, boolean vigente, boolean coincide, Long libretaId,
		Long alumnoId, Long familiaId, String alumno, String seccion, String periodo, int version, int versionVigente,
		String emitida, List<Nivel> niveles) {

	public record Nivel(String area, String competencia, String nivel) {
	}

	public static VerificacionVista noExiste(String codigo) {
		return new VerificacionVista(codigo, false, false, false, null, null, null, null, null, null, 0, 0, null, List.of());
	}
}
