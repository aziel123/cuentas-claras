package pe.edu.virgenmaria.cuentasclaras.conciliacion.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * La pantalla principal de la conciliación: SOLO las diferencias. Arriba, el resumen del último extracto; luego las
 * parejas sugeridas por confirmar, los abonos del banco sin pareja, los cargos sin explicar (correcciones del sprint 4,
 * S4-A2: los explica alguien que no subió el extracto) y lo que debía estar en el banco y no está (en rojo, con quién lo
 * registró).
 *
 * @param cubiertoHasta el último día confirmado a ciegas ({@code null} si todavía no hay ninguno)
 */
public record VistaDiferencias(LocalDate hoy, LocalDate cubiertoHasta, Resumen resumen, List<CuentaPorConfirmar> porConfirmar,
		List<Sugerida> sugeridas, List<SinPareja> sinPareja, List<SinPareja> cargosSinExplicar, List<Faltante> faltantes,
		boolean puedeRevisar, boolean puedeConfirmar, List<Opcion> categoriasAbono, List<Opcion> categoriasCargo) {

	public boolean sinDiferencias() {
		return sugeridas.isEmpty() && sinPareja.isEmpty() && cargosSinExplicar.isEmpty() && faltantes.isEmpty();
	}

	/** «Movimientos del 05/10 al 05/10: 41 · Emparejados 38 · Sugeridos 2 · Sin pareja 1». */
	public record Resumen(LocalDate desde, LocalDate hasta, String estado, int movimientos, int emparejados,
			int sugeridos, int sinPareja) {

		public int porcentaje() {
			return movimientos == 0 ? 100 : (int) Math.floor(emparejados * 100.0 / movimientos);
		}
	}

	public record CuentaPorConfirmar(Long cuentaId, String cuenta, int extractos, LocalDate hasta) {
	}

	/** Una pareja sugerida: el movimiento del banco y lo registrado, con sus avisos. {@code enRojo}: número parecido. */
	public record Sugerida(Long partidaId, LocalDate fechaBanco, BigDecimal montoBanco, String descripcionBanco,
			String operacionBanco, String objeto, LocalDate fechaObjeto, BigDecimal montoObjeto, String operacionObjeto,
			List<String> avisos, boolean enRojo, boolean confirmable) {
	}

	/** Un movimiento del banco sin pareja, con lo que se podría emparejar a mano. */
	public record SinPareja(Long movimientoId, LocalDate fecha, String tipo, boolean abono, BigDecimal monto,
			String descripcion, String operacion, boolean critico, boolean confirmado, List<Opcion> posibles) {
	}

	/** Algo que debía estar en el banco y no está. */
	public record Faltante(String tipo, String detalle, LocalDate fecha, BigDecimal monto, String operacion) {
	}

	/** Una opción de una lista (valor y texto). */
	public record Opcion(String valor, String texto) {
	}
}
