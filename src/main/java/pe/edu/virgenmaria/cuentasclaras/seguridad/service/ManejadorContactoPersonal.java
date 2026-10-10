package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.DatosSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.ManejadorSolicitud;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.MotivoCierreSesion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.SesionesFirmadas;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Aplica un CAMBIO_CONTACTO_PERSONAL aprobado por otra persona (sprint 6, tanda 2; P6). El titular nunca aprueba el suyo
 * aunque lo haya pedido Promotoría ({@link #involucrados}). Si el contacto cambió desde que se pidió, no se aplica. Guarda
 * la solicitud en {@code contacto_solicitud_id} (con {@code saveAndFlush}: trg_usuario_contacto la exige APROBADA, y la
 * bandeja la deja así antes de llamar aquí), avisa al contacto ANTERIOR en la misma transacción y queda resaltado.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class ManejadorContactoPersonal implements ManejadorSolicitud {

	private final UsuarioRepository usuarios;

	private final ContactoPropio propio;

	private final AuditoriaService auditoria;

	private final ApplicationEventPublisher eventos;

	private final SesionesUsuario sesiones;

	private final SesionesFirmadas sesionesFirmadas;

	public ManejadorContactoPersonal(UsuarioRepository usuarios, ContactoPropio propio, AuditoriaService auditoria,
			ApplicationEventPublisher eventos, SesionesUsuario sesiones, SesionesFirmadas sesionesFirmadas) {
		this.usuarios = usuarios;
		this.propio = propio;
		this.auditoria = auditoria;
		this.eventos = eventos;
		this.sesiones = sesiones;
		this.sesionesFirmadas = sesionesFirmadas;
	}

	@Override
	public TipoSolicitud tipo() {
		return TipoSolicitud.CAMBIO_CONTACTO_PERSONAL;
	}

	/** El titular tampoco lo aprueba (ni quienes prepararon su cuenta: la bandeja los suma). */
	@Override
	public Set<String> involucrados(SolicitudCambio solicitud) {
		return usuarios.findById(solicitud.getEntidadId()).map(u -> Set.of(u.getNombreUsuario())).orElse(Set.of());
	}

	@Override
	public List<String> detalle(SolicitudCambio solicitud) {
		Map<String, String> pedido = DatosSolicitud.leer(solicitud.getDatos());
		List<String> lineas = new ArrayList<>();
		lineas.add("Antes: " + ServicioContactoPersonal.visible(pedido.get("telefonoAnterior"), pedido.get("correoAnterior")));
		lineas.add("Después: " + ServicioContactoPersonal.visible(pedido.get("telefono"), pedido.get("correo")));
		return lineas;
	}

	@Override
	public String advertencia(SolicitudCambio solicitud) {
		return "Por este contacto le llegan la huella de la bitácora, el resumen diario y las alertas. Apruébalo solo si "
				+ "confirmaste en persona o por el número de siempre que el nuevo es suyo. Se avisará al contacto anterior.";
	}

	@Override
	public void aplicar(SolicitudCambio solicitud, String aprobador) {
		Usuario usuario = usuarios.bloquearPorId(solicitud.getEntidadId())
				.orElseThrow(() -> new ReglaNegocioException("La persona de la solicitud no existe."));
		if (!usuario.isActivo() || usuario.getApoderadoId() != null) {
			throw new ReglaNegocioException("La cuenta está desactivada: ya no se cambia su contacto.");
		}
		Map<String, String> pedido = DatosSolicitud.leer(solicitud.getDatos());
		String telefonoAnterior = usuario.getTelefonoWhatsapp();
		String correoAnterior = usuario.getCorreo();
		if (!ServicioContactoPersonal.vacioSiNulo(telefonoAnterior).equals(pedido.get("telefonoAnterior"))
				|| !ServicioContactoPersonal.vacioSiNulo(correoAnterior).equals(pedido.get("correoAnterior"))) {
			throw new ReglaNegocioException("El contacto de " + usuario.getNombreCompleto() + " cambió desde que se pidió: "
					+ "rechaza esta solicitud y que se pida de nuevo.");
		}
		String telefono = vacioANulo(pedido.get("telefono"));
		String correo = vacioANulo(pedido.get("correo"));
		propio.exigir(usuario, telefono, "celular");
		propio.exigir(usuario, correo, "correo");
		usuario.cambiarContactoAprobado(telefono, correo, solicitud.getId());
		usuarios.saveAndFlush(usuario);
		eventos.publishEvent(new ContactoPersonalCambiado(usuario.getId(), solicitud.getId(), telefonoAnterior,
				correoAnterior));
		auditoria.registrar(AccionAuditoria.CONTACTO_PERSONAL_CAMBIADO, "usuario", usuario.getId().toString(),
				ServicioContactoPersonal.visible(telefonoAnterior, correoAnterior),
				ServicioContactoPersonal.visible(telefono, correo), "Contacto de " + usuario.getNombreUsuario()
						+ " cambiado con la solicitud " + solicitud.getId() + ": pedida por " + solicitud.getSolicitadoPor()
						+ ", aprobada por " + aprobador + ". Se avisó al contacto anterior.");
		// Sus sesiones abiertas se cierran: si alguien más la estaba usando, tiene que volver a ingresar (sprint 7, tanda 2:
		// también las de la base, así su secreto ya no firma).
		sesionesFirmadas.cerrarDe(usuario.getId(), MotivoCierreSesion.CUENTA_CAMBIADA);
		sesiones.expirar(usuario.getId());
	}

	private static String vacioANulo(String valor) {
		return valor == null || valor.isBlank() ? null : valor;
	}
}
