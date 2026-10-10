package pe.edu.virgenmaria.cuentasclaras.operacion.salud;

import pe.edu.virgenmaria.cuentasclaras.operacion.model.ComparacionRespaldo;

import java.time.LocalDateTime;

/**
 * El último respaldo que cuenta (en prod, uno a un destino real): cuándo terminó (hora de Lima), adónde fue, qué dijo la
 * comparación con el anterior, si está al día (menos de {@code respaldo-max-horas}) y si faltan filas.
 * <p>
 * Correcciones del sprint 7 (QA-S7-1): «faltan filas» ya no sale solo del último registro. Mientras haya un respaldo con
 * FALTAN_FILAS sin resolver ({@code porResolverId}), la comparación es FALTAN_FILAS y las diferencias son las de ese
 * respaldo, aunque después haya otro que diga IGUAL. La resuelve una persona de Promotoría con motivo.
 */
public record EstadoRespaldo(boolean existe, LocalDateTime fin, String destino, String archivo,
		ComparacionRespaldo comparacion, String diferencias, boolean alDia, Long porResolverId,
		String porResolverArchivo) {

	/** Sin alerta pendiente de otro respaldo. */
	public EstadoRespaldo(boolean existe, LocalDateTime fin, String destino, String archivo,
			ComparacionRespaldo comparacion, String diferencias, boolean alDia) {
		this(existe, fin, destino, archivo, comparacion, diferencias, alDia, null, null);
	}

	static EstadoRespaldo ninguno() {
		return new EstadoRespaldo(false, null, null, null, null, null, false, null, null);
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
