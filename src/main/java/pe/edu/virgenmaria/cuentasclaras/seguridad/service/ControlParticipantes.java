package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Doble control con una segunda cuenta (auditoría antifraude, A5): quien creó la cuenta de un autor o le restableció la
 * clave en los últimos 30 días podría estar usándola. Por eso también es participante y no puede aprobar lo que ese
 * autor hizo. No exige rol: lo usan servicios que ya lo exigieron.
 */
@Component
public class ControlParticipantes {

	public static final Duration VENTANA = Duration.ofDays(30);

	private final UsuarioRepository usuarios;

	private final Clock reloj;

	public ControlParticipantes(UsuarioRepository usuarios, Clock reloj) {
		this.usuarios = usuarios;
		this.reloj = reloj;
	}

	/** Los autores más quienes crearon sus cuentas o les restablecieron la clave en los últimos 30 días. */
	@Transactional(readOnly = true)
	public Set<String> ampliar(Collection<String> autores) {
		LocalDateTime desde = LocalDateTime.now(reloj).minus(VENTANA);
		Set<String> participantes = new LinkedHashSet<>(autores);
		for (String autor : autores) {
			usuarios.findByNombreUsuario(autor).ifPresent(cuenta -> agregarPadrinos(cuenta, desde, participantes));
		}
		participantes.remove(null);
		return participantes;
	}

	private static void agregarPadrinos(Usuario cuenta, LocalDateTime desde, Set<String> participantes) {
		if (cuenta.getCreadoEn() != null && !cuenta.getCreadoEn().isBefore(desde)) {
			participantes.add(cuenta.getCreadoPor());
		}
		if (cuenta.getClaveRestablecidaEn() != null && !cuenta.getClaveRestablecidaEn().isBefore(desde)) {
			participantes.add(cuenta.getClaveRestablecidaPor());
		}
	}
}
