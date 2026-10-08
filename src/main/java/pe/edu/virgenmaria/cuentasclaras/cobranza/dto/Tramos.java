package pe.edu.virgenmaria.cuentasclaras.cobranza.dto;

/**
 * Cuántas familias (o alumnos) hay en cada tramo de atraso, según su cuota vencida más antigua (decisión 66): de 1 a 30
 * días, de 31 a 60, de 61 a 90 y más de 90.
 */
public record Tramos(long hasta30, long hasta60, long hasta90, long masDe90) {

	public static final Tramos NINGUNO = new Tramos(0, 0, 0, 0);

	/** Suma uno en el tramo de {@code dias} de atraso (al menos 1: el día del vencimiento aún no está vencido). */
	public Tramos sumar(long dias) {
		if (dias <= 30) {
			return new Tramos(hasta30 + 1, hasta60, hasta90, masDe90);
		}
		if (dias <= 60) {
			return new Tramos(hasta30, hasta60 + 1, hasta90, masDe90);
		}
		if (dias <= 90) {
			return new Tramos(hasta30, hasta60, hasta90 + 1, masDe90);
		}
		return new Tramos(hasta30, hasta60, hasta90, masDe90 + 1);
	}

	public long total() {
		return hasta30 + hasta60 + hasta90 + masDe90;
	}
}
