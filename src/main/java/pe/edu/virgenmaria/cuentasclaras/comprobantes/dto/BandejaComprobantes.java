package pe.edu.virgenmaria.cuentasclaras.comprobantes.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Bandeja de envíos al OSE: rechazados sin reemitir (primero), por vencer el plazo legal, pendientes, enviados (esperando
 * respuesta) y aceptados hoy. {@code simulado}: el proveedor es el simulado (sin valor tributario).
 */
public record BandejaComprobantes(LocalDate hoy, int plazoDias, boolean simulado, List<Fila> rechazados,
		List<Fila> porVencer, List<Fila> pendientes, List<Fila> enviados, List<Fila> aceptadosHoy) {

	public record Fila(Long id, String numero, String tipo, String receptor, BigDecimal total, LocalDate fechaEmision,
			String estado, String estadoEtiqueta, String variante, int intentos, String ultimoError, String respuesta,
			LocalDate fechaLimite, LocalDateTime proximoIntento, String reemitidoComo, String reemplazaA,
			boolean puedeReemitir, boolean puedeReintentar) {
	}

	public int totalPorAtender() {
		return rechazados.size() + porVencer.size();
	}
}
