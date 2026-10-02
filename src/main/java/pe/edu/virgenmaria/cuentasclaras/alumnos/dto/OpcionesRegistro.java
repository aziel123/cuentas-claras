package pe.edu.virgenmaria.cuentasclaras.alumnos.dto;

import pe.edu.virgenmaria.cuentasclaras.colegio.dto.SeccionOpcion;

import java.util.List;

/**
 * Listas del formulario de alumno nuevo. Si se llega desde una familia («Registrar hermano»), trae sus apoderados
 * activos para elegir al responsable de pago.
 */
public record OpcionesRegistro(Long familiaId, String familia, List<ApoderadoOpcion> apoderadosFamilia,
		List<SeccionOpcion> secciones) {

	public boolean desdeFamilia() {
		return familiaId != null;
	}
}
