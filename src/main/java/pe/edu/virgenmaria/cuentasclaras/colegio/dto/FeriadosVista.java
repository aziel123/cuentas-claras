package pe.edu.virgenmaria.cuentasclaras.colegio.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Pantalla 11: los feriados nacionales del año (sin acciones: están en el código) y los días extra del colegio.
 *
 * @param puedeEditar Promotoría o Dirección (registrar y anular)
 */
public record FeriadosVista(int anio, List<Nacional> nacionales, List<Extra> extras, boolean puedeEditar) {

	public record Nacional(LocalDate fecha, String nombre) {
	}

	/** @param anulable vigente y su fecha aún no llega */
	public record Extra(Long id, LocalDate fecha, String descripcion, boolean vigente, boolean anulable,
			String registradoPor, String anulacion) {
	}
}
