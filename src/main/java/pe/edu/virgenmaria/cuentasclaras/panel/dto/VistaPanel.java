package pe.edu.virgenmaria.cuentasclaras.panel.dto;

import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;

import java.util.List;

/**
 * El panel de Promotoría en el celular (sprint 6, sección 12.1). Todas las cifras ya vienen con su formato
 * («S/ 1,250.00», «72 %», «—» si no hubo pagos) y salen de los libros al consultar: ninguna se guarda.
 */
public record VistaPanel(String fecha, List<AlertaRevision> alertas, long criticas, int porAprobar, Hoy hoy, Mes mes,
		Deuda deuda, Rebajas rebajas, Resumen resumen, Llamadas llamadas) {

	/**
	 * Tanda 3: la llamada de control de la semana. {@code texto}: «Te faltan 2 de 3 esta semana», «Hiciste las 3 de esta
	 * semana» o que no hay familias que hayan pagado en efectivo.
	 */
	public record Llamadas(int hechas, int esperadas, int faltan, String texto) {
	}

	/**
	 * Tanda 2: el resumen de hoy. {@code estado}: «Enviado 19:30 · Entregado», «Aún no sale» o «Hoy no corresponde».
	 * {@code cambio}: si las cifras de algún día ya informado cambiaron sin explicación (P4).
	 */
	public record Resumen(String estado, boolean salioATodos, long cambiosSinExplicar) {
	}

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
