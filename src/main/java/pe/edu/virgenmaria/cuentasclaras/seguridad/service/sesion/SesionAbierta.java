package pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * La sesión de la base de quien ingresó, con su secreto (sprint 7, tanda 2). Vive SOLO en la memoria del servidor (un
 * atributo de la sesión HTTP, nunca en la cookie); la base guarda solo su SHA-256. {@link #toString()} no muestra el
 * secreto.
 */
public record SesionAbierta(Long sesionId, Long usuarioId, Long colegioId, String token) implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	public SesionAbierta {
		Objects.requireNonNull(sesionId, "sesionId");
		Objects.requireNonNull(usuarioId, "usuarioId");
		Objects.requireNonNull(colegioId, "colegioId");
		if (token == null || !token.matches("[0-9a-f]{64}")) {
			throw new IllegalArgumentException("El secreto de la sesión no es válido");
		}
	}

	@Override
	public String toString() {
		return "SesionAbierta[sesionId=" + sesionId + ", usuarioId=" + usuarioId + ", colegioId=" + colegioId + "]";
	}
}
