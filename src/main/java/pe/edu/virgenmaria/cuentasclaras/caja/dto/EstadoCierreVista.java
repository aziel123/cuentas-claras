package pe.edu.virgenmaria.cuentasclaras.caja.dto;

import pe.edu.virgenmaria.cuentasclaras.caja.model.Denominacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * La pantalla de cierre de la cajera. Mientras haya una caja por cerrar ({@code porCerrar}) NO lleva el esperado ni
 * ningún cierre de esa caja: el conteo es a ciegas. Los montos del cierre se ven solo después de cerrar.
 *
 * @param porCerrar la caja abierta más antigua (la de un día anterior primero) o {@code null}
 * @param ultimoCierre el último cierre de la cajera, solo si ya no tiene cajas por cerrar
 * @param porDepositar cajas cerradas cuyo efectivo falta depositar
 */
public record EstadoCierreVista(String cajera, LocalDate hoy, CajaPorCerrar porCerrar, CierreHecho ultimoCierre,
		List<CajaPorDepositar> porDepositar, List<String> cuentas, ReaperturaVista reapertura,
		List<Denominacion> denominaciones) {

	/** {@code anterior}: es de un día anterior (hay que cerrarla antes de cobrar hoy). {@code reconteo}: ya contó una vez. */
	public record CajaPorCerrar(Long id, LocalDate fecha, boolean anterior, BigDecimal fondo, boolean reconteo) {
	}

	public record CierreHecho(Long cajaId, LocalDate fecha, int numero, BigDecimal esperado, BigDecimal contado,
			BigDecimal primerConteo, boolean reconteo, BigDecimal diferencia, String diferenciaTexto,
			String diferenciaVariante, String explicacion, String estado, String estadoVariante, String revisadoPor,
			String comentario, String deposito) {
	}

	public record CajaPorDepositar(Long cajaId, LocalDate fecha, BigDecimal esperado) {
	}

	/** {@code puedePedir}: la caja de hoy está cerrada y sin depósito. {@code pendiente}: ya la pidió. */
	public record ReaperturaVista(boolean puedePedir, boolean pendiente) {
	}
}
