package pe.edu.virgenmaria.cuentasclaras.alumnos.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.ApoderadoRepository;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.DatosSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.ManejadorSolicitud;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Cambio de celular o correo de un apoderado aprobado por otra persona (auditoría A4). Sprint 5: el apoderado guarda la
 * solicitud que lo aprobó ({@code contacto_solicitud_id}) y la mensajería avisa al contacto ANTERIOR
 * ({@link ContactoCambiado}).
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class ManejadorContactoApoderado implements ManejadorSolicitud {

	private final ApoderadoRepository apoderados;

	private final RegistroAlumnos registro;

	private final UsuarioRepository usuarios;

	public ManejadorContactoApoderado(ApoderadoRepository apoderados, RegistroAlumnos registro,
			UsuarioRepository usuarios) {
		this.apoderados = apoderados;
		this.registro = registro;
		this.usuarios = usuarios;
	}

	/**
	 * S5-M1: la bandeja avisa a quien aprueba si el celular o el correo pedidos son (normalizados) de alguien del personal:
	 * aprobarlo hace que ESE contacto reciba los avisos de pago y el enlace del portal de la familia.
	 */
	@Override
	public String advertencia(SolicitudCambio solicitud) {
		Map<String, String> pedido = DatosSolicitud.leer(solicitud.getDatos());
		List<String> coinciden = new ArrayList<>();
		usuarios.quienTieneElContacto(pedido.get("telefono")).ifPresent(u -> coinciden.add("el celular es de "
				+ u.getNombreCompleto() + " (personal)"));
		usuarios.quienTieneElContacto(pedido.get("correo")).ifPresent(u -> coinciden.add("el correo es de "
				+ u.getNombreCompleto() + " (personal)"));
		if (coinciden.isEmpty()) {
			return null;
		}
		return "ATENCIÓN: " + String.join(" y ", coinciden) + ". Si lo apruebas, esa persona recibirá los avisos de pago "
				+ "y el enlace del portal de esta familia. Apruébalo solo si confirmaste con la familia que es suyo.";
	}

	@Override
	public TipoSolicitud tipo() {
		return TipoSolicitud.CAMBIO_CONTACTO_APODERADO;
	}

	@Override
	public void aplicar(SolicitudCambio solicitud, String aprobador) {
		Apoderado apoderado = apoderados.findById(solicitud.getEntidadId())
				.orElseThrow(() -> new ReglaNegocioException("El apoderado de la solicitud no existe."));
		registro.cambiarContactoAprobado(apoderado, solicitud.getId(), DatosSolicitud.leer(solicitud.getDatos()), solicitud.getMotivo(),
				solicitud.getSolicitadoPor(), aprobador);
	}
}
