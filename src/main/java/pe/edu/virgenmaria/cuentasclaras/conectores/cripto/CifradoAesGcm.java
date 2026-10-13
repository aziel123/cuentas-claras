package pe.edu.virgenmaria.cuentasclaras.conectores.cripto;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/**
 * Sprint 8, tanda 2 (sección 3.6, decisión 118): AES-256-GCM del JDK ({@code AES/GCM/NoPadding}, etiqueta de 128 bits)
 * con un nonce aleatorio de 12 bytes por secreto. El resultado es {@code nonce || texto cifrado || etiqueta}. Sin estado:
 * lo usan {@link SecretosConectores} (la aplicación) y {@link RecifradoConectores} (la rotación por línea de comandos).
 */
public final class CifradoAesGcm {

	public static final int BYTES_CLAVE = 32;

	static final int BYTES_NONCE = 12;

	static final int BITS_ETIQUETA = 128;

	private static final SecureRandom ALEATORIO = new SecureRandom();

	/**
	 * Las claves de desarrollo y de pruebas empiezan con este texto (en claro, antes del base64). Ninguna clave real lo
	 * lleva: {@code VerificadorConfiguracion} la rechaza fuera de dev y test.
	 */
	public static final String MARCA_CLAVE_DE_DESARROLLO = "clave-conectores-";

	private CifradoAesGcm() {
	}

	/** El AAD de un secreto: {@code conector|{colegio}|{tipo}|{clave_publica}}. Copiado a otra fila, no se descifra. */
	public record Aad(long colegioId, String tipo, String clavePublica) {

		public Aad {
			Objects.requireNonNull(tipo, "tipo");
			Objects.requireNonNull(clavePublica, "clavePublica");
		}

		public byte[] bytes() {
			return ("conector|" + colegioId + "|" + tipo + "|" + clavePublica).getBytes(StandardCharsets.UTF_8);
		}
	}

	/** Lee la clave maestra (32 bytes en base64). Lanza {@link IllegalArgumentException} si no es válida. */
	public static byte[] leerClave(String base64) {
		if (base64 == null || base64.isBlank()) {
			throw new IllegalArgumentException("La clave de los conectores está vacía");
		}
		byte[] clave;
		try {
			clave = Base64.getDecoder().decode(base64.strip());
		}
		catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("La clave de los conectores no es base64 válido");
		}
		if (clave.length != BYTES_CLAVE) {
			Arrays.fill(clave, (byte) 0);
			throw new IllegalArgumentException("La clave de los conectores debe tener 32 bytes (AES-256) en base64");
		}
		return clave;
	}

	/** Si es una clave de desarrollo o de pruebas (su texto en claro empieza con la marca). */
	public static boolean esDeDesarrollo(String base64) {
		try {
			byte[] clave = Base64.getDecoder().decode(base64.strip());
			return new String(clave, StandardCharsets.ISO_8859_1).startsWith(MARCA_CLAVE_DE_DESARROLLO);
		}
		catch (IllegalArgumentException | NullPointerException e) {
			return false;
		}
	}

	public static byte[] cifrar(byte[] clave, byte[] texto, Aad aad) {
		Objects.requireNonNull(texto, "texto");
		byte[] nonce = new byte[BYTES_NONCE];
		ALEATORIO.nextBytes(nonce);
		try {
			Cipher cifrador = Cipher.getInstance("AES/GCM/NoPadding");
			cifrador.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(clave, "AES"), new GCMParameterSpec(BITS_ETIQUETA, nonce));
			cifrador.updateAAD(aad.bytes());
			byte[] cifrado = cifrador.doFinal(texto);
			byte[] resultado = new byte[BYTES_NONCE + cifrado.length];
			System.arraycopy(nonce, 0, resultado, 0, BYTES_NONCE);
			System.arraycopy(cifrado, 0, resultado, BYTES_NONCE, cifrado.length);
			return resultado;
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("AES-GCM no disponible", e);
		}
	}

	/**
	 * Descifra y autentica. Con otra clave, otro AAD (otro colegio, otro tipo u otra clave pública) o un byte cambiado,
	 * lanza {@link SecretoIlegibleException}.
	 */
	public static byte[] descifrar(byte[] clave, byte[] cifrado, Aad aad) {
		if (cifrado == null || cifrado.length < BYTES_NONCE + BITS_ETIQUETA / 8) {
			throw new SecretoIlegibleException("El secreto cifrado está incompleto");
		}
		try {
			Cipher cifrador = Cipher.getInstance("AES/GCM/NoPadding");
			cifrador.init(Cipher.DECRYPT_MODE, new SecretKeySpec(clave, "AES"),
					new GCMParameterSpec(BITS_ETIQUETA, cifrado, 0, BYTES_NONCE));
			cifrador.updateAAD(aad.bytes());
			return cifrador.doFinal(cifrado, BYTES_NONCE, cifrado.length - BYTES_NONCE);
		}
		catch (AEADBadTagException e) {
			throw new SecretoIlegibleException("El secreto no se descifra con esta clave (o no es de este conector)");
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("AES-GCM no disponible", e);
		}
	}

	/** Excepción sin el secreto ni la clave en el mensaje. */
	public static final class SecretoIlegibleException extends RuntimeException {

		public SecretoIlegibleException(String mensaje) {
			super(mensaje);
		}
	}
}
