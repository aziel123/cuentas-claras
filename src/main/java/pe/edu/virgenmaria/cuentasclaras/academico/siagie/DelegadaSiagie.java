package pe.edu.virgenmaria.cuentasclaras.academico.siagie;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.siagie.repository.DelegacionSiagieRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Sprint 10, tanda 3 (M12): ¿quien está en sesión puede exportar las calificaciones para el SIAGIE? Dirección, o la persona
 * de Administración con una delegación vigente hoy (y que sigue sin Caja ni Docente). La misma regla de
 * trg_exportacion_calificaciones_nace.
 */
@Component
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
public class DelegadaSiagie {

	private final DelegacionSiagieRepository delegaciones;

	private final UsuarioRepository usuarios;

	private final Clock reloj;

	public DelegadaSiagie(DelegacionSiagieRepository delegaciones, UsuarioRepository usuarios, Clock reloj) {
		this.delegaciones = delegaciones;
		this.usuarios = usuarios;
		this.reloj = reloj;
	}

	public boolean puedeExportar(UsuarioAutenticado persona) {
		if (persona.roles().contains(Rol.DIRECTOR)) {
			return true;
		}
		LocalDate hoy = LocalDate.now(reloj);
		return usuarios.findById(persona.usuarioId()).filter(DelegacionesSiagie::puedeRecibir).isPresent()
				&& delegaciones.findByUsuarioIdAndHastaGreaterThanEqualOrderByIdDesc(persona.usuarioId(), hoy).stream()
						.anyMatch(d -> d.vigenteEl(hoy));
	}

	static UsuarioAutenticado persona() {
		if (SecurityContextHolder.getContext().getAuthentication() != null
				&& SecurityContextHolder.getContext().getAuthentication().getPrincipal() instanceof UsuarioAutenticado u) {
			return u;
		}
		throw new AccessDeniedException("Se requiere una persona en sesión");
	}
}
