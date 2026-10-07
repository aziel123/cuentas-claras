package pe.edu.virgenmaria.cuentasclaras.familias.dto;

import java.util.List;

/** Lo que la familia puede referir en su aviso: SUS pagos y SUS cuotas (nunca los de otra familia). */
public record OpcionesAviso(List<Opcion> pagos, List<Opcion> cuotas) {

	public record Opcion(Long id, String etiqueta) {
	}
}
