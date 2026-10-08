package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.ConsultaAuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.EventoRevision;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Avisos de la página de inicio.
 * <ul>
 *   <li>A cualquier usuario: si otra persona restableció su clave en los últimos 30 días.</li>
 *   <li>A Promotoría: restablecimientos de clave, altas y cambios de roles de los últimos 7 días
 *       (vigilancia contra cuentas fantasma y suplantaciones, hasta que en el sprint 4 la clave temporal
 *       llegue directo al titular).</li>
 * </ul>
 */
@Service
public class ServicioInicio {

	static final Duration VIGENCIA_AVISO = Duration.ofDays(30);

	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm");

	/** Una fila de la tarjeta "Para revisar". */
	public record ElementoRevision(LocalDateTime cuando, String quien, String accion, String aQuien, String cambio) {
	}

	private final UsuarioRepository usuarios;

	private final ConsultaAuditoriaService consulta;

	private final Clock reloj;

	public ServicioInicio(UsuarioRepository usuarios, ConsultaAuditoriaService consulta, Clock reloj) {
		this.usuarios = usuarios;
		this.consulta = consulta;
		this.reloj = reloj;
	}

	/** Aviso para el titular si otra persona restableció su clave hace poco; {@code null} si no hay. */
	@Transactional(readOnly = true)
	public String avisoClaveRestablecida(Long usuarioId) {
		Usuario usuario = usuarios.findById(usuarioId).orElse(null);
		if (usuario == null || usuario.getClaveRestablecidaEn() == null
				|| usuario.getClaveRestablecidaEn().isBefore(LocalDateTime.now(reloj).minus(VIGENCIA_AVISO))) {
			return null;
		}
		String quien = usuarios.findByNombreUsuario(usuario.getClaveRestablecidaPor())
				.map(Usuario::getNombreCompleto)
				.orElse(usuario.getClaveRestablecidaPor());
		return "Tu clave fue restablecida por " + quien + " el " + FECHA.format(usuario.getClaveRestablecidaEn())
				+ ". Si no lo pediste, avisa a Promotoría.";
	}

	/** Solo Promotoría (lo exige {@code ConsultaAuditoriaService.paraRevisar}). */
	@Transactional(readOnly = true)
	public List<ElementoRevision> paraRevisar() {
		List<EventoRevision> eventos = consulta.paraRevisar();
		return eventos.stream()
				.map(e -> new ElementoRevision(e.ocurridoEn(), e.quien(), e.accion(), nombreDe(e.usuarioId()), e.cambio()))
				.toList();
	}

	private String nombreDe(Long usuarioId) {
		if (usuarioId == null) {
			return "—";
		}
		return usuarios.findById(usuarioId)
				.map(u -> u.getNombreCompleto() + " (" + u.getNombreUsuario() + ")")
				.orElse("usuario " + usuarioId);
	}
}
