package pe.edu.virgenmaria.cuentasclaras.recaudacion.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Lo que ve quien confirma a ciegas: el banco, las fechas, cuántos pagos trae el archivo y unas líneas al azar para
 * buscarlas en el portal del banco. NUNCA el total ni la suma de las líneas: lo escribe quien confirma, tal como lo ve en
 * el banco.
 *
 * @param participaste quien mira subió el archivo (o preparó la cuenta de quien lo subió): no puede confirmarlo
 */
public record ConfirmacionRecaudacionVista(Long id, Long version, String banco, LocalDate desde, LocalDate hasta,
		int lineas, String subidoPor, LocalDateTime subidoEn, int intentosRestantes, boolean participaste,
		List<LineaMuestra> muestra, String estado, String estadoEtiqueta) {

	public boolean porConfirmar() {
		return "CARGADO".equals(estado);
	}
}
