package pe.edu.virgenmaria.cuentasclaras.conectores.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.AvisoPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.ConectorColegio;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.EstadoConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.TipoConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.repository.ConectorColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Sprint 11, tanda 1c (A1c-4, decisión 224): durante los 30 días siguientes a que una cuenta bancaria o una recaudación
 * entró en vigencia, quien la propuso o la aprobó no confirma la conciliación de ese colegio (los extractos, los lotes de
 * recaudación ni el cierre mensual): la confirma otra persona de Promotoría o Dirección. Así quien desvió la cuenta no
 * «confirma» que el dinero llegó. Si lo intenta, queda el evento resaltado y una alerta CRÍTICA a todas las Promotorías y
 * al correo de control (en la misma transacción, que quien llama confirma aunque rechace). En MySQL lo exigen
 * trg_extracto_bancario_estado y trg_lote_recaudacion_estado.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class CambiosRecientesConectores {

	/** Decisión 224: 30 días. */
	public static final Duration VENTANA = Duration.ofDays(30);

	private final ConectorColegioRepository conectores;

	private final AuditoriaService auditoria;

	private final ApplicationEventPublisher eventos;

	private final UsuarioRepository usuarios;

	private final Clock reloj;

	public CambiosRecientesConectores(ConectorColegioRepository conectores, AuditoriaService auditoria,
			ApplicationEventPublisher eventos, UsuarioRepository usuarios, Clock reloj) {
		this.conectores = conectores;
		this.auditoria = auditoria;
		this.eventos = eventos;
		this.usuarios = usuarios;
		this.reloj = reloj;
	}

	/** Si {@code usuario} propuso o aprobó un conector de ese tipo que entró en vigencia en los últimos 30 días. */
	@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
	public boolean cambioReciente(TipoConector tipo, String usuario) {
		LocalDateTime desde = LocalDateTime.now(reloj).minus(VENTANA);
		return conectores.findByTipoOrderByIdDesc(tipo).stream()
				.filter(c -> c.getEstado() == EstadoConector.VIGENTE || c.getEstado() == EstadoConector.REEMPLAZADO)
				.filter(c -> c.getResueltoEn() != null && !vigenteDesde(c).isBefore(desde))
				.anyMatch(c -> usuario.equals(c.getPropuestoPor()) || usuario.equals(c.getResueltoPor()));
	}

	/**
	 * Si {@code usuario} cambió la cuenta hace menos de 30 días, deja el evento y la alerta CRÍTICA y devuelve
	 * {@code true}: quien llama rechaza la confirmación (con una excepción que no revierte la transacción).
	 */
	public boolean rechazarSiCambioReciente(TipoConector tipo, String usuario, String entidad, Long entidadId, String que) {
		if (!cambioReciente(tipo, usuario)) {
			return false;
		}
		auditoria.registrar(AccionAuditoria.CONCILIACION_TRAS_CAMBIO_RECHAZADA, entidad, entidadId.toString(), null, que,
				usuario + " intentó confirmar " + que + " en los 30 días siguientes a cambiar " + tipo.descripcion()
						+ " del colegio. Se rechazó: la confirma otra persona de Promotoría o Dirección. Alerta CRÍTICA a "
						+ "todas las Promotorías y al correo de control.");
		String nombre = usuarios.findByNombreUsuario(usuario).map(Usuario::getNombreCompleto).orElse(usuario);
		eventos.publishEvent(new AvisoPlataforma(ContextoColegio.actual(), AvisoPlataforma.Tipo.CONCILIACION_BLOQUEADA,
				List.of(nombre, que), entidad, entidadId, LocalDateTime.now(reloj).withNano(0).toString(), null, false, true,
				true));
		// Sprint 11, tanda 2 (pendiente de la 1c): también los operadores, al confirmarse esta transacción.
		eventos.publishEvent(new pe.edu.virgenmaria.cuentasclaras.colegio.service.ConciliacionBloqueada(ContextoColegio.actual(),
				List.of(nombre, que), entidad, entidadId));
		return true;
	}

	private static LocalDateTime vigenteDesde(ConectorColegio c) {
		LocalDateTime espera = c.getAplicaDesde();
		return espera != null && espera.isAfter(c.getResueltoEn()) ? espera : c.getResueltoEn();
	}
}
