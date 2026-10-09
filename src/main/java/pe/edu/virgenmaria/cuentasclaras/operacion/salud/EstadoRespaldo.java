package pe.edu.virgenmaria.cuentasclaras.operacion.salud;

import pe.edu.virgenmaria.cuentasclaras.operacion.model.ComparacionRespaldo;

import java.time.LocalDateTime;

/**
 * El último respaldo que cuenta (en prod, uno a un destino real): cuándo terminó (hora de Lima), adónde fue, qué dijo la
 * comparación con el anterior, si está al día (menos de {@code respaldo-max-horas}) y si faltan filas.
 */
public record EstadoRespaldo(boolean existe, LocalDateTime fin, String destino, String archivo,
		ComparacionRespaldo comparacion, String diferencias, boolean alDia) {

	static EstadoRespaldo ninguno() {
		return new EstadoRespaldo(false, null, null, null, null, null, false);
	}

	public boolean faltanFilas() {
		return comparacion == ComparacionRespaldo.FALTAN_FILAS;
	}

	/** Lo que responde {@code /salud/respaldo} al vigilante externo: sin fechas ni datos. */
	public String paraVigilante() {
		if (faltanFilas()) {
			return "REVISAR";
		}
		return alDia ? "OK" : "ATRASADO";
	}
}
