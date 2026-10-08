package pe.edu.virgenmaria.cuentasclaras.alumnos.dto;

import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Parentesco;

import java.util.List;

/**
 * Apoderado en la ficha del alumno o de la familia. {@code responsableDe}: nombres de los alumnos activos que paga.
 * {@code ruc} y {@code razonSocial}: sus datos de facturación aprobados (B2), o {@code null}.
 */
public record ApoderadoVista(Long id, String nombreCompleto, String documento, Parentesco parentesco,
		String parentescoEtiqueta, String telefonoWhatsapp, String correo, boolean activo, List<String> responsableDe,
		String ruc, String razonSocial, boolean contactoPendiente) {

	public boolean esResponsable() {
		return !responsableDe.isEmpty();
	}
}
