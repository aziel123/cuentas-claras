package pe.edu.virgenmaria.cuentasclaras.alumnos.importacion;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Revisión de una importación, guardada SOLO en la sesión de quien subió el archivo (nunca en la base ni en disco).
 * El archivo no se guarda: solo las filas ya leídas, su huella SHA-256 y el plan.
 *
 * @param importadoAntesEn si el mismo archivo (mismo SHA-256) ya se importó, cuándo; si no, null
 */
public record VistaPreviaImportacion(UUID token, Long colegioId, Long usuarioId, Long anioId, int anio,
		String archivoNombre, String sha256, int bytes, PlanImportacion plan, List<String> avisos,
		LocalDateTime importadoAntesEn) implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	public ResumenImportacion resumen() {
		return plan.resumen();
	}

	public List<FilaImportacion> filas() {
		return plan.filas().stream().map(FilaPlan::fila).toList();
	}

	public List<ErrorFila> errores() {
		return plan.filas().stream().flatMap(f -> f.errores().stream()).toList();
	}

	public List<FilaPlan> conCambios() {
		return plan.filas().stream().filter(f -> f.clasificacion() == Clasificacion.ACTUALIZA).toList();
	}

	public List<FilaPlan> nuevos() {
		return plan.filas().stream().filter(f -> f.clasificacion() == Clasificacion.NUEVO).toList();
	}

	public List<FilaImportacion> conAdvertencias() {
		return filas().stream().filter(f -> !f.advertencias().isEmpty()).toList();
	}

	/** Los primeros caracteres de la huella, para mostrar. */
	public String sha256Corto() {
		return sha256.substring(0, 12);
	}
}
