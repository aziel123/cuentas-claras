package pe.edu.virgenmaria.cuentasclaras.panel.dto;

import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;

import java.util.List;

/**
 * El panel de Promotoría en el celular (sprint 6, sección 12.1). Todas las cifras ya vienen con su formato
 * («S/ 1,250.00», «72 %», «—» si no hubo pagos) y salen de los libros al consultar: ninguna se guarda.
 */
public record VistaPanel(String fecha, List<AlertaRevision> alertas, long criticas, int porAprobar, Hoy hoy, Mes mes,
		Deuda deuda, Rebajas rebajas) {

	public record Hoy(String cobrado, long pagos, String digital, String digitalMonto, String efectivo,
			long pagosEfectivo, long cajasAbiertas, long cajasCerradas, long cierresConDiferencia) {
	}

	public record Mes(String nombre, String cobrado, long pagos, String avance, String vence, String pagadoDeLoQueVence,
			String anulado, long anulados) {
	}

	public record Deuda(String monto, long familias, long hasta30, long hasta60, long hasta90, long masDe90) {
	}

	public record Rebajas(String descuentos, long cantidadDescuentos, List<String> aprobaronDescuentos,
			String cuotasAnuladas, long cantidadCuotasAnuladas, List<String> aprobaronAnulaciones) {
	}

	public VistaPanel {
		alertas = List.copyOf(alertas);
	}
}
