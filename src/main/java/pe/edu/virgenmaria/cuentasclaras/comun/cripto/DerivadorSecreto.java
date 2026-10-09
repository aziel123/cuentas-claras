package pe.edu.virgenmaria.cuentasclaras.comun.cripto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Deriva secretos con HMAC-SHA256 y la clave del servidor (la misma {@code AUDITORIA_CLAVE_HMAC} de la bitácora, que no
 * está en la base), con separación de dominio: cada uso antepone su propio prefijo.
 * <p>
 * Sprint 7, tanda 2 (H4, E12): la semilla EFECTIVA del muestreo es
 * {@code HMAC(clave, "muestreo|" + ámbito + "|" + fecha + "|" + aleatorio_guardado)} truncada a 64 bits. Quien lee
 * {@code semilla_muestreo} con la clave de {@code cc_app} no puede reproducir la muestra sin la clave del servidor.
 */
@Component
public class DerivadorSecreto {

	private static final String ALGORITMO = "HmacSHA256";

	private final SecretKeySpec clave;

	public DerivadorSecreto(@Value("${cuentasclaras.auditoria.clave-hmac}") String clave) {
		if (clave == null || clave.isBlank()) {
			throw new IllegalStateException("Falta la clave del servidor (cuentasclaras.auditoria.clave-hmac)");
		}
		this.clave = new SecretKeySpec(clave.getBytes(StandardCharsets.UTF_8), ALGORITMO);
	}

	/** La semilla efectiva del muestreo de ese ámbito y fecha, a partir del aleatorio guardado en la base. */
	public long semilla(String ambito, LocalDate fecha, long aleatorio) {
		Objects.requireNonNull(ambito, "ambito");
		Objects.requireNonNull(fecha, "fecha");
		byte[] huella = hmac("muestreo|" + ambito + "|" + fecha + "|" + aleatorio);
		return ByteBuffer.wrap(huella, 0, Long.BYTES).getLong();
	}

	private byte[] hmac(String mensaje) {
		try {
			Mac mac = Mac.getInstance(ALGORITMO);
			mac.init(clave);
			return mac.doFinal(mensaje.getBytes(StandardCharsets.UTF_8));
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("No se pudo derivar el secreto", e);
		}
	}
}
