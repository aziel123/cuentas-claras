package pe.edu.virgenmaria.cuentasclaras.caja.dto;

import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Lo que debe una familia (todos los hermanos) y sus pagos recientes, para elegir qué se cobra. */
public record CuentaFamilia(Long familiaId, String familia, List<AlumnoCuotas> alumnos, List<PagoReciente> pagosRecientes,
		boolean aceptaEfectivo, LocalDate cajaAnteriorAbierta, LocalDate hoy) {

	/** Las cuotas por pagar de un alumno. */
	public record AlumnoCuotas(Long alumnoId, String nombre, String grado, List<CuotaPorCobrar> cuotas) {
	}

	/**
	 * Una cuota por pagar. {@code cobrable}: se puede elegir (no tiene una anulación esperando aprobación).
	 * {@code exactoEnEfectivo}: su saldo es múltiplo de S/ 0.10.
	 */
	public record CuotaPorCobrar(Long id, String descripcion, LocalDate vencimiento, BigDecimal monto,
			BigDecimal descuento, BigDecimal pagado, BigDecimal saldo, String estadoEtiqueta, String estadoVariante,
			boolean cobrable, String motivoNoCobrable, boolean exactoEnEfectivo) {
	}

	/** Un pago reciente de la familia, para responder «¿ya pagué?». */
	public record PagoReciente(LocalDate fecha, String comprobante, BigDecimal total, String medio, String estado) {
	}

	/** Los medios de pago en el orden de la pantalla. */
	public List<MedioPago> medios() {
		return List.of(MedioPago.values());
	}

	public boolean sinDeuda() {
		return alumnos.stream().allMatch(a -> a.cuotas().isEmpty());
	}
}
