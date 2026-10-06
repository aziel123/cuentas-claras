package pe.edu.virgenmaria.cuentasclaras.comun.texto;

import java.util.Optional;

/**
 * Código de pago para el banco (sprint 4): se deriva del id, así no hay columna que migrar ni que alguien pueda cambiar.
 * <ul>
 *   <li>Alumno: 7 dígitos con ceros a la izquierda + dígito verificador de Luhn = 8 dígitos ({@code 1000 0016}).</li>
 *   <li>Cuota (base de deudas): 8 dígitos + dígito verificador = 9 dígitos.</li>
 * </ul>
 * El dígito verificador detecta un dígito cambiado o dos vecinos invertidos (un error de tipeo en la ventanilla del
 * banco): ese código no corresponde a nadie.
 */
public final class CodigoPago {

	static final int DIGITOS_ALUMNO = 7;

	static final int DIGITOS_CUOTA = 8;

	private CodigoPago() {
	}

	public static String deAlumno(long alumnoId) {
		return codigo(alumnoId, DIGITOS_ALUMNO);
	}

	public static Optional<Long> alumnoDe(String codigo) {
		return id(codigo, DIGITOS_ALUMNO);
	}

	public static String deCuota(long cuotaId) {
		return codigo(cuotaId, DIGITOS_CUOTA);
	}

	public static Optional<Long> cuotaDe(String codigo) {
		return id(codigo, DIGITOS_CUOTA);
	}

	/** «1000 0016»: en dos grupos de 4, para dictarlo o leerlo en el celular. */
	public static String legible(String codigo) {
		return codigo == null || codigo.length() != 8 ? codigo : codigo.substring(0, 4) + " " + codigo.substring(4);
	}

	private static String codigo(long id, int digitos) {
		if (id <= 0 || String.valueOf(id).length() > digitos) {
			throw new IllegalArgumentException("Id fuera de rango para un código de " + digitos + " dígitos: " + id);
		}
		String base = String.format("%0" + digitos + "d", id);
		return base + digitoLuhn(base);
	}

	private static Optional<Long> id(String codigo, int digitos) {
		if (codigo == null) {
			return Optional.empty();
		}
		String limpio = codigo.replaceAll("[\\s-]", "");
		if (!limpio.matches("\\d{" + (digitos + 1) + "}")) {
			return Optional.empty();
		}
		String base = limpio.substring(0, digitos);
		if (digitoLuhn(base) != limpio.charAt(digitos) - '0') {
			return Optional.empty();
		}
		long id = Long.parseLong(base);
		return id > 0 ? Optional.of(id) : Optional.empty();
	}

	/** Dígito de control de Luhn (mod 10) para {@code base}. */
	static int digitoLuhn(String base) {
		int suma = 0;
		boolean doblar = true;
		for (int i = base.length() - 1; i >= 0; i--) {
			int d = base.charAt(i) - '0';
			if (doblar) {
				d *= 2;
				if (d > 9) {
					d -= 9;
				}
			}
			suma += d;
			doblar = !doblar;
		}
		return (10 - suma % 10) % 10;
	}
}
