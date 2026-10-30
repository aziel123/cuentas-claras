package pe.edu.virgenmaria.cuentasclaras.comun.cripto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** La SHA-256 en hexadecimal en minúsculas de un texto en UTF-8: lo mismo que {@code SHA2(texto, 256)} de MySQL. */
public final class Sha256 {

	private Sha256() {
	}

	public static String hex(String texto) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(texto.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("La JVM no tiene SHA-256", e);
		}
	}
}
