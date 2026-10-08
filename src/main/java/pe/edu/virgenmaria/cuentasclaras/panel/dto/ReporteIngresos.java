package pe.edu.virgenmaria.cuentasclaras.panel.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Ingresos por medio de pago en un rango (en pantalla). Lo anulado (aprobado en el rango) va aparte, para que un ingreso
 * no «desaparezca» sin verse (P15).
 */
public record ReporteIngresos(LocalDate desde, LocalDate hasta, String total, long pagos, String digital,
		List<Linea> porMedio, List<Linea> porOrigen, String anulado, long anulados, boolean puedeExportar) {

	public record Linea(String etiqueta, long pagos, String total) {
	}

	public ReporteIngresos {
		porMedio = List.copyOf(porMedio);
		porOrigen = List.copyOf(porOrigen);
	}
}
