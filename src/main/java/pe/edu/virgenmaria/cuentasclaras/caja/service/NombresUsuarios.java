package pe.edu.virgenmaria.cuentasclaras.caja.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

/** Nombre completo de un usuario («Lucía Ramos») a partir del que guarda la base («caja»), para comprobantes y listas. */
@Component
@Transactional(readOnly = true)
public class NombresUsuarios {

	private final UsuarioRepository usuarios;

	public NombresUsuarios(UsuarioRepository usuarios) {
		this.usuarios = usuarios;
	}

	/** El nombre completo o, si la cuenta no existe en este colegio, el nombre de usuario. */
	public String de(String nombreUsuario) {
		if (nombreUsuario == null) {
			return null;
		}
		for (pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema actor
				: pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema.values()) {
			if (actor.usuario().equals(nombreUsuario)) {
				return switch (actor) {
					case PASARELA -> "Pago en línea (automático)";
					case RECAUDACION -> "Recaudación bancaria (automático)";
					case CONCILIACION -> "Conciliación (automático)";
					case OSE -> "Envío al OSE (automático)";
					case MENSAJERIA -> "Mensajería (automático)";
					case AUDITORIA -> "Huella de la bitácora (automático)";
					case MATRICULA -> "Matrícula del año siguiente (automático)";
					case PANEL -> "Panel del colegio (automático)";
				};
			}
		}
		return usuarios.findByNombreUsuario(nombreUsuario).map(Usuario::getNombreCompleto).orElse(nombreUsuario);
	}
}
