package pe.edu.virgenmaria.cuentasclaras.cobranza.model;

/**
 * Nombre de una deuda: «esta deuda ya existe». Es única por alumno mientras la cuota no esté anulada, así un saldo
 * inicial y una cuota generada no pueden cobrar el mismo mes.
 */
public final class Obligaciones {

	private Obligaciones() {
	}

	/** (2027, 9) → «PEN-2027-09». */
	public static String pension(int anio, int mes) {
		return String.format("PEN-%04d-%02d", anio, mes);
	}

	/** 2027 → «MAT-2027». */
	public static String matricula(int anio) {
		return String.format("MAT-%04d", anio);
	}
}
