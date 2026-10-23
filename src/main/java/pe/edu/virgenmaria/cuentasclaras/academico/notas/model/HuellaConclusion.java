package pe.edu.virgenmaria.cuentasclaras.academico.notas.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Sprint 10, tanda 2 (M8): el SHA-256 de una conclusión, en hexadecimal y sobre sus bytes UTF-8: el mismo que calcula la
 * base con {@code SHA2(texto, 256)} (lo prueba el MySQL real con tildes y «ñ»). Es lo único de la conclusión que va a la
 * bitácora y a la solicitud.
 */
public final class HuellaConclusion {

	private HuellaConclusion() {
	}

	public static String de(String texto) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("La JVM no tiene SHA-256", e);
		}
	}
}
