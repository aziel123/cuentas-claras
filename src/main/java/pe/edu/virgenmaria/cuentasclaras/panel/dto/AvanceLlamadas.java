package pe.edu.virgenmaria.cuentasclaras.panel.dto;

import java.time.LocalDate;

/**
 * Cuántas llamadas de control van en la semana (sprint 6, tanda 3): {@code esperadas} son las de la muestra (3, o menos si
 * pocas familias pagaron en efectivo) y nunca menos que las ya hechas.
 */
public record AvanceLlamadas(LocalDate semana, int hechas, int esperadas) {

	public int faltan() {
		return Math.max(0, esperadas - hechas);
	}
}
