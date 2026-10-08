package pe.edu.virgenmaria.cuentasclaras.cobranza.model;

import pe.edu.virgenmaria.cuentasclaras.comun.texto.Normalizador;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Nombre de una deuda: «esta deuda ya existe». Es única por alumno mientras la cuota no esté anulada, así un saldo
 * inicial y una cuota generada no pueden cobrar el mismo mes.
 */
public final class Obligaciones {

	private Obligaciones() {
	}

	/** (2027, 9) → «PEN-2027-09». */
	public static String pension(int anio, int mes) {
		return String.format("PEN-%04d-%02d", anio, mes);
	}

	/**
	 * Otro concepto del reglamento en un año: «OT-2026-» y 8 caracteres de la huella SHA-256 del concepto normalizado
	 * (cabe en los 20 caracteres de la columna). El mismo concepto el mismo año no se cobra dos veces.
	 */
	public static String otro(int anio, String concepto) {
		String normalizado = Normalizador.paraBusqueda(concepto);
		try {
			byte[] huella = MessageDigest.getInstance("SHA-256").digest(normalizado.getBytes(StandardCharsets.UTF_8));
			return String.format("OT-%04d-%s", anio, HexFormat.of().formatHex(huella, 0, 4));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 no disponible", e);
		}
	}

	/** 2027 → «MAT-2027». */
	public static String matricula(int anio) {
		return String.format("MAT-%04d", anio);
	}
}
