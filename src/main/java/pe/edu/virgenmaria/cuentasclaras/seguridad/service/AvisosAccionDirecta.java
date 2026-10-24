package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.AvisoPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.util.List;

/**
 * Sprint 11, tanda 1c (sección 3.16, A1c-8): lo que una Promotoría aplica directo (sin segunda aprobación) deja su evento
 * resaltado {@code ACCION_DIRECTA_PROMOTORIA} y avisa AL MOMENTO, en la MISMA transacción, a todas las Promotorías activas
 * (también a quien lo hizo: si le robaron la sesión, se entera) y al correo externo de control. Si no se puede avisar, no
 * se guarda. Lo usan los servicios de un paso (plan, saldo inicial, feriado, extracto, lote y cierre mensual); las
 * solicitudes directas avisan desde la bandeja.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class AvisosAccionDirecta {

	private final AuditoriaService auditoria;

	private final ApplicationEventPublisher eventos;

	private final UsuarioRepository usuarios;

	public AvisosAccionDirecta(AuditoriaService auditoria, ApplicationEventPublisher eventos, UsuarioRepository usuarios) {
		this.auditoria = auditoria;
		this.eventos = eventos;
		this.usuarios = usuarios;
	}

	/** {@code que}: lo que se aplicó, en palabras y sin datos personales («Plan de pensiones 2027 de Primaria»). */
	public void registrar(String entidad, Long entidadId, String que, boolean conCodigo) {
		String quien = usuario();
		auditoria.registrar(AccionAuditoria.ACCION_DIRECTA_PROMOTORIA, entidad, entidadId.toString(), null, que,
				"Lo hizo y lo aplicó directo " + quien + ", sin segunda aprobación, con su firma" + (conCodigo
						? " y su código por WhatsApp" : "") + ". Se avisó a todas las Promotorías y al correo de control.");
		String nombre = usuarios.findByNombreUsuario(quien).map(Usuario::getNombreCompleto).orElse(quien);
		eventos.publishEvent(new AvisoPlataforma(ContextoColegio.actual(), AvisoPlataforma.Tipo.ACCION_DIRECTA,
				List.of(nombre, que), entidad, entidadId, null, null, false, true, true));
	}

	private static String usuario() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		return autenticacion == null ? "sistema" : autenticacion.getName();
	}
}
