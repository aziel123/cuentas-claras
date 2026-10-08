package pe.edu.virgenmaria.cuentasclaras.recaudacion.dto;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cómo quedaría una línea del archivo si se aplicara hoy: {@code APLICAR} (con a qué cuotas), {@code EXCEPCION} (con su
 * motivo) o {@code YA_REGISTRADA} (su operación ya está en otro pago).
 */
public record LineaPrevia(int numero, int linea, LocalDate fecha, String codigo, String alumno, BigDecimal monto,
		String moneda, String operacion, String resultado, String texto) implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	public static final String APLICAR = "APLICAR";

	public static final String EXCEPCION = "EXCEPCION";

	public static final String YA_REGISTRADA = "YA_REGISTRADA";

	public String variante() {
		return switch (resultado) {
			case APLICAR -> "exito";
			case YA_REGISTRADA -> "peligro";
			default -> "alerta";
		};
	}

	public String etiqueta() {
		return switch (resultado) {
			case APLICAR -> "Se aplicará";
			case YA_REGISTRADA -> "Ya registrada";
			default -> "Por revisar";
		};
	}
}
