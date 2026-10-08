package pe.edu.virgenmaria.cuentasclaras.recaudacion.service;

import pe.edu.virgenmaria.cuentasclaras.caja.model.ImputacionPago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.ImputacionPago.CuotaPorPagar;
import pe.edu.virgenmaria.cuentasclaras.caja.model.ImputacionPago.Imputacion;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.MotivoExcepcion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * A qué cuotas va un pago hecho en el banco (sección 10.2 del diseño), con la MISMA imputación de caja
 * ({@link ImputacionPago}). Puro: sin base de datos; lo usan la vista previa (sobre los saldos de ese momento) y el
 * aplicador (sobre las cuotas ya bloqueadas).
 * <ol>
 *   <li>Moneda distinta de soles → MONEDA (nunca se convierte).</li>
 *   <li>Operación ya registrada en otro pago → OPERACION_DUPLICADA.</li>
 *   <li>Código sin alumno → CODIGO_INVALIDO.</li>
 *   <li>Con referencia (base de deudas): a ESA cuota, que debe ser del alumno y estar por pagar; menos que su saldo es
 *       un pago a cuenta, más es EXCESO.</li>
 *   <li>Sin referencia: a las cuotas por pagar del alumno (nunca de sus hermanos), de la que vence primero a la última;
 *       sin deuda → ALUMNO_SIN_DEUDA; más que la deuda → EXCESO.</li>
 * </ol>
 */
public final class ReglasRecaudacion {

	/** Una cuota del alumno con su saldo; {@code cobrable}: PENDIENTE o PARCIAL y sin anulación pendiente. */
	public record Deuda(Long cuotaId, Long alumnoId, LocalDate vencimiento, BigDecimal saldo, boolean cobrable,
			String descripcion) {

		public Deuda {
			Objects.requireNonNull(cuotaId, "cuotaId");
			Objects.requireNonNull(vencimiento, "vencimiento");
			Objects.requireNonNull(saldo, "saldo");
		}

		boolean porPagar() {
			return cobrable && saldo.signum() > 0;
		}
	}

	/**
	 * El resultado: o un motivo de excepción (con su detalle en lenguaje claro) o las imputaciones (cuota y monto, en
	 * orden) y si queda a cuenta.
	 */
	public record Resolucion(MotivoExcepcion motivo, String detalle, List<Imputacion> imputaciones, boolean aCuenta) {

		static Resolucion excepcion(MotivoExcepcion motivo, String detalle) {
			return new Resolucion(motivo, detalle, List.of(), false);
		}

		public boolean aplicable() {
			return motivo == null;
		}

		public List<Long> cuotas() {
			return imputaciones.stream().map(Imputacion::cuotaId).toList();
		}
	}

	private ReglasRecaudacion() {
	}

	/**
	 * @param alumnoId    el alumno del código, o {@code null} si el código no corresponde a ninguno
	 * @param referida    la cuota de la referencia de deuda, o {@code null} si el pago no trae referencia
	 * @param delAlumno   las cuotas del alumno (solo se usan sin referencia)
	 */
	public static Resolucion resolver(BigDecimal monto, String moneda, boolean operacionUsada, Long alumnoId,
			Deuda referida, List<Deuda> delAlumno, boolean aceptarParciales) {
		BigDecimal importe = Dinero.normalizar(monto);
		if (!Dinero.MONEDA.equals(moneda)) {
			return Resolucion.excepcion(MotivoExcepcion.MONEDA, "Pago en " + moneda + " por " + importe.toPlainString()
					+ ": no se convierte a soles.");
		}
		if (operacionUsada) {
			return Resolucion.excepcion(MotivoExcepcion.OPERACION_DUPLICADA, "La operación ya está en otro pago vigente o "
					+ "en otra línea por aplicar.");
		}
		if (alumnoId == null) {
			return Resolucion.excepcion(MotivoExcepcion.CODIGO_INVALIDO, "El código no corresponde a ningún alumno del "
					+ "colegio.");
		}
		if (referida != null) {
			return aLaCuota(importe, alumnoId, referida, aceptarParciales);
		}
		List<Deuda> porPagar = delAlumno.stream().filter(Deuda::porPagar)
				.sorted(Comparator.comparing(Deuda::vencimiento).thenComparing(Deuda::cuotaId)).toList();
		if (porPagar.isEmpty()) {
			return Resolucion.excepcion(MotivoExcepcion.ALUMNO_SIN_DEUDA, "El alumno no tiene cuotas por pagar.");
		}
		BigDecimal debe = Dinero.sumar(porPagar.stream().map(Deuda::saldo).toList());
		if (importe.compareTo(debe) > 0) {
			return Resolucion.excepcion(MotivoExcepcion.EXCESO, "Pagó " + Dinero.formatear(importe) + " y debe "
					+ Dinero.formatear(debe) + ".");
		}
		List<Imputacion> imputaciones = ImputacionPago.imputar(importe, porPagar.stream()
				.map(d -> new CuotaPorPagar(d.cuotaId(), d.vencimiento(), d.saldo())).toList());
		Imputacion ultima = imputaciones.getLast();
		BigDecimal saldoUltima = porPagar.stream().filter(d -> d.cuotaId().equals(ultima.cuotaId())).findFirst()
				.orElseThrow().saldo();
		boolean aCuenta = ultima.monto().compareTo(saldoUltima) < 0;
		if (aCuenta && !aceptarParciales) {
			return Resolucion.excepcion(MotivoExcepcion.PAGO_PARCIAL, "Pagó " + Dinero.formatear(importe) + " y no cubre la "
					+ "cuota.");
		}
		return new Resolucion(null, null, imputaciones, aCuenta);
	}

	private static Resolucion aLaCuota(BigDecimal importe, Long alumnoId, Deuda referida, boolean aceptarParciales) {
		if (!alumnoId.equals(referida.alumnoId())) {
			return Resolucion.excepcion(MotivoExcepcion.CUOTA_NO_COBRABLE, "La cuota de la referencia no es de ese "
					+ "alumno.");
		}
		if (!referida.porPagar()) {
			return Resolucion.excepcion(MotivoExcepcion.CUOTA_NO_COBRABLE, "La cuota «" + referida.descripcion()
					+ "» ya no está por pagar.");
		}
		if (importe.compareTo(referida.saldo()) > 0) {
			return Resolucion.excepcion(MotivoExcepcion.EXCESO, "Pagó " + Dinero.formatear(importe) + " y la cuota debe "
					+ Dinero.formatear(referida.saldo()) + ".");
		}
		boolean aCuenta = importe.compareTo(referida.saldo()) < 0;
		if (aCuenta && !aceptarParciales) {
			return Resolucion.excepcion(MotivoExcepcion.PAGO_PARCIAL, "Pagó " + Dinero.formatear(importe) + " y la cuota "
					+ "debe " + Dinero.formatear(referida.saldo()) + ".");
		}
		return new Resolucion(null, null, List.of(new Imputacion(referida.cuotaId(), importe)), aCuenta);
	}
}
