package pe.edu.virgenmaria.cuentasclaras.alumnos.dto;

import java.util.List;

/**
 * Familia: sus apoderados (con de quién son responsables de pago), sus alumnos (hermanos) y los cambios de contacto
 * pendientes de aprobación.
 */
public record FichaFamilia(Long id, String nombre, List<ApoderadoVista> apoderados, List<HermanoVista> alumnos,
		List<String> solicitudesPendientes) {
}
