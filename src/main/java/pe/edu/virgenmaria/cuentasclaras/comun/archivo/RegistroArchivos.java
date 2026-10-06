package pe.edu.virgenmaria.cuentasclaras.comun.archivo;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Guarda el archivo original con su SHA-256, dentro de la transacción de quien lo registra (todo o nada). Si el mismo
 * archivo (mismo tipo y SHA-256) ya está guardado, devuelve ese: no se duplica la evidencia. Sin {@code @PreAuthorize}:
 * lo usan los servicios que ya exigieron el rol de quien sube.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class RegistroArchivos {

	private final ArchivoCargadoRepository archivos;

	public RegistroArchivos(ArchivoCargadoRepository archivos) {
		this.archivos = archivos;
	}

	public ArchivoCargado guardar(TipoArchivo tipo, String nombre, byte[] contenido) {
		String sha256 = sha256(contenido);
		return archivos.findByTipoAndSha256(tipo, sha256)
				.orElseGet(() -> archivos.save(ArchivoCargado.nuevo(tipo, nombre, sha256, contenido)));
	}

	/** SHA-256 en hexadecimal (64 caracteres en minúsculas). */
	public static String sha256(byte[] contenido) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 no disponible", e);
		}
	}

	/** SHA-256 de un texto (UTF-8), para las huellas de las revisiones. */
	public static String sha256(String texto) {
		return sha256(texto.getBytes(StandardCharsets.UTF_8));
	}
}
