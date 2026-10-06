package pe.edu.virgenmaria.cuentasclaras.conciliacion.dto;

import pe.edu.virgenmaria.cuentasclaras.conciliacion.formato.ErrorExtracto;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Revisión de un extracto (paso 2 de 3). Vive SOLO en la sesión de quien lo subió y no guarda nada en la base: al
 * registrar, el servicio vuelve a leer el archivo y compara la {@code huella}. No muestra el saldo final: lo escribirá a
 * ciegas quien confirma, desde su app del banco.
 *
 * @param continuidad  «Continúa al extracto del 04/10: correcto» o «Primer extracto de la cuenta»
 * @param repetidas    movimientos de días ya cargados (idénticos a lo guardado: se omiten)
 * @param problema     si no se puede registrar (no trae días nuevos, no continúa...), por qué
 */
public record VistaPreviaExtracto(UUID token, Long colegioId, Long usuarioId, String archivoNombre, String sha256,
		int bytes, byte[] contenido, Long cuentaId, String cuenta, String formato, LocalDate desde, LocalDate hasta,
		int movimientos, int abonos, int cargos, int repetidas, String continuidad, List<ErrorExtracto> errores,
		List<PropuestaVista> propuestas, int exactas, int sugeridas, int sinPareja, String huella, String problema)
		implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	public boolean conErrores() {
		return !errores.isEmpty();
	}

	public boolean registrable() {
		return errores.isEmpty() && problema == null && movimientos > 0;
	}

	public String shaCorto() {
		return sha256 == null ? "" : sha256.substring(0, Math.min(12, sha256.length()));
	}
}
