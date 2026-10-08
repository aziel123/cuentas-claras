package pe.edu.virgenmaria.cuentasclaras.comun.excel;

import java.util.Map;

/** Una fila no vacía: número de fila de Excel (desde 1) y sus celdas por columna (0 = A). */
public record FilaXlsx(int numero, Map<Integer, CeldaXlsx> celdas) {

	/** La celda de la columna, o {@code null} si está vacía. */
	public CeldaXlsx celda(int columna) {
		CeldaXlsx celda = celdas.get(columna);
		return celda == null || celda.vacia() ? null : celda;
	}
}
