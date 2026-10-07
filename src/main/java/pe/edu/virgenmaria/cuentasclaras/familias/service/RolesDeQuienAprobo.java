package pe.edu.virgenmaria.cuentasclaras.familias.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.util.stream.Collectors;

/**
 * A la familia se le dice el ROL de quien aprobó una anulación o un descuento («Dirección»), nunca su usuario ni su
 * nombre. Si la cuenta no existe, «el colegio».
 */
@Component
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
public class RolesDeQuienAprobo {

	private final UsuarioRepository usuarios;

	public RolesDeQuienAprobo(UsuarioRepository usuarios) {
		this.usuarios = usuarios;
	}

	public String de(String nombreUsuario) {
		if (nombreUsuario == null) {
			return "el colegio";
		}
		return usuarios.findByNombreUsuario(nombreUsuario)
				.map(u -> u.getRoles().stream().filter(r -> r != Rol.APODERADO).sorted().map(Rol::etiqueta)
						.collect(Collectors.joining(" y ")))
				.filter(s -> !s.isBlank()).orElse("el colegio");
	}
}
