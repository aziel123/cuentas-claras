package pe.edu.virgenmaria.cuentasclaras.recaudacion.formato;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Un pago leído del archivo del banco, ya validado y en su forma canónica.
 *
 * @param numero          número de línea de datos (desde 1, en el orden del archivo)
 * @param linea           línea o fila del archivo donde está (para los mensajes)
 * @param codigo          código de pago del alumno tal como vino (sin espacios ni guiones)
 * @param referenciaDeuda código de la cuota (base de deudas) o {@code null}
 * @param monto           escala 2
 * @param moneda          PEN o USD
 * @param operacion       número de operación del banco en su forma canónica ({@code NumeroOperacion})
 */
public record FilaRecaudacion(int numero, int linea, LocalDate fechaPago, String codigo, String referenciaDeuda,
		BigDecimal monto, String moneda, String operacion) implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;
}
