package pe.edu.virgenmaria.cuentasclaras.colegio.dto;

import java.util.List;

/** Un año con sus secciones agrupadas por nivel y grado. */
public record AnioEscolarDetalle(AnioEscolarVista anio, List<NivelConGrados> niveles) {

	public boolean sinSecciones() {
		return niveles.isEmpty();
	}
}
