package pe.edu.virgenmaria.cuentasclaras.comunicacion.dto;

import java.time.LocalDateTime;

/**
 * Sprint 6, tanda 2: cómo salió el resumen diario a Promotoría (sin destinos ni textos).
 *
 * @param destinatarios personas de Promotoría activas
 * @param salieron      de ellas, a cuántas les salió (ENVIADO, ENTREGADO o LEIDO) por algún canal
 * @param enviadoEn     el primer envío ({@code null} si aún no salió)
 * @param estado        «Entregado», «Enviado», «Pendiente» o «No salió a todos»
 */
public record EntregaResumen(int destinatarios, int salieron, LocalDateTime enviadoEn, String estado) {

	/** Salió a todas las personas de Promotoría (y hay al menos una). */
	public boolean completa() {
		return destinatarios > 0 && salieron >= destinatarios;
	}
}
