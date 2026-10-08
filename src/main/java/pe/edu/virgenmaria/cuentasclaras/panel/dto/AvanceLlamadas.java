package pe.edu.virgenmaria.cuentasclaras.panel.dto;

import java.time.LocalDate;

/**
 * Cuántas llamadas de control van en la semana (sprint 6, tanda 3): {@code esperadas} son las plazas de la muestra (3, o
 * menos si hay pocas candidatas) y nunca menos que las ya hechas. Una plaza está hecha cuando la familia confirma, no
 * confirma o no contestó dos veces (S6-M2). Los conteos de resultados van en el resumen diario (S6-M2).
 *
 * @param confirman     llamadas con «Confirma»
 * @param noConfirman   llamadas con «No confirma»
 * @param noContestan   llamadas (intentos) con «No contesta»
 * @param reemplazadas  familias que no contestaron dos veces y se reemplazaron
 */
public record AvanceLlamadas(LocalDate semana, int hechas, int esperadas, int confirman, int noConfirman,
		int noContestan, int reemplazadas) {

	public AvanceLlamadas(LocalDate semana, int hechas, int esperadas) {
		this(semana, hechas, esperadas, 0, 0, 0, 0);
	}

	public int faltan() {
		return Math.max(0, esperadas - hechas);
	}
}
