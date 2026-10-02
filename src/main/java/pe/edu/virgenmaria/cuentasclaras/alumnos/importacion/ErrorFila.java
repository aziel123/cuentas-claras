package pe.edu.virgenmaria.cuentasclaras.alumnos.importacion;

import java.io.Serial;
import java.io.Serializable;

/**
 * Un error de una fila del Excel. {@code columna}: letra (o null si es de la fila entera); {@code campo}: encabezado.
 * Se muestra solo a quien subió el archivo; nunca va a los logs.
 */
public record ErrorFila(int fila, String columna, String campo, String mensaje) implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	/** «Fila 12, columna B (N.° de documento del alumno): el DNI debe tener 8 dígitos; escribiste «1234567».» */
	public String texto() {
		String donde = columna == null ? "Fila " + fila : "Fila " + fila + ", columna " + columna + " (" + campo + ")";
		return donde + ": " + minuscula(mensaje);
	}

	private static String minuscula(String texto) {
		if (texto == null || texto.isEmpty() || texto.length() > 1 && Character.isUpperCase(texto.charAt(1))) {
			return texto;
		}
		return Character.toLowerCase(texto.charAt(0)) + texto.substring(1);
	}
}
