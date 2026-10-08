package pe.edu.virgenmaria.cuentasclaras.colegio.dto;

import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;

/** Sección activa ofrecida en una lista para matricular («2027 · 5.° Primaria A»). */
public record SeccionOpcion(Long id, Long anioId, int anio, Grado grado, String etiqueta) {

	public String etiquetaConAnio() {
		return anio + " · " + etiqueta;
	}
}
