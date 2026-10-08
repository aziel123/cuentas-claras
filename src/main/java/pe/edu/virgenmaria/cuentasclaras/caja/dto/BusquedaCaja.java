package pe.edu.virgenmaria.cuentasclaras.caja.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Pantalla de búsqueda de caja.
 *
 * @param cajaAnteriorAbierta fecha de una caja anterior sin cerrar (no se puede cobrar hasta cerrarla) o null
 */
public record BusquedaCaja(String texto, List<ResultadoBusqueda> resultados, boolean buscado,
		LocalDate cajaAnteriorAbierta) {
}
