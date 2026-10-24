package pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model;

import java.util.Locale;

/**
 * Sprint 10, tanda 1 (S1, decisión 171): cómo se divide el año para evaluar. Por defecto BIMESTRE (4 periodos); por colegio
 * y año, TRIMESTRE (3) o SEMESTRE (2). La base exige el mismo par tipo y número ({@code ck_configuracion_evaluacion_periodos}).
 */
public enum TipoPeriodo {

	BIMESTRE("Bimestre", 4),
	TRIMESTRE("Trimestre", 3),
	SEMESTRE("Semestre", 2);

	private static final String[] ROMANOS = { "I", "II", "III", "IV" };

	private final String etiqueta;

	private final int periodos;

	TipoPeriodo(String etiqueta, int periodos) {
		this.etiqueta = etiqueta;
		this.periodos = periodos;
	}

	public String etiqueta() {
		return etiqueta;
	}

	/** Cuántos periodos tiene el año. */
	public int periodos() {
		return periodos;
	}

	/** «I bimestre», «III trimestre»… */
	public String nombreDe(int numero) {
		if (numero < 1 || numero > periodos) {
			throw new IllegalArgumentException("El año tiene " + periodos + " periodos de tipo " + name());
		}
		return ROMANOS[numero - 1] + " " + etiqueta.toLowerCase(Locale.ROOT);
	}
}
