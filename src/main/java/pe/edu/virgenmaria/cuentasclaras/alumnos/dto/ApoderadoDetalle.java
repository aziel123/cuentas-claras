package pe.edu.virgenmaria.cuentasclaras.alumnos.dto;

/** Apoderado para el formulario de corrección: sus datos actuales y su familia. */
public record ApoderadoDetalle(Long id, String nombreCompleto, Long familiaId, String familia, boolean activo,
		ApoderadoRequest datos) {
}
