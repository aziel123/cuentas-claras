package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

import pe.edu.virgenmaria.cuentasclaras.seguridad.model.PropositoEnlace;

import java.util.Objects;

/**
 * Sprint 5: alguien dio o restableció un acceso. Lo escucha la mensajería (en la MISMA transacción) y crea el mensaje
 * de activación al contacto REGISTRADO del titular, sin token: el enlace se genera recién al enviarlo, dentro del
 * proceso de envío ({@code sistema.mensajeria}). Quien da el acceso nunca ve el enlace.
 *
 * @param apoderadoId el apoderado de la cuenta ({@code null} para el personal)
 */
public record EnvioEnlaceSolicitado(Long usuarioId, PropositoEnlace proposito, Long apoderadoId) {

	public EnvioEnlaceSolicitado {
		Objects.requireNonNull(usuarioId, "usuarioId");
		Objects.requireNonNull(proposito, "proposito");
	}
}
