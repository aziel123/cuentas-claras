package pe.edu.virgenmaria.cuentasclaras.panel.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * La foto de un día ya informado contra lo que dicen HOY los libros (sprint 6, tanda 2; P4). {@code cambio}: alguna
 * cifra de pagos (del día o del mes hasta ese día) ya no es la de la foto. {@code explicado}: la diferencia la explican
 * EXACTAMENTE las anulaciones aprobadas y los pagos registrados después del corte; si no, es CRÍTICA.
 */
public record ComparacionResumen(Long resumenId, LocalDate fecha, LocalDateTime cortadoEn, BigDecimal fotoTotal,
		long fotoPagos, BigDecimal hoyTotal, long hoyPagos, BigDecimal fotoMes, BigDecimal hoyMes, boolean cambio,
		boolean explicado, String explicacion) {

	/** Cambió y nada lo explica: un pago borrado o alterado por SQL (CRÍTICA). */
	public boolean sinExplicar() {
		return cambio && !explicado;
	}
}
