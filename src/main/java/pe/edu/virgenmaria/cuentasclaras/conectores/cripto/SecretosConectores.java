package pe.edu.virgenmaria.cuentasclaras.conectores.cripto;

import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.conectores.config.PropiedadesConectores;
import pe.edu.virgenmaria.cuentasclaras.conectores.cripto.CifradoAesGcm.Aad;
import pe.edu.virgenmaria.cuentasclaras.conectores.cripto.CifradoAesGcm.SecretoIlegibleException;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.ConectorColegio;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Sprint 8, tanda 2 (sección 3.6, decisión 118): cifra y descifra los secretos de los conectores con la clave maestra
 * ({@code CC_CLAVE_CONECTORES}, fuera de la base) y AES-256-GCM. El AAD es {@code conector|{colegio}|{tipo}|{clave_publica}}:
 * un secreto copiado a la fila de otro colegio (o de otro conector) no se descifra.
 * <ul>
 *   <li>Un conector guarda uno o más secretos con nombre (el token del OSE; la llave secreta y el secreto de los avisos
 *       de la pasarela), juntos en un JSON que se cifra entero.</li>
 *   <li>En la base quedan el cifrado, la versión de la clave y la pista (los 4 últimos caracteres del secreto
 *       principal). Nadie vuelve a ver un secreto: el formulario lo pide entero en cada cambio.</li>
 *   <li>{@link #descifrar} solo lo llama el paquete {@code conectores} (regla ArchUnit {@code soloConectoresDescifra}).
 *       Ningún mensaje de error ni log lleva el secreto ni la clave.</li>
 * </ul>
 */
@Component
public class SecretosConectores {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final byte[] clave;

	private final byte[] claveAnterior;

	private final int version;

	public SecretosConectores(PropiedadesConectores propiedades) {
		this.version = propiedades.claveVersion();
		this.clave = propiedades.conClave() ? leer(propiedades.clave(), "CC_CLAVE_CONECTORES") : null;
		this.claveAnterior = propiedades.claveAnterior().isEmpty() ? null
				: leer(propiedades.claveAnterior(), "CC_CLAVE_CONECTORES_ANTERIOR");
	}

	private static byte[] leer(String base64, String variable) {
		try {
			return CifradoAesGcm.leerClave(base64);
		}
		catch (IllegalArgumentException e) {
			throw new IllegalStateException(variable + ": " + e.getMessage() + ". Genérala con «openssl rand -base64 32» "
					+ "y guárdala en el gestor de secretos.");
		}
	}

	/** Si hay clave maestra (sin ella no se guarda ningún secreto nuevo). */
	public boolean disponible() {
		return clave != null;
	}

	/** La versión con que se cifra ahora. */
	public int version() {
		return version;
	}

	/** Si esta instalación descifra los secretos de esa versión (la vigente o, durante una rotación, la anterior). */
	public boolean descifraVersion(int versionSecreto) {
		return (versionSecreto == version && clave != null) || (versionSecreto == version - 1 && claveAnterior != null);
	}

	/** Cifra los secretos de un conector nuevo (nombre → valor) con la clave vigente. */
	public byte[] cifrar(Map<String, String> secretos, long colegioId, String tipo, String clavePublica) {
		if (clave == null) {
			throw new ReglaNegocioException("Falta la clave de los secretos de los conectores (CC_CLAVE_CONECTORES): sin ella "
					+ "no se guarda ningún token ni llave. Pide al responsable técnico que la configure.");
		}
		Objects.requireNonNull(secretos, "secretos");
		byte[] texto = JSON.writeValueAsString(new LinkedHashMap<>(secretos)).getBytes(StandardCharsets.UTF_8);
		try {
			return CifradoAesGcm.cifrar(clave, texto, new Aad(colegioId, tipo, clavePublica));
		}
		finally {
			Arrays.fill(texto, (byte) 0);
		}
	}

	/**
	 * Sprint 11, tanda 2 (sección 3.9): cifra la clave de una exportación con la clave vigente (AAD
	 * {@code exportacion|{colegio}|{id}}). Se guarda hasta que el titular la ve.
	 */
	public byte[] cifrarClaveExportacion(String claveZip, long colegioId, long exportacionId) {
		if (clave == null) {
			throw new ReglaNegocioException("Falta la clave maestra (CC_CLAVE_CONECTORES): sin ella no se guarda la clave "
					+ "de la exportación.");
		}
		return CifradoAesGcm.cifrar(clave, claveZip.getBytes(StandardCharsets.UTF_8),
				new CifradoAesGcm.Aad(colegioId, "exportacion", Long.toString(exportacionId)));
	}

	/** Descifra la clave de una exportación (una vez: después la columna queda en NULL). */
	public String descifrarClaveExportacion(byte[] cifrada, int versionClave, long colegioId, long exportacionId) {
		byte[] texto = CifradoAesGcm.descifrar(llaveDe(versionClave), cifrada,
				new CifradoAesGcm.Aad(colegioId, "exportacion", Long.toString(exportacionId)));
		try {
			return new String(texto, StandardCharsets.UTF_8);
		}
		finally {
			Arrays.fill(texto, (byte) 0);
		}
	}

	/** Los secretos de un conector (nombre → valor). Solo desde el paquete {@code conectores}. */
	public Map<String, String> descifrar(ConectorColegio conector) {
		if (!conector.conSecreto()) {
			return Map.of();
		}
		byte[] llave = llaveDe(conector.getClaveVersion());
		byte[] texto = CifradoAesGcm.descifrar(llave, conector.getSecretoCifrado(),
				new Aad(conector.getColegioId(), conector.getTipo().name(), conector.getClavePublica()));
		try {
			return JSON.readValue(texto, new TypeReference<LinkedHashMap<String, String>>() {
			});
		}
		catch (JacksonException e) {
			throw new SecretoIlegibleException("El secreto descifrado no tiene el formato esperado");
		}
		finally {
			Arrays.fill(texto, (byte) 0);
		}
	}

	private byte[] llaveDe(Integer versionSecreto) {
		if (versionSecreto != null && versionSecreto == version && clave != null) {
			return clave;
		}
		if (versionSecreto != null && versionSecreto == version - 1 && claveAnterior != null) {
			return claveAnterior;
		}
		throw new SecretoIlegibleException("El secreto se cifró con la versión " + versionSecreto + " de la clave y esta "
				+ "instalación tiene la versión " + version + (clave == null ? " (sin clave)" : "")
				+ ": falta CC_CLAVE_CONECTORES_ANTERIOR o la rotación (recifrar-conectores).");
	}

	/** Los 4 últimos caracteres del secreto principal (lo único que vuelve a la pantalla). */
	public static String pista(String secreto) {
		if (secreto == null || secreto.isBlank()) {
			return null;
		}
		String limpio = secreto.strip();
		return limpio.length() <= 4 ? "****".substring(limpio.length()) + limpio : limpio.substring(limpio.length() - 4);
	}
}
