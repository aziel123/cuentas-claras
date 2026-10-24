package pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model;

/**
 * Sprint 10, tanda 1 (sección 3.3): PROGRAMADO → ABIERTO ({@code sistema.academico} el día de inicio, o Dirección con su
 * firma antes) → CERRADO (Dirección con su firma, desde {@code fin − dias_cierre_anticipado} y con el anterior CERRADO).
 * CERRADO es final (decisión 172): no se reabre. Lo exige trg_periodo_evaluacion_cambio.
 */
public enum EstadoPeriodo {

	PROGRAMADO("Programado", "neutro"),
	ABIERTO("Abierto", "exito"),
	CERRADO("Cerrado", "info");

	private final String etiqueta;

	private final String variante;

	EstadoPeriodo(String etiqueta, String variante) {
		this.etiqueta = etiqueta;
		this.variante = variante;
	}

	public String etiqueta() {
		return etiqueta;
	}

	/** Variante del badge: siempre con su texto. */
	public String variante() {
		return variante;
	}
}
