package pe.edu.virgenmaria.cuentasclaras.recaudacion.dto;

import pe.edu.virgenmaria.cuentasclaras.recaudacion.formato.ErrorFila;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Revisión de un archivo de recaudación (paso 2 de 3). Vive SOLO en la sesión de quien lo subió y no guarda nada en la
 * base: al registrar, el servicio vuelve a leer el archivo y compara la {@code huella} (si algo cambió, pide revisar de
 * nuevo). No muestra el total en grande: lo escribirá a ciegas quien confirma.
 *
 * @param yaCargado si el mismo archivo ya está cargado en un lote vigente, el aviso (no se puede registrar)
 */
public record VistaPreviaRecaudacion(UUID token, Long colegioId, Long usuarioId, String archivoNombre, String sha256,
		int bytes, byte[] contenido, String banco, String formato, LocalDate desde, LocalDate hasta, int lineas,
		List<LineaPrevia> detalle, List<ErrorFila> errores, int aplicaran, int excepciones, int yaRegistradas,
		String huella, String yaCargado) implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	public boolean conErrores() {
		return !errores.isEmpty();
	}

	public boolean registrable() {
		return errores.isEmpty() && yaCargado == null && lineas > 0;
	}

	/** Los primeros 12 caracteres del SHA-256 (para reconocerlo). */
	public String shaCorto() {
		return sha256 == null ? "" : sha256.substring(0, Math.min(12, sha256.length()));
	}
}
