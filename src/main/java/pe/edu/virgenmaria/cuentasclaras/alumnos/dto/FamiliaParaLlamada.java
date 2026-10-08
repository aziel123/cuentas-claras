package pe.edu.virgenmaria.cuentasclaras.alumnos.dto;

import java.util.List;

/**
 * Lo que necesita la llamada de control de una familia (sprint 6, tanda 3; decisión 77): su nombre, sus apoderados
 * ACTIVOS con el celular REGISTRADO (nunca uno escrito en pantalla) y si alguno usa el portal. Sin documentos ni correos.
 *
 * @param apoderadosActivos cuántos apoderados activos tiene (con uno solo, nadie más mira la cuenta)
 * @param conPortal         si algún apoderado activo tiene su cuenta en línea activa y ya entró al portal
 */
public record FamiliaParaLlamada(Long familiaId, String nombre, int apoderadosActivos, boolean conPortal,
		List<Contacto> contactos) {

	/** Un apoderado activo: nombre, parentesco y celular registrado ({@code null} si no tiene). */
	public record Contacto(String nombre, String parentesco, String celular) {
	}

	public FamiliaParaLlamada {
		contactos = List.copyOf(contactos);
	}

	/** Prioridad de la muestra: 2 si no usa el portal Y tiene un solo apoderado, 1 si cumple una, 0 si ninguna. */
	public int prioridad() {
		return (conPortal ? 0 : 1) + (apoderadosActivos <= 1 ? 1 : 0);
	}
}
