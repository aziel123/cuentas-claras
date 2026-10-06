package pe.edu.virgenmaria.cuentasclaras.alumnos.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.ApoderadoRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

/**
 * El apoderado de la cuenta en línea que está en sesión (sprint 4). Su familia sale SIEMPRE de aquí (del enlace de su
 * cuenta con su registro de apoderado), nunca de un parámetro de la URL: así no puede ver ni pagar lo de otra familia.
 * Si la cuenta no está enlazada o el apoderado se desactivó, responde 404.
 */
@Component
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
public class SesionApoderado {

	private final ApoderadoRepository apoderados;

	public SesionApoderado(ApoderadoRepository apoderados) {
		this.apoderados = apoderados;
	}

	public Apoderado apoderado() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		Long apoderadoId = autenticacion != null && autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario
				? usuario.apoderadoId() : null;
		if (apoderadoId == null) {
			throw new RecursoNoEncontradoException("Tu cuenta no está enlazada a una familia");
		}
		return apoderados.findById(apoderadoId).filter(Apoderado::isActivo)
				.orElseThrow(() -> new RecursoNoEncontradoException("Tu cuenta no está enlazada a una familia"));
	}

	public Long familiaId() {
		return apoderado().getFamilia().getId();
	}
}
