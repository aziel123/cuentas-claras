package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Sprint 11, tanda 1c (A1c-5): el puerto con el que la seguridad lee un código enviado (lo implementa la mensajería, que
 * es la dueña del mensaje): de quién es, la SHA-256 del código, la huella de la acción y cuándo salió. Nunca el código.
 */
public interface CodigosEnviados {

	/** Lo que se sabe de un mensaje de código del colegio en contexto. */
	record CodigoEnviado(Long mensajeId, Long usuarioId, boolean contacto, String codigoSha256, String huella,
			LocalDateTime creadoEn, String clave) {
	}

	Optional<CodigoEnviado> buscar(Long mensajeId);
}
